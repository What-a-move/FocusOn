import json
import os
import subprocess
import sys
from pathlib import Path

import pytest

from AI.evaluation.relevance_eval import fixture_input, metrics
from AI.src.preprocessing.adapter import adapt_content, preprocessing_context
from AI.src.preprocessing.models import PassageKind
from AI.src.preprocessing.validator import validate_and_preprocess

ROOT = Path(__file__).resolve().parents[2]


@pytest.mark.parametrize("split", ["development", "evaluation"])
def test_fake_cli_is_logic_only(split):
    completed = subprocess.run([sys.executable, "-m", "AI.evaluation.relevance_eval", "--mode", "fake", "--split", split,
                                "--cases", "AI/evaluation/relevance_cases.json", "--config", "AI/evaluation/relevance_calibration.json"], cwd=ROOT,
                               text=True, capture_output=True, check=False)
    assert completed.returncode == 0, completed.stdout + completed.stderr
    report = json.loads(completed.stdout)
    assert report["logicChecks"] == {"matched": 8, "total": 8}
    assert "provisionalMetrics" not in report
    assert "useState" not in completed.stdout and "synthetic@example" not in completed.stdout
    cases = {case["caseId"]: case for case in report["cases"]}
    reasons = {
        "development": {
            "dev-unrelated-title-related-body": "CONFLICTING_EVIDENCE",
            "dev-title-heading-only": "INSUFFICIENT_BODY",
            "dev-black-screen": "BLACK_SCREEN",
            "dev-long-title-insufficient-body": "INSUFFICIENT_EVIDENCE",
        },
        "evaluation": {
            "eval-related-title-unrelated-body": "CONFLICTING_EVIDENCE",
            "eval-heading-body-conflict": "CONFLICTING_EVIDENCE",
            "eval-unrelated-title-related-body": "CONFLICTING_EVIDENCE",
            "eval-sensitive": "PRIVACY_BLOCKED",
        },
    }
    for case_id, reason in reasons[split].items():
        assert cases[case_id]["reasonCode"] == reason
        assert not cases[case_id]["evidenceIds"]
    if split == "development":
        assert cases["dev-state-direct"]["evidenceIds"] == ["passage-001", "passage-002", "passage-003"]
    else:
        # A long neutral title must not dilute valid body coverage or become evidence.
        assert cases["eval-partial"]["evidenceIds"] == ["passage-002"]


def test_updated_dataset_preserves_split_and_structured_model_inputs():
    dataset = json.loads((ROOT / "AI/evaluation/relevance_cases.json").read_text())
    assert dataset["version"] == "synthetic-school-v2"
    assert dataset["synthetic"] is True and len(dataset["cases"]) == 16
    assert "human review pending" in dataset["annotationStatus"]
    families = {split: {case["templateId"] for case in dataset["cases"] if case["split"] == split}
                for split in ("development", "evaluation")}
    assert not families["development"] & families["evaluation"]
    assert len({case["caseId"] for case in dataset["cases"]}) == 16
    for split in families:
        assert sum(case["split"] == split for case in dataset["cases"]) == 8
    for case in dataset["cases"]:
        content, _, context = fixture_input(case)
        prepared = validate_and_preprocess(adapt_content(content, context), preprocessing_context(context))
        if prepared.should_analyze:
            assert len(case["fakePassageVectors"]) == len(prepared.passages)
            assert all(len(vector) == 6 for vector in case["fakePassageVectors"])
        if case["caseId"] == "dev-code-language-entity":
            assert [p.kind for p in prepared.passages] == [PassageKind.TITLE, PassageKind.HEADING, PassageKind.CODE]
            assert '```javascript title="state.js"' in prepared.passages[-1].text
            assert 'const literal = "&lt;";' in prepared.passages[-1].text


def test_real_requires_revision_and_does_not_fallback():
    env = {**os.environ, "FOCUSON_EMBEDDING_REVISION": ""}
    completed = subprocess.run([sys.executable, "-m", "AI.evaluation.relevance_eval", "--mode", "real",
                                "--cases", "AI/evaluation/relevance_cases.json", "--config", "AI/evaluation/relevance_calibration.json"],
                               cwd=ROOT, env=env, text=True, capture_output=True, check=False)
    report = json.loads(completed.stdout)
    assert completed.returncode == 2 and report["fakeFallback"] is False
    assert report["mode"] == "real" and report["status"] == "FAILED"


def test_metric_math_uses_all_expected_labels():
    rows = [{"expected": "RELATED", "predicted": "UNRELATED", "elapsedMs": 10, "role": "SUPPORTING"},
            {"expected": "RELATED", "predicted": "RELATED", "elapsedMs": 20, "role": "DIRECT"},
            {"expected": "UNCERTAIN", "predicted": "UNCERTAIN", "elapsedMs": 30, "role": None}]
    report = metrics(rows)
    assert report["perLabel"]["RELATED"]["precision"] == 1
    assert report["perLabel"]["RELATED"]["recall"] == 0.5
    assert report["supportingUnrelatedRate"] == 1
    assert report["latencyMs"] == {"p50": 20, "p95": 30}
