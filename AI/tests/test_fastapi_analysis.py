"""Exercise the E5 decision gates through the existing Backend JSON contract."""
import json
from pathlib import Path

import numpy as np
import pytest
from fastapi.testclient import TestClient

from AI.src.main import create_app
from AI.src.models.screen_activity.activity import model_result
from AI.src.models.screen_activity.topic_bank import TopicBank

KEY = "local-test-key-only-0123456789abcdef"
PATH = "/internal/v1/analysis-runs"
HEADERS = {"Authorization": f"Bearer {KEY}"}
# Goal attribution needs at least 80 body characters beyond the title.
LONG_BODY = ("JWT 서명을 검증하고 토큰의 만료 시간을 확인합니다. 서버는 공개 키로 서명을 확인한 뒤 "
             "발급자와 대상 클레임을 비교하고, 만료된 토큰은 거부하여 다시 로그인하도록 안내합니다.")


def task_vector(similarity):
    # Every screen is (1, 0, 0), so a task's cosine to the screen equals `similarity`.
    return np.array([similarity, np.sqrt(1 - similarity * similarity), 0.])


def other_topic(similarity):
    # Same cosine to the screen, but a different topic: far from every task vector.
    return np.array([similarity, 0., np.sqrt(1 - similarity * similarity)])


class FixedScores:
    def __init__(self, scores=(.9, .05, .05), similarities=None, bank=((0., 0., 1.),), passage=None):
        self.scores = scores
        self.inputs = []
        self.encoder = self
        self.similarities = similarities or {}
        self.embeddings = []
        self.topic_bank = TopicBank(bank, bank)
        self.passage = passage or (lambda text: np.array([1., 0., 0.]))

    def predict(self, payloads):
        self.inputs.extend(payloads)
        return [model_result(self.scores) for _ in payloads]

    def embed(self, texts, *, role="passage"):
        texts = tuple(texts)
        self.embeddings.append((role, texts))
        if role == "query":
            return {text: task_vector(self.similarities.get(text, .4)) for text in texts}
        return {text: self.passage(text) for text in texts}


@pytest.fixture
def payload():
    return json.loads((Path(__file__).parents[1] / "examples/analysis-reception.json").read_text())


@pytest.mark.parametrize("source,scores,decision,reason", [
    ("TAB_META", (.9, .05, .05), "RELATED", "SEMANTIC_GOAL_MATCH"),
    ("TAB_META", (.05, .9, .05), "UNCERTAIN", "INSUFFICIENT_CONTEXT"),
    ("APP_META", (.05, .9, .05), "UNCERTAIN", "INSUFFICIENT_CONTEXT"),
    ("CHROME_DOM", (.05, .9, .05), "UNRELATED", "SEMANTIC_GOAL_MISMATCH"),
    ("DESKTOP_APP_OCR", (.05, .9, .05), "UNRELATED", "SEMANTIC_GOAL_MISMATCH"),
    ("CHROME_DOM", (.499, .495, .006), "UNCERTAIN", "AMBIGUOUS_CONTEXT"),
    ("CHROME_DOM", (.599, .3, .101), "UNCERTAIN", "INSUFFICIENT_CONTEXT"),
    ("CHROME_DOM", (.6, .3, .1), "RELATED", "SEMANTIC_GOAL_MATCH"),
])
def test_decision_contract(payload, source, scores, decision, reason):
    payload["request"]["contentSource"] = payload["context"]["contentSource"] = source
    if source in {"APP_META", "DESKTOP_APP_OCR"}:
        payload["request"]["metadata"]["appName"] = "Preview"
    if source.endswith("_META"):
        payload["request"].pop("contentPayload")

    model = FixedScores(scores)
    with TestClient(create_app(api_key=KEY, classifier_factory=lambda: model)) as client:
        response = client.post(PATH, json=payload, headers=HEADERS)

    assert response.status_code == 200
    assert response.json() == {"decision": decision, "confidence": None, "reasonCode": reason,
                               "modelVersion": "e5-activity-10168",
                               "promptVersion": "e5-activity-v1-uncalibrated",
                               "matchedGoalId": None,
                               "matchStatus": "NOT_ENOUGH" if decision == "RELATED" else None}
    if decision != "RELATED":
        assert not model.embeddings
    assert model.inputs == [{
        "contentSource": source,
        "metadata": {**payload["request"]["metadata"], "windowTitle": payload["request"]["metadata"].get("windowTitle") or ""},
        "contentPayload": {"text": payload["request"].get("contentPayload", {}).get("text", "")},
    }]


