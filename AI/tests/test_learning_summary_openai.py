import unittest
from types import SimpleNamespace
from unittest.mock import Mock, patch

from src.learning_summary_demo import DemoEvidence
from src.learning_summary_openai import (
    SYNTHETIC_DOM_PAGES,
    SYNTHETIC_GOAL,
    _validated_summary,
    generate_fixed_synthetic_summary,
    generate_synthetic_dom_summary,
    LearningSummary,
    SummaryDraft,
)
from src.learning_summary_demo import SyntheticDOMPage, evidence_from_dom_pages

FIXTURE_EVIDENCE = evidence_from_dom_pages(SYNTHETIC_DOM_PAGES)


def valid_output():
    return {
        "keyConcepts": [
            {
                "name": "queryKey",
                "briefExplanation": "같은 queryKey와 기존 캐시 재사용",
                "sessionEvidence": "queryKey와 캐시 재사용 내용 확인",
                "evidenceIds": ["e1"],
            },
            {
                "name": "staleTime",
                "briefExplanation": "staleTime과 신선한 데이터로 취급",
                "sessionEvidence": "staleTime 동안 신선한 데이터 확인",
                "evidenceIds": ["e2"],
            },
        ],
    }


class LearningSummaryOpenAITests(unittest.TestCase):
    def test_fixed_fixture_only_and_provider_contract(self):
        client = Mock()
        client.responses.parse.return_value = SimpleNamespace(output_parsed=valid_output())
        result = generate_fixed_synthetic_summary(client=client, model="test-model")
        self.assertEqual(result.status, "READY")
        kwargs = client.responses.parse.call_args.kwargs
        self.assertEqual(kwargs["model"], "test-model")
        self.assertIs(kwargs["store"], False)
        self.assertEqual(kwargs["timeout"], 20.0)
        self.assertEqual(kwargs["text_format"].__name__, "SummaryDraft")
        sent = kwargs["input"][0]["content"]
        self.assertIn(SYNTHETIC_GOAL, sent)
        self.assertNotIn(SYNTHETIC_DOM_PAGES[0].title, sent)
        self.assertIn("queryKey", sent)
        self.assertNotIn("https://", sent)
        self.assertEqual(set(result.summary), {"overview", "keyConcepts", "nextStartPrompt"})
        self.assertEqual(set(result.summary["keyConcepts"][0]), {"name", "briefExplanation", "sessionEvidence"})
        self.assertEqual(result.summary["nextStartPrompt"], "다음에는 queryKey에서 확인한 내용을 이어서 살펴보세요.")

    def test_no_key_does_not_call_provider(self):
        with patch.dict("os.environ", {}, clear=True):
            self.assertEqual(generate_fixed_synthetic_summary().status, "FAILED")

    def test_bad_model_output_is_rejected(self):
        cases = []
        no_evidence = valid_output()
        no_evidence["keyConcepts"] = []
        cases.append(no_evidence)
        invented = valid_output()
        invented["keyConcepts"][0]["evidenceIds"] = ["invented"]
        cases.append(invented)
        wrong_concept = valid_output()
        wrong_concept["keyConcepts"][0]["name"] = "invented"
        cases.append(wrong_concept)
        pii = valid_output()
        pii["keyConcepts"][0]["briefExplanation"] = "test@example.com 주소를 확인했습니다."
        cases.append(pii)
        achievement = valid_output()
        achievement["keyConcepts"][0]["briefExplanation"] = "queryKey를 완벽히 이해했습니다."
        cases.append(achievement)
        unsupported = valid_output()
        unsupported["keyConcepts"][0]["briefExplanation"] = "queryKey가 메모리 사용량을 줄입니다."
        cases.append(unsupported)
        extra_field = valid_output()
        extra_field["url"] = "hidden"
        cases.append(extra_field)
        unsupported_overview = valid_output()
        unsupported_overview["overview"] = "메모리 사용량이 감소했습니다."
        cases.append(unsupported_overview)
        for output in cases:
            with self.subTest(output=output):
                client = Mock()
                client.responses.parse.return_value = SimpleNamespace(output_parsed=output)
                self.assertEqual(generate_fixed_synthetic_summary(client=client).status, "FAILED")

    def test_refusal_timeout_and_provider_failure_are_failed(self):
        for response in (SimpleNamespace(output_parsed=None), TimeoutError(), RuntimeError("secret")):
            client = Mock()
            if isinstance(response, Exception):
                client.responses.parse.side_effect = response
            else:
                client.responses.parse.return_value = response
            self.assertEqual(generate_fixed_synthetic_summary(client=client).status, "FAILED")

    def test_quiz_and_recommendation_fields_are_rejected_at_both_boundaries(self):
        from pydantic import ValidationError
        for field in ("reviewQuestions", "recommendedConcepts", "nextStartPrompt"):
            output = valid_output()
            output[field] = [] if field != "nextStartPrompt" else "새로운 개념을 공부하세요."
            self.assertIsNone(_validated_summary(output, FIXTURE_EVIDENCE))
        summary = _validated_summary(valid_output(), FIXTURE_EVIDENCE)
        for field in ("reviewQuestions", "recommendedConcepts"):
            with self.assertRaises(ValidationError):
                LearningSummary.model_validate(summary | {field: []})
        self.assertIs(SummaryDraft.model_json_schema()["additionalProperties"], False)
        self.assertIs(LearningSummary.model_json_schema()["additionalProperties"], False)

    def test_source_copies_questions_and_ungrounded_concepts_are_rejected(self):
        quote = valid_output()
        quote["keyConcepts"][0]["sessionEvidence"] = FIXTURE_EVIDENCE[0].observation
        question = valid_output()
        question["keyConcepts"][0]["briefExplanation"] = "queryKey는 무엇인가요?"
        ungrounded = valid_output()
        ungrounded["keyConcepts"][0]["name"] = "GraphQL"
        for output in (quote, question, ungrounded):
            self.assertIsNone(_validated_summary(output, FIXTURE_EVIDENCE))

    def test_overview_is_deterministic_and_not_taken_from_model(self):
        result = _validated_summary(valid_output(), FIXTURE_EVIDENCE)
        self.assertEqual(
            result["overview"],
            f"관련 근거 {len(FIXTURE_EVIDENCE)}개를 확인했습니다. 이해·구현 완료 여부는 이 기록만으로 판단하지 않습니다.",
        )

    def test_withdrawn_consent_does_not_call_model(self):
        client = Mock()
        result = generate_synthetic_dom_summary(SYNTHETIC_GOAL, SYNTHETIC_DOM_PAGES, client=client, record_consent=False)
        self.assertEqual(result.status, "INSUFFICIENT_DATA")
        client.responses.parse.assert_not_called()

    def test_more_than_five_concepts_and_oversized_output_are_rejected(self):
        too_many = valid_output()
        too_many["keyConcepts"] = [too_many["keyConcepts"][0]] * 6
        self.assertIsNone(_validated_summary(too_many, FIXTURE_EVIDENCE))
        too_long = valid_output()
        too_long["overview"] = "가" * 1201 + ". 핵심을 정리했습니다."
        self.assertIsNone(_validated_summary(too_long, FIXTURE_EVIDENCE))

    def test_unrelated_or_sensitive_evidence_cannot_be_used_as_grounding(self):
        result = _validated_summary(
            valid_output(),
            [DemoEvidence("다른 개념", "다른 자료", "RELATED")],
        )
        self.assertIsNone(result)

    def test_same_page_distinct_paragraphs_count_separately(self):
        page = SyntheticDOMPage(
            "DOCUMENTATION",
            "React Query 가이드",
            "useQuery는 queryKey를 기준으로 캐시합니다.\nstaleTime 동안 데이터는 신선한 것으로 취급됩니다.",
        )
        evidence = evidence_from_dom_pages([page])
        self.assertEqual(len(evidence), 2)
        self.assertEqual(evidence[0].concept, evidence[1].concept)

    def test_duplicate_paragraphs_and_non_related_pages_are_ignored(self):
        pages = [
            SyntheticDOMPage("DOCUMENTATION", "가이드", "캐시 데이터를 재사용합니다.\n캐시 데이터를 재사용합니다."),
            SyntheticDOMPage("DOCUMENTATION", "제외", "다른 내용을 설명하는 근거 문장입니다.", relation="EXCLUDED"),
        ]
        evidence = evidence_from_dom_pages(pages)
        self.assertEqual(len(evidence), 1)

    def test_openai_generation_skips_provider_when_less_than_two_paragraphs(self):
        client = Mock()
        page = SyntheticDOMPage("DOCUMENTATION", "가이드", "캐시 설명 한 문단입니다.")
        result = generate_synthetic_dom_summary("캐시 사용법을 공부한다", [page], client=client)
        self.assertEqual(result.status, "INSUFFICIENT_DATA")
        client.responses.parse.assert_not_called()

    def test_otp_in_goal_or_page_never_reaches_provider(self):
        client = Mock()
        pages = [SyntheticDOMPage(
            "DOCUMENTATION", "가이드",
            "One-time code: 123456. 캐시 데이터를 재사용합니다.\nstaleTime 동안 신선한 데이터를 확인합니다.",
        )]
        result = generate_synthetic_dom_summary("React Query 공부", pages, client=client)
        self.assertEqual(result.status, "INSUFFICIENT_DATA")
        client.responses.parse.assert_not_called()

        result = generate_synthetic_dom_summary("OTP: 123456 인증을 공부한다", SYNTHETIC_DOM_PAGES, client=client)
        self.assertEqual(result.status, "FAILED")
        client.responses.parse.assert_not_called()


