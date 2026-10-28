"""Calibrate the Streamlit experiment on development goals, then freeze for evaluation.

Only synthetic fixtures should be used with this diagnostic CLI. Reports omit
source text, goal text, prompts, and vectors. No settings file is written unless
--config-out is explicitly supplied.
"""
from __future__ import annotations

import argparse
import hashlib
import json
from collections import Counter
from dataclasses import asdict
from datetime import datetime, timezone
from pathlib import Path
from typing import Iterable, Sequence

from AI.evaluation.graphless_hybrid import GraphlessConfig

LABELS = ("RELATED", "UNRELATED", "UNCERTAIN")
NORMAL_ABSTENTION_REASONS = frozenset({"EMPTY_CONTENT", "INSUFFICIENT_BODY", "TOO_SHORT"})


def semantic_eligible(row: dict) -> bool:
    return row.get("status") == "COMPLETED" or (
        row.get("status") == "SKIPPED"
        and row.get("reasonCode") in NORMAL_ABSTENTION_REASONS
        and row.get("relevanceLabel") == "UNCERTAIN"
    )


def evaluate_rows(rows: Sequence[dict]) -> dict:
    """Separate semantic abstention from pipeline failure; never reward failures."""
    labelled = [row for row in rows if row.get("expectedLabel") in LABELS]
    eligible = [row for row in labelled if semantic_eligible(row)]
    confusion = {gold: {pred: 0 for pred in LABELS} for gold in LABELS}
    for row in eligible:
        predicted = row.get("relevanceLabel")
        if predicted not in LABELS:
            raise ValueError("CALIBRATION_PREDICTION_INVALID")
        confusion[row["expectedLabel"]][predicted] += 1
    per_class = {}
    for label in LABELS:
        tp = confusion[label][label]
        predicted_count = sum(confusion[gold][label] for gold in LABELS)
        support = sum(confusion[label].values())
        precision = tp / predicted_count if predicted_count else 0.0
        recall = tp / support if support else 0.0
        f1 = 2 * precision * recall / (precision + recall) if precision + recall else 0.0
        per_class[label] = {"precision": precision, "recall": recall, "f1": f1, "support": support}
    correct = sum(confusion[label][label] for label in LABELS)
    return {
        "cases": len(rows), "labelledCases": len(labelled), "semanticEligibleCases": len(eligible),
        "excludedFromSemanticMetrics": len(labelled) - len(eligible),
        "statusCounts": dict(Counter(row.get("status", "UNKNOWN") for row in rows)),
        "pipelineFailures": sum(row.get("status") == "FAILED" for row in rows),
        "confusion": confusion, "perClass": per_class,
        "macroF1": sum(item["f1"] for item in per_class.values()) / len(LABELS),
        "relatedRecall": per_class["RELATED"]["recall"],
        "unrelatedToRelated": confusion["UNRELATED"]["RELATED"],
        "uncertainRate": sum(row.get("relevanceLabel") == "UNCERTAIN" for row in eligible) / max(1, len(eligible)),
        "semanticAccuracy": correct / max(1, len(eligible)),
        "endToEndAccuracy": correct / max(1, len(labelled)),
        "correct": correct,
    }


def threshold_grid() -> tuple[GraphlessConfig, ...]:
    configs = []
    for minimum in (.30, .35, .40, .45, .50, .55):
        for related in (.35, .40, .45, .50, .55, .60):
            for unrelated in (.20, .25, .30):
                if related >= minimum and unrelated < minimum:
                    configs.append(GraphlessConfig(
                        keyword_boost=.20, min_related_cosine=minimum,
                        related_passage_threshold=related, unrelated_cosine_threshold=unrelated,
                        unrelated_coverage_threshold=.70, unrelated_min_body_chars=80,
                    ))
    return tuple(configs)


def replay_rows(rows: Sequence[dict], config: GraphlessConfig) -> list[dict]:
    # Import lazily: offline metric inspection need not initialize the tokenizer.
    from AI.evaluation.graphless_hybrid import decide_matches
    result = []
    for row in rows:
        replayed = dict(row)
        if row.get("status") == "COMPLETED" and row.get("matches"):
            decision = decide_matches(row["matches"], config,
                                      allow_unrelated=row.get("allowUnrelated", True))
            replayed.update(decision)
        result.append(replayed)
    return result


