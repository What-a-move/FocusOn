import httpx
import pytest
from unittest.mock import AsyncMock, Mock

from src.analysis.goal_analyzer import GoalAssessment, GeneratedResultVerification
from src.api.routes import get_goal_workflow
from src.api.schemas import ClarityStatus
from src.config import Settings
from src.main import create_app
from src.models.errors import ModelConfigurationError, ModelUnavailableError
from src.models.fakes import FakeGoalGenerationClient, FakeJevClient
from src.workflow.goal_assistance import GoalAssistanceWorkflow
from src.api.schemas import GoalClarifyRequest
import src.api.routes as routes


PASS = GeneratedResultVerification(0.95, 0.95, 0.95)


async def post(app, payload):
    async with httpx.AsyncClient(
        transport=httpx.ASGITransport(app=app), base_url="http://test"
    ) as client:
        return await client.post("/internal/v1/goals/clarify", json=payload)


@pytest.mark.asyncio
async def test_api_uses_camel_case_and_never_confirms_automatically():
    app = create_app()
    workflow = GoalAssistanceWorkflow(
        Settings(),
        FakeJevClient(
            [GoalAssessment(0.9, 0.1, 0.1, 3, 0.9)],
            [PASS],
        ),
        FakeGoalGenerationClient(),
    )
    app.dependency_overrides[get_goal_workflow] = lambda: workflow

    response = await post(app, {"requestId": "req-1", "originalText": "캐싱 원리 학습"})

    assert response.status_code == 200
    body = response.json()["data"]
    assert body["clarityStatus"] == "CLEAR"
    assert body["requiresUserConfirmation"] is True
    assert "goalProfileDraft" in body
    assert "goal_profile_draft" not in body


@pytest.mark.asyncio
async def test_schema_validation_has_common_safe_error_shape():
    app = create_app()
    response = await post(app, {"requestId": "req-2", "originalText": "   "})

    assert response.status_code == 422
    assert response.json()["code"] == "VALIDATION_ERROR"
    assert response.json()["retryable"] is False
    assert "   " not in response.text


@pytest.mark.asyncio
async def test_dynamic_length_validation_does_not_call_jev():
    app = create_app()
    jev = FakeJevClient(
        [GoalAssessment(0.9, 0.1, 0.1, 3, 0.9)],
        [PASS],
    )
    workflow = GoalAssistanceWorkflow(
        Settings(goal_text_max_length=5), jev, FakeGoalGenerationClient()
    )
    app.dependency_overrides[get_goal_workflow] = lambda: workflow

    response = await post(app, {"requestId": "req-3", "originalText": "123456"})

    assert response.status_code == 422
    assert jev.assess_calls == 0


@pytest.mark.asyncio
async def test_model_outage_is_503_not_invalid():
    class DownJev:
        async def assess_goal(self, request):
            raise ModelUnavailableError("secret provider detail")

        async def aclose(self):
            return None

    app = create_app()
    app.dependency_overrides[get_goal_workflow] = lambda: GoalAssistanceWorkflow(
        Settings(), DownJev(), FakeGoalGenerationClient()
    )

    response = await post(app, {"requestId": "req-4", "originalText": "React 학습"})

    assert response.status_code == 503
    body = response.json()
    assert body["code"] == "AI_UNAVAILABLE"
    assert body["retryable"] is True
    assert "secret provider detail" not in response.text


@pytest.mark.asyncio
async def test_missing_model_configuration_is_not_marked_retryable():
    class MisconfiguredJev:
        async def assess_goal(self, request):
            raise ModelConfigurationError("test-only configuration failure")

        async def aclose(self):
            return None

    app = create_app()
    app.dependency_overrides[get_goal_workflow] = lambda: GoalAssistanceWorkflow(
        Settings(), MisconfiguredJev(), FakeGoalGenerationClient()
    )

    response = await post(app, {"requestId": "req-5", "originalText": "React 학습"})

    assert response.status_code == 503
    body = response.json()
    assert body["code"] == "AI_UNAVAILABLE"
    assert body["retryable"] is False
    assert body["details"]["fallbackAllowed"] is True


