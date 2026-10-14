import unittest

from src.learning_summary_demo import DemoEvidence, copy_text, eligible_evidence, make_demo_summary


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
        self.assertIn(self.two[0].text, copy_text(result))
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

    def test_failure_is_not_empty_ready(self):
        result = make_demo_summary("React Query 공부", self.two, simulate_failure=True)
        self.assertEqual(result.status, "FAILED")
        self.assertEqual(copy_text(result), "")


if __name__ == "__main__":
    unittest.main()
