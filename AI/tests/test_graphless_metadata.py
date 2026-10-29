"""Metadata uses actual titles and its own thresholds, never body gates."""
import json
import math

import pytest

from AI.evaluation.graphless_hybrid import score_metadata, metadata_titles, metadata_is_informative
from AI.evaluation.streamlit_runner import compare_graphless, parse_inputs


class Client:
    fingerprint = "metadata-unit-test"
    dimensions = 2

    def __init__(self, fail=False):
        self.calls = 0
        self.inputs = []
        self.fail = fail

    def embed(self, texts):
        self.calls += 1
        self.inputs.extend(texts)
        if self.fail:
            raise ValueError("test model unavailable")
        return tuple((1., 0.) for _ in texts)


def run(payload, client=None):
    goal, cases = parse_inputs("JWT 인증 구현하기", json.dumps(payload), "TAB_META")
    client = client or Client()
    return compare_graphless(goal, cases, client)["rows"][0], client


@pytest.mark.parametrize("source,key", [("APP_META", "windowTitle"), ("TAB_META", "pageTitle")])
def test_short_title_without_body_can_pass(source, key):
    row, client = run({"contentSource": source, key: "JWT", "text": ""})
    assert row["status"] == "COMPLETED"
    assert row["relevanceLabel"] == "RELATED"
    assert row["analysisPath"] == "METADATA" and row["coverageApplicable"] is False
    assert row["totalBodyChars"] == 0 and row["bodyScore"] is None
    assert row["metadataScore"] == 1 and row["evidence"][0]["text"] == "JWT"
    assert client.calls == 1


@pytest.mark.parametrize("source,next_source", [("TAB_META", "CHROME_DOM"), ("APP_META", "DESKTOP_APP_OCR")])
def test_generic_title_is_completed_uncertain_without_model(source, next_source):
    row, client = run({"contentSource": source, "windowTitle": "학습 자료", "text": ""})
    assert row["status"] == "COMPLETED" and row["relevanceLabel"] == "UNCERTAIN"
    assert row["nextContentSource"] == next_source and client.calls == 0


@pytest.mark.parametrize("source,app,title", [
    ("APP_META", "Preview", "학습 자료 - Preview"),
    ("APP_META", "Visual Studio Code", "참고 문서 — Visual Studio Code"),
    ("TAB_META", "Google Chrome", "참고 페이지 - Google Chrome"),
    ("TAB_META", "Google Chrome", "문서 내용 - Google Chrome"),
    ("TAB_META", "Safari", "자료 파일 | Safari"),
    ("TAB_META", "Firefox", "Untitled document – Firefox"),
    ("APP_META", "Preview", "Preview"),
])
def test_app_decorations_do_not_turn_generic_metadata_into_evidence(source, app, title):
    row, client = run({"contentSource": source, "appName": app, "windowTitle": title, "text": ""})
    assert row["status"] == "COMPLETED" and row["relevanceLabel"] == "UNCERTAIN"
    assert row["reasonCode"] == "METADATA_INSUFFICIENT" and client.calls == 0
    assert row["evidence"] == []
    assert row["nextContentSource"] == ("CHROME_DOM" if source == "TAB_META" else "DESKTOP_APP_OCR")


def test_generic_metadata_is_not_unrelated_even_with_low_cosine():
    titles = ("문서 내용", "참고 페이지 - Google Chrome", "학습 자료 - Preview")
    result = score_metadata("JWT 인증", titles, (1., 0.), ((0., 1.),) * len(titles))
    assert result["relevanceLabel"] == "UNCERTAIN" and result["evidenceIds"] == []
    assert not any(row["unrelatedEvidence"] for row in result["matches"])


def test_generic_window_title_cannot_conflict_with_informative_page_title():
    titles = ("JWT 인증", "참고 문서 — Visual Studio Code")
    result = score_metadata("JWT 인증", titles, (1., 0.), ((1., 0.), (0., 1.)),
                            app_name="Visual Studio Code")
    assert result["relevanceLabel"] == "RELATED"
    assert result["matches"][1]["informative"] is False
    assert result["evidenceIds"] == ["metadata-0"]


