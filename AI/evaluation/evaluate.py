from __future__ import annotations

import argparse
import asyncio
import json
import os
from collections import Counter
from pathlib import Path

from src.analysis.goal_analyzer import GoalAssessment, route_goal_assessment
from src.api.schemas import GoalClarifyRequest
from src.config import Settings
from src.models.jev_client import TypeSafeJevClient


CASES_PATH = Path(__file__).with_name("evaluation_cases.json")


def load_cases() -> list[dict]:
    return json.loads(CASES_PATH.read_text(encoding="utf-8"))


def offline_evaluate(settings: Settings) -> int:
    outcomes: list[tuple[str, str, str]] = []
    for case in load_cases():
        item = case["offlineAssessment"]
        result = route_goal_assessment(
            GoalAssessment(
                is_usable_goal=item["isUsableGoal"],
                has_multiple_main_goals=item["hasMultipleMainGoals"],
                has_unclear_term=item["hasUnclearTerm"],
                specificity_level=item["specificityLevel"],
                specificity_confidence=item["specificityConfidence"],
            ),
            settings,
        ).value
        outcomes.append((case["id"], case["expectedStatus"], result))
    errors = [outcome for outcome in outcomes if outcome[1] != outcome[2]]
    print(json.dumps({"mode": "offline", "cases": len(outcomes), "errors": errors}, ensure_ascii=False))
    return 1 if errors else 0


async def live_evaluate(settings: Settings) -> int:
    if os.getenv("FOCUSON_LIVE_GOAL_EVAL") != "1":
        raise SystemExit("Set FOCUSON_LIVE_GOAL_EVAL=1 to allow external JEV calls.")
    client = TypeSafeJevClient(settings)
    confusion: Counter[tuple[str, str]] = Counter()
    try:
        for case in load_cases():
            assessment = await client.assess_goal(GoalClarifyRequest.model_validate(case["input"]))
            actual = route_goal_assessment(assessment, settings).value
            confusion[(case["expectedStatus"], actual)] += 1
    finally:
        await client.aclose()
    result = {
        "mode": "live-jev",
        "cases": sum(confusion.values()),
        "confusionMatrix": [
            {"expected": expected, "actual": actual, "count": count}
            for (expected, actual), count in sorted(confusion.items())
        ],
    }
    print(json.dumps(result, ensure_ascii=False))
    return int(any(expected != actual for expected, actual in confusion))


def main() -> int:
    parser = argparse.ArgumentParser(description="Evaluate goal-assistance routing.")
    parser.add_argument("--live", action="store_true", help="Call JEV explicitly.")
    args = parser.parse_args()
    settings = Settings()
    if args.live:
        return asyncio.run(live_evaluate(settings))
    return offline_evaluate(settings)


if __name__ == "__main__":
    raise SystemExit(main())
