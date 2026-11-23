import json

import httpx
import pytest

from AI.evaluation.goal_profile_experiment import compare_profile, effective_label
from AI.evaluation.goal_profile_jev import JevPageJudge
from src.api.schemas import GoalProfile


GOAL = "React useState로 카운터 구현하기"
BODY = "Initialize the counter with useState and call setCount in the click handler."
PROFILE = {
    "mainTopic": "React state",
    "purpose": "OTHER",
    "coreTopics": ["useState"],
    "supportingTopics": ["React components"],
    "expectedActivities": ["implement a counter"],
}


def run_comparison(cases, respond):
    with httpx.Client(transport=httpx.MockTransport(respond)) as client:
        judge = JevPageJudge(client, "jev-latest", 0.65)
        return compare_profile(GOAL, GoalProfile.parse_safe(PROFILE), cases, None, jev_judge=judge)


def choice(label, probability):
    return httpx.Response(200, json={"answers": {"relevance": {
        "type": "choice", "choice": label, "probabilities": {label: probability},
    }}})


def test_jev_compares_same_prepared_page_with_both_goals_and_skips_protected_content():
    payloads = []

    def respond(request):
        payloads.append(json.loads(request.content))
        return choice("RELATED", 0.90)

    cases = [
        {"caseId": "clean", "text": BODY, "expectedLabel": "RELATED"},
        {"caseId": "excluded", "text": BODY, "isExcluded": True},
        {"caseId": "sensitive", "text": BODY, "isSensitive": True},
        {"caseId": "personal-data", "text": "Contact student@example.com for React help."},
        {"caseId": "failed", "text": BODY, "extractionStatus": "FAILED"},
    ]
    before, after = run_comparison(cases, respond)
    assert len(payloads) == 2
    assert payloads[0]["state"]["goal"] == GOAL
    assert payloads[1]["state"]["goal"].startswith(GOAL + "\n")
    assert "React components" in payloads[1]["state"]["goal"]
    assert payloads[0]["state"]["page_passages"] == payloads[1]["state"]["page_passages"] == [BODY]
    assert payloads[0]["questions"] == payloads[1]["questions"]
    assert "expectedLabel" not in json.dumps(payloads)
    for report in (before, after):
        assert [row["jevCalls"] for row in report["rows"]] == [1, 0, 0, 0, 0]
        assert [effective_label(row) for row in report["rows"]] == [
            "RELATED", "EXCLUDED", "PRIVACY_BLOCKED", "PRIVACY_BLOCKED", "ANALYSIS_UNAVAILABLE",
        ]
        assert report["embeddingCalls"] == 0


@pytest.mark.parametrize(("case", "probability", "expected_reason"), [
    ({"text": BODY}, 0.64, "JEV_LOW_PROBABILITY"),
    ({"contentSource": "TAB_META", "pageTitle": "Comprehensive guide to lawn irrigation"},
     0.98, "JEV_BODY_REQUIRED"),
])
def test_jev_abstains_with_weak_evidence(case, probability, expected_reason):
    for report in run_comparison([case], lambda _: choice("UNRELATED", probability)):
        row = report["rows"][0]
        assert effective_label(row) == "UNCERTAIN"
        assert row["reasonCode"] == expected_reason
        assert row["jevProbability"] == probability


def test_jev_api_failure_is_not_a_relevance_label():
    for report in run_comparison([{"text": BODY}], lambda _: httpx.Response(429, text="private error")):
        row = report["rows"][0]
        assert effective_label(row) == "ANALYSIS_UNAVAILABLE"
        assert row["reasonCode"] == "JEV_HTTP_429"
        assert "private error" not in repr(report)
