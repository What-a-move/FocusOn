"""Current Hybrid harness parsing and diagnostic vector lookup."""
import json
from pathlib import Path

import pytest

from AI.evaluation.streamlit_runner import RecordingClient, parse_inputs


class Client:
    fingerprint = "test-only-not-a-real-model"
    dimensions = 2

    def __init__(self):
        self.calls = 0

    def embed(self, texts):
        self.calls += 1
        return tuple((1., 0.) for _ in texts)


def inputs(payload=None, source="CHROME_DOM"):
    payload = payload or {"text": "React useState 상태 업데이트를 학습합니다."}
    return parse_inputs("React useState로 객체 상태 변경하기", json.dumps(payload, ensure_ascii=False), source)


def test_complete_existing_fixture_identity_is_preserved():
    fixture = Path(__file__).resolve().parents[1] / "evaluation" / "demo-chrome-dom.json"
    raw = fixture.read_text()
    goal, cases = parse_inputs("React useState로 객체 상태 변경하기", raw, "DESKTOP_APP_OCR", template_id="react-object-state")
    assert goal.goal_id == cases[0][1].goal_id == "goal-react-input-demo"
    assert cases[0][1].content_source.value == "CHROME_DOM"
    assert cases[0][1].client_run_id == "demo-dom-001"


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


def test_expired_diagnostic_vectors_do_not_trigger_another_model_call():
    client = Client()
    recorded = RecordingClient(client)
    recorded.embed(("synthetic text",))
    assert recorded.lookup(("synthetic text",)) == ((1., 0.),)
    recorded.vectors.clear()
    with pytest.raises(LookupError, match="DIAGNOSTIC_VECTORS_EXPIRED"):
        recorded.lookup(("synthetic text",))
    assert client.calls == 1
