import unittest

from src.learning_summary_demo import (
    DemoEvidence, SyntheticDOMPage, copy_text, eligible_evidence,
    evidence_from_dom_pages, make_demo_summary,
)


class LearningSummaryDemoTests(unittest.TestCase):
    def setUp(self):
        self.two = [
            DemoEvidence("React Query", "캐시 설명 자료 확인"),
            DemoEvidence("쿼리 키", "구성 예시 확인"),
        ]

    def test_ready_uses_only_observed_evidence(self):
        result = make_demo_summary("React Query 공부", self.two)
        self.assertEqual(result.status, "READY")
        self.assertEqual(result.accepted_count, 2)
        self.assertIn(self.two[0].observation, copy_text(result))
        self.assertNotIn("구현했습니다", copy_text(result))

    def test_zero_one_and_duplicate_are_insufficient(self):
        for evidence in ([], self.two[:1], [self.two[0], self.two[0]]):
            self.assertEqual(make_demo_summary("React Query 공부", evidence).status, "INSUFFICIENT_DATA")

    def test_excluded_sensitive_and_no_consent_are_ignored(self):
        blocked = [DemoEvidence("계정", "로그인 화면", "PRIVACY_BLOCKED")]
        self.assertEqual(make_demo_summary("React Query 공부", self.two[:1] + blocked).status, "INSUFFICIENT_DATA")
        self.assertEqual(make_demo_summary("React Query 공부", self.two, record_consent=False).status, "INSUFFICIENT_DATA")

    def test_sensitive_text_is_not_accepted(self):
        evidence = DemoEvidence("계정", "test@example.com 메일 확인")
        self.assertEqual(eligible_evidence(self.two[:1] + [evidence], True), self.two[:1])

    def test_otp_and_authentication_codes_are_rejected(self):
        for sensitive in (
            "One-time code: 123456 인증 문구",
            "OTP: 123456 코드 확인",
            "인증번호 123456 확인 문구",
        ):
            with self.subTest(sensitive=sensitive):
                self.assertEqual(
                    evidence_from_dom_pages([SyntheticDOMPage("DOCUMENTATION", "가이드", sensitive)]),
                    [],
                )

    def test_long_sentences_are_chunked_instead_of_discarded(self):
        first = "queryKey를 기준으로 조회 대상을 구분하고 같은 키를 사용하면 기존 캐시 데이터를 다시 사용할 수 있도록 관리합니다. " * 2
        second = "staleTime은 데이터가 신선한 것으로 취급되는 시간을 지정하고 설정한 시간이 지나면 쿼리를 다시 실행할 수 있습니다. " * 2
        evidence = evidence_from_dom_pages([
            SyntheticDOMPage("DOCUMENTATION", "가이드", f"{first.strip()}\n{second.strip()}")
        ])
        self.assertGreaterEqual(len(evidence), 2)
        self.assertTrue(all(len(item.observation) <= 120 for item in evidence))

    def test_long_unbroken_text_is_split_without_losing_characters(self):
        paragraph = "캐시데이터" * 30
        evidence = evidence_from_dom_pages([
            SyntheticDOMPage("DOCUMENTATION", "가이드", paragraph)
        ])
        self.assertGreaterEqual(len(evidence), 2)
        self.assertTrue(all(len(item.observation) <= 120 for item in evidence))
        self.assertEqual("".join(item.observation for item in evidence), paragraph)

    def test_failure_is_not_empty_ready(self):
        result = make_demo_summary("React Query 공부", self.two, simulate_failure=True)
        self.assertEqual(result.status, "FAILED")
        self.assertEqual(copy_text(result), "")


if __name__ == "__main__":
    unittest.main()
