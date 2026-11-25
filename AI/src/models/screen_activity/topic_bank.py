"""Fixed study topics used as open-set alternatives when attributing a screen to a task."""
import json

import numpy as np

from .topic_family import TopicFamilies

TOPIC_BANK_FILE = "topic-bank.json"


class TopicBank:
    """Each entry has a task sentence and a short topic name, both embedded as E5 queries."""

    def __init__(self, task_vectors, topic_vectors, fields=()):
        self.task_vectors = np.asarray(task_vectors, dtype=np.float32)
        self.topic_vectors = np.asarray(topic_vectors, dtype=np.float32)
        self.families = TopicFamilies(fields) if fields else None

    @classmethod
    def load(cls, path, encoder):
        entries = json.loads(path.read_text(encoding="utf-8"))["topics"]
        tasks = [entry["task"] for entry in entries]
        topics = [entry["topic"] for entry in entries]
        vectors = encoder.embed([*tasks, *topics], role="query")
        return cls([vectors[text] for text in tasks], [vectors[text] for text in topics],
                   [entry["field"] for entry in entries])

    def similarity(self, vector):
        """Best of each entry's sentence and name against one passage or query vector."""
        vector = np.asarray(vector, dtype=np.float32)
        return np.maximum(self.task_vectors @ vector, self.topic_vectors @ vector)
