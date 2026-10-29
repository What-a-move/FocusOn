"""MVP contract boundaries: currentness, privacy, bounded input and abstention."""
import json

import pytest

from src.api.relevance_contract import (ScreenAnalysisRequest, ScreenAnalysisResponse,
                                      adapt_screen_request, public_response, validate_provider_response)
from src.api.schemas import ConfirmedGoal, SafeInputError, VerifiedContext
from src.analysis.acquisition_policy import next_content_source
from src.preprocessing.models import ContentSource, ExtractionStatus


def wire(**changes):
    return {"sessionId": "session-1", "clientRunId": "cc3a11f9-3d96-42de-bc10-b8fcaaea4e37",
            "observedAt": "2026-10-26T01:00:00Z", "sequence": 1,
            "contentSource": "CHROME_DOM", "goalSnapshot": "JWT 인증 구현",
            "metadata": {"appName": "Google Chrome", "pageTitle": "JWT 서명 검증", "urlHost": "example.com"},
            "contentPayload": {"text": "JWT 서명과 만료 시간을 검증합니다."}, **changes}


def inputs(payload=None):
    request = ScreenAnalysisRequest.parse_safe(payload or wire())
    context = VerifiedContext.parse_safe({"userId": "user-1", "sessionId": request.session_id,
        "runId": "server-run", "goalId": "server-goal", "goalVersion": 2,
        "clientRunId": request.client_run_id, "observedAt": request.observed_at.isoformat(),
        "sequence": request.sequence, "navigationId": "server-nav", "contentSource": request.content_source.value,
        "isActiveTarget": True, "sessionStatus": "RUNNING", "exclusionMode": "NONE",
        "resumeRequired": False, "policyVersion": "v1", "extractorVersion": "client-v1", "stateVersion": 1})
    goal = ConfirmedGoal.parse_safe({"goalId": "server-goal", "goalVersion": 2,
                                    "text": "JWT 인증 구현", "confirmed": True})
    return request, goal, context


def test_public_input_preserves_server_context_and_text_without_inventing_ocr_quality():
    request, goal, context = inputs(wire(contentSource="DESKTOP_APP_OCR"))
    content = adapt_screen_request(request, goal, context, extraction_status=ExtractionStatus.SUCCESS)
    assert content.run_id == "server-run" and content.goal_version == 2
    assert content.text == request.content_payload.text and content.page_title == request.metadata.page_title
    assert content.ocr is None


@pytest.mark.parametrize("length", [0, 2000, 2001])
def test_public_text_length_is_checked_before_scoring(length):
    payload = wire(contentPayload={"text": "가" * length})
    if length > 2000:
        with pytest.raises(SafeInputError, match="^INVALID_INPUT$"):
            ScreenAnalysisRequest.parse_safe(payload)
    else:
        assert len(ScreenAnalysisRequest.parse_safe(payload).content_payload.text) == length


@pytest.mark.parametrize("changes", [
    {"clientRunId": "event-1"}, {"observedAt": "2026-10-26T01:00:00"},
    {"metadata": {"urlHost": "https://example.com/private?secret=x"}},
    {"contentSource": "TAB_META"}, {"runId": "untrusted-client-run"},
])
def test_public_contract_rejects_unagreed_or_unsafe_shapes(changes):
    with pytest.raises(SafeInputError):
        ScreenAnalysisRequest.parse_safe(wire(**changes))


def test_guard_event_cannot_contain_content_or_enter_ai_adapter():
    payload = wire(guardStatus="EXCLUDED")
    with pytest.raises(SafeInputError):
        ScreenAnalysisRequest.parse_safe(payload)
    for name in ("metadata", "contentPayload", "goalSnapshot"):
        payload.pop(name)
    request = ScreenAnalysisRequest.parse_safe(payload)
    _, goal, context = inputs()
    with pytest.raises(ValueError, match="GUARD_BLOCKED"):
        adapt_screen_request(request, goal, context, extraction_status=ExtractionStatus.SUCCESS)