if __name__ == "__main__":
    unittest.main()


class SyntheticEvidenceContractTests(unittest.TestCase):
    def test_related_evidence_is_deduplicated_and_validated_before_generation(self):
        from src.learning_summary_openai import generate_synthetic_evidence_summary

        client = Mock()
        client.responses.parse.return_value = SimpleNamespace(output_parsed=valid_output())
        evidence = list(FIXTURE_EVIDENCE) + [
            FIXTURE_EVIDENCE[0],
            DemoEvidence("secret", "contact person@example.com"),
            DemoEvidence("other", "unrelated observation", label="UNRELATED"),
        ]
        result = generate_synthetic_evidence_summary(SYNTHETIC_GOAL, evidence, client=client)
        self.assertEqual(result.status, "READY")
        self.assertEqual(result.accepted_count, 2)
        self.assertEqual(set(result.summary), {"overview", "keyConcepts", "nextStartPrompt"})
        sent = client.responses.parse.call_args.kwargs["input"][0]["content"]
        self.assertNotIn("person@example.com", sent)
        self.assertNotIn("unrelated observation", sent)

    def test_insufficient_or_denied_consent_never_calls_model(self):
        from src.learning_summary_openai import generate_synthetic_evidence_summary

        client = Mock()
        self.assertEqual(generate_synthetic_evidence_summary(
            SYNTHETIC_GOAL, [FIXTURE_EVIDENCE[0], FIXTURE_EVIDENCE[0]], client=client
        ).status, "INSUFFICIENT_DATA")
        self.assertEqual(generate_synthetic_evidence_summary(
            SYNTHETIC_GOAL, FIXTURE_EVIDENCE, client=client, record_consent=False
        ).status, "INSUFFICIENT_DATA")
        client.responses.parse.assert_not_called()

    def test_provider_failure_and_invalid_output_fail_closed(self):
        from src.learning_summary_openai import generate_synthetic_evidence_summary

        client = Mock()
        client.responses.parse.side_effect = TimeoutError()
        self.assertEqual(generate_synthetic_evidence_summary(
            SYNTHETIC_GOAL, FIXTURE_EVIDENCE, client=client
        ).status, "FAILED")
        client.responses.parse.side_effect = None
        client.responses.parse.return_value = SimpleNamespace(output_parsed={"keyConcepts": []})
        self.assertEqual(generate_synthetic_evidence_summary(
            SYNTHETIC_GOAL, FIXTURE_EVIDENCE, client=client
        ).status, "FAILED")


    def test_page_type_sensitive_data_is_dropped_and_text_is_trimmed(self):
        from src.learning_summary_openai import generate_synthetic_evidence_summary

        client = Mock()
        client.responses.parse.return_value = SimpleNamespace(output_parsed=valid_output())
        padded = DemoEvidence(FIXTURE_EVIDENCE[0].concept,
                              " " * 130 + FIXTURE_EVIDENCE[0].observation + " ")
        sensitive_page = DemoEvidence("safe", "a safe observation", page_type="person@example.com")
        result = generate_synthetic_evidence_summary(
            SYNTHETIC_GOAL, [padded, FIXTURE_EVIDENCE[1], sensitive_page], client=client)
        self.assertEqual(result.status, "READY")
        self.assertEqual(result.accepted_count, 2)
        sent = client.responses.parse.call_args.kwargs["input"][0]["content"]
        self.assertNotIn("person@example.com", sent)
        self.assertNotIn(" " * 130, sent)