@pytest.mark.parametrize("failure", ["startup", "inference", "extraction"])
def test_failure_is_uncertain(payload, failure, caplog):
    class Broken:
        def predict(self, payloads):
            raise RuntimeError("private model error")

    def factory():
        if failure == "startup":
            raise ValueError("private model path")
        if failure == "extraction":
            return FixedScores()
        return Broken()

    if failure == "extraction":
        payload["extractionStatus"] = "FAILED"
        payload["request"].pop("contentPayload")
    with TestClient(create_app(api_key=KEY, classifier_factory=factory)) as client:
        result = client.post(PATH, json=payload, headers=HEADERS)
    assert result.status_code == 200
    assert result.json()["decision"] == "UNCERTAIN"
    assert result.json()["reasonCode"] == "ANALYSIS_UNAVAILABLE"
    assert result.json()["confidence"] is None
    assert result.json()["matchedGoalId"] is None and result.json()["matchStatus"] is None
    assert "private" not in result.text + caplog.text


def test_sensitive_data_is_rejected_before_model(payload):
    payload["request"]["contentPayload"]["text"] = "private@example.com"
    model = FixedScores()
    with TestClient(create_app(api_key=KEY, classifier_factory=lambda: model)) as client:
        response = client.post(PATH, json=payload, headers=HEADERS)
    assert response.status_code == 422
    assert not model.inputs



def test_chrome_year_selector_is_sanitized_without_bypassing_card_guard(payload):
    model = FixedScores((.05, .9, .05))
    years = " ".join(str(year) for year in range(2026, 1999, -1))
    body = f"Grand Prix results {years} Drivers and teams"
    payload["request"]["contentPayload"]["text"] = body
    with TestClient(create_app(api_key=KEY, classifier_factory=lambda: model)) as client:
        accepted = client.post(PATH, json=payload, headers=HEADERS)
        assert accepted.json()["decision"] == "UNRELATED"
        assert model.inputs[0]["contentPayload"]["text"] == "Grand Prix results [연도 목록] Drivers and teams"

        # Public test PAN used by payment processors.
        for text in (f"{body} 4111 1111 1111 1111", f"{years} 4111 1111 1111 1111",
                     f"4111 1111 1111 1111 {years}"):
            payload["request"]["contentPayload"]["text"] = text
            assert client.post(PATH, json=payload, headers=HEADERS).json() == {"detail": "CONTENT_BLOCKED"}
    assert len(model.inputs) == 1


def test_tab_title_can_attribute_but_app_window_title_cannot(payload):
    payload.pop("goal")
    request = payload["request"]
    request.pop("goalSnapshot")
    request.update(goalVersion=1, goals=[
        {"goalId": "goal-demo", "goalText": "React 실습", "goalProfile": None},
        {"goalId": "auth", "goalText": "인증 구현", "goalProfile": {
            "mainTopic": "인증", "purpose": "CONCEPT_LEARNING", "coreTopics": ["JWT 서명"],
        }},
    ])
    request.pop("contentPayload")
    request["contentSource"] = payload["context"]["contentSource"] = "TAB_META"
    model = FixedScores(similarities={"JWT 서명": .86})
    with TestClient(create_app(api_key=KEY, classifier_factory=lambda: model)) as client:
        tab = client.post(PATH, json=payload, headers=HEADERS).json()
        assert (tab["decision"], tab["matchedGoalId"], tab["matchStatus"]) == ("RELATED", "auth", "MATCHED")
        assert model.embeddings[-2] == ("query", ("React 실습", "인증 구현", "인증", "JWT 서명"))
        embedded = len(model.embeddings)
        request["contentSource"] = payload["context"]["contentSource"] = "APP_META"
        request["metadata"] = {"appName": "Preview", "windowTitle": "jwt-notes.txt"}
        app = client.post(PATH, json=payload, headers=HEADERS).json()
        assert (app["decision"], app["matchedGoalId"], app["matchStatus"]) == ("RELATED", None, "NOT_ENOUGH")
        assert len(model.embeddings) == embedded
        request["goalVersion"] = 2
        stale = client.post(PATH, json=payload, headers=HEADERS)
        assert stale.status_code == 422
        assert len(model.inputs) == 2