def test_delayed_public_observation_cannot_be_attached_to_new_server_context():
    request, goal, context = inputs()
    newer = context.model_copy(update={"sequence": 2, "content_source": ContentSource.APP_META})
    with pytest.raises(ValueError, match="OBSERVATION_CONTEXT_MISMATCH"):
        adapt_screen_request(request, goal, newer, extraction_status=ExtractionStatus.SUCCESS)


@pytest.mark.parametrize("label", ["UNRELATED", "UNCERTAIN"])
@pytest.mark.parametrize("source,next_source", [(ContentSource.TAB_META, ContentSource.CHROME_DOM),
                                               (ContentSource.APP_META, ContentSource.DESKTOP_APP_OCR)])
def test_nonrelated_metadata_acquires_body_regardless_of_context_reason(label, source, next_source):
    for reason in ("INSUFFICIENT_CONTEXT", "AMBIGUOUS_CONTEXT", "SEMANTIC_GOAL_MISMATCH"):
        assert next_content_source(source, label, reason) is next_source


@pytest.mark.parametrize("reason", ["STALE_EVENT", "INACTIVE_TARGET", "MODEL_OR_SCORING_FAILED", "PRIVACY_BLOCKED"])
def test_invalid_metadata_does_not_trigger_capture(reason):
    assert next_content_source(ContentSource.TAB_META, "UNCERTAIN", reason) is None


def response(label, confidence, reason="GOAL_CONNECTED", source=ContentSource.CHROME_DOM):
    return public_response(label, reason, source=source, confidence=confidence,
                           model_version="qwen-v1", prompt_version="hybrid-v1")


def test_similarity_is_never_synthesized_into_public_confidence():
    result = response("RELATED", None)
    assert result.decision == "UNCERTAIN" and result.confidence is None
    assert result.reason_code == "ANALYSIS_UNAVAILABLE"
    assert response("RELATED", .74).decision == "UNCERTAIN"
    accepted = response("RELATED", .75).model_dump(by_alias=True)
    assert accepted == {"decision": "RELATED", "confidence": .75, "reasonCode": "SEMANTIC_GOAL_MATCH",
                        "modelVersion": "qwen-v1", "promptVersion": "hybrid-v1"}


def test_metadata_unrelated_and_chrome_ocr_never_become_public_offtask():
    assert response("UNRELATED", .99, source=ContentSource.TAB_META).decision == "UNCERTAIN"
    assert response("RELATED", .99, source=ContentSource.CHROME_VIEWPORT_OCR).decision == "UNCERTAIN"
    assert response("UNRELATED", .99).decision == "UNRELATED"


@pytest.mark.parametrize("value", [True, float("nan"), float("inf"), -1., 1.01])
def test_invalid_confidence_is_not_clamped(value):
    with pytest.raises(SafeInputError):
        response("RELATED", value)


def test_public_uncertain_reasons_and_stale_outputs_are_distinct():
    assert response("UNCERTAIN", None, "METADATA_CONFLICTING").reason_code == "AMBIGUOUS_CONTEXT"
    assert response("UNCERTAIN", None, "METADATA_INSUFFICIENT").reason_code == "INSUFFICIENT_CONTEXT"
    assert response("UNCERTAIN", None, "MODEL_OR_SCORING_FAILED").reason_code == "ANALYSIS_UNAVAILABLE"
    with pytest.raises(ValueError, match="RESULT_NOT_CURRENT"):
        response("RELATED", .99, "STALE_EVENT")
    with pytest.raises(SafeInputError):
        ScreenAnalysisResponse.parse_safe({"decision": "RELATED", "confidence": .99,
            "reasonCode": "INSUFFICIENT_CONTEXT", "modelVersion": "qwen-v1", "promptVersion": "hybrid-v1"})


@pytest.mark.parametrize("changes", [{"decision": "OFF_TASK"}, {"reasonCode": "PRIVATE_REASON"},
    {"confidence": float("nan")}, {"text": "private@example.com"}, {"modelVersion": "wrong-model"},
    {"reasonCode": "SEMANTIC_GOAL_MISMATCH"}])
