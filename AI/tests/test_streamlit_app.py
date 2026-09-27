from pathlib import Path

from streamlit.testing.v1 import AppTest


def test_streamlit_test_ui_renders_without_external_calls():
    app_path = Path(__file__).resolve().parents[1] / "streamlit_app.py"

    app = AppTest.from_file(app_path).run(timeout=10)

    assert not app.exception
    assert app.title[0].value == "🎯 FocusOn 목표 설정 AI 테스트"
    assert app.text_area[0].label == "학습 목표"
    assert app.sidebar.button[0].label == "전체 초기화"
    assert app.button[0].label == "목표 분석하기"
