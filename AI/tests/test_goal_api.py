import httpx
import pytest

from src.analysis.goal_analyzer import GoalAssessment, GeneratedResultVerification
from src.api.routes import get_goal_workflow
from src.api.schemas import ClarityStatus
from src.config import Settings
from src.main import create_app
from src.models.errors import ModelUnavailableError
from src.models.fakes import FakeGoalGenerationClient, FakeJevClient
from src.workflow.goal_assistance import GoalAssistanceWorkflow


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
        FakeJevClient([GoalAssessment(0.9, 0.1, 0.1, 3, 0.9)], [PASS]),
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
    jev = FakeJevClient([GoalAssessment(0.9, 0.1, 0.1, 3, 0.9)], [PASS])
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
    app = create_app()

    response = await post(app, {"requestId": "req-5", "originalText": "React 학습"})

    assert response.status_code == 503
    body = response.json()
    assert body["code"] == "AI_UNAVAILABLE"
    assert body["retryable"] is False
    assert body["details"]["fallbackAllowed"] is True