def select_config(development_rows: Sequence[dict],
                  candidates: Iterable[GraphlessConfig] | None = None) -> tuple[GraphlessConfig, dict, list[dict]]:
    if not development_rows:
        raise ValueError("CALIBRATION_DEVELOPMENT_EMPTY")
    usable = evaluate_rows(development_rows)
    if not usable["semanticEligibleCases"]:
        raise ValueError("CALIBRATION_DEVELOPMENT_UNUSABLE")
    if any(usable["perClass"][label]["support"] == 0 for label in LABELS):
        raise ValueError("CALIBRATION_CLASS_SUPPORT_REQUIRED")
    trials = []
    for config in (threshold_grid() if candidates is None else candidates):
        metrics = evaluate_rows(replay_rows(development_rows, config))
        trials.append((config, metrics))
    if not trials:
        raise ValueError("CALIBRATION_GRID_EMPTY")
    safe = [trial for trial in trials if trial[1]["unrelatedToRelated"] == 0]
    # If the safety constraint cannot be met, report that explicitly. The caller
    # must not silently publish an unsafe configuration as a successful result.
    pool = safe or trials
    selected, metrics = max(pool, key=lambda item: (
        -item[1]["unrelatedToRelated"], item[1]["macroF1"], -item[1]["uncertainRate"],
        item[0].min_related_cosine, item[0].related_passage_threshold,
        -item[0].unrelated_cosine_threshold,
    ))
    selection = {
        "constraintSatisfied": bool(safe),
        "criterion": "zero development UNRELATED->RELATED, maximum macro-F1, minimum abstention, conservative thresholds",
        "developmentMetrics": metrics,
        "candidateCount": len(trials),
    }
    return selected, selection, [{"config": asdict(config), "metrics": metric} for config, metric in trials]


def _public_rows(rows: Sequence[dict]) -> list[dict]:
    """Reports contain diagnostics, not the texts/vectors used for inference."""
    return [{
        "caseId": row["caseId"], "expectedLabel": row.get("expectedLabel"),
        "actualLabel": row.get("relevanceLabel"), "status": row.get("status"),
        "reasonCode": row.get("reasonCode"),
        "relatedCoverage": row.get("relatedCoverage"), "unrelatedCoverage": row.get("unrelatedCoverage"),
        "scores": [{key: match.get(key) for key in (
            "passageId", "unitType", "sourceIds", "kind", "cosine", "keywordScore", "hybridScore",
            "relatedEvidence", "unrelatedEvidence",
        )} for match in row.get("matches", [])],
    } for row in rows]


def calibrate_reports(group_reports: Sequence[dict],
                      candidates: Iterable[GraphlessConfig] | None = None) -> dict:
    if any(group.get("split") not in {"development", "evaluation"} for group in group_reports):
        raise ValueError("CALIBRATION_SPLIT_INVALID")
    development = [group for group in group_reports if group["split"] == "development"]
    evaluation = [group for group in group_reports if group["split"] == "evaluation"]
    if not development or not evaluation:
        raise ValueError("CALIBRATION_BOTH_SPLITS_REQUIRED")
    if {group["goalId"] for group in development} & {group["goalId"] for group in evaluation}:
        raise ValueError("CALIBRATION_GOAL_LEAKAGE")
    dev_rows = [row for group in development for row in group["rows"]]
    eval_rows = [row for group in evaluation for row in group["rows"]]
    selected, selection, trials = select_config(dev_rows, candidates)
    # Freeze before evaluation rows or labels are scored. No evaluation label
    # participates in candidate ranking or tie breaking.
    frozen_config = asdict(selected)
    dev_replayed = replay_rows(dev_rows, selected)
    eval_replayed = replay_rows(eval_rows, selected)
    return {
        "selectedConfig": frozen_config, "selection": selection,
        "development": {"metrics": evaluate_rows(dev_replayed), "rows": _public_rows(dev_replayed)},
        "evaluation": {"metrics": evaluate_rows(eval_replayed), "rows": _public_rows(eval_replayed)},
        "initialDevelopmentMetrics": evaluate_rows(dev_rows),
        "initialEvaluationMetrics": evaluate_rows(eval_rows),
        "trials": trials,
        "limitations": [
            "Manually authored synthetic development/evaluation data; not independently human-labelled real screens.",
            "Evaluation goals were excluded from threshold selection. Reusing these results for tuning makes them development data.",
            "Pipeline failures are excluded from semantic confusion but counted as failures and wrong in end-to-end accuracy.",
            "UNCERTAIN is a normal semantic label. SKIPPED empty-content abstentions are eligible; failed model requests are not.",
        ],
    }


