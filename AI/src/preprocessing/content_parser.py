"""Validate passage structure without echoing rejected input in errors."""

import math
import re

from .models import Passage


KINDS = frozenset({"TITLE", "HEADING", "BODY", "CODE", "TABLE", "CAPTION", "OCR"})
_SAFE_ID = re.compile(r"[A-Za-z0-9][A-Za-z0-9._:-]{0,127}\Z")
MAX_PASSAGES = 64
MAX_TEXT_LENGTH = 8000


class InvalidStructure(ValueError):
    """A fixed, non-sensitive code for invalid input."""

    def __init__(self, code: str = "INVALID_INPUT") -> None:
        super().__init__(code)
        self.code = code


def parse_passages(value: object, method: str) -> tuple[Passage, ...]:
    if not isinstance(value, list) or len(value) > MAX_PASSAGES:
        raise InvalidStructure()

    parsed: list[Passage] = []
    ids: set[str] = set()
    orders: set[int] = set()
    for item in value:
        if type(item) is not dict or set(item) - {
            "id", "kind", "text", "order", "recognitionConfidence"
        }:
            raise InvalidStructure()
        passage_id = item.get("id")
        kind = item.get("kind")
        order = item.get("order")
        content = item.get("text")
        confidence = item.get("recognitionConfidence")
        if (
            not isinstance(passage_id, str)
            or not _SAFE_ID.fullmatch(passage_id)
            or passage_id in ids
            or not isinstance(kind, str)
            or kind not in KINDS
            or (method == "DOM" and kind == "OCR")
            or type(order) is not int
            or order < 0
            or order in orders
            or not isinstance(content, str)
            or len(content) > MAX_TEXT_LENGTH
        ):
            raise InvalidStructure()
        if confidence is not None and (
            type(confidence) not in (int, float)
            or not math.isfinite(confidence)
            or not 0 <= confidence <= 1
            or method != "OCR"
        ):
            raise InvalidStructure()
        ids.add(passage_id)
        orders.add(order)
        parsed.append(Passage(passage_id, kind, order, content, confidence))

    return tuple(sorted(parsed, key=lambda passage: passage.order))
