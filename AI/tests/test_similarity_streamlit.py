import json
from pathlib import Path

from AI.evaluation.similarity_methods import (
    parse_cases,
    parse_goal,
    prepare_case,
    score_method,
)


FIXTURE = Path(__file__).resolve().parents[1] / "evaluation" / "similarity-dummy-cases.json"


class FakeEmbedder:
    def __init__(self):
        self.inputs = []

    def encode(self, texts, **kwargs):
        self.inputs.extend(texts)
        return [[1.0, 0.0] if "파인튜닝" in text else [0.0, 1.0] for text in texts]


class FakeReranker:
    def __init__(self):
        self.calls = 0

    def predict(self, pairs, **kwargs):
        self.calls += 1
        return [0.8 if "파인튜닝" in passage else -0.3 for _, passage in pairs]


class ConceptEmbedder:
    def encode(self, texts, **kwargs):
        return [
            [0.0, 1.0] if "행렬" in text else [1.0, 0.0]
            for text in texts
        ]


def test_existing_dummy_json_uses_expected_labels_only_as_metadata():
    data = json.loads(FIXTURE.read_text(encoding="utf-8"))
    goal = parse_goal(json.dumps(data["goal"], ensure_ascii=False))
    cases = parse_cases(json.dumps(data["cases"], ensure_ascii=False), "CHROME_DOM")
    assert len(cases) == 2
    assert cases[0].expected_label == "RELATED"
    assert cases[1].expected_label == "UNCERTAIN"

    study = prepare_case(cases[0])
    assert study.can_score
    before = score_method("1", goal, study)
    data["cases"][0]["expectedRelevanceLabel"] = "UNRELATED"
    changed = parse_cases(json.dumps(data["cases"], ensure_ascii=False), "CHROME_DOM")
    after = score_method("1", goal, prepare_case(changed[0]))
    assert (before.score, before.relevance_label) == (after.score, after.relevance_label)


def test_vector_and_reranker_use_actual_model_interfaces_without_auto_unrelated():
    goal = parse_goal("파인튜닝 학습")
    case = parse_cases(json.dumps({
        "pageTitle": "파인튜닝 실습",
        "text": "파인튜닝 모델을 직접 실행하고 결과를 살펴보는 학습 페이지입니다.",
    }, ensure_ascii=False), "CHROME_DOM")[0]
    prepared = prepare_case(case)
    assert prepared.can_score
    embedder = FakeEmbedder()
    reranker = FakeReranker()
    cosine = score_method("3", goal, prepared, embedder=embedder)
    reranked = score_method("7", goal, prepared, embedder=embedder, reranker=reranker)
    assert cosine.status == "COMPLETED"
    assert cosine.score == 1.0
    assert cosine.relevance_label == "UNCERTAIN"
    assert reranked.status == "COMPLETED"
    assert reranked.score == 0.8
    assert reranked.relevance_label == "UNCERTAIN"
    assert reranker.calls == 1
    assert all(text.startswith(("query: ", "passage: ")) for text in embedder.inputs)


def test_private_or_failed_extraction_is_not_scored():
    goal = parse_goal("파인튜닝 학습")
    cases = parse_cases(json.dumps([
        {"text": "파인튜닝 학습 자료에 보이는 연락처는 test@example.com입니다."},
        {"text": "파인튜닝 학습 자료입니다.", "extractionStatus": "FAILED"},
    ], ensure_ascii=False), "CHROME_DOM")
    for case in cases:
        prepared = prepare_case(case)
        assert not prepared.can_score
        result = score_method("1", goal, prepared)
        assert result.status == "SKIPPED"
        assert result.relevance_label == "UNCERTAIN"


def test_expansion_and_goal_graph_use_supporting_concepts():
    goal = parse_goal(json.dumps({
        "mainTopic": "파인튜닝 학습",
        "coreTopics": ["LoRA"],
        "supportingTopics": ["행렬"],
    }, ensure_ascii=False))
    case = parse_cases(json.dumps({
        "pageTitle": "행렬 곱셈 설명",
        "text": "행렬 곱과 벡터 연산을 순서대로 연습하는 선형대수 수업입니다.",
    }, ensure_ascii=False), "CHROME_DOM")[0]
    prepared = prepare_case(case)
    assert prepared.can_score

    baseline = score_method("1", goal, prepared)
    expanded = score_method("2", goal, prepared)
    graph = score_method("5", goal, prepared)
    assert baseline.relevance_label == "UNCERTAIN"
    assert expanded.candidate == "SUPPORTING"
    assert expanded.relevance_label == "RELATED"
    assert "SUPPORTING: 행렬" in expanded.detail
    assert graph.candidate == "SUPPORTING"
    assert graph.relevance_label == "RELATED"
    assert "파인튜닝 학습 → SUPPORTING → 행렬" in graph.detail


def test_multi_concept_embedding_compares_direct_and_supporting_separately():
    goal = parse_goal(json.dumps({
        "mainTopic": "파인튜닝 학습",
        "coreTopics": ["LoRA"],
        "supportingTopics": ["행렬"],
    }, ensure_ascii=False))
    case = parse_cases(json.dumps({
        "pageTitle": "행렬 곱셈 설명",
        "text": "행렬 곱과 벡터 연산을 순서대로 연습하는 선형대수 수업입니다.",
    }, ensure_ascii=False), "CHROME_DOM")[0]
    result = score_method("4", goal, prepare_case(case), embedder=ConceptEmbedder())
    assert result.status == "COMPLETED"
    assert result.candidate == "SUPPORTING"
    assert result.relevance_label == "UNCERTAIN"  # No calibrated threshold.
    assert "DIRECT: LoRA=" in result.detail
    assert "SUPPORTING: 행렬=" in result.detail
