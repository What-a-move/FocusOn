"""Small, explicit goal templates. Content never adds nodes to a goal graph."""
from __future__ import annotations

import hashlib
import json
import re
from dataclasses import asdict, dataclass, field
from enum import Enum


class ContentRole(str, Enum):
    DIRECT = "DIRECT"
    PREREQUISITE = "PREREQUISITE"
    SUPPORTING = "SUPPORTING"


@dataclass(frozen=True, slots=True)
class Concept:
    id: str
    description: str
    aliases: tuple[str, ...]
    role: ContentRole | None
    connection: str


@dataclass(frozen=True, slots=True)
class GoalGraph:
    template_id: str
    version: str
    goal_text: str = field(repr=False)
    concepts: tuple[Concept, ...]
    unrelated_topics: tuple[Concept, ...]

    @property
    def fingerprint(self) -> str:
        return hashlib.sha256(json.dumps(asdict(self), ensure_ascii=False, sort_keys=True).encode()).hexdigest()


def keyword_match(text: str, alias: str) -> bool:
    # ASCII identifier boundaries stop Promise/Promiseland and useState/useStates.
    # Korean particles can follow a term; preceding Hangul cannot be part of it.
    left = r"(?<![\w])" if alias[0].isascii() else r"(?<![가-힣A-Za-z0-9_])"
    right = r"(?![A-Za-z0-9_])" if alias[-1].isascii() else ""
    return bool(re.search(left + re.escape(alias) + right, text, re.IGNORECASE))


def _concept(identifier: str, description: str, role: ContentRole, *aliases: str) -> Concept:
    return Concept(identifier, description, aliases, role, f"template:{identifier}:goal-connection-v1")


_TEMPLATES: dict[str, tuple[tuple[str, ...], tuple[Concept, ...]]] = {
    "react-object-state": (("useState", "객체 상태"), (
        _concept("state-update", "React useState object state updates with a setter", ContentRole.DIRECT, "useState", "setState", "객체 상태", "상태 변경 함수", "상태 업데이트"),
        _concept("object-spread", "JavaScript object spread and immutable copies needed to update state", ContentRole.PREREQUISITE, "전개 구문", "spread syntax", "JavaScript 객체", "객체 복사", "...prev"),
        _concept("state-debug", "Inspect React object state with React DevTools", ContentRole.SUPPORTING, "React DevTools", "상태 확인"),
    )),
    "jwt-auth": (("JWT",), (
        _concept("jwt-auth", "Implement JWT authentication and verification", ContentRole.DIRECT, "JWT", "인증 필터", "토큰 검증"),
        _concept("jwt-errors", "Debug JWT authentication 403 errors and Spring Security filter order", ContentRole.SUPPORTING, "403", "인증 오류", "필터 순서", "Spring Security"),
    )),
    "model-finetuning": (("파인튜닝", "fine-tuning", "finetuning"), (
        _concept("finetuning", "Fine-tune language models with LoRA and training data", ContentRole.DIRECT, "파인튜닝", "fine-tuning", "LoRA", "학습 데이터"),
        _concept("embedding-vectors", "Numerical embedding vectors for machine learning", ContentRole.PREREQUISITE, "임베딩", "embedding", "벡터", "vector"),
    )),
    "javascript-promise": (("Promise",), (
        _concept("promise", "JavaScript Promise asynchronous resolve reject and await", ContentRole.DIRECT, "Promise", "resolve", "reject", "await", "비동기"),
        _concept("promise-errors", "Debug JavaScript Promise rejections", ContentRole.SUPPORTING, "UnhandledPromiseRejection", "비동기 오류"),
    )),
    "react-basics": (("React",), (
        _concept("react", "Learn React components hooks and rendering", ContentRole.DIRECT, "React", "컴포넌트", "component", "hook", "렌더링"),
    )),
}

# Explicit alternative subjects provide positive unrelated evidence. The absence
# of a graph match alone is never such evidence. Coverage remains conservative.
_UNRELATED = (
    Concept("vector-art", "Create vector graphics posters in Illustrator", ("벡터 그래픽", "포스터", "Illustrator", "vector graphics"), None, "alternative-topic-v1"),
    Concept("music", "Song lyrics music albums and singers", ("노래", "가사", "lyrics", "앨범", "singer"), None, "alternative-topic-v1"),
    Concept("cooking", "Cooking recipes and ingredients", ("요리", "레시피", "조리", "recipe"), None, "alternative-topic-v1"),
)


def build_goal_graph(goal_text: str, template_id: str) -> GoalGraph | None:
    template = _TEMPLATES.get(template_id)
    if not template or not any(keyword_match(goal_text, anchor) for anchor in template[0]):
        return None
    if template_id == "react-object-state" and not any(keyword_match(goal_text, anchor) for anchor in ("React", "useState")):
        return None
    return GoalGraph(template_id, "school-templates-v1", goal_text, template[1], _UNRELATED)
