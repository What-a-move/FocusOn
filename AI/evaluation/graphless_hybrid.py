"""Experimental keyword + embedding relevance baseline without a goal graph."""
from __future__ import annotations

import math
import json
import hashlib
import re
import unicodedata
from dataclasses import dataclass
from functools import lru_cache
from typing import Sequence
from pathlib import Path

from src.analysis.hybrid_scoring import cosine_similarity
from src.preprocessing.models import CleanedPassage, PassageKind
from src.analysis.passage_grouping import CodeContextGroup

STOPWORDS = frozenset({
    "학습", "공부", "하기", "배우기", "공부하기", "알아보기", "구현", "방법", "이해", "파악",
    "a", "an", "the", "of", "to", "and", "or", "for", "with", "in", "on", "by", "from", "using",
    "learn", "learning", "study", "studying", "understand", "understanding", "implement", "implementing", "how",
})
GENERAL_TERMS = frozenset({
    "선택", "비교", "정리", "확인", "차이", "상황",
    "selection", "comparison", "summary", "checking", "difference", "differences", "scenario",
})
# Broad nouns remain usable, but are weaker than distinctive goal terms.
BROAD_TERMS = frozenset({"상태", "객체", "값", "결과", "자료", "정보", "내용", "화면", "데이터",
                         "state", "object", "value", "result", "data", "information", "content", "screen"})
KEYWORD_EXTRACTOR_VERSION = "kiwi-weighted-terms-v3"
SCORING_VERSION = "body-context-hybrid-v3"
_TECH_TERM = re.compile(r"[A-Za-z][A-Za-z0-9_]*(?:[+#]{1,2}|(?:[.-][A-Za-z0-9_]+)*)")


@lru_cache(maxsize=1)
def _tokenizer():
    # Cache only the analyzer, never page text or extracted keyword lists.
    from kiwipiepy import Kiwi
    return Kiwi(num_workers=1)


def _term_sequences(text: str) -> tuple[tuple[str, ...], ...]:
    normalized = unicodedata.normalize("NFKC", text)
    # Protect code identifiers before Korean morphology splits attached particles.
    spans = [(match.start(), match.end(), "SL") for match in _TECH_TERM.finditer(normalized)]
    tokens = _tokenizer().tokenize(normalized, pretokenized=spans)
    sequences, current = [], []
    previous_end = 0
    for token in tokens:
        if "\n" in normalized[previous_end:token.start] and current:
            sequences.append(tuple(current))
            current = []
        term = token.form.casefold()
        if token.tag in {"NNG", "NNP", "SL"} and term not in STOPWORDS:
            current.append(term)
        elif not token.tag.startswith("J") and current:
            # Only particles may connect phrase terms; verbs and punctuation cannot.
            sequences.append(tuple(current))
            current = []
        previous_end = token.start + token.len
    if current:
        sequences.append(tuple(current))
    return tuple(sequences)


def _terms(sequences: Sequence[Sequence[str]]) -> tuple[str, ...]:
    return tuple(dict.fromkeys(term for sequence in sequences for term in sequence))


def _pairs(sequences: Sequence[Sequence[str]]) -> set[tuple[str, str]]:
    return {pair for sequence in sequences for pair in zip(sequence, sequence[1:])}


def _goal_terms(goal_text: str):
    sequences = _term_sequences(goal_text)
    terms = _terms(sequences)
    core = tuple(term for term in terms if term not in GENERAL_TERMS)
    general = tuple(term for term in terms if term in GENERAL_TERMS)
    # Preserve expressions such as 선택 정렬 and 프로토콜 선택 as ordered noun pairs.
    phrases = tuple(sorted(pair for pair in _pairs(sequences)
                           if (pair[0] in GENERAL_TERMS) != (pair[1] in GENERAL_TERMS)))
    return core, general, phrases


@dataclass(frozen=True)
class GraphlessConfig:
    # Initial experiment values, not calibrated production thresholds.
    keyword_boost: float = 0.20
    related_passage_threshold: float = 0.60
    unrelated_cosine_threshold: float = 0.25
    min_related_cosine: float = 0.55
    unrelated_coverage_threshold: float = 0.70
    unrelated_min_body_chars: int = 80

    def __post_init__(self) -> None:
        numeric = (self.keyword_boost,
                   self.related_passage_threshold, self.unrelated_cosine_threshold,
                   self.min_related_cosine, self.unrelated_coverage_threshold)
        if any(isinstance(value, bool) or not isinstance(value, (int, float))
               or not math.isfinite(value) or not 0 <= value <= 1 for value in numeric):
            raise ValueError("GRAPHLESS_THRESHOLD_OUT_OF_RANGE")
        if self.unrelated_cosine_threshold >= self.min_related_cosine:
            raise ValueError("GRAPHLESS_COSINE_THRESHOLDS_MUST_LEAVE_UNCERTAIN_BAND")
        if not self.unrelated_coverage_threshold:
            raise ValueError("GRAPHLESS_COVERAGE_MUST_BE_POSITIVE")
        if (isinstance(self.unrelated_min_body_chars, bool)
                or not isinstance(self.unrelated_min_body_chars, int) or self.unrelated_min_body_chars < 1):
            raise ValueError("GRAPHLESS_BODY_LIMIT_INVALID")


