"""Synthetic-only learning summary behavior for the #29 local demo.

This module is not a provider, storage, or product API implementation.
"""

from __future__ import annotations

from dataclasses import dataclass
import re


MAX_EVIDENCE_CHARS = 120
MAX_EVIDENCE_COUNT = 20
MAX_SUMMARY_CHARS = 1200

_SENSITIVE = (
    re.compile(r"https?://|www\.", re.IGNORECASE),
    re.compile(r"\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}\b", re.IGNORECASE),
    re.compile(r"\b(?:sk-|ghp_|gho_|github_pat_|AKIA)[A-Za-z0-9_-]{8,}\b"),
    re.compile(r"\b(?:\+?82[-. ]?)?0?1[016789][-. ]?\d{3,4}[-. ]?\d{4}\b"),
)


@dataclass(frozen=True)
class DemoEvidence:
    concept: str
    observation: str
    label: str = "RELATED"
    page_type: str = "DOCUMENTATION"

    @property
    def text(self) -> str:
        return f"{self.concept.strip()}: {self.observation.strip()}"


@dataclass(frozen=True)
class SyntheticDOMPage:
    """Editable fake page shaped like the proposed DOM extraction contract."""

    page_type: str
    title: str
    text: str
    extraction_method: str = "DOM"
    extraction_status: str = "SUCCESS"
    extractor_version: str = "synthetic-fixture-v1"
    relation: str = "RELATED"
    ocr: dict[str, object] | None = None


@dataclass(frozen=True)
class DemoOutcome:
    status: str
    summary: dict[str, object] | None = None
    accepted_count: int = 0


def _has_sensitive_value(text: str) -> bool:
    return any(pattern.search(text) for pattern in _SENSITIVE)


def eligible_evidence(evidences: list[DemoEvidence], record_consent: bool) -> list[DemoEvidence]:
    """Keep at most 20 distinct, short, non-sensitive final RELATED observations."""
    if not record_consent:
        return []
    accepted: list[DemoEvidence] = []
    seen: set[str] = set()
    for evidence in evidences:
        if evidence.label != "RELATED":
            continue
        if not evidence.concept.strip() or not evidence.observation.strip():
            continue
        if len(evidence.observation.strip()) > MAX_EVIDENCE_CHARS or _has_sensitive_value(evidence.text):
            continue
        normalized = " ".join(evidence.observation.split()).casefold()
        if normalized not in seen:
            accepted.append(evidence)
            seen.add(normalized)
        if len(accepted) == MAX_EVIDENCE_COUNT:
            break
    return accepted


def evidence_from_dom_pages(
    pages: list[SyntheticDOMPage], *, record_consent: bool = True
) -> list[DemoEvidence]:
    """Turn allowed DOM text paragraphs into distinct note evidence.

    This is a local demo preprocessor, not the production quality scorer. It
    accepts only successful DOM extraction marked RELATED and treats separate
    paragraphs on one page as separate observations.
    """
    if not record_consent:
        return []
    candidates: list[DemoEvidence] = []
    for page in pages:
        if page.relation != "RELATED" or page.extraction_status != "SUCCESS":
            continue
        if page.extraction_method != "DOM" or page.ocr is not None:
            continue
        if (
            not page.page_type.strip()
            or not page.title.strip()
            or _has_sensitive_value(page.page_type)
            or _has_sensitive_value(page.title)
        ):
            continue
        if _has_sensitive_value(page.text):
            continue
        for raw_paragraph in re.split(r"\n+", page.text):
            paragraph = " ".join(raw_paragraph.split())
            if len(paragraph) < 12:
                continue
            # Keep short paragraphs intact; split only oversized paragraphs.
            chunks = [paragraph]
            if len(paragraph) > MAX_EVIDENCE_CHARS:
                chunks = []
                current = ""
                for sentence in re.split(r"(?<=[.!?。！？])\s+", paragraph):
                    sentence = sentence.strip()
                    if not sentence:
                        continue
                    candidate = f"{current} {sentence}".strip()
                    if current and len(candidate) > MAX_EVIDENCE_CHARS:
                        chunks.append(current)
                        current = sentence
                    else:
                        current = candidate
                if current:
                    chunks.append(current)
            for chunk in chunks:
                if len(chunk) < 12 or len(chunk) > MAX_EVIDENCE_CHARS:
                    continue
                candidates.append(DemoEvidence(page.title.strip(), chunk, page_type=page.page_type.strip()))
    return eligible_evidence(candidates, record_consent=True)


def make_demo_summary(
    goal: str,
    evidences: list[DemoEvidence],
    *,
    record_consent: bool = True,
    simulate_failure: bool = False,
) -> DemoOutcome:
    """Return a deterministic mock; it never calls a model or writes data."""
    clean_goal = goal.strip()
    if not 2 <= len(clean_goal) <= 40 or _has_sensitive_value(clean_goal):
        return DemoOutcome("FAILED")
    accepted = eligible_evidence(evidences, record_consent)
    if len(accepted) < 2:
        return DemoOutcome("INSUFFICIENT_DATA", accepted_count=len(accepted))
    if simulate_failure:
        return DemoOutcome("FAILED", accepted_count=len(accepted))

    selected = accepted[:5]
    concepts = [
        {
            "name": item.concept.strip(),
            "briefExplanation": "이 문단에서 확인된 내용을 다시 살펴볼 수 있습니다.",
            "sessionEvidence": item.observation.strip(),
        }
        for item in selected
    ]
    summary: dict[str, object] = {
        "overview": f"'{clean_goal}' 목표와 관련된 자료 {len(accepted)}가지를 살펴봤습니다. 이해·구현 완료 여부는 확인되지 않았습니다.",
        "keyConcepts": concepts,
        "nextStartPrompt": f"다음에는 {selected[0].concept.strip()} 자료의 핵심을 다시 확인해 보세요.",
    }
    # The mock must obey the planned output size, even with long synthetic inputs.
    rendered = _render_summary(summary)
    if len(rendered) > MAX_SUMMARY_CHARS:
        return DemoOutcome("FAILED", accepted_count=len(accepted))
    return DemoOutcome("READY", summary, len(accepted))


def _render_summary(summary: dict[str, object]) -> str:
    concepts = summary["keyConcepts"]
    assert isinstance(concepts, list)
    lines = [str(summary["overview"])]
    for item in concepts:
        assert isinstance(item, dict)
        lines.append(f"- {item['name']}: {item['briefExplanation']} (근거: {item['sessionEvidence']})")
    lines.append(str(summary["nextStartPrompt"]))
    return "\n".join(lines)


def copy_text(outcome: DemoOutcome) -> str:
    if outcome.status != "READY" or outcome.summary is None:
        return ""
    return _render_summary(outcome.summary)
