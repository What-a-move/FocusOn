"""Local comparison of the existing Hybrid scorer with an expanded goal."""

import json
import logging
import os

from openai import APIError, APIStatusError, OpenAI

from AI.evaluation.analysis_runner import compare_graphless, parse_inputs
from AI.evaluation.graphless_hybrid import GraphlessConfig
from src.analysis.vector_math import normalize_vector
from src.api.schemas import GoalClarifyRequest, GoalProfile, GoalProfileDraft
from src.models.embedding_client import EmbeddingError
from src.preprocessing.validator import contains_sensitive_data
from src.prompts.goal_prompt import SYSTEM_PROMPT, profile_prompt
from src.utils.logger import log_event


EMBEDDING_MODEL = "text-embedding-3-small"
BASE_URL = "https://api.openai.com/v1"
SEMANTIC_LABELS = ("RELATED", "UNRELATED", "UNCERTAIN")
logger = logging.getLogger(__name__)


def checked_goal_text(text):
    text = text.strip()
    if not 2 <= len(text) <= 40:
        raise ValueError("목표를 2~40자로 입력해 주세요.")
    if contains_sensitive_data(text):
        raise ValueError("목표에서 민감정보가 감지됐습니다.")
    return text


def generate_profile(text, api_key, *, base_url=BASE_URL, model=None, reasoning_effort=None):
    """Use the existing Goal AI prompt on a single confirmed test goal."""
    text = checked_goal_text(text)
    request = GoalClarifyRequest.model_validate({
        "requestId": "local-goal-profile-evaluation",
        "goalText": text,
        "conversationTurn": 0,
    })
    model = model or os.environ.get("OPENAI_MODEL") or "gpt-5-mini"
    effort = reasoning_effort or os.environ.get("OPENAI_REASONING_EFFORT") or "minimal"
    with OpenAI(api_key=api_key, base_url=base_url, timeout=25, max_retries=0) as client:
        response = client.responses.parse(
            model=model, instructions=SYSTEM_PROMPT, input=profile_prompt(request),
            text_format=GoalProfileDraft, max_output_tokens=1000,
            reasoning={"effort": effort},
            store=False,
        )
    draft = response.output_parsed
    if response.status != "completed" or draft is None or draft.original_text != text:
        raise ValueError("목표 확장 응답을 확인할 수 없습니다. 다시 생성하거나 직접 입력해 주세요.")
    return GoalProfile.parse_safe({
        "mainTopic": draft.main_topic,
        "purpose": {"CONCEPT_LEARNING": "LEARNING", "PRESENTATION_OR_ASSIGNMENT": "OTHER"}.get(
            draft.purpose.value, draft.purpose.value),
        "coreTopics": draft.core_topics,
        "supportingTopics": draft.supporting_topics,
        "expectedActivities": draft.expected_activities,
    })


class ExperimentEmbeddings:
    def __init__(self, client, *, query_model=EMBEDDING_MODEL, passage_model=EMBEDDING_MODEL, dimensions=1536):
        self.client = client
        self.query_model = query_model
        self.passage_model = passage_model
        self.dimensions = dimensions
        self.fingerprint = f"{query_model}:{passage_model}:{dimensions}"
        self.calls = 0

    def embed_query(self, texts):
        return self._embed(texts, self.query_model)

    def embed(self, texts):
        return self._embed(texts, self.passage_model)

    def _embed(self, texts, model):
        output = []
        for start in range(0, len(texts), 32):
            batch = texts[start:start + 32]
            self.calls += 1
            try:
                response = self.client.embeddings.create(model=model, input=list(batch), encoding_format="float")
            except APIStatusError as exc:
                log_event(logger, "request.fail", level=logging.WARNING, error_code="EMBEDDING_HTTP_ERROR",
                          http_status=exc.status_code, model=model)
            except APIError:
                log_event(logger, "request.fail", level=logging.WARNING, error_code="EMBEDDING_UNAVAILABLE",
                          model=model)
            else:
                rows = sorted(response.data, key=lambda row: row.index)
                if response.model != model or [row.index for row in rows] != list(range(len(batch))):
                    raise EmbeddingError("EMBEDDING_RESPONSE_INVALID")
                output.extend(normalize_vector(row.embedding, self.dimensions) for row in rows)
                continue
            raise EmbeddingError("EMBEDDING_UNAVAILABLE")
        return tuple(output)


def compare_profile(text, profile, cases, client, *, config=GraphlessConfig()):
    """Hold inputs, model, and scorer settings fixed across the two runs."""
    text = checked_goal_text(text)
    goal, observations = parse_inputs(text, json.dumps({"cases": cases}, ensure_ascii=False), "CHROME_DOM")
    topics = [profile.main_topic, *profile.core_topics, *profile.supporting_topics]
    terms = [text, *topics, *(f"{profile.main_topic} {activity}" for activity in profile.expected_activities)]
    if any(contains_sensitive_data(part) for part in terms):
        raise ValueError("목표 확장 항목에서 민감정보가 감지됐습니다.")
    comparison_text = "\n".join(dict.fromkeys(terms))
    if len(comparison_text) > 2000:
        raise ValueError("목표 확장 목록을 합계 2,000자 이하로 줄여 주세요.")
    # The copied goal is scoped to this experiment. The baseline and observation IDs are identical.
    expanded = goal.model_copy(update={"text": comparison_text})
    baseline_result = compare_graphless(goal, observations, client, config=config)
    expanded_result = compare_graphless(expanded, observations, client, config=config)
    for report in (baseline_result, expanded_result):
        for row in report["rows"]:
            row.pop("evidence", None)
            for match in row["matches"]:
                match.pop("text", None)
    return baseline_result, expanded_result


def effective_label(row):
    if row["status"] == "FAILED":
        return "ANALYSIS_UNAVAILABLE"
    label = row["relevanceLabel"]
    if row["analysisPath"] == "METADATA" and label == "UNRELATED":
        return "UNCERTAIN"
    return label


def summarize(rows):
    labelled = [row for row in rows if row["expectedLabel"] in SEMANTIC_LABELS
                and effective_label(row) in SEMANTIC_LABELS and row["status"] == "COMPLETED"]
    related = [row for row in labelled if row["expectedLabel"] == "RELATED"]
    unrelated = [row for row in labelled if row["expectedLabel"] == "UNRELATED"]
    return {
        "판정 일치": f"{sum(effective_label(row) == row['expectedLabel'] for row in labelled)}/{len(labelled)}",
        "학습 화면을 무관으로 판정": f"{sum(effective_label(row) == 'UNRELATED' for row in related)}/{len(related)}",
        "무관 화면을 잡지 못함": f"{sum(effective_label(row) != 'UNRELATED' for row in unrelated)}/{len(unrelated)}",
        "분석 실패": sum(row["status"] == "FAILED" for row in rows),
        "미실행·차단": sum(row["status"] == "SKIPPED" for row in rows),
    }
