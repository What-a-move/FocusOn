"""Replay a body-only FAR fallback on the saved production matching decisions."""

import argparse
import csv
from pathlib import Path

import torch

from AI.evaluation.tech_family.evaluate import (
    PREPROCESSING,
    TEST,
    Signals,
    csv_rows,
    decide,
    digest,
    dump,
    load_split,
    read,
    score,
)
from AI.src.models.screen_activity.activity import parts
from AI.src.models.screen_activity.beta_decision import beta_decision
from AI.src.preprocessing.models import PassageKind
from AI.src.preprocessing.text_cleaner import clean_text


class BodyStudySignals(Signals):
    def screen(self, payload):
        if payload["contentSource"] not in {"CHROME_DOM", "DESKTOP_APP_OCR"}:
            return False, None
        stage = super().screen(payload)
        if stage[1] is None:
            return stage
        title, text = parts(payload)
        body = clean_text(
            text.removeprefix(title).strip(), PassageKind.BODY, PREPROCESSING.ui_fragments, strip_html=False
        ).text
        body_payload = {"contentSource": payload["contentSource"], "contentPayload": {"text": body}}
        result = beta_decision(body_payload, self.model.predict([body_payload])[0])
        return stage if result["label"] == "STUDY" else (False, None)


def combine(rows, cache, config, names):
    for row in rows:
        status = row["matchStatus"]
        tech = row["verdict_tech"]
        received_body = " > " in row["stages"]
        if status == "NOT_ENOUGH" and row["relation"] == "RELATED" and received_body:
            tasks, pages = cache[row["set"]]
            goals = {key: tasks[key] for key in row["goals"].split("+")}
            _, body = pages[row["screen"]]
            body_status, _ = decide(body, goals, config, names, meta=False)
            if body_status == "FAR":
                status = "FAR"
                tech = "wrong_far" if row["same_tech"] == "True" else "correct_far"
        # Switching between FAR and NOT_ENOUGH does not attach or change a task.
        yield {**row, "combined_status": status, "combined_goal": row["matched"],
               "combined_tech": tech, "combined_subtopic": row["verdict_subtopic"]}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for key in ("data", "model", "out"):
        parser.add_argument("--" + key, type=Path, required=True)
    args = parser.parse_args()
    frozen = read(Path(__file__).with_name("frozen.json"))
    files = (Path(__file__).with_name("evaluate.py"), args.model / "manifest.json", args.model / "topic-bank.json")
    if {p.name: digest(p) for p in files} != frozen["hashes"]:
        raise SystemExit("Implementation or model changed since tuning")
    rows = [r for r in csv_rows(args.data, "results.csv", TEST) if r["version"] == "new"]
    torch.set_num_threads(3)
    signals = BodyStudySignals(args.model)
    cache = load_split(args.data, TEST, signals)
    results = list(combine(rows, cache, frozen["config"], signals.names))

    args.out.mkdir(exist_ok=True, parents=True)
    dump(args.out / "combined.json", {
        "original": score(rows, ("verdict_tech", "verdict_subtopic")),
        "combined": score(results, ("combined_tech", "combined_subtopic")),
        "rule_sha256": digest(Path(__file__)),
    })
    with (args.out / "combined.csv").open("w", newline="") as file:
        writer = csv.DictWriter(file, fieldnames=results[0])
        writer.writeheader()
        writer.writerows(results)


if __name__ == "__main__":
    main()
