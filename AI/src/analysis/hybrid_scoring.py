from __future__ import annotations

import math
from dataclasses import dataclass
from typing import Sequence

from .goal_graph import Concept, ContentRole, GoalGraph, keyword_match
from ..preprocessing.models import CleanedPassage, PassageKind


class VectorError(ValueError):
    pass


def normalize_vector(vector: Sequence[float], dimensions: int) -> tuple[float, ...]:
    if not isinstance(vector, (list, tuple)) or len(vector) != dimensions:
        raise VectorError("VECTOR_DIMENSION_INVALID")
    if any(isinstance(value, bool) or not isinstance(value, (int, float)) or not math.isfinite(value) for value in vector):
        raise VectorError("VECTOR_NONFINITE")
    # Scaling avoids norm overflow on finite but large provider values.
    scale = max((abs(value) for value in vector), default=0)
    if scale == 0:
        raise VectorError("VECTOR_ZERO")
    scaled = tuple(value / scale for value in vector)
    norm = math.sqrt(sum(value * value for value in scaled))
    return tuple(value / norm for value in scaled)


def cosine_similarity(first: Sequence[float], second: Sequence[float]) -> float:
    a = normalize_vector(first, len(first))
    b = normalize_vector(second, len(first))
    return max(-1.0, min(1.0, sum(x * y for x, y in zip(a, b))))


@dataclass(frozen=True, slots=True)
class HybridConfig:
    # All numbers are an uncalibrated experimental policy, not release criteria.
    keyword_weight: float = 0.35
    vector_weight: float = 0.65
    direct_threshold: float = 0.72
    prerequisite_threshold: float = 0.76
    supporting_threshold: float = 0.76
    min_cosine: float = 0.55
    related_coverage: float = 0.60
    unrelated_coverage: float = 0.70
    unrelated_threshold: float = 0.80

    def __post_init__(self) -> None:
        values = tuple(getattr(self, name) for name in self.__dataclass_fields__)
        if any(isinstance(value, bool) or not isinstance(value, (int, float)) or not math.isfinite(value) or not 0 <= value <= 1 for value in values):
            raise ValueError("INVALID_HYBRID_CONFIG")
        if not math.isclose(self.keyword_weight + self.vector_weight, 1.0):
            raise ValueError("HYBRID_WEIGHTS_MUST_SUM_TO_ONE")
        if not self.related_coverage or not self.unrelated_coverage:
            raise ValueError("COVERAGE_MUST_BE_POSITIVE")

    def threshold(self, role: ContentRole) -> float:
        return {ContentRole.DIRECT: self.direct_threshold,
                ContentRole.PREREQUISITE: self.prerequisite_threshold,
                ContentRole.SUPPORTING: self.supporting_threshold}[role]


@dataclass(frozen=True, slots=True)
class Match:
    passage_id: str
    concept_id: str
    role: ContentRole | None
    keyword_score: float
    cosine: float
    hybrid_score: float


@dataclass(frozen=True, slots=True)
class Decision:
    label: str
    reason_code: str
    evidence_ids: tuple[str, ...] = ()
    roles: tuple[ContentRole, ...] = ()
    matches: tuple[Match, ...] = ()


def match_passage(passage: CleanedPassage, concept: Concept,
                  passage_vector: Sequence[float], concept_vector: Sequence[float],
                  config: HybridConfig) -> Match:
    hits = sum(keyword_match(passage.text, alias) for alias in concept.aliases)
    keyword_score = min(1.0, hits / 2)  # One incidental mention is insufficient.
    cosine = cosine_similarity(passage_vector, concept_vector)
    # Cosine [-1,1] -> clipped positive evidence [0,1]; negative is no evidence.
    hybrid = config.keyword_weight * keyword_score + config.vector_weight * max(0, cosine)
    return Match(passage.id, concept.id, concept.role, keyword_score, cosine, hybrid)


def decide_relevance(passages: tuple[CleanedPassage, ...], graph: GoalGraph,
                     passage_vectors: Sequence[Sequence[float]],
                     concept_vectors: Sequence[Sequence[float]], config: HybridConfig) -> Decision:
    concepts = graph.concepts + graph.unrelated_topics
    if len(passage_vectors) != len(passages) or len(concept_vectors) != len(concepts):
        raise VectorError("VECTOR_COUNT_INVALID")
    # Titles/headings describe context; they do not increase body coverage.
    main = tuple(p for p in passages if p.kind not in {PassageKind.TITLE, PassageKind.HEADING})
    total = sum(len(p.text) for p in main)
    if not total:
        return Decision("UNCERTAIN", "INSUFFICIENT_BODY")
    matches = tuple(match_passage(passage, concept, passage_vectors[index], concept_vectors[node], config)
                    for index, passage in enumerate(passages)
                    for node, concept in enumerate(concepts))
    related = tuple(match for match in matches if match.role is not None
                    and match.keyword_score > 0 and match.cosine >= config.min_cosine
                    and match.hybrid_score >= config.threshold(match.role))
    unrelated = tuple(match for match in matches if match.role is None
                      and match.keyword_score == 1 and match.cosine >= config.min_cosine
                      and match.hybrid_score >= config.unrelated_threshold)
    related_ids = {match.passage_id for match in related}
    unrelated_ids = {match.passage_id for match in unrelated}
    related_coverage = sum(len(p.text) for p in main if p.id in related_ids) / total
    unrelated_coverage = sum(len(p.text) for p in main if p.id in unrelated_ids) / total
    # Competing themes in the same content need additional context, not a score sum.
    if related_ids and unrelated_ids:
        return Decision("UNCERTAIN", "CONFLICTING_EVIDENCE", matches=matches)
    if related_coverage >= config.related_coverage:
        ids = tuple(p.id for p in passages if p.id in related_ids)
        roles = tuple(role for role in ContentRole if any(m.role is role for m in related))
        return Decision("RELATED", "GOAL_CONNECTED", ids, roles, matches)
    if unrelated_coverage >= config.unrelated_coverage:
        return Decision("UNRELATED", "POSITIVE_UNRELATED_TOPIC", tuple(p.id for p in passages if p.id in unrelated_ids), matches=matches)
    return Decision("UNCERTAIN", "INSUFFICIENT_EVIDENCE", matches=matches)
