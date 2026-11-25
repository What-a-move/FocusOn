"""Attribute an already-classified study screen to confirmed session goals."""
import math
from typing import Literal

import numpy as np

from .activity import parts
from .beta_decision import beta_decision
from .task_evidence import Evidence
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
SEMANTIC_BODY_FLOOR = 0.83
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


def _task_candidates(bank, goals, terms_by_goal, views, queries, metadata_only):
    # Score each selected goal on independent body chunks, with the bank as a competing explanation.
    bank_scores = {text: bank.similarity(vector) for text, vector in views.items()}
    families = bank.families
    found = []
    for goal in goals:
        terms = terms_by_goal[goal.goal_id]
        task_vectors = [queries[term] for term in terms]
        closeness = np.maximum.reduce([bank.similarity(vector) for vector in task_vectors])
        alias, rival = closeness >= ALIAS_SIMILARITY, closeness < NEIGHBOUR_SIMILARITY
        family_names = families.explicit(" ".join(terms)) if families is not None else ()
        patterns = tuple(pattern for i, present in enumerate(family_names) if present
                         for pattern in families.name_patterns[i])
        primary = Evidence(" ".join(terms[:2]), patterns)
        profile = Evidence(" ".join(terms), patterns)
        best = None
        for text, vector in views.items():
            scores = bank_scores[text]
            if not np.isfinite(scores).all():
                continue
            direct = max(float(q @ vector) for q in task_vectors)
            own = max(direct, float(scores[alias].max()) if alias.any() else -math.inf)
            gap = own - float(scores[rival].max()) if rival.any() else math.inf
            visible = Evidence(text, patterns)
            same_family = any(pattern.search(text) for pattern in patterns)
            explicit, operation_score = primary.against(visible, same_family)
            code, profile_score = profile.against(visible, same_family)
            direct_evidence = explicit or (not metadata_only and code and bool(profile.symbols & visible.symbols))
            if metadata_only:
                # At least two task-specific words can establish a named topic on a short title.
                named_topic = len(profile.words & visible.words) >= 2
                supported = ((direct_evidence and operation_score >= 3) or (
                    (operation_score >= 2 or named_topic) and gap >= -RIVAL_MARGIN and direct >= MATCH_FLOOR
                ))
            else:
                supported = direct_evidence or (operation_score > 0 and gap >= -RIVAL_MARGIN and direct >= MATCH_FLOOR)
                supported = supported or (direct >= SEMANTIC_BODY_FLOOR and gap >= RIVAL_MARGIN)
            if supported and math.isfinite(direct):
                score = (direct_evidence, operation_score, profile_score, direct)
                best = score if best is None else max(best, score)
        if best is not None:
            found.append((best, goal.goal_id))
    found.sort(reverse=True, key=lambda item: item[0])
    if not found:
        return None
    if len(found) > 1 and found[0][0][:-1] == found[1][0][:-1] and found[0][0][-1] - found[1][0][-1] < RIVAL_MARGIN:
        return None
    return found[0][1]


def _far_from_goal_families(bank, encoder, title, body_chunks, views, goals, queries):
    if bank.families is None:
        return False
    screen_text = tuple(dict.fromkeys(text for text in (title, *body_chunks) if text))
    missing = [text for text in screen_text if text not in views]
    extra_views = encoder.embed(missing) if missing else {}
    screen = [(text, views[text] if text in views else extra_views[text]) for text in screen_text]
    goal_evidence = [[(term, queries[term]) for term in _match_terms(goal)] for goal in goals]
    return bank.families.clearly_different(screen, goal_evidence, bank.similarity)


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

    targets = {goal.goal_id: goal_terms(goal) for goal in goals}
    terms_by_goal = {goal.goal_id: _match_terms(goal) for goal in goals}
    if not targets or any(not terms for terms in terms_by_goal.values()):
        return None, "NOT_ENOUGH"
    encoder = classifier.encoder
    queries = encoder.embed((term for terms in targets.values() for term in terms), role="query")
    body_chunks = () if metadata_only else split_text(body, CHUNK_CHARS, CHUNK_OVERLAP, False)
    views = encoder.embed([text, title, body, *body_chunks])
    evidence = {view: views[view] for view in ((title,) if metadata_only else dict.fromkeys(body_chunks)) if view}
    bank = classifier.topic_bank
    matched = _task_candidates(bank, goals, terms_by_goal, evidence, queries, metadata_only)
    if matched is not None:
        return matched, "MATCHED"

    # Only a complete body with independently recognized study evidence can be FAR.
    if metadata_only or content.extraction_status is not ExtractionStatus.SUCCESS:
        return None, "NOT_ENOUGH"
    # embed() includes a zero vector for the empty string; exclude that non-query.
    closer = [float(query @ views[view]) for term, query in queries.items() if term for view in (title, body) if view]
    if not all(map(math.isfinite, closer)):
        return None, "NOT_ENOUGH"
    whole_screen_fit = [float(query @ views[text]) for term, query in queries.items() if term]
    distant = all(math.isfinite(score) and score < FAR_COSINE for score in (*whole_screen_fit, *closer))
    if not distant and not _far_from_goal_families(bank, encoder, title, body_chunks, views, goals, queries):
        return None, "NOT_ENOUGH"
    body_payload = {"contentSource": payload["contentSource"], "contentPayload": {"text": body}}
    prediction = classifier.predict([body_payload])[0]
    is_study = beta_decision(body_payload, prediction, "SUCCESS")["label"] == "STUDY"
    return None, "FAR" if is_study else "NOT_ENOUGH"
