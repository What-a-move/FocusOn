"""Security tests for cloud JEV payload redaction and secret-dump blocking."""
from __future__ import annotations

import asyncio
from types import SimpleNamespace

import pytest

from AI.src.config import Settings
from AI.src.models.errors import ModelUnavailableError
from AI.src.models.screen_activity.cloud_sanitizer import sanitize_text_for_cloud
from AI.src.models.screen_activity.goal_match import match_goal_async
from AI.src.models.screen_activity.jev_goal_matcher import TypeSafeJevGoalMatcher
from AI.src.preprocessing.models import ContentSource, ExtractionStatus


def _goal(goal_id: str, text: str):
    return SimpleNamespace(goal_id=goal_id, text=text, profile=None)


def test_sanitize_text_redacts_secrets_and_pii_while_keeping_study_context():
    raw = (
        "FastAPI에서 SQLAlchemy와 PostgreSQL을 연동할 때는 비동기 엔진 풀을 설정하고 트랜잭션 격리 수준을 맞춥니다. "
        "연결 문자열은 postgres://admin:SuperSecretPass99@10.0.1.25:5432/app 형태로 관리하며, "
        "외부 API 호출 시 Authorization: Bearer abcdef1234567890 헤더와 "
        "OPENAI_API_KEY=sk-proj-1234567890abcdefghij1234 환경변수를 주입합니다. "
        "실습 중 오류가 나면 담당자 dev@example.com 또는 010-1234-5678 로 로그를 공유하지 않도록 주의합니다."
    )
    res = sanitize_text_for_cloud(raw)
    assert not res.blocked
    assert "SuperSecretPass99" not in res.text
    assert "abcdef1234567890" not in res.text
    assert "sk-proj-1234567890abcdefghij1234" not in res.text
    assert "dev@example.com" not in res.text
    assert "010-1234-5678" not in res.text
    assert "10.0.1.25" not in res.text
    assert "[CREDENTIALS]" in res.text
    assert "[AUTH_TOKEN]" in res.text
    assert "[API_KEY]" in res.text
    assert "[EMAIL]" in res.text
    assert "[PHONE]" in res.text
    assert "[IP_ADDR]" in res.text
    assert "FastAPI에서 SQLAlchemy와 PostgreSQL을 연동" in res.text


def test_sanitize_blocks_pem_private_key_and_excessive_env_dump():
    pem = "서버 인증서 설정 방법\n-----BEGIN RSA PRIVATE KEY-----\nMIIEowIBAAKCAQEA..."
    pem_res = sanitize_text_for_cloud(pem)
    assert pem_res.blocked is True
    assert pem_res.reason == "PEM_PRIVATE_KEY"

    env_dump = (
        "API_KEY=sk-1234567890abcdefghij1234\n"
        "DB_PASSWORD=my_very_long_secret_password_999\n"
        "JWT_SECRET=another_super_long_secret_token_value\n"
        "CLIENT_SECRET=yet_another_secret_credential_string_1234"
    )
    env_res = sanitize_text_for_cloud(env_dump)
    assert env_res.blocked is True
    assert env_res.reason == "EXCESSIVE_REDACTION"


def test_jev_matcher_never_calls_api_when_blocked_and_redacts_both_state_and_questions():
    class RecordingClient:
        def __init__(self):
            self.calls = []

        async def system_one(self, *, state, questions):
            self.calls.append((state, questions))
            return SimpleNamespace(
                answers={
                    "match_g1": SimpleNamespace(noul=0.90),
                    "is_different_study_topic": SimpleNamespace(noul=0.05),
                }
            )

        async def aclose(self):
            return None

    matcher = TypeSafeJevGoalMatcher(Settings(), timeout_seconds=1.0, max_retries=1)
    client = RecordingClient()
    matcher._client = client

    # 1. PEM private key -> blocked before any API call
    with pytest.raises(ModelUnavailableError):
        asyncio.run(
            matcher.judge(
                title="SSH 키",
                body="-----BEGIN OPENSSH PRIVATE KEY-----\n" + "A" * 120,
                content_source="CHROME_DOM",
                goals=[_goal("g1", "SSH 인증 공부")],
            )
        )
    assert len(client.calls) == 0

    # 2. Study text with embedded secret -> API called with redacted state & questions
    secret_goal = _goal("g1", "FastAPI JWT 인증 Bearer eyJhbGciOiJIUzI1NiJ9.abcdef12345.sig123456 구현")
    study_body = (
        "FastAPI에서 JWT 토큰을 검증할 때 Authorization: Bearer eyJhbGciOiJIUzI1NiJ9.abcdef12345.sig123456 "
        "헤더를 파싱하고 공개 키로 서명을 검증한 뒤 만료 시간을 확인하여 사용자 인증을 처리합니다."
    )
    result = asyncio.run(
        matcher.judge(
            title="FastAPI JWT 인증",
            body=study_body,
            content_source="CHROME_DOM",
            goals=[secret_goal],
        )
    )
    assert result.goal_scores == {"g1": 0.90}
    assert len(client.calls) == 1
    sent_state, sent_questions = client.calls[0]
    assert "eyJhbGciOiJIUzI1NiJ9" not in str(sent_state)
    assert "eyJhbGciOiJIUzI1NiJ9" not in sent_questions["match_g1"].instructions
    assert "[AUTH_TOKEN]" in sent_state["screen"]["body"]


def test_match_goal_async_returns_not_enough_when_cloud_sanitizer_blocks():
    from AI.tests.test_goal_match_jev_e5 import FakeClassifier, _content

    matcher = TypeSafeJevGoalMatcher(Settings(), timeout_seconds=1.0, max_retries=1)
    matcher._client = SimpleNamespace(
        system_one=lambda **kwargs: (_ for _ in ()).throw(AssertionError("should not be called")),
        aclose=lambda: None,
    )
    pem_body = "SSH 키 설정 방법\n-----BEGIN RSA PRIVATE KEY-----\n" + "X" * 100
    content = _content(text=pem_body, page_title="SSH 키")
    payload = {"contentSource": "CHROME_DOM", "metadata": {"pageTitle": "SSH 키"}, "contentPayload": {"text": pem_body}}
    classifier = FakeClassifier({"SSH 인증": 0.90})

    res = asyncio.run(match_goal_async(classifier, content, payload, [_goal("g1", "SSH 인증")], jev_matcher=matcher))
    assert res == (None, "NOT_ENOUGH")