def validate_fixture(fixture: dict) -> list[dict]:
    groups = fixture.get("groups", [])
    if not groups or any(group.get("split") not in {"development", "evaluation"} for group in groups):
        raise ValueError("CALIBRATION_FIXTURE_INVALID")
    split_goals = {split: {group["goal"]["goalId"] for group in groups if group["split"] == split}
                   for split in ("development", "evaluation")}
    if not all(split_goals.values()):
        raise ValueError("CALIBRATION_BOTH_SPLITS_REQUIRED")
    if split_goals["development"] & split_goals["evaluation"]:
        raise ValueError("CALIBRATION_GOAL_LEAKAGE")
    case_ids = []
    for group in groups:
        if not group.get("cases") or any(case.get("expectedLabel") not in LABELS for case in group["cases"]):
            raise ValueError("CALIBRATION_LABEL_INVALID")
        case_ids.extend(case["caseId"] for case in group["cases"])
    if len(case_ids) != len(set(case_ids)):
        raise ValueError("CALIBRATION_CASE_ID_DUPLICATE")
    return groups


def _markdown(report: dict) -> str:
    lines = ["# FocusOn Graphless 임계값 보정 실험", "",
             "개발 목표로만 기준을 선택한 뒤 동결하고 별도 목표를 평가했다. 합성 자료의 실험 결과이며 실제 화면 정확도가 아니다.", "",
             "## 선택한 기준", "", "```json", json.dumps(report["selectedConfig"], ensure_ascii=False, indent=2), "```", "",
             f"개발 무관→관련 0건 조건 충족: {report['selection']['constraintSatisfied']}", "",
             "## 지표", "", "| 분할 | 사례 | Macro-F1 | 관련 재현율 | 무관→관련 | 보류율 | 파이프라인 실패 | 종단 일치율 |",
             "|---|---:|---:|---:|---:|---:|---:|---:|"]
    for split in ("development", "evaluation"):
        for phase, metric in (("초기 기준", report["initialDevelopmentMetrics" if split == "development" else "initialEvaluationMetrics"]),
                              ("개발 보정 기준", report[split]["metrics"])):
            lines.append(f"| {split} · {phase} | {metric['cases']} | {metric['macroF1']:.4f} | {metric['relatedRecall']:.1%} | "
                         f"{metric['unrelatedToRelated']} | {metric['uncertainRate']:.1%} | {metric['pipelineFailures']} | {metric['endToEndAccuracy']:.1%} |")
    for split in ("development", "evaluation"):
        lines += ["", f"## {split} 사례", "", "| 사례 | 기대 | 실제 | 상태 | 사유 |", "|---|---|---|---|---|"]
        for row in report[split]["rows"]:
            lines.append(f"| {row['caseId']} | {row['expectedLabel']} | {row['actualLabel']} | {row['status']} | {row['reasonCode']} |")
    lines += ["", "## 제한사항", "", *[f"- {item}" for item in report["limitations"]]]
    return "\n".join(lines) + "\n"


def parse_fixture_group(group: dict):
    from AI.evaluation.streamlit_runner import parse_inputs
    payload = json.dumps({"goal": group["goal"], "cases": group["cases"]}, ensure_ascii=False)
    # Passing just goal.text would replace the declared identity with lab-goal.
    return parse_inputs(json.dumps(group["goal"], ensure_ascii=False), payload, "CHROME_DOM")


