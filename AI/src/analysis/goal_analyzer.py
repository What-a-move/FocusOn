from __future__ import annotations

import hashlib
import json
import logging
from threading import RLock
from typing import Annotated, Protocol

import httpx
from pydantic import Field, ValidationError, field_validator, model_validator

from ..api.schemas import ConfirmedGoal, Identifier, InternalSchema
from ..models.llm_client import GoalGraphLLMClient, GoalGraphLLMError
from ..preprocessing.validator import contains_sensitive_data
from ..prompts import goal_prompt
from ..utils.cache import TTLCache
from .goal_graph import Concept, ContentRole, GoalGraph, build_goal_graph


logger = logging.getLogger(__name__)


class GoalGraphGenerationError(ValueError):
    pass


def _failure_details(error: Exception) -> dict:
    """Return bounded diagnostics without provider bodies, input values or tracebacks."""
    codes = {"GOAL_GRAPH_INPUT_BLOCKED", "GOAL_GRAPH_PROVIDER_FAILED",
             "GOAL_GRAPH_RESPONSE_INVALID", "GOAL_GRAPH_RESPONSE_BLOCKED",
             "GOAL_GRAPH_PROVIDER_RESPONSE_INVALID", "GOAL_GRAPH_PROMPT_INVALID"}
    details = {"code": str(error) if str(error) in codes else "GOAL_GRAPH_GENERATION_FAILED"}
    fields = {"concepts", "unrelated_topics", "id", "description", "aliases", "role", "connection"}
    validators = {"GOAL_ALIASES_INVALID", "GOAL_CONCEPT_IDS_DUPLICATED",
                  "GOAL_DIRECT_CONCEPT_REQUIRED", "GOAL_ALIASES_OVERLAP"}
    cause = error
    for _ in range(8):
        if isinstance(cause, GoalGraphLLMError):
            details.update(httpStatus=cause.http_status, providerCode=cause.provider_code,
                           providerParameter=cause.provider_param)
        if isinstance(cause, ValidationError):
            details["validationErrors"] = [
                {
                    "type": item["type"],
                    "location": [part if isinstance(part, int) or part in fields else "<extra>"
                                 for part in item["loc"]],
                    "rule": str(item.get("ctx", {}).get("error", ""))
                    if str(item.get("ctx", {}).get("error", "")) in validators else None,
                }
                for item in cause.errors(include_input=False, include_url=False)[:10]
            ]
        if isinstance(cause, json.JSONDecodeError):
            details.update(jsonLine=cause.lineno, jsonColumn=cause.colno)
        if isinstance(cause, (TimeoutError, httpx.TimeoutException)):
            details["failureKind"] = "TIMEOUT"
        elif isinstance(cause, httpx.NetworkError):
            details["failureKind"] = "NETWORK"
        cause = cause.__cause__
        if cause is None:
            break
    return details


class GoalGraphStore(Protocol):
    def load(self, key: str) -> GoalGraph | None:
        ...

    def save(self, key: str, graph: GoalGraph) -> None:
        ...

    def clear(self) -> None:
        ...


ShortText = Annotated[str, Field(min_length=1, max_length=500)]
Alias = Annotated[str, Field(min_length=1, max_length=120)]


class GeneratedConcept(InternalSchema):
    id: Identifier
    description: ShortText
    aliases: Annotated[list[Alias], Field(min_length=1, max_length=8)]
    role: ContentRole
    connection: ShortText

    @field_validator("aliases")
    @classmethod
    def unique_aliases(cls, values: list[str]) -> list[str]:
        normalized = [value.strip() for value in values]
        if any(not value for value in normalized) or len({value.casefold() for value in normalized}) != len(normalized):
            raise ValueError("GOAL_ALIASES_INVALID")
        return normalized


class GeneratedUnrelatedTopic(InternalSchema):
    id: Identifier
    description: ShortText
    aliases: Annotated[list[Alias], Field(min_length=2, max_length=8)]
    role: None = None
    connection: ShortText

    @field_validator("aliases")
    @classmethod
    def unique_aliases(cls, values: list[str]) -> list[str]:
        return GeneratedConcept.unique_aliases(values)


class GeneratedGoalGraph(InternalSchema):
    concepts: Annotated[list[GeneratedConcept], Field(min_length=1, max_length=12)]
    unrelated_topics: Annotated[list[GeneratedUnrelatedTopic], Field(min_length=1, max_length=6)]

    @model_validator(mode="after")
    def valid_graph(self):
        all_nodes = [*self.concepts, *self.unrelated_topics]
        if len({concept.id for concept in all_nodes}) != len(all_nodes):
            raise ValueError("GOAL_CONCEPT_IDS_DUPLICATED")
        if not any(concept.role is ContentRole.DIRECT for concept in self.concepts):
            raise ValueError("GOAL_DIRECT_CONCEPT_REQUIRED")
        aliases = [alias.casefold() for concept in all_nodes for alias in concept.aliases]
        if len(set(aliases)) != len(aliases):
            raise ValueError("GOAL_ALIASES_OVERLAP")
        return self


def _generated_id(goal: ConfirmedGoal) -> str:
    return f"generated:{goal.goal_id}:v{goal.goal_version}"


