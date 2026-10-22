from __future__ import annotations

import math
import os
from dataclasses import dataclass
from pathlib import Path

from dotenv import load_dotenv


AI_ROOT = Path(__file__).resolve().parents[1]


def _int_setting(name: str, default: int, *, minimum: int, maximum: int) -> int:
    try:
        value = int(os.getenv(name, str(default)))
    except ValueError as error:
        raise ValueError(f"INVALID_{name}") from error
    if not minimum <= value <= maximum:
        raise ValueError(f"INVALID_{name}")
    return value


def _float_setting(name: str, default: float, *, minimum: float, maximum: float) -> float:
    try:
        value = float(os.getenv(name, str(default)))
    except ValueError as error:
        raise ValueError(f"INVALID_{name}") from error
    if not math.isfinite(value) or not minimum <= value <= maximum:
        raise ValueError(f"INVALID_{name}")
    return value


@dataclass(frozen=True, slots=True)
class GoalGraphRuntimeSettings:
    llm_base_url: str
    llm_api_key: str
    llm_model: str
    llm_timeout_seconds: float
    llm_max_retries: int
    chroma_path: str
    chroma_collection: str

    @property
    def llm_configured(self) -> bool:
        values = (self.llm_api_key, self.llm_model)
        if any(values) and not all(values):
            raise ValueError("LLM_CONFIGURATION_INCOMPLETE")
        return all(values)

    @classmethod
    def from_env(cls, env_path: str | Path | None = None) -> "GoalGraphRuntimeSettings":
        path = Path(env_path) if env_path is not None else AI_ROOT / ".env"
        load_dotenv(path, override=False)
        chroma_value = os.getenv("GOAL_GRAPH_CHROMA_PATH", ".chroma/goal_graphs").strip()
        chroma_path = Path(chroma_value)
        if not chroma_path.is_absolute():
            chroma_path = AI_ROOT / chroma_path
        return cls(
            llm_base_url=os.getenv("GOAL_GRAPH_LLM_BASE_URL", "https://api.openai.com/v1").strip(),
            llm_api_key=os.getenv("GOAL_GRAPH_LLM_API_KEY", "").strip(),
            llm_model=os.getenv("GOAL_GRAPH_LLM_MODEL", "").strip(),
            llm_timeout_seconds=_float_setting(
                "GOAL_GRAPH_LLM_TIMEOUT_SECONDS", 20, minimum=1, maximum=120
            ),
            llm_max_retries=_int_setting("GOAL_GRAPH_LLM_MAX_RETRIES", 1, minimum=0, maximum=2),
            chroma_path=str(chroma_path),
            chroma_collection=os.getenv(
                "GOAL_GRAPH_CHROMA_COLLECTION", "focuson_goal_graphs"
            ).strip(),
        )
