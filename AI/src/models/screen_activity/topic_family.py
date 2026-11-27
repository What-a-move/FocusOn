"""Identify clearly different study topics for the FAR fallback."""
import re

import numpy as np


# Group alternate names and closely related subfields from the existing topic bank.
FAMILY_BY_FIELD = {
    "CSS 레이아웃": "CSS", "CSS 스타일링과 애니메이션": "CSS",
    "JavaScript 기초": "JavaScript", "JavaScript 비동기와 고급 문법": "JavaScript",
    "Spring Boot": "Spring", "Spring MVC": "Spring", "Spring Security": "Spring",
    "Python 기초 문법": "Python", "Python 고급 문법": "Python",
    "GitHub 협업": "Git", "정렬과 탐색 알고리즘": "알고리즘", "그래프 알고리즘": "알고리즘",
    "동적 계획법": "알고리즘", "자료구조": "알고리즘", "코딩테스트 구현 문제": "알고리즘",
}
NEARBY_FAMILIES = (
    {"Next.js", "React", "React Native"}, {"Java 언어", "Spring", "JPA와 Hibernate"},
    {"Python", "Django", "FastAPI와 Flask"}, {"Kotlin", "Android 앱 기본", "Jetpack Compose"},
    {"SQL 문법", "PostgreSQL", "MySQL", "트랜잭션과 인덱스", "데이터베이스 설계와 정규화"},
    {"JavaScript", "TypeScript", "Node.js와 Express", "NestJS"},
    {"확률과 통계", "통계 분석"},
)
TOP_TOPICS = 3
MIN_VOTE_SHARE = 0.35
MIN_VOTE_LEAD = 0.10
NAME_VOTE_WEIGHT = 0.75
FIELD_WORD = re.compile(r"[A-Za-z][A-Za-z0-9.+#-]*")


class TopicFamilies:
    def __init__(self, fields):
        self.names, self.family_ids = np.unique(
            [FAMILY_BY_FIELD.get(field, field) for field in fields], return_inverse=True
        )
        fields_by_family = {name: set() for name in self.names}
        for field in fields:
            fields_by_family[FAMILY_BY_FIELD.get(field, field)].add(field)
        self.name_patterns = []
        for family in self.names:
            names = set()
            for field in fields_by_family[family]:
                names.update(FIELD_WORD.findall(field) or [field])
            names.update(name[:-3] for name in tuple(names) if name.endswith(".js"))
            if family == "React Native":
                names = {"React Native"}
            self.name_patterns.append(tuple(
                re.compile(r"(?<![a-zA-Z0-9])" + re.escape(name) + r"(?![a-zA-Z0-9])",
                           0 if name in {"C", "Go"} else re.I)
                for name in sorted(names) if len(name) > 1 or name == "C"
            ))

    def explicit(self, text):
        return np.array([any(pattern.search(text) for pattern in group) for group in self.name_patterns])

    def identify(self, evidence, similarity):
        scores = np.stack([similarity(vector) for _, vector in evidence])
        if not np.isfinite(scores).all():
            return None
        top = np.argsort(-scores, axis=1, kind="stable")[:, :TOP_TOPICS]
        votes = np.bincount(self.family_ids[top].ravel(), minlength=len(self.names)).astype(float)
        votes /= top.size
        named = np.stack([self.explicit(text) for text, _ in evidence]).mean(axis=0)
        if named.any():
            votes += NAME_VOTE_WEIGHT * named / named.sum()
        order = np.argsort(-votes, kind="stable")
        winner = order[0]
        runner_up = votes[order[1]] if len(order) > 1 else 0
        if votes[winner] / votes.sum() < MIN_VOTE_SHARE or votes[winner] - runner_up < MIN_VOTE_LEAD:
            return None
        return self.names[winner]

    def clearly_different(self, screen, goals, similarity):
        screen_names = self.explicit("\n".join(text for text, _ in screen))
        screen_family = self.identify(screen, similarity)
        if screen_family is None:
            return False
        for goal in goals:
            # If an explicit family is shared, a conflicting embedding vote cannot authorize FAR.
            goal_names = self.explicit("\n".join(text for text, _ in goal))
            if (screen_names & goal_names).any():
                return False
            goal_family = self.identify(goal, similarity)
            if goal_family is None or goal_family == screen_family:
                return False
            if any({screen_family, goal_family} <= group for group in NEARBY_FAMILIES):
                return False
        return True
