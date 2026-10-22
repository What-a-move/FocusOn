"""UI harness must call the existing analyzer and never leak expectations into inference."""
import json
from pathlib import Path

import pytest

from AI.evaluation.streamlit_runner import compare, parse_inputs
from src.analysis.goal_analyzer import GoalGraphResolver


class Client:
    fingerprint = "test-only-not-a-real-model"
    dimensions = 2
    calls = 0

    def __init__(self):
        self.inputs = []

    def embed(self, texts):
        self.calls += 1
        self.inputs.extend(texts)
        return tuple((0., 1.) if any(term in text for term in (
            "Cooking recipes", "Song lyrics", "Create vector graphics")) else (1., 0.) for text in texts)


def inputs(payload=None, source="CHROME_DOM"):
    payload = payload or {"pageTitle": "React useState", "text": "useState 객체 상태 업데이트는 새 객체를 상태 변경 함수에 전달하여 처리합니다."}
    return parse_inputs("React useState로 객체 상태 변경하기", json.dumps(payload, ensure_ascii=False),
                        source, template_id="react-object-state")


def test_current_hybrid_scores_evidence_and_expectation_is_metadata_only():
    payload = {"text": "useState 객체 상태 업데이트는 새 객체를 상태 변경 함수에 전달하여 처리합니다.", "expectedLabel": "RELATED"}
    goal, cases = inputs(payload)
    first_client = Client()
    report = compare(goal, cases, ["6"], first_client, GoalGraphResolver())
    row = report["rows"][0]
    assert row["status"] == "SUCCEEDED"
    assert row["relevanceLabel"] == "RELATED"
    assert row["reasonCode"] == "GOAL_CONNECTED"
    assert row["matches"] and row["evidence"]
    assert report["embeddingCalls"] == 2
    payload["expectedLabel"] = "UNRELATED"
    goal, cases = inputs(payload)
    second_client = Client()
    changed = compare(goal, cases, ["6"], second_client, GoalGraphResolver())
    assert first_client.inputs == second_client.inputs
    assert changed["rows"][0]["relevanceLabel"] == "RELATED"
    assert changed["summary"][0]["기대 일치율 %"] == 0


@pytest.mark.parametrize("source,extra", [
    ("CHROME_DOM", {"isSensitive": True}),
    ("CHROME_DOM", {"text": "연락할 이메일은 test@example.com입니다."}),
    ("DESKTOP_APP_OCR", {"ocr": {"confidence": .9, "captureStatus": "BLACK_SCREEN"}}),
    ("DESKTOP_APP_OCR", {}),
    ("CHROME_DOM", {"isActiveTarget": False}),
    ("CHROME_DOM", {"sessionStatus": "PAUSED"}),
])
def test_all_methods_preserve_current_policy_and_quality_gates(source, extra):
    goal, cases = inputs({"text": "useState 객체 상태 업데이트는 새 객체를 상태 변경 함수에 전달하여 처리합니다.", **extra}, source)
    client = Client()
    report = compare(goal, cases, list("1234567"), client, GoalGraphResolver())
    assert client.calls == 0
    assert all(r["relevanceLabel"] != "RELATED" for r in report["rows"])
    assert not any(r["evidence"] for r in report["rows"])


def test_qwen_baselines_translate_e5_prefixes_and_seventh_needs_reranker():
    goal, cases = inputs()
    client = Client()
    report = compare(goal, cases, ["3", "4", "7"], client, GoalGraphResolver())
    assert any(text.startswith("Instruct:") for text in client.inputs)
    assert not any(text.startswith(("query: ", "passage: ")) for text in client.inputs)
    assert report["rows"][-1]["status"] == "NOT_RUN"


