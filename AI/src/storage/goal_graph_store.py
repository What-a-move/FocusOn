from __future__ import annotations

import hashlib
import json
from typing import Annotated

from pydantic import BaseModel, ConfigDict, Field, ValidationError, model_validator

from ..analysis.goal_graph import Concept, ContentRole, GoalGraph


class GoalGraphStoreError(RuntimeError):
    """Fixed-code storage failure without graph contents."""


Text = Annotated[str, Field(min_length=1, max_length=2_000)]


class _StoredConcept(BaseModel):
    model_config = ConfigDict(extra="forbid", strict=True, hide_input_in_errors=True)

    id: Annotated[str, Field(min_length=1, max_length=128)]
    description: Annotated[str, Field(min_length=1, max_length=500)]
    aliases: Annotated[list[Annotated[str, Field(min_length=1, max_length=120)]], Field(min_length=1, max_length=8)]
    role: ContentRole | None
    connection: Annotated[str, Field(min_length=1, max_length=500)]


class _StoredGoalGraph(BaseModel):
    model_config = ConfigDict(extra="forbid", strict=True, hide_input_in_errors=True)

    template_id: Text
    version: Text
    goal_text: Text
    concepts: Annotated[list[_StoredConcept], Field(min_length=1, max_length=12)]
    unrelated_topics: Annotated[list[_StoredConcept], Field(max_length=6)]

    @model_validator(mode="after")
    def valid_roles(self):
        if any(concept.role is None for concept in self.concepts):
            raise ValueError("GOAL_GRAPH_CONCEPT_ROLE_REQUIRED")
        if any(topic.role is not None for topic in self.unrelated_topics):
            raise ValueError("GOAL_GRAPH_UNRELATED_ROLE_INVALID")
        nodes = [*self.concepts, *self.unrelated_topics]
        if len({node.id for node in nodes}) != len(nodes):
            raise ValueError("GOAL_GRAPH_IDS_DUPLICATED")
        return self

    def to_domain(self) -> GoalGraph:
        def concept(value: _StoredConcept) -> Concept:
            return Concept(value.id, value.description, tuple(value.aliases), value.role, value.connection)

        return GoalGraph(
            self.template_id,
            self.version,
            self.goal_text,
            tuple(concept(value) for value in self.concepts),
            tuple(concept(value) for value in self.unrelated_topics),
        )


def _serialize(graph: GoalGraph) -> str:
    payload = {
        "template_id": graph.template_id,
        "version": graph.version,
        "goal_text": graph.goal_text,
        "concepts": [
            {
                "id": item.id,
                "description": item.description,
                "aliases": list(item.aliases),
                "role": item.role.value if item.role else None,
                "connection": item.connection,
            }
            for item in graph.concepts
        ],
        "unrelated_topics": [
            {
                "id": item.id,
                "description": item.description,
                "aliases": list(item.aliases),
                "role": None,
                "connection": item.connection,
            }
            for item in graph.unrelated_topics
        ],
    }
    return json.dumps(payload, ensure_ascii=False, sort_keys=True, separators=(",", ":"))


class ChromaGoalGraphStore:
    """Exact-key persistence for generated graphs; no page content is stored."""

    def __init__(self, *, path: str, collection_name: str = "focuson_goal_graphs"):
        if not path.strip() or not collection_name.strip():
            raise ValueError("INVALID_GOAL_GRAPH_STORE_CONFIGURATION")
        try:
            import chromadb

            self._client = chromadb.PersistentClient(path=path)
            self._collection = self._client.get_or_create_collection(
                name=collection_name,
                embedding_function=None,
                metadata={"purpose": "focuson-confirmed-goal-graphs", "schema": "v1"},
            )
        except Exception as error:
            raise GoalGraphStoreError("GOAL_GRAPH_STORE_UNAVAILABLE") from error

    def load(self, key: str) -> GoalGraph | None:
        try:
            result = self._collection.get(ids=[key], include=["documents", "metadatas"])
            documents = result.get("documents") or []
            metadatas = result.get("metadatas") or []
            if not documents:
                return None
            document = documents[0]
            metadata = metadatas[0] if metadatas else None
            if not isinstance(document, str) or not isinstance(metadata, dict):
                raise ValueError("INVALID_STORED_GRAPH")
            digest = hashlib.sha256(document.encode()).hexdigest()
            if metadata.get("document_sha256") != digest:
                raise ValueError("STORED_GRAPH_DIGEST_MISMATCH")
            json.loads(document)
            return _StoredGoalGraph.model_validate_json(document).to_domain()
        except (json.JSONDecodeError, ValidationError, ValueError, TypeError, IndexError):
            try:
                self._collection.delete(ids=[key])
            except Exception:
                pass
            return None
        except Exception as error:
            raise GoalGraphStoreError("GOAL_GRAPH_STORE_READ_FAILED") from error

    def save(self, key: str, graph: GoalGraph) -> None:
        document = _serialize(graph)
        digest = hashlib.sha256(document.encode()).hexdigest()
        try:
            self._collection.upsert(
                ids=[key],
                embeddings=[[0.0]],
                documents=[document],
                metadatas=[{
                    "document_sha256": digest,
                    "graph_version": graph.version,
                    "template_id": graph.template_id,
                }],
            )
        except Exception as error:
            raise GoalGraphStoreError("GOAL_GRAPH_STORE_WRITE_FAILED") from error

    def clear(self) -> None:
        try:
            ids = self._collection.get(include=[]).get("ids") or []
            if ids:
                self._collection.delete(ids=ids)
        except Exception as error:
            raise GoalGraphStoreError("GOAL_GRAPH_STORE_CLEAR_FAILED") from error
