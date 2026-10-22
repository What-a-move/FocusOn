from __future__ import annotations

from ..config import GoalGraphRuntimeSettings
from ..models.llm_client import OpenAICompatibleGoalGraphClient
from ..storage.goal_graph_store import ChromaGoalGraphStore
from .goal_analyzer import GoalGraphResolver, LLMGoalGraphGenerator


def build_goal_graph_resolver(
    settings: GoalGraphRuntimeSettings | None = None,
) -> GoalGraphResolver:
    settings = settings or GoalGraphRuntimeSettings.from_env()
    if not settings.llm_configured:
        return GoalGraphResolver()
    client = OpenAICompatibleGoalGraphClient(
        base_url=settings.llm_base_url,
        api_key=settings.llm_api_key,
        model=settings.llm_model,
        timeout_seconds=settings.llm_timeout_seconds,
        max_retries=settings.llm_max_retries,
    )
    store = ChromaGoalGraphStore(
        path=settings.chroma_path,
        collection_name=settings.chroma_collection,
    )
    return GoalGraphResolver(LLMGoalGraphGenerator(client), store=store)
