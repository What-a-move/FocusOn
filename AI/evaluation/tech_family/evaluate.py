"""Two-phase, offline replay of the handoff's metadata-first sessions with the actual E5."""
import argparse
import csv
import hashlib
import itertools
import json
import re
from collections import Counter, defaultdict
from pathlib import Path

import numpy as np
import torch

from AI.src.models.screen_activity.activity import ActivityClassifier, parts
from AI.src.models.screen_activity.beta_decision import beta_decision
from AI.src.preprocessing.models import PassageKind, PreprocessingConfig
from AI.src.preprocessing.text_cleaner import clean_text, split_text


# Merge spelling/subfields, not everything in the same broad discipline.
FAMILIES = {
    "CSS 레이아웃": "CSS", "CSS 스타일링과 애니메이션": "CSS",
    "JavaScript 기초": "JavaScript", "JavaScript 비동기와 고급 문법": "JavaScript",
    "Spring Boot": "Spring", "Spring MVC": "Spring", "Spring Security": "Spring",
    "Python 기초 문법": "Python", "Python 고급 문법": "Python",
    "GitHub 협업": "Git", "정렬과 탐색 알고리즘": "알고리즘", "그래프 알고리즘": "알고리즘",
    "동적 계획법": "알고리즘", "자료구조": "알고리즘", "코딩테스트 구현 문제": "알고리즘",
}
# Adjacency permits abstention on unresolved parent/child relationships; it cannot produce a match.
NEIGHBOURS = [
    {"Next.js", "React", "React Native"}, {"Java 언어", "Spring", "JPA와 Hibernate"},
    {"Python", "Django", "FastAPI와 Flask"}, {"Kotlin", "Android 앱 기본", "Jetpack Compose"},
    {"SQL 문법", "PostgreSQL", "MySQL", "트랜잭션과 인덱스", "데이터베이스 설계와 정규화"},
    {"JavaScript", "TypeScript", "Node.js와 Express", "NestJS"},
    {"확률과 통계", "통계 분석"},
]
GRID = list(itertools.product((3, 7, 15), (.35, .50, .65), (0., .75), (False, True)))
TRAIN = {"01", "02", "03", "04", "05"}
TEST = {"06", "07"}
PREPROCESSING = PreprocessingConfig()


def read(path):
    return json.loads(path.read_text())


def dump(path, value):
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n")


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


class Signals:
    def __init__(self, model_dir):
        self.model = ActivityClassifier(model_dir)
        entries = read(model_dir / "topic-bank.json")["topics"]
        families = [FAMILIES.get(x["field"], x["field"]) for x in entries]
        self.names, self.ids = np.unique(families, return_inverse=True)
        self.patterns = []
        for family in self.names:
            # Derive explicit identifiers from bank field names, never from screen IDs or hosts.
            fields = {x["field"] for x, f in zip(entries, families) if f == family}
            keys = {w for field in fields for w in re.findall(r"[A-Za-z][A-Za-z0-9.+#-]*", field)}
            keys.update(field for field in fields if not re.search(r"[A-Za-z]", field))
            keys.update(k[:-3] for k in tuple(keys) if k.endswith(".js"))
            if family == "React Native":
                keys = {"React Native"}
            self.patterns.append([re.compile(r"(?<![a-z0-9])" + re.escape(k) + r"(?![a-zA-Z0-9])", 0 if k in {"Go", "C"} else re.I)
                                  for k in keys if len(k) > 1 or k == "C"])

    def signal(self, texts, role):
        vecs = self.model.encoder.embed(texts, role=role)
        considered = [t for t in texts if t]
        if not considered:
            return None
        vectors = np.stack([vecs[t] for t in considered])
        scores = np.stack([self.model.topic_bank.similarity(v) for v in vectors])
        ordered = np.argsort(-scores, axis=1, kind="stable")[:, :15]
        explicit = np.array([[any(rx.search(t) for rx in group) for group in self.patterns] for t in considered])
        named = explicit.mean(axis=0)
        if named.sum():
            named /= named.sum()
        # Keep every view for final task ranking. This includes body evidence when the title is generic.
        return (vectors, {k: np.bincount(self.ids[ordered[:, :k]].ravel(), minlength=len(self.names))
                             / (len(considered) * k) for k in (3, 7, 15)}, named)

    def screen(self, payload):
        study = beta_decision(payload, self.model.predict([payload])[0])["label"] == "STUDY"
        title, full = parts(payload)
        body = full.removeprefix(title).strip()
        source = payload["contentSource"]
        body = clean_text(body, PassageKind.BODY, PREPROCESSING.ui_fragments, strip_html=False).text
        usable = (source == "TAB_META" or len(body) >= 80) and source != "APP_META"
        texts = [title, *split_text(body, 550, 100, False)] if body else [title]
        return study, self.signal(texts, "passage") if study and usable else None


def classify(signal, config):
    if signal is None:
        return None
    k, threshold, extra, _ = config
    _, votes, named = signal
    scores = votes[k] + extra * named
    order = np.argsort(-scores, kind="stable")
    winner, runner = order[:2]
    if scores[winner] / scores.sum() < threshold or scores[winner] - scores[runner] < 0.1:
        return None
    return winner


def decide(stage, goal_signals, config, names, meta):
    study, signal = stage
    if not study:
        return "not_study", ""
    family = classify(signal, config)
    if family is None or (meta and not config[3]):
        return "NOT_ENOUGH", ""
    candidates = {g: classify(s, config) for g, s in goal_signals.items()}
    matches = [g for g, f in candidates.items() if f == family]
    if matches:
        best = max(matches, key=lambda g: float((signal[0] @ goal_signals[g][0].T).max(axis=1).mean()))
        return "MATCHED", best
    if meta or None in candidates.values():
        return "NOT_ENOUGH", ""
    if any({names[family], names[f]} <= group for f in candidates.values() for group in NEIGHBOURS):
        return "NOT_ENOUGH", ""
    return "FAR", ""