def extract_goal_keywords(goal_text: str) -> tuple[str, ...]:
    """Goal-only lexical terms; no generated aliases or semantic expansion."""
    return _goal_terms(goal_text)[0]


def load_calibrated_config(path: Path | None = None) -> tuple[GraphlessConfig, dict]:
    path = path or Path(__file__).with_name("graphless-calibrated-config.json")
    if not path.exists():
        return GraphlessConfig(), {"status": "UNCALIBRATED"}
    value = json.loads(path.read_text(encoding="utf-8"))
    from src.analysis.relevance_analyzer import DEFAULT_INSTRUCTION
    embedding = value.get("embedding", {})
    if (value.get("scoringVersion") != SCORING_VERSION
            or value.get("keywordExtractorVersion") != KEYWORD_EXTRACTOR_VERSION
            or value.get("constraintSatisfied") is not True
            or value.get("selectedUsing") != "development-only"
            or embedding.get("model") != "qwen3-embedding:4b-fp16"
            or embedding.get("dimensions") != 2560
            or not embedding.get("configuredRevision")
            or embedding.get("instructionSha256") != hashlib.sha256(DEFAULT_INSTRUCTION.encode()).hexdigest()):
        raise ValueError("CALIBRATION_VERSION_OR_SAFETY_MISMATCH")
    return GraphlessConfig(**value["config"]), value


@dataclass(frozen=True, slots=True)
class ScreenContext:
    passage: CleanedPassage
    source_ids: tuple[str, ...]


def build_screen_contexts(passages: Sequence[CleanedPassage], *, window_title: str = "",
                          max_bytes: int = 8_000) -> tuple[ScreenContext, ...]:
    """Only actual same-screen metadata; never create headings or goal terms."""
    title = next((p.text for p in passages if p.kind is PassageKind.TITLE), window_title.strip())
    headings: list[tuple[int, str]] = []
    groups = []
    for passage in passages:
        if passage.kind is PassageKind.HEADING:
            match = re.match(r"^ {0,3}(#{1,3})[ \t]+(.+)", passage.text)
            if match:
                depth = len(match[1])
                headings = [(level, text) for level, text in headings if level < depth]
                headings.append((depth, match[2]))
            continue
        if passage.kind is PassageKind.TITLE or not (title or headings):
            continue
        parts = (["[page title]\n" + title] if title else [])
        parts += ["[heading]\n" + text for _, text in headings]
        parts.append("[body]\n" + passage.text)
        text = "\n\n".join(parts)
        if len(text.encode("utf-8")) <= max_bytes:
            groups.append(ScreenContext(CleanedPassage(
                "screen-context:" + passage.id, passage.kind, text, passage.order), (passage.id,)))
    return tuple(groups)