def test_subject_and_app_mentions_survive_decoration_cleanup():
    assert metadata_titles("JWT 인증", "JWT 인증 - Google Chrome") == ("JWT 인증", "JWT 인증 - Google Chrome")
    for title in ("자료구조", "문서 객체 모델 DOM", "Preview 기능 구현", "Chrome 확장 프로그램"):
        assert metadata_titles(title, "") == (title,) and metadata_is_informative(title)
    row, client = run({"contentSource": "TAB_META", "appName": "Google Chrome",
                       "windowTitle": "JWT 인증 - Google Chrome", "text": ""})
    assert row["relevanceLabel"] == "RELATED" and client.calls == 1
    assert client.inputs[-1] == "JWT 인증 - Google Chrome"


def test_unrelated_metadata_requires_body_in_mvp():
    class UnrelatedClient(Client):
        def embed(self, texts):
            super().embed(texts)
            return tuple((1., 0.) if text.startswith("Instruct:") else (0., 1.) for text in texts)

    row, client = run({"contentSource": "TAB_META", "appName": "Google Chrome",
                       "windowTitle": "게임 방송 - Google Chrome", "text": ""}, UnrelatedClient())
    assert row["relevanceLabel"] == "UNCERTAIN" and client.calls == 1
    assert row["nextContentSource"] == "CHROME_DOM"


def test_app_host_and_accidental_body_are_not_metadata_evidence():
    row, client = run({"contentSource": "TAB_META", "pageTitle": "학습 자료", "urlHost": "jwt.example",
                       "appName": "JWT", "text": "JWT 인증 구현"})
    assert row["relevanceLabel"] == "UNCERTAIN" and client.calls == 0


def test_title_conflict_and_short_unrelated_do_not_use_body_coverage():
    decision = score_metadata("JWT 인증", ("JWT 서명", "게임 방송"), (1., 0.), ((1., 0.), (0., 1.)))
    assert decision["relevanceLabel"] == "UNCERTAIN" and decision["reasonCode"] == "METADATA_CONFLICTING"
    decision = score_metadata("JWT 인증", ("게임",), (1., 0.), ((0., 1.),))
    assert decision["relevanceLabel"] == "UNCERTAIN" and decision["totalBodyChars"] == 0
    assert score_metadata("JWT 인증", ("게임",), (1., 0.), ((0., 1.),), allow_unrelated=False)["relevanceLabel"] == "UNCERTAIN"


def test_metadata_keeps_conservative_threshold_independent_of_body_calibration():
    vector = (.4, math.sqrt(1 - .4 ** 2))
    assert score_metadata("JWT 인증", ("JWT",), (1., 0.), (vector,))["relevanceLabel"] == "UNCERTAIN"
    with pytest.raises(ValueError, match="VECTOR_COUNT_INVALID"):
        score_metadata("JWT 인증", ("JWT",), (1., 0.), ())


@pytest.mark.parametrize("extra", [{"isSensitive": True}, {"isExcluded": True},
                                   {"isActiveTarget": False}, {"pageTitle": "test@example.com"}])
def test_metadata_retains_policy_guards(extra):
    row, client = run({"contentSource": "TAB_META", "pageTitle": "JWT 인증", **extra})
    assert row["relevanceLabel"] != "RELATED" and client.calls == 0


def test_model_failure_not_reported_as_correct_uncertain():
    row, _ = run({"contentSource": "TAB_META", "pageTitle": "JWT", "expectedLabel": "UNCERTAIN"}, Client(fail=True))
    assert row["status"] == "FAILED" and row["matchesExpected"] is False


def test_expectations_never_enter_metadata_inference():
    first, a = run({"contentSource": "TAB_META", "pageTitle": "JWT", "expectedLabel": "RELATED"})
    second, b = run({"contentSource": "TAB_META", "pageTitle": "JWT", "expectedLabel": "UNRELATED"})
    assert a.inputs == b.inputs and first["relevanceLabel"] == second["relevanceLabel"]