@pytest.mark.parametrize("bank,expected", [
    # Another study topic explains the screen better than the selected task.
    ((other_topic(.95),), (None, "NOT_ENOUGH")),
    # The same task in other words does not compete with it.
    ((task_vector(.95),), ("goal-demo", "MATCHED")),
    # A topic too close to the task to tell apart (query cosine about 0.915) does not block it.
    (((.95, .192, .246),), ("goal-demo", "MATCHED")),
    ((other_topic(.80),), ("goal-demo", "MATCHED")),
])
def test_other_study_topic_blocks_attribution(payload, bank, expected):
    payload["request"]["contentPayload"]["text"] = LONG_BODY
    model = FixedScores(similarities={"JWT 인증 구현": .86}, bank=bank)
    with TestClient(create_app(api_key=KEY, classifier_factory=lambda: model)) as client:
        result = client.post(PATH, json=payload, headers=HEADERS).json()
    assert (result["decision"], result["matchedGoalId"], result["matchStatus"]) == ("RELATED", *expected)


def test_task_below_match_floor_is_not_attributed(payload):
    payload["request"]["contentPayload"]["text"] = LONG_BODY
    model = FixedScores(similarities={"JWT 인증 구현": .79})
    with TestClient(create_app(api_key=KEY, classifier_factory=lambda: model)) as client:
        result = client.post(PATH, json=payload, headers=HEADERS).json()
    assert (result["matchedGoalId"], result["matchStatus"]) == (None, "FAR")


@pytest.mark.parametrize("main_topic,expected_terms", [
    ("인증", ("인증 구현", "인증", "Straße")),
    ("\u00a0", ("인증 구현",)),
])
def test_analysis_accepts_saved_profiles_with_unicode_whitespace_or_duplicates(payload, main_topic, expected_terms):
    payload.pop("goal")
    request = payload["request"]
    request.pop("goalSnapshot")
    request.update(goalVersion=1, goals=[
        {"goalId": "goal-demo", "goalText": "인증 구현", "goalProfile": {
            "mainTopic": main_topic, "purpose": "CONCEPT_LEARNING",
            "coreTopics": ["Straße", "Strasse", "\u00a0"],
        }},
    ])
    payload["request"]["contentPayload"]["text"] = LONG_BODY
    model = FixedScores(similarities={"인증 구현": .9})
    with TestClient(create_app(api_key=KEY, classifier_factory=lambda: model)) as client:
        response = client.post(PATH, json=payload, headers=HEADERS)
    assert response.status_code == 200
    assert response.json()["decision"] == "RELATED"
    assert model.embeddings[-2] == ("query", expected_terms)


@pytest.mark.parametrize("quality", [None, {"confidence": .2, "captureStatus": "LOW_CONFIDENCE"}])
def test_ocr_preserves_study_when_match_evidence_is_insufficient(payload, quality):
    payload["request"]["contentSource"] = payload["context"]["contentSource"] = "DESKTOP_APP_OCR"
    payload["request"]["metadata"] = {"appName": "Preview", "windowTitle": "학습 메모"}
    payload["request"]["contentPayload"]["text"] = LONG_BODY
    if quality:
        payload["ocr"] = quality
    # 0.83 is near the goal (no FAR), but another study topic explains the screen better.
    model = FixedScores(similarities={"JWT 인증 구현": .99 if quality else .83}, bank=(other_topic(.9),))
    with TestClient(create_app(api_key=KEY, classifier_factory=lambda: model)) as client:
        result = client.post(PATH, json=payload, headers=HEADERS).json()
    assert (result["decision"], result["matchedGoalId"], result["matchStatus"]) == (
        "RELATED", None, "NOT_ENOUGH")
    assert bool(model.embeddings) == (quality is None)


def test_body_that_only_repeats_title_does_not_attribute_goal(payload):
    request = payload["request"]
    request["contentPayload"]["text"] = request["metadata"]["pageTitle"]
    model = FixedScores(similarities={"JWT 인증 구현": .95})
    with TestClient(create_app(api_key=KEY, classifier_factory=lambda: model)) as client:
        result = client.post(PATH, json=payload, headers=HEADERS).json()
    assert (result["decision"], result["matchedGoalId"], result["matchStatus"]) == (
        "RELATED", None, "NOT_ENOUGH")
    assert not model.embeddings


