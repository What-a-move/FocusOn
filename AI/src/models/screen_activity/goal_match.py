"""Attribute an already-classified study screen to confirmed session goals."""
import math
from typing import Literal

import numpy as np

from .activity import parts
from .beta_decision import beta_decision
from ...analysis.acquisition_policy import METADATA_SOURCES
from ...preprocessing.models import ContentSource, ExtractionStatus, PassageKind, PreprocessingConfig
from ...preprocessing.text_cleaner import clean_text, split_text

# A screen is compared with the selected tasks and with a fixed bank of other study topics.
# Comparing only the selected tasks always picks one of them, even for a neighbouring topic.
FAR_COSINE = 0.81  # below this a task is far from the screen
MATCH_FLOOR = 0.80  # the task must reach this cosine on the page or one of its chunks
# Bank topics are compared with the task text, main topic and core topics (query-query cosine).
# At ALIAS_SIMILARITY or above a topic is the task in other words and speaks for it. Topics
# between NEIGHBOUR_SIMILARITY and ALIAS_SIMILARITY are too close to tell apart, so they
# neither support nor block the task. Only clearly different topics can block it.
ALIAS_SIMILARITY = 0.93
NEIGHBOUR_SIMILARITY = 0.90
RIVAL_MARGIN = 0.01  # a different topic must beat the task by more than this to block it
MIN_BODY_CHARS = 80  # below this a body cannot support a task (file name, menu, one line)
# E5 reads about 192 tokens, so site menus or language lists can fill the whole view.
# Each body chunk is judged separately with the page title in front of it.
CHUNK_CHARS = 550
CHUNK_OVERLAP = 100
PREPROCESSING = PreprocessingConfig()
MatchStatus = Literal["MATCHED", "FAR", "NOT_ENOUGH"]


def goal_terms(goal):
    profile = goal.profile
    terms = (goal.text,)
    if profile is not None:
        terms += (profile.main_topic, *profile.core_topics, *profile.supporting_topics, *profile.expected_activities)
    return tuple(dict.fromkeys(term for item in terms if (term := item.strip())))


def _match_terms(goal):
    # Supporting topics and activities are broad and match neighbouring tasks.
    profile = goal.profile
    terms = (goal.text,) if profile is None else (goal.text, profile.main_topic, *profile.core_topics)
    return tuple(dict.fromkeys(term for item in terms if (term := item.strip())))


def _supports(bank, alias, rival, task_score, view):
    """The task explains this view at least as well as every clearly different bank topic."""
    if not math.isfinite(task_score) or task_score < MATCH_FLOOR:
        return False
    if not rival.any():
        return True
    scores = bank.similarity(view)
    # Other wordings of the task speak for it, e.g. an English bank name for a Korean task.
    own = max(task_score, float(scores[alias].max()) if alias.any() else -math.inf)
    return float(scores[rival].max()) - own <= RIVAL_MARGIN


def match_goal(classifier, content, payload, goals) -> tuple[str | None, MatchStatus]:
    # A desktop window title is usually a file name; attribution needs the OCR body.
    if content.content_source is ContentSource.APP_META:
        return None, "NOT_ENOUGH"
    metadata_only = content.content_source in METADATA_SOURCES
    body = ""
    if not metadata_only:
        if content.content_source is ContentSource.DESKTOP_APP_OCR and content.ocr is not None:
            if content.ocr.capture_status != "SUCCESS" or content.ocr.confidence < PREPROCESSING.min_quality_score:
                return None, "NOT_ENOUGH"
        cleaned = clean_text(content.text, PassageKind.BODY, PREPROCESSING.ui_fragments, strip_html=False).text
        payload = {**payload, "contentPayload": {"text": cleaned}}
    title, text = parts(payload)
    if not metadata_only:
        body = text.removeprefix(title).strip()
        if len(body) < MIN_BODY_CHARS:
            return None, "NOT_ENOUGH"
    if not text:
        return None, "NOT_ENOUGH"

    by_id = {goal.goal_id: goal for goal in goals}
    targets = [(goal.goal_id, goal_terms(goal)) for goal in goals]
    if not targets or any(not terms for _, terms in targets):
        return None, "NOT_ENOUGH"
    encoder = classifier.encoder
    queries = encoder.embed((term for _, terms in targets for term in terms), role="query")
    chunks = () if metadata_only else tuple(
        f"{title}\n{chunk}" if title else chunk for chunk in split_text(body, CHUNK_CHARS, CHUNK_OVERLAP, False))
    views = encoder.embed([text, title, body, *chunks])
    raw = {goal_id: max(float(queries[term] @ views[text]) for term in terms) for goal_id, terms in targets}
    fit = {goal_id: max(float(queries[term] @ views[text]) for term in _match_terms(by_id[goal_id]))
           for goal_id, _ in targets}
    if not all(map(math.isfinite, (*raw.values(), *fit.values()))):
        return None, "NOT_ENOUGH"
    # The closest selected task is attributed when the page, or any chunk of its body, supports it.
    best = max(fit, key=fit.__getitem__)
    terms = _match_terms(by_id[best])
    bank = classifier.topic_bank
    closeness = np.max([bank.similarity(queries[term]) for term in terms], axis=0)
    alias, rival = closeness >= ALIAS_SIMILARITY, closeness < NEIGHBOUR_SIMILARITY
    for view in dict.fromkeys((text, *chunks)):
        task_score = max(float(queries[term] @ views[view]) for term in terms)
        if _supports(bank, alias, rival, task_score, views[view]):
            return best, "MATCHED"

    # Only a complete body with independently recognized study evidence can be FAR.
    # A title, a partial extraction or a screen near any goal stays eligible for reacquisition.
    if metadata_only or content.extraction_status is not ExtractionStatus.SUCCESS or max(raw.values()) >= FAR_COSINE:
        return None, "NOT_ENOUGH"
    # embed() includes a zero vector for the empty string; exclude that non-query.
    closer = [float(query @ views[view]) for term, query in queries.items() if term for view in (title, body) if view]
    if not all(math.isfinite(score) and score < FAR_COSINE for score in closer):
        return None, "NOT_ENOUGH"
    body_payload = {"contentSource": payload["contentSource"], "contentPayload": {"text": body}}
    prediction = classifier.predict([body_payload])[0]
    is_study = beta_decision(body_payload, prediction, "SUCCESS")["label"] == "STUDY"
    return None, "FAR" if is_study else "NOT_ENOUGH"