def test_invalid_provider_output_becomes_uncertain_without_echo(changes):
    payload = {"decision": "RELATED", "confidence": .99, "reasonCode": "SEMANTIC_GOAL_MATCH",
               "modelVersion": "qwen-v1", "promptVersion": "hybrid-v1", **changes}
    result = validate_provider_response(payload, source=ContentSource.CHROME_DOM,
                                       model_version="qwen-v1", prompt_version="hybrid-v1")
    assert result.decision == "UNCERTAIN" and result.reason_code == "ANALYSIS_UNAVAILABLE"
    assert "private@example.com" not in result.model_dump_json()


def test_valid_provider_low_confidence_is_normalized_instead_of_rejected():
    result = validate_provider_response({"decision": "RELATED", "confidence": .5,
        "reasonCode": "SEMANTIC_GOAL_MATCH", "modelVersion": "qwen-v1", "promptVersion": "hybrid-v1"},
        source=ContentSource.CHROME_DOM, model_version="qwen-v1", prompt_version="hybrid-v1")
    assert result.decision == "UNCERTAIN" and result.confidence == .5


def test_streamlit_accepts_public_dom_shape_and_preserves_limits():
    from AI.evaluation.streamlit_runner import parse_inputs
    goal, cases = parse_inputs("JWT 인증 구현", json.dumps(wire()), "CHROME_DOM")
    content = cases[0][1]
    assert content.text == wire()["contentPayload"]["text"] and content.client_run_id == wire()["clientRunId"]
    assert content.page_title == "JWT 서명 검증"
    with pytest.raises(ValueError, match="CONTENT_TEXT_TOO_LONG"):
        parse_inputs("JWT 인증 구현", json.dumps(wire(contentPayload={"text": "가" * 2001})), "CHROME_DOM")


def test_chrome_ocr_is_skipped_without_embedding_and_metadata_failure_does_not_capture():
    from AI.evaluation.streamlit_runner import compare_graphless, parse_inputs

    class Forbidden:
        fingerprint = "no-model-call"
        dimensions = 2
        calls = 0
        def embed(self, texts):
            pytest.fail("P1 OCR must not reach model")

    goal, cases = parse_inputs("JWT 인증 구현", json.dumps({"contentSource": "CHROME_VIEWPORT_OCR", "text": "JWT 서명 검증"}), "CHROME_DOM")
    row = compare_graphless(goal, cases, Forbidden())["rows"][0]
    assert row["reasonCode"] == "CHROME_OCR_DEFERRED_P1"
    assert row["relevanceLabel"] == "UNCERTAIN" and row["nextContentSource"] is None


@pytest.mark.parametrize("source", ["APP_META", "DESKTOP_APP_OCR"])
@pytest.mark.parametrize("app,reason", [("Google Chrome", "CHROME_EXTENSION_REQUIRED"),
                                      ("Safari", "CLIENT_GUARD_REQUIRED"),
                                      ("FocusOn", "CLIENT_GUARD_REQUIRED")])
def test_desktop_cannot_substitute_for_chrome_or_an_excluded_target(source, app, reason):
    from AI.evaluation.streamlit_runner import compare_graphless, parse_inputs

    class Forbidden:
        fingerprint = "no-capture-bypass"
        dimensions = 2
        calls = 0
        def embed(self, texts):
            pytest.fail("Misrouted desktop target must not reach model")

    goal, cases = parse_inputs("JWT 인증 구현", json.dumps({"contentSource": source, "appName": app,
        "windowTitle": "JWT 서명 검증", "text": "JWT 서명을 검증합니다."}), "APP_META")
    row = compare_graphless(goal, cases, Forbidden())["rows"][0]
    assert row["reasonCode"] == reason and row["relevanceLabel"] == "UNCERTAIN"
    assert row["nextContentSource"] is None