def decide_matches(matches: Sequence[dict], config: GraphlessConfig = GraphlessConfig(), *,
                   allow_unrelated: bool = True) -> dict:
    """Replay thresholds on fixed, unrounded signals without another model call."""
    body = {row["passageId"]: row for row in matches
            if row.get("unitType", "PASSAGE") == "PASSAGE" and row["kind"] not in {"TITLE", "HEADING"}}
    total_chars = sum(len(row["text"]) for row in body.values())
    related_ids, unrelated_ids = set(), set()
    rows = []
    for original in matches:
        row = dict(original)
        ids = row["sourceIds"]
        valid_body = bool(ids) and all(pid in body for pid in ids)
        if row.get("unitType") == "SCREEN_CONTEXT":
            # A relevant title cannot override a weak/unrelated raw body.
            raw_floor = min((body[pid]["cosine"] for pid in ids if pid in body), default=-1.)
            related = (valid_body and raw_floor >= config.min_related_cosine
                       and raw_floor > config.unrelated_cosine_threshold
                       and row["hybridScore"] >= config.related_passage_threshold)
            unrelated = False
        else:
            related = (valid_body and row["cosine"] >= config.min_related_cosine
                       and row["hybridScore"] >= config.related_passage_threshold)
            # A keyword hit is evidence, never a categorical veto of low V.
            unrelated = (row["passageId"] in body
                         and row["cosine"] <= config.unrelated_cosine_threshold)
        row.update(relatedEvidence=bool(related), unrelatedEvidence=bool(unrelated))
        if related:
            related_ids.update(ids)
        if unrelated:
            unrelated_ids.add(row["passageId"])
        rows.append(row)
    related_chars = sum(len(row["text"]) for pid, row in body.items() if pid in related_ids)
    unrelated_chars = sum(len(row["text"]) for pid, row in body.items() if pid in unrelated_ids)
    related_coverage = related_chars / total_chars if total_chars else 0.
    unrelated_coverage = unrelated_chars / total_chars if total_chars else 0.
    if related_ids and unrelated_ids:
        label, reason = "UNCERTAIN", "CONFLICTING_EVIDENCE"
    elif related_ids:
        label, reason = "RELATED", "RELATED_EVIDENCE_FOUND"
    elif (allow_unrelated and total_chars >= config.unrelated_min_body_chars
          and unrelated_coverage >= config.unrelated_coverage_threshold):
        label, reason = "UNRELATED", "UNRELATED_COVERAGE_MET"
    else:
        label, reason = "UNCERTAIN", "INSUFFICIENT_EVIDENCE"
    return {"relevanceLabel": label, "reasonCode": reason,
            "relatedCoverage": round(related_coverage, 4), "unrelatedCoverage": round(unrelated_coverage, 4),
            "totalBodyChars": total_chars, "relatedBodyChars": related_chars, "unrelatedBodyChars": unrelated_chars,
            "matches": rows, "evidenceIds": sorted(related_ids | unrelated_ids)}


def score_passages(
    goal_text: str,
    passages: Sequence[CleanedPassage],
    goal_vector: Sequence[float],
    passage_vectors: Sequence[Sequence[float]],
    config: GraphlessConfig = GraphlessConfig(),
    *, context_groups: Sequence[CodeContextGroup] = (),
    context_vectors: Sequence[Sequence[float]] = (), allow_unrelated: bool = True,
    screen_contexts: Sequence[ScreenContext] = (),
    screen_vectors: Sequence[Sequence[float]] = (),
) -> dict:
    if (len(passages) != len(passage_vectors) or len(context_groups) != len(context_vectors)
            or len(screen_contexts) != len(screen_vectors)):
        raise ValueError("VECTOR_COUNT_INVALID")
    body = tuple(p for p in passages if p.kind not in {PassageKind.TITLE, PassageKind.HEADING})
    total_chars = sum(len(p.text) for p in body)
    keywords, general_terms, goal_phrases = _goal_terms(goal_text)
    weights = {term: .25 if term in BROAD_TERMS else 1. for term in keywords}
    keyword_info = {"keywordTerms": list(keywords), "generalTerms": list(general_terms),
                    "goalPhrases": [" ".join(pair) for pair in goal_phrases], "keywordWeights": weights}
    if not body or total_chars == 0 or not keywords:
        return {"relevanceLabel": "UNCERTAIN",
                "reasonCode": "INSUFFICIENT_BODY" if not body or total_chars == 0 else "INSUFFICIENT_GOAL_KEYWORDS",
                "relatedCoverage": 0., "unrelatedCoverage": 0., "totalBodyChars": total_chars,
                "relatedBodyChars": 0, "unrelatedBodyChars": 0,
                "matchedKeywords": [], "matches": [], "evidenceIds": [], **keyword_info}
    rows = []
    units = [(p, v, (p.id,), "PASSAGE") for p, v in zip(passages, passage_vectors)]
    units.extend((g.passage, v, g.source_ids, "CODE_CONTEXT") for g, v in zip(context_groups, context_vectors))
    base_units = {p.id: p for p in passages}
    units.extend((g.passage, v, g.source_ids, "SCREEN_CONTEXT") for g, v in zip(screen_contexts, screen_vectors))
    for passage, vector, source_ids, unit_type in units:
        # Metadata may affect the context vector, never the body's lexical K.
        lexical_text = ("\n".join(base_units[pid].text for pid in source_ids)
                        if unit_type == "SCREEN_CONTEXT" else passage.text)
        sequences = _term_sequences(lexical_text)
        passage_terms = frozenset(_terms(sequences))
        hits = [term for term in keywords if term in passage_terms]
        phrase_hits = [pair for pair in goal_phrases if pair in _pairs(sequences)]
        general_hits = [term for term in general_terms if term in passage_terms]
        contextual_general = {term for pair in phrase_hits for term in pair if term in GENERAL_TERMS}
        keyword_score = sum(weights[term] for term in hits) / sum(weights.values())
        cosine = cosine_similarity(goal_vector, vector)
        vector_base = min(1., max(0., cosine))
        keyword_boost = config.keyword_boost * keyword_score * (1. - vector_base)
        rows.append({"passageId": passage.id, "sourceIds": list(source_ids), "kind": passage.kind.value,
                     "unitType": unit_type, "keywordHits": hits, "keywordScore": keyword_score,
                     "generalKeywordHits": general_hits,
                     "ignoredGeneralHits": [term for term in general_hits if term not in contextual_general],
                     "goalPhraseHits": [" ".join(pair) for pair in phrase_hits],
                     "cosine": cosine, "hybridScore": vector_base + keyword_boost,
                     "vectorBase": vector_base, "keywordBoost": keyword_boost,
                     "charCount": len(passage.text), "text": passage.text})
    decision = decide_matches(rows, config, allow_unrelated=allow_unrelated)
    return {**decision, "matchedKeywords": sorted({term for row in rows if row["kind"] not in {"TITLE", "HEADING"}
                                                   for term in row["keywordHits"]}), **keyword_info}


