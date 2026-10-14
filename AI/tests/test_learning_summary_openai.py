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
)
from src.learning_summary_demo import SyntheticDOMPage, evidence_from_dom_pages

FIXTURE_EVIDENCE = evidence_from_dom_pages(SYNTHETIC_DOM_PAGES)


def valid_output():
    return {
        "overview": "React Query 캐시와 쿼리 키를 다룬 가상 자료를 살펴봤습니다. 이해 여부는 확인되지 않았습니다.",
        "keyConcepts": [
            {
                "name": FIXTURE_EVIDENCE[0].concept,
                "briefExplanation": "캐시와 갱신 개념을 다룬 자료를 확인했습니다.",
                "sessionEvidence": FIXTURE_EVIDENCE[0].observation,
            },
            {
                "name": "queryKey",
                "briefExplanation": "쿼리 키 구성 예시를 다룬 자료를 확인했습니다.",
                "sessionEvidence": FIXTURE_EVIDENCE[1].observation,
            },
        ],
        "nextStartPrompt": "다음에는 쿼리 키 구성 예시를 다시 살펴보세요.",
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
        self.assertEqual(kwargs["text_format"].__name__, "LearningSummary")
        sent = kwargs["input"][0]["content"]
        self.assertIn(SYNTHETIC_GOAL, sent)
        self.assertIn(SYNTHETIC_DOM_PAGES[0].title, sent)
        self.assertIn("queryKey", sent)
        self.assertNotIn("https://", sent)

    def test_no_key_does_not_call_provider(self):
        with patch.dict("os.environ", {}, clear=True):
            self.assertEqual(generate_fixed_synthetic_summary().status, "FAILED")

    def test_bad_model_output_is_rejected(self):
        cases = []
        no_evidence = valid_output()
        no_evidence["keyConcepts"] = []
        cases.append(no_evidence)
        invented = valid_output()
        invented["keyConcepts"][0]["sessionEvidence"] = "invented"
        cases.append(invented)
        wrong_concept = valid_output()
        wrong_concept["keyConcepts"][0]["name"] = "invented"
        cases.append(wrong_concept)
        pii = valid_output()
        pii["overview"] = "test@example.com"
        cases.append(pii)
        achievement = valid_output()
        achievement["overview"] = "React Query를 완벽히 이해했습니다."
        cases.append(achievement)
        extra_field = valid_output()
        extra_field["url"] = "hidden"
        cases.append(extra_field)
        one_sentence = valid_output()
        one_sentence["overview"] = "자료를 살펴봤습니다."
        cases.append(one_sentence)
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

    def test_more_than_five_concepts_and_oversized_output_are_rejected(self):
        too_many = valid_output()
        too_many["keyConcepts"] = [too_many["keyConcepts"][0]] * 6
        self.assertIsNone(_validated_summary(too_many, FIXTURE_EVIDENCE))
        too_long = valid_output()
        too_long["overview"] = "가" * 1201
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


if __name__ == "__main__":
    unittest.main()