def main(argv: Sequence[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--fixture", type=Path, default=Path(__file__).with_name("graphless-calibration-cases.json"))
    parser.add_argument("--output-dir", type=Path, default=Path("/private/tmp"))
    parser.add_argument("--config-out", type=Path)
    parser.add_argument("--base-url")
    parser.add_argument("--revision")
    parser.add_argument("--timeout", type=float, default=120.0)
    args = parser.parse_args(argv)
    from AI.evaluation.streamlit_runner import (
        DEFAULT_REVISION, DEFAULT_URL, GraphlessSession, compare_graphless, parse_inputs,
    )
    from AI.evaluation.graphless_hybrid import SCORING_VERSION, KEYWORD_EXTRACTOR_VERSION
    from src.models.embedding_client import EmbeddingSettings, OllamaEmbeddingClient
    from src.models.embedding_client import DEFAULT_INSTRUCTION

    fixture_bytes = args.fixture.read_bytes()
    fixture = json.loads(fixture_bytes)
    groups = validate_fixture(fixture)
    reports, timing = [], []
    settings = EmbeddingSettings(
        base_url=args.base_url or DEFAULT_URL, revision=args.revision or DEFAULT_REVISION,
        timeout_seconds=args.timeout,
    )
    with OllamaEmbeddingClient(settings) as client:
        embedding = {"model": settings.model, "dimensions": settings.dimensions,
                     "configuredRevision": settings.revision, "fingerprint": client.fingerprint,
                     "instructionSha256": hashlib.sha256(DEFAULT_INSTRUCTION.encode()).hexdigest()}
        session = GraphlessSession(client)
        # Traverse development first even if the file order differs. Freeze the
        # selected thresholds before running the held-out goals through models.
        ordered = [group for split in ("development", "evaluation") for group in groups if group["split"] == split]
        frozen_before_evaluation = None
        for group in ordered:
            if group["split"] == "evaluation" and frozen_before_evaluation is None:
                frozen_before_evaluation = asdict(select_config(
                    [row for prior in reports for row in prior["rows"]]
                )[0])
            goal, cases = parse_fixture_group(group)
            result = compare_graphless(goal, cases, client, config=GraphlessConfig(), session=session)
            reports.append({"goalId": goal.goal_id, "split": group["split"], "rows": result["rows"]})
            timing.append({"goalId": goal.goal_id, "split": group["split"],
                           **{key: result[key] for key in ("totalMs", "embeddingMs", "localMs", "embeddingCalls")}})
            print(json.dumps({"goalId": goal.goal_id, "split": group["split"], "cases": len(cases),
                              "embeddingCalls": result["embeddingCalls"]}), flush=True)
    report = calibrate_reports(reports)
    if report["selectedConfig"] != frozen_before_evaluation:
        raise ValueError("CALIBRATION_CONFIG_CHANGED_AFTER_EVALUATION")
    report.update({"createdAt": datetime.now(timezone.utc).isoformat(), "scoringVersion": SCORING_VERSION,
                   "keywordExtractorVersion": KEYWORD_EXTRACTOR_VERSION,
                   "fixtureVersion": fixture.get("version"),
                   "fixtureSha256": hashlib.sha256(fixture_bytes).hexdigest(), "timing": timing,
                   "embedding": embedding})
    args.output_dir.mkdir(parents=True, exist_ok=True)
    json_path = args.output_dir / "focuson-graphless-calibration-results.json"
    md_path = args.output_dir / "FocusOn_Graphless_보정_평가.md"
    json_path.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    md_path.write_text(_markdown(report), encoding="utf-8")
    if args.config_out:
        if not report["selection"]["constraintSatisfied"]:
            raise ValueError("CALIBRATION_SAFETY_CONSTRAINT_UNMET_CONFIG_NOT_WRITTEN")
        args.config_out.parent.mkdir(parents=True, exist_ok=True)
        args.config_out.write_text(json.dumps({
            "scoringVersion": SCORING_VERSION, "config": report["selectedConfig"],
            "keywordExtractorVersion": KEYWORD_EXTRACTOR_VERSION,
            "constraintSatisfied": report["selection"]["constraintSatisfied"],
            "status": "SYNTHETIC_DEVELOPMENT_CALIBRATED",
            "fixtureSha256": report["fixtureSha256"], "selectedUsing": "development-only",
            "embedding": embedding,
            "developmentMetrics": report["development"]["metrics"],
            "evaluationMetrics": report["evaluation"]["metrics"],
            "reportPath": str(json_path),
        }, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({"json": str(json_path), "markdown": str(md_path),
                      "selectedConfig": report["selectedConfig"],
                      "development": report["development"]["metrics"],
                      "evaluation": report["evaluation"]["metrics"]}, ensure_ascii=False))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
