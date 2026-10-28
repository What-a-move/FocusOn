"""Schema, preprocessing and vector-cache contracts independent of scoring."""
import pytest

from src.api.schemas import ConfirmedGoal, ContentInput, SafeInputError, VerifiedContext
from src.preprocessing.adapter import adapt_content, preprocessing_context
from src.preprocessing.validator import validate_and_preprocess
from src.utils.cache import TTLCache


def inputs(**changes):
    observation = dict(sessionId="session-1", runId="run-1", goalId="goal-1", goalVersion=1,
                       clientRunId="event-1", sequence=1, navigationId="nav-1",
                       observedAt="2026-10-07T01:00:00Z", contentSource="CHROME_DOM",
                       isActiveTarget=True, sessionStatus="ACTIVE", exclusionMode="NONE")
    content = ContentInput.parse_safe({**observation, "contentType": "DOCUMENT",
                                      "text": "useState를 사용하여 객체 상태 업데이트를 수행하는 상태 변경 함수 예제입니다.",
                                      "extractionStatus": "SUCCESS", "isSensitive": False, "isExcluded": False, **changes})
    content_data = content.model_dump(mode="json", by_alias=True)
    context_observation = {key: content_data[key] for key in observation}
    context = VerifiedContext.parse_safe({**context_observation, "userId": "user-1", "resumeRequired": False,
                                         "policyVersion": "policy-1", "extractorVersion": "extractor-1", "stateVersion": 1})
    goal = ConfirmedGoal.parse_safe({"goalId": "goal-1", "goalVersion": 1, "text": "React useState로 객체 상태 변경하기", "confirmed": True, "templateId": "react-object-state"})
    return content, goal, context



@pytest.mark.parametrize("changes", [{"goalVersion": True}, {"clientRunId": "secret with spaces"}, {"sequence": True}, {"urlHost": "https://example.com/?secret=1"}, {"unknown": "private"}, {"observedAt": "2026-10-07T01:00:00"}, {"ocr": {"confidence": float("nan"), "captureStatus": "SUCCESS"}}, {"isSensitive": "false"}])
def test_safe_schema_errors(changes):
    content, _, _ = inputs()
    with pytest.raises(SafeInputError) as error:
        ContentInput.parse_safe({**content.model_dump(mode="json", by_alias=True), **changes})
    assert str(error.value) == "INVALID_INPUT"
    assert error.value.__context__ is None



def test_fenced_code_preserves_indentation_and_short_error_is_valid():
    content, _, context = inputs(text="공개 설명\n\n```python\nif token:\n    verify(token)\n```", contentType="DOCUMENT")
    prepared = validate_and_preprocess(adapt_content(content, context), preprocessing_context(context))
    assert "    verify(token)" in prepared.passages[-1].text
    short, _, _ = inputs(text="403 Forbidden", contentType="ERROR")
    assert validate_and_preprocess(adapt_content(short, context), preprocessing_context(context)).should_analyze



def test_ttl_lru_and_clear():
    now = [0.0]
    cache = TTLCache(2, 10, clock=lambda: now[0])
    cache.put("a", 1); cache.put("b", 2)
    assert cache.get("a") == 1
    cache.put("c", 3)
    assert cache.get("b") is None
    now[0] = 11
    assert len(cache) == 0
    cache.put("d", 4)
    cache.clear()
    assert len(cache) == 0



@pytest.mark.parametrize("confirmed", [False, 1, "true"])
def test_goal_confirmation_is_strict(confirmed):
    _, goal, _ = inputs()
    with pytest.raises(SafeInputError):
        ConfirmedGoal.parse_safe({**goal.model_dump(mode="json", by_alias=True), "confirmed": confirmed})