def test_short_body_cannot_support_goal_attribution(payload):
    # Filename, menu or a single sentence is not evidence for a task.
    model = FixedScores(similarities={"JWT 인증 구현": .99})
    with TestClient(create_app(api_key=KEY, classifier_factory=lambda: model)) as client:
        result = client.post(PATH, json=payload, headers=HEADERS).json()
    assert (result["decision"], result["matchedGoalId"], result["matchStatus"]) == (
        "RELATED", None, "NOT_ENOUGH")
    assert not model.embeddings


def test_closest_selected_task_is_attributed(payload):
    request = payload["request"]
    request["contentPayload"]["text"] = LONG_BODY
    request.update(goalVersion=1, goals=[
        {"goalId": "goal-demo", "goalText": "JWT 인증 구현"},
        {"goalId": "other", "goalText": "세션 쿠키 관리"},
    ])
    model = FixedScores(similarities={"JWT 인증 구현": .95, "세션 쿠키 관리": .94})
    with TestClient(create_app(api_key=KEY, classifier_factory=lambda: model)) as client:
        result = client.post(PATH, json=payload, headers=HEADERS).json()
    assert (result["decision"], result["matchedGoalId"], result["matchStatus"]) == ("RELATED", "goal-demo", "MATCHED")


@pytest.mark.parametrize("marker,expected", [(True, ("goal-demo", "MATCHED")), (False, (None, "FAR"))])
def test_body_chunk_after_site_menu_can_support_task(payload, marker, expected):
    # The encoder reads only the start of a long view; evidence later in the body is a chunk.
    menu = "메뉴 홈 소개 문서 커뮤니티 블로그 다운로드 검색 로그인 " * 25
    evidence = "CHUNK-EVIDENCE 서명 검증 순서와 만료 확인을 예제로 설명합니다. " if marker else ""
    payload["request"]["contentPayload"]["text"] = menu + evidence + "토큰 검증 절차를 정리합니다. " * 20
    near = lambda text: np.array([1., 0., 0.]) if "CHUNK-EVIDENCE" in text and len(text) < 700 else np.array([0., 0., 1.])
    model = FixedScores(similarities={"JWT 인증 구현": .9}, bank=((0., 1., 0.),), passage=near)
    with TestClient(create_app(api_key=KEY, classifier_factory=lambda: model)) as client:
        result = client.post(PATH, json=payload, headers=HEADERS).json()
    assert (result["decision"], result["matchedGoalId"], result["matchStatus"]) == ("RELATED", *expected)


def test_matching_failure_keeps_activity_decision(payload):
    model = FixedScores()

    def failed_embed(*args, **kwargs):
        raise RuntimeError("private model error")

    model.embed = failed_embed
    with TestClient(create_app(api_key=KEY, classifier_factory=lambda: model)) as client:
        response = client.post(PATH, json=payload, headers=HEADERS)
    assert response.status_code == 200
    result = response.json()
    assert (result["decision"], result["matchedGoalId"], result["matchStatus"]) == (
        "RELATED", None, "NOT_ENOUGH")


def test_versioned_goals_can_include_matching_legacy_snapshot(payload):
    request = payload["request"]
    request.update(goalVersion=1, goals=[
        {"goalId": "goal-demo", "goalText": "JWT 인증 구현"},
        {"goalId": "other", "goalText": "쿠키 읽기"},
    ])
    with TestClient(create_app(api_key=KEY, classifier_factory=FixedScores)) as client:
        assert client.post(PATH, json=payload, headers=HEADERS).status_code == 200
        request["goalSnapshot"] = "다른 목표"
        assert client.post(PATH, json=payload, headers=HEADERS).status_code == 422


def test_far_requires_full_body_with_independent_study_signal(payload):
    payload["request"]["contentPayload"]["text"] = (
        "행렬을 연립방정식으로 바꾼 뒤 가우스 소거법을 사용한다. "
        "첫 번째 열의 피벗 아래 원소를 모두 제거하고 두 번째 열도 같은 방법으로 계산한다. "
        "맨 아래 행에서부터 역대입하여 각 미지수의 값을 구한다."
    )
    model = FixedScores(similarities={"JWT 인증 구현": .75})
    with TestClient(create_app(api_key=KEY, classifier_factory=lambda: model)) as client:
        far = client.post(PATH, json=payload, headers=HEADERS).json()
        assert (far["decision"], far["matchStatus"], far["matchedGoalId"]) == ("RELATED", "FAR", None)
        payload["extractionStatus"] = "PARTIAL"
        partial = client.post(PATH, json=payload, headers=HEADERS).json()
        assert (partial["decision"], partial["matchStatus"]) == ("RELATED", "NOT_ENOUGH")


