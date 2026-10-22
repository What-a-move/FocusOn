"""Optional UI checks: install evaluation/requirements-streamlit.txt first."""
from pathlib import Path
import json

import pytest

pytest.importorskip("streamlit")
from streamlit.testing.v1 import AppTest
from AI.evaluation import streamlit_runner
from AI.evaluation.graphless_hybrid import GraphlessConfig


@pytest.fixture(autouse=True)
def fixed_test_config(monkeypatch):
    from AI.evaluation import graphless_hybrid
    monkeypatch.setattr(graphless_hybrid, "load_calibrated_config",
                        lambda: (GraphlessConfig(), {"status": "TEST_FIXED"}))

APP = Path(__file__).resolve().parents[1] / "evaluation" / "similarity_streamlit.py"


def test_page_requires_compare_click_and_hides_results_after_input_edit(monkeypatch):
    calls = []
    runtimes = []
    configs = []
    monkeypatch.setenv("GOAL_GRAPH_LLM_API_KEY", "")
    monkeypatch.setenv("GOAL_GRAPH_LLM_MODEL", "")

    def compare_graphless(*args, **kwargs):
        calls.append(args)
        runtimes.append(kwargs["session"])
        configs.append(kwargs["config"])
        return {"graphMs": 0, "totalMs": 10, "embeddingCalls": 0,
                "rows": [], "graph": None, "config": {},
                "summary": [{"방식": "6", "기대 일치": "1/1", "기대 일치율 %": 100}]}
    monkeypatch.setattr(streamlit_runner, "compare_graphless", compare_graphless)
    app = AppTest.from_file(str(APP), default_timeout=15).run()
    assert not app.exception
    assert not calls
    assert not app.multiselect
    assert all(box.label != "목표 Graph 준비" for box in app.selectbox)
    assert all(slider.label != "키워드 가중치" for slider in app.slider)
    assert all(control.label not in {"관련 문단 결합 점수 이상", "무관 후보 문단 결합 점수 이하"}
               for control in app.number_input)
    app.button[0].click().run()
    assert not app.exception
    assert "useState" in app.text_area[1].value
    assert not calls
    app.button[2].click().run()
    assert not app.exception
    assert not app.error
    assert len(calls) == 1
    assert len(calls[0]) == 3
    assert configs[0].keyword_boost == .2
    assert configs[0].unrelated_cosine_threshold == .25
    assert configs[0].related_passage_threshold == .6
    assert not hasattr(configs[0], "related_coverage_threshold")
    assert any("관련 커버리지는 참고 지표" in caption.value for caption in app.caption)
    assert len(app.dataframe) == 1
    app.button[2].click().run()
    assert not app.exception
    assert len(calls) == 2
    assert runtimes[0] is runtimes[1]
    app.text_area[1].set_value("다른 입력입니다.").run()
    assert not app.exception
    assert len(calls) == 2
    assert not app.dataframe


def test_invalid_json_does_not_call_model(monkeypatch):
    def forbidden(*args, **kwargs):
        pytest.fail("Invalid inputs must not reach inference")
    monkeypatch.setattr(streamlit_runner, "compare_graphless", forbidden)
    app = AppTest.from_file(str(APP), default_timeout=15).run()
    app.text_area(key="content").set_value('{"text":')
    app.button[2].click().run()
    assert not app.exception
    assert app.error


def test_different_dataset_goal_is_explained_before_inference(monkeypatch):
    def forbidden(*args, **kwargs):
        pytest.fail("A mismatched labelled dataset must not reach inference")
    monkeypatch.setattr(streamlit_runner, "compare_graphless", forbidden)
    app = AppTest.from_file(str(APP), default_timeout=15).run()
    app.text_area(key="goal").set_value("QueryDSL 동적 조회 구현")
    app.text_area(key="content").set_value(
        '{"goal":{"text":"로드 밸런싱 학습"},"cases":[{"text":"요청 분산과 헬스 체크를 공부합니다."}]}')
    app.button[2].click().run()
    assert not app.exception
    assert "goal.text" in app.error[0].value


def test_graphless_page_renders_scores_coverage_and_uses_no_graph(monkeypatch):
    from src.models import embedding_client
    from src.analysis import goal_graph_runtime

    def forbidden(*args, **kwargs):
        pytest.fail("Graph generation must not run from the graphless page")

    class Client:
        fingerprint, dimensions = "ui-synthetic", 2

        def __init__(self, settings):
            self.settings, self.calls = settings, 0

        def embed(self, texts):
            self.calls += 1
            return tuple((1., 0.) for _ in texts)

        def close(self):
            pass

    monkeypatch.setattr(goal_graph_runtime, "build_goal_graph_resolver", forbidden)
    monkeypatch.setattr(embedding_client, "OllamaEmbeddingClient", Client)
    app = AppTest.from_file(str(APP), default_timeout=15).run()
    app.button[0].click().run()
    app.button[2].click().run()
    assert not app.exception
    assert not app.error
    assert len(app.dataframe) == 2  # Summary and individual passage scores.
    assert [metric.value for metric in app.metric] == ["100.0%", "0.0%"]
    assert app.session_state["graphless_runtime"].client.calls == 1
    assert any("OpenAI API: 미호출 · 0회 · 0.000초" in caption.value for caption in app.caption)
    assert any("kiwi-weighted-terms-v3" in caption.value for caption in app.caption)
    assert any("H = S + 0.20 × K × (1 − S)" in caption.value for caption in app.caption)
    assert any("body-context-hybrid-v3" in caption.value for caption in app.caption)
    assert any("일반 표현 (단독 미반영)" in json.loads(item.value) for item in app.json)
    app.button[2].click().run()
    assert not app.exception
    assert app.session_state["graphless_runtime"].client.calls == 1


def test_metadata_page_shows_no_body_coverage(monkeypatch):
    from src.models import embedding_client

    class Client:
        fingerprint, dimensions = "metadata-ui-test", 2

        def __init__(self, settings):
            self.settings, self.calls = settings, 0

        def embed(self, texts):
            self.calls += 1
            return tuple((1., 0.) for _ in texts)

        def close(self):
            pass

    monkeypatch.setattr(embedding_client, "OllamaEmbeddingClient", Client)
    app = AppTest.from_file(str(APP), default_timeout=15).run()
    app.text_area[0].set_value("JWT 인증 구현하기")
    app.text_area[1].set_value(json.dumps({"contentSource": "APP_META", "windowTitle": "JWT 서명 검증", "text": "", "expectedLabel": "RELATED"}))
    app.button[2].click().run()
    assert not app.exception and not app.error
    assert not app.metric
    assert any("본문 커버리지: 해당 없음" in item.value for item in app.caption)
    assert len(app.dataframe) == 2
    assert any('METADATA_HYBRID_RELATED' in item.value for item in app.json)
