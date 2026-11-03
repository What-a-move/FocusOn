"""Synthetic-score checks for development-only calibration, with no model calls."""
import copy
import json
from dataclasses import asdict

import pytest

from AI.evaluation.calibrate_graphless import (
    calibrate_reports, evaluate_rows, main, replay_rows, select_config,
    threshold_grid, validate_fixture,
)
from AI.evaluation.graphless_hybrid import GraphlessConfig


def test_real_fixture_preserves_distinct_goal_ids_before_model_calls():
    from pathlib import Path
    from AI.evaluation.calibrate_graphless import parse_fixture_group
    path = Path(__file__).resolve().parents[1] / 'evaluation' / 'graphless-calibration-cases.json'
    fixture = json.loads(path.read_text())
    ids = []
    for group in fixture['groups']:
        goal, cases = parse_fixture_group(group)
        assert goal.goal_id == group['goal']['goalId']
        assert all(content.goal_id == goal.goal_id for _, content, _, _ in cases)
        ids.append(goal.goal_id)
    assert len(set(ids)) == len(ids)


def config(minimum=.4, related=.5, unrelated=.25):
    return GraphlessConfig(min_related_cosine=minimum, related_passage_threshold=related,
                           unrelated_cosine_threshold=unrelated)


def row(case_id, gold, cosine, hybrid=None, **extra):
    return {
        "caseId": case_id, "expectedLabel": gold, "status": "COMPLETED",
        "relevanceLabel": "UNCERTAIN", "reasonCode": "INSUFFICIENT_EVIDENCE",
        "allowUnrelated": True,
        "matches": [{"passageId": case_id + "-body", "sourceIds": [case_id + "-body"],
                     "unitType": "PASSAGE", "kind": "BODY", "text": "본문 근거 진단용 문자열 " * 20,
                     "cosine": cosine, "hybridScore": cosine if hybrid is None else hybrid,
                     "keywordScore": 0.0}],
        **extra,
    }


def groups():
    return [
        {"goalId": "dev-goal", "split": "development", "rows": [
            row("dev-r", "RELATED", .52, .54),
            row("dev-n", "UNRELATED", .15),
            row("dev-u", "UNCERTAIN", .33, .35),
        ]},
        {"goalId": "held-out-goal", "split": "evaluation", "rows": [
            row("eval-r", "RELATED", .51, .55),
            row("eval-n", "UNRELATED", .10),
            row("eval-u", "UNCERTAIN", .34, .35),
        ]},
    ]


def test_grid_separates_thresholds_and_keeps_fixed_noncalibrated_settings():
    candidates = threshold_grid()
    assert candidates
    assert len({tuple(asdict(candidate).items()) for candidate in candidates}) == len(candidates)
    for candidate in candidates:
        assert candidate.unrelated_cosine_threshold < candidate.min_related_cosine
        assert candidate.related_passage_threshold >= candidate.min_related_cosine
        assert candidate.keyword_boost == .2
        assert candidate.unrelated_coverage_threshold == .7
        assert candidate.unrelated_min_body_chars == 80


def test_evaluation_labels_cannot_select_or_break_ties_for_thresholds():
    candidates = [config(), config(.55, .6)]
    original = calibrate_reports(groups(), candidates)
    changed = groups()
    for item in changed[1]["rows"]:
        item["expectedLabel"] = "UNRELATED" if item["expectedLabel"] != "UNRELATED" else "RELATED"
    other = calibrate_reports(changed, candidates)
    assert original["selectedConfig"] == other["selectedConfig"] == asdict(config())
    assert original["selection"] == other["selection"]
    assert original["evaluation"]["metrics"] != other["evaluation"]["metrics"]


def test_failures_are_not_rewarded_as_correct_uncertain_predictions():
    rows = [
        row("complete", "RELATED", .8, relevanceLabel="RELATED"),
        {"caseId": "empty", "expectedLabel": "UNCERTAIN", "relevanceLabel": "UNCERTAIN",
         "status": "SKIPPED", "reasonCode": "EMPTY_CONTENT", "matches": []},
        {"caseId": "failed", "expectedLabel": "UNCERTAIN", "relevanceLabel": "UNCERTAIN",
         "status": "FAILED", "reasonCode": "MODEL_OR_SCORING_FAILED", "matches": []},
    ]
    metrics = evaluate_rows(rows)
    assert metrics["pipelineFailures"] == 1
    assert metrics["semanticEligibleCases"] == 2
    assert metrics["excludedFromSemanticMetrics"] == 1
    assert metrics["confusion"]["UNCERTAIN"]["UNCERTAIN"] == 1
    assert metrics["correct"] == 2
    assert metrics["endToEndAccuracy"] == pytest.approx(2 / 3)
    assert metrics["semanticAccuracy"] == 1


