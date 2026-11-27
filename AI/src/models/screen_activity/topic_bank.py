"""Fixed study topics used as open-set alternatives when attributing a screen to a task."""
import json
import re
import unicodedata

import numpy as np

from .topic_family import TopicFamilies

TOPIC_BANK_FILE = "topic-bank.json"


class TopicBank:
    """Each entry has a task sentence and a short topic name, both embedded as E5 queries."""

    def __init__(self, task_vectors, topic_vectors, fields=(), *, topics=()):
        self.task_vectors = np.asarray(task_vectors, dtype=np.float32)
        self.topic_vectors = np.asarray(topic_vectors, dtype=np.float32)
        self.families = TopicFamilies(fields) if fields else None
        self.concept_labels = tuple(sorted(set(topics), key=lambda label: (-len(label), label)))

    @classmethod
    def load(cls, path, encoder):
        entries = json.loads(path.read_text(encoding="utf-8"))["topics"]
        tasks = [entry["task"] for entry in entries]
        topics = [entry["topic"] for entry in entries]
        vectors = encoder.embed([*tasks, *topics], role="query")
        return cls([vectors[text] for text in tasks], [vectors[text] for text in topics],
                   fields=[entry["field"] for entry in entries], topics=topics)

    def similarity(self, vector):
        """Best of each entry's sentence and name against one passage or query vector."""
        vector = np.asarray(vector, dtype=np.float32)
        return np.maximum(self.task_vectors @ vector, self.topic_vectors @ vector)

    def observed_concept(self, body: str, goal_terms: tuple[str, ...]) -> str | None:
        """Return a fixed label supported by both the matched goal and the body."""
        body = unicodedata.normalize("NFC", body).casefold()
        goal = "\n".join(unicodedata.normalize("NFC", term).casefold() for term in goal_terms)
        for label in self.concept_labels:
            term = unicodedata.normalize("NFC", label).casefold()
            if term not in body or term not in goal:
                continue
            # Keep whole concept names, allowing Korean particles immediately after them.
            pattern = rf"(?<!\w){re.escape(term)}(?:은|는|이|가|을|를|의|에|에서|로|으로|와|과|도)?(?!\w)"
            if re.search(pattern, body) and re.search(pattern, goal):
                return label
        return None