def load_split(root, split, signals):
    result = {}
    for folder in sorted((root / "sets").iterdir()):
        if folder.name[:2] not in split:
            continue
        tasks = {}
        for key, entry in read(folder / "task-profiles.json").items():
            prof = entry.get("goalProfile") or {}
            tasks[key] = signals.signal([entry["goalText"], prof.get("mainTopic", ""), *prof.get("coreTopics", [])], "query")
        pages = {}
        chrome = read(folder / "chrome-inputs.json")["cases"]
        inputs = [(c["name"], c["meta"], c["body"]) for c in chrome]
        for c in read(folder / "native-inputs.json"):
            md = {"appName": "텍스트 편집기", "windowTitle": c["title"]}
            inputs.append((c["name"], {"contentSource": "APP_META", "metadata": md},
                           {"contentSource": "DESKTOP_APP_OCR", "metadata": md, "contentPayload": {"text": c["recognized"]}}))
        for name, meta, body in inputs:
            pages[name] = (signals.screen(meta), signals.screen(body))
        result[folder.name] = tasks, pages
    return result


def csv_rows(root, name, split):
    with (root / name).open(newline="") as fd:
        return [row for row in csv.DictReader(fd) if row["set"][:2] in split]


def score(rows, metrics):
    counts = defaultdict(Counter)
    for r in rows:
        n = counts[r["set"]]
        n["rows"] += 1
        v, s = (r[k] for k in metrics)
        n[v] += 1
        if r["same_tech"] == "True" and s not in {"ambiguous", "not_study"}:
            n["sub_total"] += 1
            n["sub_correct"] += s in {"correct", "correct_reject"}
    return counts


def evaluate(config, cache, rows, truth, names):
    out = []
    for r in rows:
        tasks, pages = cache[r["set"]]
        chosen = {g: tasks[g] for g in r["goals"].split("+")}
        meta, body = pages[r["screen"]]
        status, gid = decide(meta, chosen, config, names, True)
        ended = status == "MATCHED"
        if not ended:
            status, gid = decide(body, chosen, config, names, False)
        same = r["same_tech"] == "True"
        labels = truth[r["set"], r["screen_id"]]
        if status == "not_study":
            tech = sub = "not_study"
        elif same:
            tech = {"MATCHED": "correct", "FAR": "wrong_far", "NOT_ENOUGH": "hold_miss"}[status]
            if gid:
                sub = {"y": "correct", "n": "wrong_task", "u": "ambiguous"}[labels[gid]]
            else:
                selected = [labels.get(g) for g in chosen]
                sub = "miss" if "y" in selected else "ambiguous" if "u" in selected else "correct_reject"
        else:
            tech = {"MATCHED": "wrong_attach", "FAR": "correct_far", "NOT_ENOUGH": "hold"}[status]
            sub = tech
        out.append({**r, "family_status": status, "family_goal": gid, "metadata_final": ended,
                    "family_tech": tech, "family_sub": sub})
    return out


def objective(counts):
    # Each class and each represented set within a class receives equal weight.
    rates = []
    for keys in (("correct", "wrong_far", "hold_miss"), ("correct_far", "wrong_attach", "hold")):
        rates.append(np.mean([c[keys[0]] / sum(c[k] for k in keys) for c in counts.values() if sum(c[k] for k in keys)]))
    return float(np.mean(rates))


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("phase", choices=["tune", "grade"])
    for key in ("data", "model", "out"):
        parser.add_argument("--" + key, type=Path, required=True)
    args = parser.parse_args()
    args.out.mkdir(exist_ok=True, parents=True)
    lock = args.out / "frozen.json"
    hashes = {p.name: digest(p) for p in (Path(__file__), args.model / "manifest.json", args.model / "topic-bank.json")}
    if args.phase == "tune" and (args.out / "grade.csv").exists():
        raise SystemExit("This output directory already contains a held-out grade")
    if args.phase == "grade":
        frozen = read(lock)
        if hashes != frozen["hashes"]:
            raise SystemExit("Implementation or model changed since tuning")
    split = TRAIN if args.phase == "tune" else TEST
    rows = [r for r in csv_rows(args.data, "results.csv", split) if r["version"] == "new"]
    truth = defaultdict(dict)
    for r in csv_rows(args.data, "cases.csv", split):
        truth[r["set"], r["screen_id"]][r["task_id"]] = r["truth"]
    torch.set_num_threads(3)
    model = Signals(args.model)
    cache = load_split(args.data, split, model)
    if args.phase == "tune":
        candidates = []
        for config in GRID:
            results = evaluate(config, cache, rows, truth, model.names)
            counts = score(results, ("family_tech", "family_sub"))
            candidates.append((objective(counts), -sum(c["wrong_attach"] for c in counts.values()), config))
        winner = max(candidates)
        frozen = {"config": winner[2], "train_objective": winner[0], "grid_size": len(GRID), "hashes": hashes}
        dump(lock, frozen)
    results = evaluate(frozen["config"], cache, rows, truth, model.names)
    dump(args.out / (args.phase + ".json"), {"original": score(rows, ("verdict_tech", "verdict_subtopic")),
                                             "family": score(results, ("family_tech", "family_sub"))})
    with (args.out / (args.phase + ".csv")).open("w", newline="") as fd:
        writer = csv.DictWriter(fd, fieldnames=results[0])
        writer.writeheader()
        writer.writerows(results)


if __name__ == "__main__":
    main()