def test_safety_constraint_takes_priority_over_more_related_predictions():
    dev = [row("r", "RELATED", .8), row("n", "UNRELATED", .52, .54),
           row("u", "UNCERTAIN", .33, .35)]
    selected, selection, trials = select_config(dev, [config(), config(.55, .6)])
    assert selected == config(.55, .6)
    assert selection["constraintSatisfied"]
    assert selection["developmentMetrics"]["unrelatedToRelated"] == 0
    assert len(trials) == 2


def test_no_safe_candidate_is_explicitly_reported():
    _, selection, _ = select_config([row("n", "UNRELATED", .9), row("r", "RELATED", .8),
                                     row("u", "UNCERTAIN", .33)], [config()])
    assert not selection["constraintSatisfied"]
    assert selection["developmentMetrics"]["unrelatedToRelated"] == 1


def test_failed_only_or_missing_class_data_cannot_publish_safe_calibration():
    failed = {'caseId': 'failure', 'expectedLabel': 'UNRELATED', 'status': 'FAILED',
              'relevanceLabel': 'UNCERTAIN', 'reasonCode': 'MODEL_OR_SCORING_FAILED', 'matches': []}
    with pytest.raises(ValueError, match='CALIBRATION_DEVELOPMENT_UNUSABLE'):
        select_config([failed])
    with pytest.raises(ValueError, match='CALIBRATION_CLASS_SUPPORT_REQUIRED'):
        select_config([row('n', 'UNRELATED', .1)])


def test_ties_prefer_conservative_thresholds():
    dev = groups()[0]["rows"]
    higher = config(.45, .5, .2)
    lower = config(.4, .5, .25)
    selected, _, _ = select_config(dev, [lower, higher])
    assert selected == higher


def test_replay_keeps_partial_extraction_guard_and_does_not_mutate_raw_signals():
    source = row("partial", "UNCERTAIN", .1, allowUnrelated=False)
    before = copy.deepcopy(source)
    replayed = replay_rows([source], config())
    assert source == before
    assert replayed[0]["relevanceLabel"] == "UNCERTAIN"
    assert replayed[0]["unrelatedCoverage"] == 1


def test_reports_do_not_persist_source_text_or_vectors():
    report = calibrate_reports(groups(), [config()])
    encoded = json.dumps(report, ensure_ascii=False)
    assert "본문 근거 진단용 문자열" not in encoded
    assert '"text"' not in encoded
    assert '"vectors"' not in encoded
    assert report["development"]["rows"][0]["scores"][0]["cosine"] == .52


def test_goal_overlap_between_splits_is_rejected():
    data = groups()
    data[1]["goalId"] = data[0]["goalId"]
    with pytest.raises(ValueError, match="CALIBRATION_GOAL_LEAKAGE"):
        calibrate_reports(data, [config()])


def test_fixture_rejects_split_leakage_before_model_client_is_created(tmp_path, monkeypatch):
    from src.models import embedding_client

    def forbidden(*args, **kwargs):
        pytest.fail("Invalid fixture must not create a model client")

    monkeypatch.setattr(embedding_client, "OllamaEmbeddingClient", forbidden)
    fixture = {"groups": [
        {"goal": {"goalId": "same"}, "split": split,
         "cases": [{"caseId": split, "expectedLabel": "RELATED"}]}
        for split in ("development", "evaluation")
    ]}
    path = tmp_path / "fixture.json"
    path.write_text(json.dumps(fixture))
    with pytest.raises(ValueError, match="CALIBRATION_GOAL_LEAKAGE"):
        main(["--fixture", str(path), "--output-dir", str(tmp_path)])


def test_fixture_requires_unique_case_ids_and_both_splits():
    fixture = {"groups": [
        {"goal": {"goalId": "dev"}, "split": "development",
         "cases": [{"caseId": "same", "expectedLabel": "RELATED"}]},
        {"goal": {"goalId": "evaluation"}, "split": "evaluation",
         "cases": [{"caseId": "same", "expectedLabel": "UNCERTAIN"}]},
    ]}
    with pytest.raises(ValueError, match="CALIBRATION_CASE_ID_DUPLICATE"):
        validate_fixture(fixture)
    fixture["groups"] = fixture["groups"][:1]
    with pytest.raises(ValueError, match="CALIBRATION_BOTH_SPLITS_REQUIRED"):
        validate_fixture(fixture)