def test_complete_existing_fixture_identity_is_preserved():
    fixture = Path(__file__).resolve().parents[1] / "evaluation" / "demo-chrome-dom.json"
    raw = fixture.read_text()
    goal, cases = parse_inputs("React useState로 객체 상태 변경하기", raw, "DESKTOP_APP_OCR", template_id="react-object-state")
    assert goal.goal_id == cases[0][1].goal_id == "goal-react-input-demo"
    assert cases[0][1].content_source.value == "CHROME_DOM"
    assert cases[0][1].client_run_id == "demo-dom-001"


def test_explicit_mismatching_goal_is_not_silently_overwritten():
    raw = json.dumps({"goalId": "other-goal", "text": "useState 객체 상태 업데이트로 새 객체를 상태 변경 함수에 전달합니다."})
    goal, cases = parse_inputs(json.dumps({"goalId": "confirmed-goal", "text": "React useState 객체 상태"}), raw,
                               "CHROME_DOM", template_id="react-object-state")
    report = compare(goal, cases, ["6"], Client(), GoalGraphResolver())
    assert report["rows"][0]["reasonCode"] == "GOAL_VERSION_MISMATCH"


def test_invalid_or_ambiguous_inputs_are_not_flattened():
    with pytest.raises(ValueError):
        inputs({"text": "some text", "txet": "typo"})
    with pytest.raises(ValueError):
        inputs({"cases": [{"observations": [{"text": "a"}, {"text": "b"}]}]})


def test_labelled_dataset_requires_matching_goal_but_accepts_whitespace_changes():
    fixture = {"goal": {"text": "QueryDSL로 JPA 동적 조회 구현하기"},
               "cases": [{"text": "QueryDSL의 검색 조건을 조합하여 회원을 조회합니다.", "expectedLabel": "RELATED"}]}
    raw = json.dumps(fixture, ensure_ascii=False)
    with pytest.raises(ValueError, match="DATASET_GOAL_MISMATCH"):
        parse_inputs("로드 밸런싱 학습", raw, "CHROME_DOM")
    goal, cases = parse_inputs("querydsl로 JPA동적 조회 구현하기", raw, "CHROME_DOM")
    assert len(cases) == 1
    assert goal.text == "querydsl로 JPA동적 조회 구현하기"


def test_code_context_diagnostics_use_same_group_as_current_analyzer():
    text = "useState 객체 상태 업데이트는 새 객체를 상태 변경 함수에 전달하여 처리합니다.\n\n```js\nsetState(prev => ({...prev, n: prev.n+1}));\n```"
    goal, cases = inputs({"text": text})
    report = compare(goal, cases, ["6"], Client(), GoalGraphResolver())
    row = report["rows"][0]
    assert row["status"] == "SUCCEEDED"
    assert any(match["passage_id"].startswith("code-context:") for match in row["matches"])
    assert all(not evidence["id"].startswith("code-context:") for evidence in row["evidence"])


def test_model_failure_cannot_count_as_correct_uncertain():
    class Broken(Client):
        def embed(self, texts):
            raise RuntimeError("private provider message")
    goal, cases = inputs({"text": "useState 객체 상태 업데이트로 새 객체를 상태 변경 함수에 전달합니다.", "expectedLabel": "UNCERTAIN"})
    report = compare(goal, cases, ["6"], Broken(), GoalGraphResolver())
    assert report["rows"][0]["status"] == "FAILED"
    assert report["summary"][0]["기대 일치율 %"] == 0


