"""Attribute an already-classified study screen to confirmed session goals."""
import math
from typing import Literal

from .activity import parts
from .beta_decision import beta_decision
from ...analysis.acquisition_policy import METADATA_SOURCES
from ...preprocessing.models import ContentSource, ExtractionStatus, PassageKind, PreprocessingConfig
from ...preprocessing.text_cleaner import clean_text

MATCH_COSINE = 0.86
FAR_COSINE = 0.81
MIN_FAR_BODY_CHARS = 80
PREPROCESSING = PreprocessingConfig()
MatchStatus = Literal["MATCHED", "FAR", "NOT_ENOUGH"]


def goal_terms(goal):
    profile = goal.profile
    terms = (goal.text,)
    if profile is not None:
        terms += (profile.main_topic, *profile.core_topics, *profile.supporting_topics, *profile.expected_activities)
    return tuple(dict.fromkeys(term for item in terms if (term := item.strip())))


def match_goal(classifier, content, payload, goals) -> tuple[str | None, MatchStatus]:
    metadata_only = content.content_source in METADATA_SOURCES
    body = ""
    if not metadata_only:
        if content.content_source is ContentSource.DESKTOP_APP_OCR and content.ocr is not None:
            if content.ocr.capture_status != "SUCCESS" or content.ocr.confidence < PREPROCESSING.min_quality_score:
                return None, "NOT_ENOUGH"
        body = clean_text(content.text, PassageKind.BODY, PREPROCESSING.ui_fragments, strip_html=False).text
        payload = {**payload, "contentPayload": {"text": body}}
    title, text = parts(payload)
    if not text or (not metadata_only and text == title):
        return None, "NOT_ENOUGH"

    targets = [(goal.goal_id, goal_terms(goal)) for goal in goals]
    if not targets or any(not terms for _, terms in targets):
        return None, "NOT_ENOUGH"
    encoder = classifier.encoder
    queries = encoder.embed((term for _, terms in targets for term in terms), role="query")
    screen = encoder.embed([text])[text]
    scores = {}
    for goal_id, terms in targets:
        similarities = [float(queries[term] @ screen) for term in terms]
        if not all(map(math.isfinite, similarities)):
            return None, "NOT_ENOUGH"
        scores[goal_id] = max(similarities)
    best = max(scores, key=scores.__getitem__)
    if scores[best] >= MATCH_COSINE:
        return best, "MATCHED"

    # Only a complete body with independently recognized study evidence can be FAR.
    # A title, a partial extraction, or weak evidence stays eligible for reacquisition.
    body = text.removeprefix(title).strip()
    if (metadata_only or content.extraction_status is not ExtractionStatus.SUCCESS
            or len(body) < MIN_FAR_BODY_CHARS or scores[best] >= FAR_COSINE):
        return None, "NOT_ENOUGH"
    views = encoder.embed([title, body])
    closer = [float(query @ views[view]) for term, query in queries.items() if term for view in (title, body) if view]
    # embed() includes a zero vector for the empty string; exclude that non-query.
    if not all(math.isfinite(score) and score < FAR_COSINE for score in closer):
        return None, "NOT_ENOUGH"
    body_payload = {"contentSource": payload["contentSource"], "contentPayload": {"text": body}}
    prediction = classifier.predict([body_payload])[0]
    is_study = beta_decision(body_payload, prediction, "SUCCESS")["label"] == "STUDY"
    return None, "FAR" if is_study else "NOT_ENOUGH"
