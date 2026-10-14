"""Provider-free tests for the learning-summary MVP contract."""

import unittest

from src.learning_summary import (
    ReviewEvidence,
    SummaryContractError,
    SummaryRequest,
    generate_summary,
    prepare_payload,
)


def evidence(index: int, **changes: object) -> ReviewEvidence:
    values = {
        "concept": f"JWT {index}",
        "observation": f"인증 필터 자료 {index}을 살펴봄",
        "observed_at": f"2026-10-14T01:00:{index:02d}Z",
        "confidence": 0.9,
        "content_hash": f"{index:064x}",
        "relevance_label": "RELATED",
    }
    values.update(changes)
    return ReviewEvidence(**values)


def request(*items: ReviewEvidence, **changes: object) -> SummaryRequest:
    values = {
        "goal_snapshot": "JWT 인증 흐름 학습",
        "evidences": items,
        "session_status": "ENDED",
    }
    values.update(changes)
    return SummaryRequest(**values)


def safe_output(first: str) -> dict[str, object]:
    return {
        "overview": "JWT 인증 흐름과 필터 관련 자료를 살펴봤습니다.",
        "keyConcepts": [
            {
                "name": "JWT 인증 필터",
                "briefExplanation": "인증 필터에 관한 자료가 관찰됐습니다.",
                "sessionEvidence": first,
            }
        ],
        "nextStartPrompt": "다음에는 인증 필터 순서 자료부터 다시 살펴보세요.",
    }


class LearningSummaryTests(unittest.TestCase):
    def test_two_distinct_related_evidences_generate_ready(self) -> None:
        calls = []

        def fake_generate(system: str, payload: dict[str, object]) -> dict[str, object]:
            calls.append((system, payload))
            return safe_output(payload["evidence"][0]["text"])

        result = generate_summary(request(evidence(1), evidence(2)), fake_generate)
        self.assertEqual("READY", result.status)
        self.assertEqual(set(safe_output(evidence(1).text)), set(result.summary))
        self.assertEqual(1, len(calls))
        self.assertIn("untrusted page content", calls[0][0])
        self.assertEqual("JWT 인증 흐름 학습", calls[0][1]["goalSnapshot"])

    def test_insufficient_and_duplicate_evidence_never_call_model(self) -> None:
        for items in (
            (),
            (evidence(1),),
            (evidence(1), evidence(2, content_hash=evidence(1).content_hash)),
            (evidence(1), evidence(2, concept=evidence(1).concept, observation=evidence(1).observation)),
        ):
            with self.subTest(items=items):
                called = []
                result = generate_summary(request(*items), lambda *_: called.append(True))
                self.assertEqual("INSUFFICIENT_DATA", result.status)
                self.assertEqual([], called)

    def test_excluded_uncertain_low_confidence_and_sensitive_evidence_are_skipped(self) -> None:
        items = (
            evidence(1),
            evidence(2, relevance_label="UNRELATED"),
            evidence(3, relevance_label="UNCERTAIN"),
            evidence(4, relevance_label="EXCLUDED"),
            evidence(5, relevance_label="PRIVACY_BLOCKED"),
            evidence(6, confidence=0.74),
            evidence(7, observation="이메일 test@example.com 확인"),
            evidence(8, observation="https://example.com/path 방문"),
            evidence(9),
        )
        payload = prepare_payload(request(*items))
        self.assertEqual([evidence(1).text, evidence(9).text], [item["text"] for item in payload["evidence"]])

    def test_no_consent_or_record_only_never_call_model(self) -> None:
        for settings in ({"record_consent": False}, {"record_only": True}):
            called = []
            result = generate_summary(
                request(evidence(1), evidence(2), **settings),
                lambda *_: called.append(True),
            )
            self.assertEqual("INSUFFICIENT_DATA", result.status)
            self.assertEqual([], called)

    def test_session_must_be_ended(self) -> None:
        with self.assertRaisesRegex(SummaryContractError, "SESSION_NOT_ENDED"):
            prepare_payload(request(evidence(1), evidence(2), session_status="RUNNING"))

    def test_caps_evidence_and_payload_size(self) -> None:
        items = tuple(evidence(index, observation="자료를 살펴봄" + "가" * 70) for index in range(1, 31))
        payload = prepare_payload(request(*items))
        self.assertLessEqual(len(payload["evidence"]), 20)
        import json

        self.assertLessEqual(len(json.dumps(payload, ensure_ascii=False, separators=(",", ":"))), 2400)
        self.assertGreaterEqual(len(payload["evidence"]), 2)

    def test_rejects_hallucinated_evidence_and_claims(self) -> None:
        items = (evidence(1), evidence(2))
        payload = prepare_payload(request(*items))
        invented = safe_output("존재하지 않는 근거")
        with self.assertRaisesRegex(SummaryContractError, "MODEL_OUTPUT_UNGROUNDED"):
            generate_summary(request(*items), lambda *_: invented)

        achievement = safe_output(payload["evidence"][0]["text"])
        achievement["overview"] = "JWT 구현을 완료했다."
        with self.assertRaisesRegex(SummaryContractError, "MODEL_OUTPUT_UNGROUNDED"):
            generate_summary(request(*items), lambda *_: achievement)

    def test_rejects_sensitive_oversized_and_extra_model_output(self) -> None:
        items = (evidence(1), evidence(2))
        first = evidence(1).text
        cases = (
            (dict(safe_output(first), reviewQuestions=[]), "MODEL_OUTPUT_INVALID"),
            (dict(safe_output(first), overview="test@example.com"), "MODEL_OUTPUT_SENSITIVE"),
            (dict(safe_output(first), overview="가" * 1300), "MODEL_OUTPUT_TOO_LONG"),
        )
        for output, code in cases:
            with self.subTest(code=code), self.assertRaisesRegex(SummaryContractError, code):
                generate_summary(request(*items), lambda *_: output)

    def test_provider_failure_does_not_expose_raw_exception(self) -> None:
        def failing_provider(*_: object) -> dict[str, object]:
            raise RuntimeError("secret page text must not be logged")

        with self.assertRaises(SummaryContractError) as caught:
            generate_summary(request(evidence(1), evidence(2)), failing_provider)
        self.assertEqual("MODEL_GENERATION_FAILED", str(caught.exception))

    def test_invalid_goal_and_evidence_are_rejected_or_skipped(self) -> None:
        with self.assertRaisesRegex(SummaryContractError, "GOAL_SNAPSHOT_INVALID"):
            prepare_payload(request(evidence(1), evidence(2), goal_snapshot="test@example.com"))
        self.assertIsNone(prepare_payload(request(evidence(1), evidence(2, confidence="bad"))))


if __name__ == "__main__":
    unittest.main()