@pytest.mark.parametrize("source,extraction,title,goal,expected", [
    ("CHROME_DOM", "SUCCESS", "AWS Lambda 함수", "Django 모델 만들기", "FAR"),
    ("CHROME_DOM", "SUCCESS", "Express 라우팅", "Node.js fs 설명 읽기", "NOT_ENOUGH"),
    ("CHROME_DOM", "PARTIAL", "AWS Lambda 함수", "Django 모델 만들기", "NOT_ENOUGH"),
    ("TAB_META", "SUCCESS", "AWS Lambda 함수", "Django 모델 만들기", "NOT_ENOUGH"),
])
def test_family_far_only_for_complete_body_and_different_family(payload, source, extraction, title, goal, expected):
    request = payload["request"]
    request["contentSource"] = payload["context"]["contentSource"] = source
    request["goalSnapshot"] = payload["goal"]["text"] = goal
    request["metadata"]["pageTitle"] = title
    payload["extractionStatus"] = extraction
    if source == "CHROME_DOM":
        request["contentPayload"]["text"] = f"{title} 실습. " + LONG_BODY
    else:
        request.pop("contentPayload")
    model = FixedScores(similarities={goal: .82})
    vectors = np.repeat([other_topic(.92), task_vector(.65), [0., 0., -1.]], 3, axis=0)
    fields = ["AWS"] * 3 + ["Django"] * 3 + ["Node.js와 Express"] * 3
    model.topic_bank = TopicBank(vectors, vectors, fields)

    with TestClient(create_app(api_key=KEY, classifier_factory=lambda: model)) as client:
        response = client.post(PATH, json=payload, headers=HEADERS)

    assert response.status_code == 200
    assert response.json()["decision"] == "RELATED"
    assert response.json()["matchStatus"] == expected
    assert response.json()["matchedGoalId"] is None


def test_ocr_operation_symbols_can_attribute_without_product_name(payload):
    request = payload["request"]
    request.pop("goalSnapshot")
    payload.pop("goal")
    request.update(goalVersion=1, goals=[
        {"goalId": "goal-demo", "goalText": "MongoDB find로 문서 조회하기"},
        {"goalId": "aggregate", "goalText": "MongoDB aggregation 파이프라인으로 집계하기",
         "goalProfile": {"mainTopic": "집계 파이프라인", "purpose": "CONCEPT_LEARNING",
                         "coreTopics": ["$match 스테이지", "$group 스테이지", "$sort 스테이지"]}},
    ])
    request["contentSource"] = payload["context"]["contentSource"] = "DESKTOP_APP_OCR"
    request["metadata"] = {"appName": "Preview", "windowTitle": "notes.txt"}
    request["contentPayload"]["text"] = (
        "db.orders.aggregate([{ $match: { status: 'paid' } }, { $group: { _id: '$category', "
        "total: { $sum: '$amount' } } }, { $sort: { total: -1 } }])"
    )
    model = FixedScores(similarities={"MongoDB find로 문서 조회하기": .91})
    vectors = [other_topic(.93), task_vector(.58), other_topic(.94)]
    model.topic_bank = TopicBank(vectors, vectors, ["MongoDB"] * 3)

    with TestClient(create_app(api_key=KEY, classifier_factory=lambda: model)) as client:
        response = client.post(PATH, json=payload, headers=HEADERS)

    assert response.status_code == 200
    result = response.json()
    assert (result["decision"], result["matchStatus"], result["matchedGoalId"]) == ("RELATED", "MATCHED", "aggregate")


def test_family_name_only_cannot_confirm_specific_task_from_metadata(payload):
    request = payload["request"]
    request["goalSnapshot"] = payload["goal"]["text"] = "React Context로 전역 상태 공유하기"
    request["contentSource"] = payload["context"]["contentSource"] = "TAB_META"
    request["metadata"]["pageTitle"] = "React 공식 문서"
    request.pop("contentPayload")
    model = FixedScores(similarities={"React Context로 전역 상태 공유하기": .94})
    vectors = [task_vector(.95)] * 3
    model.topic_bank = TopicBank(vectors, vectors, ["React"] * 3)

    with TestClient(create_app(api_key=KEY, classifier_factory=lambda: model)) as client:
        response = client.post(PATH, json=payload, headers=HEADERS)

    assert response.status_code == 200
    assert response.json()["matchStatus"] == "NOT_ENOUGH"
