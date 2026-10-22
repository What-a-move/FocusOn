"""Synthetic school harness. `fake` verifies logic; `real` never falls back."""
from __future__ import annotations

import argparse
import hashlib
import json
import math
import os
import platform
import statistics
import sys
import time
from pathlib import Path

from pydantic import Field

from ..src.api.schemas import ConfirmedGoal, ContentInput, InternalSchema, VerifiedContext
from ..src.analysis.goal_graph import build_goal_graph
from ..src.analysis.hybrid_scoring import HybridConfig
from ..src.analysis.relevance_analyzer import DEFAULT_INSTRUCTION, RelevanceAnalyzer, concept_queries
from ..src.models.embedding_client import EmbeddingError, EmbeddingSettings, OllamaEmbeddingClient
from ..src.preprocessing.adapter import adapt_content, preprocessing_context
from ..src.preprocessing.validator import validate_and_preprocess


class EvaluationConfig(InternalSchema):
    version: str
    status: str
    instruction: str = DEFAULT_INSTRUCTION
    hybrid: dict[str, float]
    cache_capacity: int = Field(ge=1, le=10_000)
    cache_ttl_seconds: float = Field(gt=0, le=3_600)


class FakeEmbeddingClient:
    """Exact synthetic vector table. No model and no expected-label inference."""
    fingerprint = "fake-scripted-v1"

    def __init__(self, dimensions: int = 6):
        self.dimensions, self.calls = dimensions, 0
        self._vectors: dict[str, tuple[float, ...]] = {}

    def register(self, text: str, vector: list[float] | tuple[float, ...]) -> None:
        self._vectors[hashlib.sha256(text.encode()).hexdigest()] = tuple(vector)

    def embed(self, texts):
        self.calls += 1
        try:
            return tuple(self._vectors[hashlib.sha256(text.encode()).hexdigest()] for text in texts)
        except KeyError:
            pass
        raise EmbeddingError("FAKE_VECTOR_NOT_REGISTERED")


def fixture_input(case: dict, policy_version: str = "school-v1") -> tuple[ContentInput, ConfirmedGoal, VerifiedContext]:
    observation = {
        "sessionId": "synthetic-session", "runId": "synthetic-run", "goalId": "synthetic-goal",
        "goalVersion": 1, "clientRunId": case["caseId"], "sequence": 1,
        "navigationId": case["caseId"], "observedAt": "2026-10-07T01:00:00Z",
        "contentSource": "CHROME_DOM", "isActiveTarget": True,
        "sessionStatus": "ACTIVE", "exclusionMode": "NONE",
    }
    content = ContentInput.parse_safe({**observation, "contentType": "DOCUMENT",
                                      "text": case["text"], "extractionStatus": "SUCCESS",
                                      "isSensitive": False, "isExcluded": False, **case.get("input", {})})
    content_data = content.model_dump(mode="json", by_alias=True)
    context_observation = {key: content_data[key] for key in observation}
    context = VerifiedContext.parse_safe({**context_observation, "userId": "synthetic-user", "resumeRequired": False,
                                         "policyVersion": policy_version, "extractorVersion": "synthetic-v1", "stateVersion": 1})
    goal = ConfirmedGoal.parse_safe({"goalId": "synthetic-goal", "goalVersion": 1, "text": case["goal"],
                                    "confirmed": True, "templateId": case["templateId"]})
    return content, goal, context


def register_fake_case(client: FakeEmbeddingClient, content, goal, context, vectors, instruction):
    graph = build_goal_graph(goal.text, goal.template_id)
    if graph is None:
        return
    queries = concept_queries(graph, instruction)
    if len(queries) > client.dimensions:
        raise ValueError("FAKE_DIMENSION_TOO_SMALL")
    for index, query in enumerate(queries):
        client.register(query, tuple(float(column == index) for column in range(client.dimensions)))
    prepared = validate_and_preprocess(adapt_content(content, context), preprocessing_context(context))
    if prepared.should_analyze:
        if len(vectors) != len(prepared.passages):
            raise ValueError("FIXTURE_VECTOR_COUNT_INVALID")
        for passage, vector in zip(prepared.passages, vectors):
            client.register(passage.text, vector)


