"""Unit and API tests for parallel E5 || JEV goal matching without topic-bank.json."""
from __future__ import annotations

import asyncio
import json
from pathlib import Path
from types import SimpleNamespace

import numpy as np
import pytest

from AI.src.models.screen_activity.goal_match import (
    DEFAULT_MATCH_CONFIG,
    decide_goal_match,
    match_goal_async,
)
from AI.src.models.screen_activity.jev_goal_matcher import (
    JevGoalMatchResult,
    TypeSafeJevGoalMatcher,
    build_goal_match_questions,
    build_goal_match_state,
)
from AI.src.preprocessing.models import ContentSource, ExtractionStatus

LONG_BODY = (
    "JWT 서명을 검증하고 토큰의 만료 시간을 확인합니다. 서버는 공개 키로 서명을 확인한 뒤 "
    "발급자와 대상 클레임을 비교하고, 만료된 토큰은 거부하여 다시 로그인하도록 안내합니다."
)


def _goal(goal_id: str, text: str, *, main_topic: str = "", core_topics: tuple[str, ...] = ()):
    profile = (
        SimpleNamespace(main_topic=main_topic, core_topics=core_topics, supporting_topics=(), expected_activities=())
        if main_topic or core_topics
        else None
    )
    return SimpleNamespace(goal_id=goal_id, text=text, profile=profile)


def _content(
    *,
    source: ContentSource = ContentSource.CHROME_DOM,
    extraction: ExtractionStatus = ExtractionStatus.SUCCESS,
    text: str = LONG_BODY,
    page_title: str = "JWT 인증",
    window_title: str = "",
    app_name: str = "Google Chrome",
):
    return SimpleNamespace(
        content_source=source,
        extraction_status=extraction,
        text=text,
        page_title=page_title,
        window_title=window_title,
        app_name=app_name,
        ocr=None,
    )


class FakeEncoder:
    def __init__(self, similarities: dict[str, float]):
        self.similarities = similarities
        self.calls: list[tuple[str, tuple[str, ...]]] = []

    def embed(self, texts, *, role="passage"):
        items = tuple(texts)
        self.calls.append((role, items))
        if role == "query":
            out = {}
            for text in items:
                sim = self.similarities.get(text, 0.4)
                out[text] = np.array([sim, np.sqrt(max(0.0, 1.0 - sim * sim)), 0.0], dtype=np.float32)
            return out
        return {text: np.array([1.0, 0.0, 0.0], dtype=np.float32) for text in items}


class FakeClassifier:
    def __init__(self, similarities: dict[str, float], *, activity_label: str = "STUDY"):
        self.encoder = FakeEncoder(similarities)
        self.activity_label = activity_label

    def predict(self, payloads):
        if self.activity_label == "STUDY":
            scores = {"STUDY": 0.9, "OFF_TASK": 0.05, "UNCERTAIN": 0.05}
        elif self.activity_label == "OFF_TASK":
            scores = {"STUDY": 0.05, "OFF_TASK": 0.9, "UNCERTAIN": 0.05}
        else:
            scores = {"STUDY": 0.4, "OFF_TASK": 0.35, "UNCERTAIN": 0.25}
        return [{"label": self.activity_label, "scores": scores, "decisionSource": "model"} for _ in payloads]


class FakeJevJudge:
    def __init__(self, result: JevGoalMatchResult | Exception, *, delay: float = 0.0):
        self.result = result
        self.delay = delay
        self.calls: list[dict] = []

    async def judge(self, *, title: str, body: str, content_source: str, goals):
        self.calls.append({"title": title, "body": body, "content_source": content_source, "goals": list(goals)})
        if self.delay > 0:
            await asyncio.sleep(self.delay)
        if isinstance(self.result, Exception):
            raise self.result
        return self.result

    async def aclose(self) -> None:
        return None