def test_browser_session_reuses_goal_vectors_and_identical_results():
    from AI.evaluation.streamlit_runner import HybridSession
    client, resolver = Client(), GoalGraphResolver()
    runtime = HybridSession(client, resolver)
    goal, cases = inputs()
    first = compare(goal, cases, ["6"], client, resolver, session=runtime)
    assert first["embeddingCalls"] == 2
    goal, repeated_cases = inputs()
    assert cases[0][1].client_run_id != repeated_cases[0][1].client_run_id
    second = compare(goal, repeated_cases, ["6"], client, resolver, session=runtime)
    assert second["embeddingCalls"] == 0
    assert second["rows"][0]["cacheHit"] is True
    assert second["rows"][0]["status"] == "SUCCEEDED"
    assert second["rows"][0]["matches"] == first["rows"][0]["matches"]
    assert second["embeddingMs"] == 0
    goal, changed = inputs({"text": "useState 객체 상태 업데이트에서 새 객체를 상태 변경 함수에 전달하고 React가 화면을 다시 렌더링합니다."})
    third = compare(goal, changed, ["6"], client, resolver, session=runtime)
    assert third["embeddingCalls"] == 1
    assert third["rows"][0]["cacheHit"] is False
    assert third["rows"][0]["status"] == "SUCCEEDED"
    assert third["localMs"] >= 0


def test_browser_cache_is_scoped_to_confirmed_goal_and_keeps_explicit_event_ids():
    from AI.evaluation.streamlit_runner import HybridSession
    client, resolver = Client(), GoalGraphResolver()
    runtime = HybridSession(client, resolver)
    goal, cases = inputs()
    compare(goal, cases, ["6"], client, resolver, session=runtime)
    jwt, jwt_cases = parse_inputs("JWT 인증 구현", json.dumps({"text": "JWT 인증 필터에서 토큰 검증을 진행해 로그인 요청을 처리합니다."}),
                                 "CHROME_DOM", template_id="jwt-auth")
    report = compare(jwt, jwt_cases, ["6"], client, resolver, session=runtime)
    assert report["embeddingCalls"] == 2
    assert not report["rows"][0]["cacheHit"]
    payload = {"clientRunId": "explicit-client-event", "observedAt": "2026-10-21T00:00:00Z", "text": "JWT 인증 필터에서 토큰 검증을 진행해 로그인 요청을 처리합니다."}
    goal, original = parse_inputs("JWT 인증 구현", json.dumps(payload), "CHROME_DOM", template_id="jwt-auth")
    compare(goal, original, ["6"], client, resolver, session=runtime)
    payload["text"] = "다른 본문입니다."
    goal, changed = parse_inputs("JWT 인증 구현", json.dumps(payload), "CHROME_DOM", template_id="jwt-auth")
    result = compare(goal, changed, ["6"], client, resolver, session=runtime)
    assert result["rows"][0]["reasonCode"] == "IDEMPOTENCY_CONFLICT"
    assert result["embeddingCalls"] == 0


def test_expired_diagnostic_vectors_do_not_trigger_another_model_call():
    from AI.evaluation.streamlit_runner import HybridSession
    client, resolver = Client(), GoalGraphResolver()
    runtime = HybridSession(client, resolver)
    goal, cases = inputs()
    compare(goal, cases, ["6"], client, resolver, session=runtime)
    runtime.recorded.vectors.clear()
    goal, cases = inputs()
    result = compare(goal, cases, ["6"], client, resolver, session=runtime)
    assert result["embeddingCalls"] == 0
    assert result["rows"][0]["status"] == "SUCCEEDED"
    assert result["rows"][0]["cacheHit"]
    assert not result["rows"][0]["matches"]


def test_private_screen_clears_session_vectors_and_results():
    from AI.evaluation.streamlit_runner import HybridSession
    client, resolver = Client(), GoalGraphResolver()
    runtime = HybridSession(client, resolver)
    goal, cases = inputs()
    compare(goal, cases, ["6"], client, resolver, session=runtime)
    goal, private = inputs({"text": "useState 객체 상태 학습 자료입니다.", "isSensitive": True})
    blocked = compare(goal, private, ["6"], client, resolver, session=runtime)
    assert blocked["rows"][0]["relevanceLabel"] == "PRIVACY_BLOCKED"
    assert blocked["embeddingCalls"] == 0
    assert len(runtime.recorded.vectors) == 0
    goal, cases = inputs()
    assert compare(goal, cases, ["6"], client, resolver, session=runtime)["embeddingCalls"] == 2
