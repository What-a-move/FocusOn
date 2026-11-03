"""Bounded context for explicitly identified code; source passages stay intact."""
from __future__ import annotations

from dataclasses import dataclass

from ..preprocessing.models import CleanedPassage, PassageKind


@dataclass(frozen=True, slots=True)
class CodeContextGroup:
    passage: CleanedPassage
    source_ids: tuple[str, str]


def build_code_context_groups(
    passages: tuple[CleanedPassage, ...], *, max_bytes: int = 8_000,
    source_pairs: tuple[tuple[str, str], ...] | None = None,
) -> tuple[CodeContextGroup, ...]:
    groups = []
    for explanation, code in zip(passages, passages[1:]):
        if (explanation.kind not in {PassageKind.BODY, PassageKind.OCR}
                or code.kind is not PassageKind.CODE or code.order != explanation.order + 1):
            continue
        # Cleaning can remove a heading/table or split a long block. Require
        # provenance in an adjacent original pair when called by the pipeline.
        if source_pairs is not None and not any(
            explanation.text in body and code.text in source_code for body, source_code in source_pairs
        ):
            continue
        text = explanation.text + "\n\n" + code.text
        if len(text.encode("utf-8")) > max_bytes:
            continue
        groups.append(CodeContextGroup(
            CleanedPassage(f"code-context:{explanation.id}:{code.id}", PassageKind.CODE, text, code.order),
            (explanation.id, code.id),
        ))
    return tuple(groups)