def metrics(rows: list[dict]) -> dict:
    labels = ("RELATED", "UNRELATED", "UNCERTAIN", "EXCLUDED", "PRIVACY_BLOCKED")
    matrix = {expected: {predicted: 0 for predicted in labels} for expected in labels}
    for row in rows:
        if row["expected"] in matrix and row["predicted"] in labels:
            matrix[row["expected"]][row["predicted"]] += 1
    per_label, f1s = {}, []
    for label in labels:
        tp = matrix[label][label]
        precision_den = sum(matrix[expected][label] for expected in labels)
        recall_den = sum(matrix[label].values())
        precision, recall = tp / precision_den if precision_den else 0.0, tp / recall_den if recall_den else 0.0
        f1 = 2 * precision * recall / (precision + recall) if precision + recall else 0.0
        per_label[label] = {"precision": precision, "recall": recall, "f1": f1}
        f1s.append(f1)
    samples = sorted(row["elapsedMs"] for row in rows)
    supporting = [row for row in rows if row["role"] in {"PREREQUISITE", "SUPPORTING"}]
    return {"perLabel": per_label, "macroF1": statistics.mean(f1s), "confusionMatrix": matrix,
            "uncertainRate": sum(row["predicted"] == "UNCERTAIN" for row in rows) / len(rows),
            "supportingUnrelatedRate": sum(row["predicted"] == "UNRELATED" for row in supporting) / len(supporting) if supporting else None,
            "latencyMs": {"p50": statistics.median(samples), "p95": samples[max(0, math.ceil(0.95 * len(samples)) - 1)]}}


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--mode", choices=("fake", "real"), required=True)
    parser.add_argument("--cases", type=Path, required=True)
    parser.add_argument("--config", type=Path, required=True)
    parser.add_argument("--split", choices=("development", "evaluation"), default="evaluation")
    args = parser.parse_args(argv)
    client = None
    try:
        dataset = json.loads(args.cases.read_text())
        config = EvaluationConfig.parse_safe(json.loads(args.config.read_text()))
        hybrid = HybridConfig(**config.hybrid)
        if dataset.get("synthetic") is not True or not isinstance(dataset.get("cases"), list):
            raise ValueError("SYNTHETIC_FIXTURES_REQUIRED")
        cases = [case for case in dataset["cases"] if case["split"] == args.split]
        if not cases or len({case["caseId"] for case in cases}) != len(cases):
            raise ValueError("INVALID_CASE_SET")
        if any(case.get("expected") not in {"RELATED", "UNRELATED", "UNCERTAIN", "EXCLUDED", "PRIVACY_BLOCKED"}
               or case.get("role") not in {None, "DIRECT", "PREREQUISITE", "SUPPORTING"}
               for case in cases):
            raise ValueError("INVALID_EXPECTATION")
        # Goal families cannot leak between development and final evaluation.
        families = {split: {case["templateId"] for case in dataset["cases"] if case["split"] == split}
                    for split in ("development", "evaluation")}
        if families["development"] & families["evaluation"]:
            raise ValueError("GOAL_SPLIT_LEAKAGE")
        if args.mode == "fake":
            client = FakeEmbeddingClient()
        else:
            if not os.environ.get("FOCUSON_EMBEDDING_REVISION"):
                raise EmbeddingError("MODEL_REVISION_REQUIRED")
            client = OllamaEmbeddingClient(EmbeddingSettings(
                base_url=os.environ.get("FOCUSON_EMBEDDING_URL", "http://127.0.0.1:11435"),
                revision=os.environ.get("FOCUSON_EMBEDDING_REVISION", ""),
            ))
        analyzer = RelevanceAnalyzer(client, hybrid=hybrid, instruction=config.instruction,
                                     cache_capacity=config.cache_capacity, cache_ttl_seconds=config.cache_ttl_seconds)
        rows = []
        for case in cases:
            content, goal, context = fixture_input(case, config.version)
            if args.mode == "fake":
                register_fake_case(client, content, goal, context, case.get("fakePassageVectors", []), config.instruction)
            started = time.perf_counter()
            result = analyzer.analyze(content, goal, context, current_context=lambda: context)
            rows.append({"caseId": case["caseId"], "expected": case["expected"], "predicted": result.relevance_label,
                         "status": result.analysis_status, "reasonCode": result.reason_code, "evidenceIds": result.evidence_ids,
                         "roles": result.content_roles, "role": case.get("role"), "cacheHit": result.cache_hit,
                         "elapsedMs": (time.perf_counter() - started) * 1_000})
        report = {"mode": args.mode, "synthetic": True, "split": args.split,
                  "annotationStatus": dataset.get("annotationStatus", "unreviewed"),
                  "configStatus": config.status, "configHash": hashlib.sha256(args.config.read_bytes()).hexdigest(),
                  "datasetHash": hashlib.sha256(args.cases.read_bytes()).hexdigest(),
                  "python": platform.python_version(), "modelVersion": client.fingerprint,
                  "policyVersion": config.version, "embeddingCalls": client.calls, "cases": rows}
        if args.mode == "fake":
            report["logicChecks"] = {"matched": sum(row["expected"] == row["predicted"] for row in rows), "total": len(rows)}
            report["measurement"] = "scripted vectors; not model accuracy or GPU performance"
        else:
            report["provisionalMetrics"] = metrics(rows)
            report["serviceVersion"] = client.service_version
            report["inputTokens"] = client.prompt_tokens
            report["measurement"] = "synthetic full-pipeline timings; not isolated warm GPU timings or release validation"
        print(json.dumps(report, ensure_ascii=False))
        unexpected_failure = any(row["status"] == "FAILED" and row["reasonCode"] not in {"EXTRACTION_FAILED", "UNSUPPORTED"} for row in rows)
        return 2 if unexpected_failure else (1 if args.mode == "fake" and report["logicChecks"]["matched"] != len(rows) else 0)
    except (OSError, ValueError, TypeError, KeyError, EmbeddingError) as error:
        safe_codes = {"MODEL_REVISION_REQUIRED", "MODEL_REVISION_MISMATCH", "MODEL_PRECISION_MISMATCH", "MODEL_NOT_EMBEDDING", "MODEL_DIMENSION_MISMATCH", "MODEL_METADATA_INVALID", "SERVICE_VERSION_INVALID", "EMBEDDING_TIMEOUT", "EMBEDDING_UNAVAILABLE", "EMBEDDING_HTTP_ERROR", "EMBEDDING_RESPONSE_INVALID", "EMBEDDING_RESPONSE_TOO_LARGE"}
        code = str(error) if isinstance(error, EmbeddingError) and str(error) in safe_codes else "EVALUATION_FAILED"
        print(json.dumps({"mode": args.mode, "status": "FAILED", "reasonCode": code, "fakeFallback": False}))
        return 2
    finally:
        if isinstance(client, OllamaEmbeddingClient):
            client.close()


if __name__ == "__main__":
    sys.exit(main())