def test_decide_goal_match_veto_blocks_high_e5_domain_false_positive():
    goals = [_goal("g1", "React Query 캐시 무효화")]
    # E5 is very high due to shared React domain, but JEV rejects the subtopic.
    e5_scores = {"g1": 0.93}
    jev_result = JevGoalMatchResult(goal_scores={"g1": 0.20}, different_study_topic=0.88)
    goal_id, status = decide_goal_match(
        e5_scores,
        jev_result,
        goals,
        metadata_only=False,
        extraction_status=ExtractionStatus.SUCCESS,
        is_study=True,
    )
    assert (goal_id, status) == (None, "FAR")


def test_decide_goal_match_weighted_sum_selects_best_goal():
    goals = [_goal("g1", "JWT 인증 구현"), _goal("g2", "OAuth2 토큰 갱신")]
    e5_scores = {"g1": 0.85, "g2": 0.86}
    jev_result = JevGoalMatchResult(goal_scores={"g1": 0.90, "g2": 0.45}, different_study_topic=0.10)
    goal_id, status = decide_goal_match(
        e5_scores,
        jev_result,
        goals,
        metadata_only=False,
        extraction_status=ExtractionStatus.SUCCESS,
        is_study=True,
        config=DEFAULT_MATCH_CONFIG,
    )
    assert (goal_id, status) == ("g1", "MATCHED")


def test_match_goal_async_runs_e5_and_jev_in_parallel_and_skips_short_body():
    goals = [_goal("g1", "JWT 인증 구현")]
    classifier = FakeClassifier({"JWT 인증 구현": 0.88})
    jev = FakeJevJudge(JevGoalMatchResult(goal_scores={"g1": 0.85}, different_study_topic=0.1))

    # Short body skips both E5 and JEV.
    short_content = _content(text="짧은 본문", page_title="JWT")
    short_payload = {"contentSource": "CHROME_DOM", "metadata": {"pageTitle": "JWT"}, "contentPayload": {"text": "짧은 본문"}}
    res_short = asyncio.run(match_goal_async(classifier, short_content, short_payload, goals, jev_matcher=jev))
    assert res_short == (None, "NOT_ENOUGH")
    assert len(jev.calls) == 0
    assert len(classifier.encoder.calls) == 0

    # Valid body runs both E5 and JEV.
    content = _content()
    payload = {"contentSource": "CHROME_DOM", "metadata": {"pageTitle": "JWT 인증"}, "contentPayload": {"text": LONG_BODY}}
    res_ok = asyncio.run(match_goal_async(classifier, content, payload, goals, jev_matcher=jev))
    assert res_ok == ("g1", "MATCHED")
    assert len(jev.calls) == 1
    assert len(classifier.encoder.calls) > 0


def test_match_goal_async_falls_back_to_not_enough_when_jev_fails():
    goals = [_goal("g1", "JWT 인증 구현")]
    classifier = FakeClassifier({"JWT 인증 구현": 0.95})
    jev = FakeJevJudge(RuntimeError("timeout"))
    content = _content()
    payload = {"contentSource": "CHROME_DOM", "metadata": {"pageTitle": "JWT 인증"}, "contentPayload": {"text": LONG_BODY}}

    res = asyncio.run(match_goal_async(classifier, content, payload, goals, jev_matcher=jev))
    assert res == (None, "NOT_ENOUGH")


def test_typesafe_jev_matcher_retries_once_on_timeout_then_succeeds():
    class FlakyClient:
        def __init__(self):
            self.attempts = 0

        async def system_one(self, *, state, questions):
            self.attempts += 1
            if self.attempts == 1:
                raise asyncio.TimeoutError("first timeout")
            return SimpleNamespace(
                answers={
                    "match_g1": SimpleNamespace(noul=0.88),
                    "is_different_study_topic": SimpleNamespace(noul=0.12),
                }
            )

        async def aclose(self):
            return None

    from AI.src.config import Settings

    matcher = TypeSafeJevGoalMatcher(Settings(), timeout_seconds=1.0, max_retries=1)
    matcher._client = FlakyClient()
    goals = [_goal("g1", "JWT 인증 구현", main_topic="JWT", core_topics=("서명 검증",))]

    result = asyncio.run(matcher.judge(title="JWT", body=LONG_BODY, content_source="CHROME_DOM", goals=goals))
    assert matcher._client.attempts == 2
    assert result.goal_scores == {"g1": 0.88}
    assert result.different_study_topic == 0.12

    state = build_goal_match_state(title="JWT", body=LONG_BODY, content_source="CHROME_DOM", goals=goals)
    questions = build_goal_match_questions(goals)
    assert set(questions.keys()) == {"match_g1", "is_different_study_topic"}
    assert state["goals"][0]["goal_id"] == "g1"