@pytest.mark.asyncio
async def test_agreed_fields_accept_answers_without_question_id_and_echo_session():
    app = create_app()
    jev = FakeJevClient([GoalAssessment(0.9, 0.1, 0.1, 3, 0.9)], [PASS])
    app.dependency_overrides[get_goal_workflow] = lambda: GoalAssistanceWorkflow(
        Settings(), jev, FakeGoalGenerationClient()
    )
    response = await post(app, {
        "requestId": "req-contract",
        "goalText": "캐싱 원리 학습",
        "conversationTurn": 1,
        "clarificationSessionId": "test-dialog-1",
        "previousAnswers": [{"question": "어떤 범위인가요?", "answer": "캐시 갱신"}],
    })
    assert response.status_code == 200
    assert response.json()["data"]["clarificationSessionId"] == "test-dialog-1"
    request = jev.assessed_requests[0]
    assert request.original_text == "캐싱 원리 학습"
    assert request.clarification_answers[0].question_id is None
    serialized = request.model_dump(by_alias=True)
    assert serialized["previousAnswers"] == [{"question": "어떤 범위인가요?", "answer": "캐시 갱신"}]
    assert "goalText" in serialized
    assert "originalText" not in serialized
    assert "clarificationAnswers" not in serialized


@pytest.mark.asyncio
async def test_initial_request_leaves_id_issuance_to_backend():
    app = create_app()
    app.dependency_overrides[get_goal_workflow] = lambda: GoalAssistanceWorkflow(
        Settings(), FakeJevClient([GoalAssessment(0.9, 0.1, 0.1, 3, 0.9)], [PASS]),
        FakeGoalGenerationClient(),
    )
    response = await post(app, {"requestId": "first", "goalText": "캐시 학습", "conversationTurn": 0, "previousAnswers": []})
    assert response.status_code == 200
    assert response.json()["data"]["clarificationSessionId"] is None


@pytest.mark.asyncio
@pytest.mark.parametrize("changes", [
    {"conversationTurn": -1}, {"conversationTurn": 3}, {"conversationTurn": True},
    {"conversationTurn": 1}, {"clarificationSessionId": " "},
    {"previousAnswers": [{"question": "?", "answer": "a"}] * 3},
    {"previousAnswers": [{"question": " ", "answer": "a"}], "conversationTurn": 1},
])
async def test_invalid_conversation_fields_are_rejected_before_workflow(changes):
    app = create_app()
    workflow = Mock(run=AsyncMock())
    app.dependency_overrides[get_goal_workflow] = lambda: workflow
    payload = {"requestId": "invalid", "goalText": "캐시 학습", "conversationTurn": 0, "previousAnswers": []}
    response = await post(app, payload | changes)
    assert response.status_code == 422
    workflow.run.assert_not_called()


def test_canonical_requests_require_turn_and_openapi_advertises_new_names():
    from pydantic import ValidationError
    with pytest.raises(ValidationError):
        GoalClarifyRequest.model_validate({"requestId": "missing", "goalText": "캐시 학습"})
    properties = GoalClarifyRequest.model_json_schema()["properties"]
    assert {"goalText", "previousAnswers", "conversationTurn", "clarificationSessionId"} <= properties.keys()
    assert "conversationTurn" in GoalClarifyRequest.model_json_schema()["required"]


@pytest.mark.asyncio
async def test_http_dependency_disables_provider_retries_without_mutating_local_settings(monkeypatch):
    settings = Settings(goal_assistance_max_retries=2)
    jev = Mock(aclose=AsyncMock())
    generator = Mock(aclose=AsyncMock())
    make_jev = Mock(return_value=jev)
    make_generator = Mock(return_value=generator)
    monkeypatch.setattr(routes, "get_settings", lambda: settings)
    monkeypatch.setattr(routes, "TypeSafeJevClient", make_jev)
    monkeypatch.setattr(routes, "OpenAIGoalGenerationClient", make_generator)
    dependency = get_goal_workflow()
    await anext(dependency)
    await dependency.aclose()
    assert make_jev.call_args.args[0].goal_assistance_max_retries == 0
    assert make_generator.call_args.args[0].goal_assistance_max_retries == 0
    assert settings.goal_assistance_max_retries == 2
    jev.aclose.assert_awaited_once()
    generator.aclose.assert_awaited_once()