# Metadata thresholds are an uncalibrated, conservative experiment baseline.
# They are independent of the body-only calibration file.
METADATA_SCORING_VERSION = "title-hybrid-v1"
METADATA_CONFIG = GraphlessConfig(keyword_boost=.20, min_related_cosine=.55,
                                  related_passage_threshold=.60, unrelated_cosine_threshold=.25)
_METADATA_GENERIC_TERMS = frozenset({"자료", "문서", "노트", "페이지", "참고", "탭", "새", "제목",
                                      "document", "untitled", "notes", "page", "tab", "new"})


def metadata_titles(page_title: str, window_title: str) -> tuple[str, ...]:
    return tuple(dict.fromkeys(text for text in (page_title.strip(), window_title.strip()) if text))


def metadata_is_informative(text: str) -> bool:
    return bool(set(_terms(_term_sequences(text))) - _METADATA_GENERIC_TERMS - GENERAL_TERMS)


def score_metadata(goal_text: str, titles: Sequence[str], goal_vector: Sequence[float],
                   title_vectors: Sequence[Sequence[float]], *, allow_unrelated: bool = True) -> dict:
    """Judge actual titles; never use a synthetic body or coverage/length gates."""
    if len(titles) != len(title_vectors):
        raise ValueError("VECTOR_COUNT_INVALID")
    config = METADATA_CONFIG
    keywords, general, phrases = _goal_terms(goal_text)
    weights = {term: .25 if term in BROAD_TERMS else 1. for term in keywords}
    rows = []
    for index, (title, vector) in enumerate(zip(titles, title_vectors)):
        sequences = _term_sequences(title)
        terms = set(_terms(sequences))
        hits = [term for term in keywords if term in terms]
        k = sum(weights[term] for term in hits) / sum(weights.values()) if weights else 0.
        cosine = cosine_similarity(goal_vector, vector)
        base = min(1., max(0., cosine))
        boost = config.keyword_boost * k * (1. - base)
        informative = metadata_is_informative(title)
        related = bool(keywords) and informative and cosine >= config.min_related_cosine and base + boost >= config.related_passage_threshold
        unrelated = bool(keywords) and informative and allow_unrelated and cosine <= config.unrelated_cosine_threshold
        rows.append({"passageId": f"metadata-{index}", "sourceIds": [f"metadata-{index}"],
                     "kind": "TITLE", "unitType": "METADATA", "text": title,
                     "keywordHits": hits, "keywordScore": k, "cosine": cosine,
                     "vectorBase": base, "keywordBoost": boost, "hybridScore": base + boost,
                     "informative": informative, "relatedEvidence": related,
                     "unrelatedEvidence": unrelated})
    positive = [row for row in rows if row["relatedEvidence"]]
    negative = [row for row in rows if row["unrelatedEvidence"]]
    if positive and negative:
        label, reason = "UNCERTAIN", "METADATA_CONFLICTING"
    elif positive:
        label, reason = "RELATED", "METADATA_HYBRID_RELATED"
    elif negative:
        label, reason = "UNRELATED", "METADATA_HYBRID_UNRELATED"
    else:
        label, reason = "UNCERTAIN", "METADATA_INSUFFICIENT"
    return {"relevanceLabel": label, "reasonCode": reason, "matches": rows,
            "evidenceIds": [row["passageId"] for row in positive + negative],
            "matchedKeywords": sorted({term for row in rows for term in row["keywordHits"]}),
            "keywordTerms": list(keywords), "generalTerms": list(general),
            "goalPhrases": [" ".join(pair) for pair in phrases], "keywordWeights": weights,
            "relatedCoverage": 0., "unrelatedCoverage": 0., "totalBodyChars": 0,
            "relatedBodyChars": 0, "unrelatedBodyChars": 0}