def compile_minimal_goal_graph(goal: ConfirmedGoal) -> GoalGraph:
    """Build a conservative graph without inventing topics or relationships."""
    direct_topics: list[str] = []
    supporting_topics: list[str] = []
    if goal.profile:
        direct_topics.extend((goal.profile.main_topic, *goal.profile.core_topics))
        supporting_topics.extend((*goal.profile.supporting_topics, *goal.profile.expected_activities))
    else:
        direct_topics.append(goal.text.strip())

    def unique(values: list[str], excluded: set[str] | None = None) -> list[str]:
        seen = set(excluded or ())
        result = []
        for value in values:
            key = value.casefold()
            if key not in seen:
                seen.add(key)
                result.append(value)
        return result

    direct_topics = unique(direct_topics)
    direct_keys = {value.casefold() for value in direct_topics}
    supporting_topics = unique(supporting_topics, direct_keys)
    concepts = tuple(
        Concept(
            f"direct-{index}",
            topic,
            (topic,),
            ContentRole.DIRECT,
            f"confirmed-goal:{goal.goal_id}:v{goal.goal_version}:direct-{index}",
        )
        for index, topic in enumerate(direct_topics, start=1)
    ) + tuple(
        Concept(
            f"supporting-{index}",
            topic,
            (topic,),
            ContentRole.SUPPORTING,
            f"confirmed-goal:{goal.goal_id}:v{goal.goal_version}:supporting-{index}",
        )
        for index, topic in enumerate(supporting_topics, start=1)
    )
    return GoalGraph(_generated_id(goal), "confirmed-goal-profile-v1", goal.text, concepts, ())


class LLMGoalGraphGenerator:
    def __init__(self, client: GoalGraphLLMClient, *, max_response_chars: int = 32_000):
        if max_response_chars < 1 or max_response_chars > 128_000:
            raise ValueError("INVALID_GOAL_GRAPH_RESPONSE_LIMIT")
        self.client = client
        self.max_response_chars = max_response_chars

    @property
    def fingerprint(self) -> str:
        identity = json.dumps([self.client.fingerprint, goal_prompt.GOAL_GRAPH_PROMPT_VERSION,
                               goal_prompt.SYSTEM_PROMPT], ensure_ascii=False)
        return "llm:" + hashlib.sha256(identity.encode()).hexdigest()[:16]

    def generate(self, goal: ConfirmedGoal) -> GoalGraph:
        input_values = [goal.text]
        if goal.profile:
            input_values.extend((
                goal.profile.main_topic,
                *goal.profile.core_topics,
                *goal.profile.supporting_topics,
                *goal.profile.expected_activities,
            ))
        if any(contains_sensitive_data(value) for value in input_values):
            raise GoalGraphGenerationError("GOAL_GRAPH_INPUT_BLOCKED")
        try:
            raw = self.client.complete(goal_prompt.build_goal_graph_prompt(goal))
        except Exception as error:
            raise GoalGraphGenerationError("GOAL_GRAPH_PROVIDER_FAILED") from error
        if not isinstance(raw, str) or not raw or len(raw) > self.max_response_chars:
            raise GoalGraphGenerationError("GOAL_GRAPH_RESPONSE_INVALID")
        try:
            payload = json.loads(raw)
            generated = GeneratedGoalGraph.model_validate(payload, strict=False)
        except (json.JSONDecodeError, ValidationError, TypeError, ValueError) as error:
            raise GoalGraphGenerationError("GOAL_GRAPH_RESPONSE_INVALID") from error

        for concept in (*generated.concepts, *generated.unrelated_topics):
            values = (concept.description, concept.connection, *concept.aliases)
            if any(contains_sensitive_data(value) for value in values):
                raise GoalGraphGenerationError("GOAL_GRAPH_RESPONSE_BLOCKED")
        concepts = tuple(
            Concept(
                concept.id,
                concept.description,
                tuple(concept.aliases),
                concept.role,
                concept.connection,
            )
            for concept in generated.concepts
        )
        unrelated_topics = tuple(
            Concept(
                topic.id,
                topic.description,
                tuple(topic.aliases),
                None,
                topic.connection,
            )
            for topic in generated.unrelated_topics
        )
        return GoalGraph(
            _generated_id(goal),
            f"llm-goal-graph-v1:{self.fingerprint}",
            goal.text,
            concepts,
            unrelated_topics,
        )


class GoalGraphResolver:
    """Resolve one immutable graph per confirmed goal version; never uses page text."""

    def __init__(self, generator: LLMGoalGraphGenerator | None = None, *, store: GoalGraphStore | None = None,
                 cache_capacity: int = 128, cache_ttl_seconds: float = 3_600):
        self.generator = generator
        self.store = store
        self._cache: TTLCache[GoalGraph] = TTLCache(cache_capacity, cache_ttl_seconds)
        self._lock = RLock()

    def resolve(self, goal: ConfirmedGoal) -> GoalGraph | None:
        if goal.template_id is not None:
            return build_goal_graph(goal.text, goal.template_id)
        serialized = goal.model_dump(mode="json", by_alias=True, exclude_none=True)
        generator_version = self.generator.fingerprint if self.generator else "minimal-v1"
        key = hashlib.sha256(json.dumps([serialized, generator_version], ensure_ascii=False, sort_keys=True).encode()).hexdigest()
        with self._lock:
            cached = self._cache.get(key)
            if cached is not None:
                return cached
            if self.store is not None:
                try:
                    stored = self.store.load(key)
                except Exception:
                    stored = None
                if stored is not None:
                    self._cache.put(key, stored)
                    return stored
            graph = None
            generated = False
            if self.generator is not None:
                try:
                    graph = self.generator.generate(goal)
                    generated = True
                except (GoalGraphGenerationError, GoalGraphLLMError) as error:
                    logger.warning("GOAL_GRAPH_FALLBACK %s", json.dumps(_failure_details(error)))
                    graph = None
            graph = graph or compile_minimal_goal_graph(goal)
            if self.store is not None and (self.generator is None or generated):
                try:
                    self.store.save(key, graph)
                except Exception:
                    pass
            self._cache.put(key, graph)
            return graph

    def clear(self) -> None:
        with self._lock:
            self._cache.clear()
            if self.store is not None:
                try:
                    self.store.clear()
                except Exception:
                    pass
