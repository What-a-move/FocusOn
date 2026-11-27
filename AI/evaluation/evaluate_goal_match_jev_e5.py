"""Offline evaluator and threshold sweeper for E5 || JEV goal matching.

Supports either an exported JSON dataset (with precomputed or live E5/JEV signals)
or ``--smoke`` mode for verifying scoring and constraint checks without external data.
"""
from __future__ import annotations

import argparse
from dataclasses import asdict
import itertools
import json
from pathlib import Path
from types import SimpleNamespace
from typing import Iterable, Sequence

from AI.src.models.screen_activity.goal_match import DEFAULT_MATCH_CONFIG, MatchConfig, decide_goal_match
from AI.src.models.screen_activity.jev_goal_matcher import JevGoalMatchResult
from AI.src.preprocessing.models import ExtractionStatus


def score_cases(cases: Sequence[dict], config: MatchConfig = DEFAULT_MATCH_CONFIG) -> dict:
    """Evaluate MatchConfig on pre-collected E5 + JEV signals."""
    total = len(cases)
    exact_matches = 0
    expected_match_total = 0
    correct_match = 0
    wrong_match = 0
    expected_far_total = 0
    correct_far = 0
    wrong_far = 0

    for item in cases:
        goals = [SimpleNamespace(goal_id=gid) for gid in item["goalIds"]]
        jev_result = JevGoalMatchResult(
            goal_scores=item["jevGoalScores"],
            different_study_topic=float(item["jevDifferentStudyTopic"]),
        )
        pred_goal, pred_status = decide_goal_match(
            item["e5Scores"],
            jev_result,
            goals,
            metadata_only=bool(item.get("metadataOnly", False)),
            extraction_status=ExtractionStatus(item.get("extractionStatus", "SUCCESS")),
            is_study=bool(item.get("isStudy", True)),
            config=config,
        )
        exp_status = item["expectedStatus"]
        exp_goal = item.get("expectedGoalId")
        if (pred_status, pred_goal) == (exp_status, exp_goal):
            exact_matches += 1
        if exp_status == "MATCHED":
            expected_match_total += 1
            if (pred_status, pred_goal) == ("MATCHED", exp_goal):
                correct_match += 1
        elif pred_status == "MATCHED":
            wrong_match += 1
        elif exp_status == "FAR":
            expected_far_total += 1
            if pred_status == "FAR":
                correct_far += 1
        if pred_status == "FAR" and exp_status != "FAR":
            wrong_far += 1

    meets_target = (
        wrong_match <= 2
        and (expected_match_total == 0 or correct_match / expected_match_total >= 13 / 14)
        and (expected_far_total == 0 or correct_far / expected_far_total >= 10 / 14)
        and wrong_far <= 1
    )
    return {
        "total": total,
        "exactMatches": exact_matches,
        "expectedMatchTotal": expected_match_total,
        "correctMatch": correct_match,
        "wrongMatch": wrong_match,
        "expectedFarTotal": expected_far_total,
        "correctFar": correct_far,
        "wrongFar": wrong_far,
        "meetsTarget": meets_target,
    }


def sweep_thresholds(cases: Sequence[dict]) -> list[dict]:
    """Grid-search MatchConfig without re-invoking E5 or JEV."""
    candidates: list[dict] = []
    for w_jev, veto, match_min, floor, far_min in itertools.product(
        (0.55, 0.65, 0.75),
        (0.30, 0.35, 0.40),
        (0.50, 0.55, 0.60),
        (0.68, 0.72, 0.76),
        (0.60, 0.65, 0.70),
    ):
        cfg = MatchConfig(
            w_jev=w_jev,
            jev_veto_threshold=veto,
            jev_match_min=match_min,
            combined_match_floor=floor,
            jev_far_min=far_min,
        )
        metrics = score_cases(cases, cfg)
        candidates.append({"config": asdict(cfg), "metrics": metrics})
    candidates.sort(
        key=lambda row: (
            row["metrics"]["meetsTarget"],
            row["metrics"]["exactMatches"],
            -row["metrics"]["wrongMatch"],
            -row["metrics"]["wrongFar"],
        ),
        reverse=True,
    )
    return candidates


def _smoke_cases() -> list[dict]:
    return [
        {
            "caseId": "match-1",
            "goalIds": ["g1", "g2"],
            "e5Scores": {"g1": 0.88, "g2": 0.85},
            "jevGoalScores": {"g1": 0.90, "g2": 0.25},
            "jevDifferentStudyTopic": 0.10,
            "expectedStatus": "MATCHED",
            "expectedGoalId": "g1",
        },
        {
            "caseId": "veto-far-1",
            "goalIds": ["g1"],
            "e5Scores": {"g1": 0.92},
            "jevGoalScores": {"g1": 0.18},
            "jevDifferentStudyTopic": 0.88,
            "expectedStatus": "FAR",
            "expectedGoalId": None,
        },
        {
            "caseId": "not-enough-1",
            "goalIds": ["g1"],
            "e5Scores": {"g1": 0.84},
            "jevGoalScores": {"g1": 0.45},
            "jevDifferentStudyTopic": 0.30,
            "expectedStatus": "NOT_ENOUGH",
            "expectedGoalId": None,
        },
    ]


def main(argv: Iterable[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--cases", type=Path, help="Precomputed E5+JEV JSON cases file")
    parser.add_argument("--sweep", action="store_true", help="Run threshold grid search")
    parser.add_argument("--smoke", action="store_true", help="Run built-in smoke cases")
    args = parser.parse_args(list(argv) if argv is not None else None)

    if args.smoke:
        cases = _smoke_cases()
    elif args.cases is not None:
        cases = json.loads(args.cases.read_text(encoding="utf-8"))
    else:
        raise SystemExit("--cases 또는 --smoke를 지정하세요.")

    baseline = score_cases(cases, DEFAULT_MATCH_CONFIG)
    output: dict = {"baseline": {"config": asdict(DEFAULT_MATCH_CONFIG), "metrics": baseline}}
    if args.sweep:
        output["best"] = sweep_thresholds(cases)[0]
    print(json.dumps(output, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