def test_typesafe_jev_matcher_raises_after_single_retry_exhausted():
    class AlwaysTimeoutClient:
        def __init__(self):
            self.attempts = 0

        async def system_one(self, *, state, questions):
            self.attempts += 1
            raise asyncio.TimeoutError("always timeout")

        async def aclose(self):
            return None

    from AI.src.config import Settings
    from AI.src.models.errors import ModelUnavailableError

    matcher = TypeSafeJevGoalMatcher(Settings(), timeout_seconds=1.0, max_retries=1)
    matcher._client = AlwaysTimeoutClient()
    goals = [_goal("g1", "JWT 인증 구현")]

    with pytest.raises(ModelUnavailableError):
        asyncio.run(matcher.judge(title="JWT", body=LONG_BODY, content_source="CHROME_DOM", goals=goals))
    assert matcher._client.attempts == 2


def test_match_goal_async_executes_e5_and_jev_concurrently():
    import time

    class SlowEncoder(FakeEncoder):
        def embed(self, texts, *, role="passage"):
            time.sleep(0.12)
            return super().embed(texts, role=role)

    goals = [_goal("g1", "JWT 인증 구현")]
    classifier = FakeClassifier({"JWT 인증 구현": 0.90})
    classifier.encoder = SlowEncoder({"JWT 인증 구현": 0.90})
    jev = FakeJevJudge(JevGoalMatchResult(goal_scores={"g1": 0.88}, different_study_topic=0.1), delay=0.12)
    content = _content()
    payload = {"contentSource": "CHROME_DOM", "metadata": {"pageTitle": "JWT 인증"}, "contentPayload": {"text": LONG_BODY}}

    started = time.perf_counter()
    res = asyncio.run(match_goal_async(classifier, content, payload, goals, jev_matcher=jev))
    elapsed = time.perf_counter() - started

    assert res == ("g1", "MATCHED")
    # If serial, 2 embed calls (0.24s) + JEV (0.12s) would take >= 0.36s; parallel overlaps JEV with E5.
    assert elapsed < 0.32


def test_fastapi_analysis_endpoint_with_jev_matcher():
    from fastapi.testclient import TestClient
    from AI.src.main import create_app

    KEY = "local-test-key-only-0123456789abcdef"
    HEADERS = {"Authorization": f"Bearer {KEY}"}
    payload = json.loads((Path(__file__).parents[1] / "examples/analysis-reception.json").read_text())
    payload["request"]["contentPayload"]["text"] = LONG_BODY

    classifier = FakeClassifier({"JWT 인증 구현": 0.88})
    jev = FakeJevJudge(JevGoalMatchResult(goal_scores={"goal-demo": 0.85}, different_study_topic=0.1))

    with TestClient(
        create_app(api_key=KEY, classifier_factory=lambda: classifier, jev_goal_matcher_factory=lambda: jev)
    ) as client:
        matched = client.post("/internal/v1/analysis-runs", json=payload, headers=HEADERS)
    assert matched.status_code == 200
    assert (matched.json()["decision"], matched.json()["matchedGoalId"], matched.json()["matchStatus"]) == (
        "RELATED", "goal-demo", "MATCHED"
    )

    # JEV veto triggers FAR when study topic is different.
    jev_far = FakeJevJudge(JevGoalMatchResult(goal_scores={"goal-demo": 0.15}, different_study_topic=0.90))
    with TestClient(
        create_app(api_key=KEY, classifier_factory=lambda: classifier, jev_goal_matcher_factory=lambda: jev_far)
    ) as client:
        far = client.post("/internal/v1/analysis-runs", json=payload, headers=HEADERS)
    assert far.status_code == 200
    assert (far.json()["decision"], far.json()["matchedGoalId"], far.json()["matchStatus"]) == (
        "RELATED", None, "FAR"
    )
