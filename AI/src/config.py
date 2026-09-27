from functools import lru_cache
from pathlib import Path

from pydantic import AliasChoices, Field, SecretStr
from pydantic_settings import BaseSettings, SettingsConfigDict


AI_ROOT = Path(__file__).resolve().parents[1]


class Settings(BaseSettings):
    """Runtime configuration. Thresholds are experimental and environment-tunable."""

    model_config = SettingsConfigDict(
        env_file=AI_ROOT / ".env",
        env_file_encoding="utf-8",
        extra="ignore",
    )

    typesafe_api_key: SecretStr | None = None
    typesafe_default_model: str = Field(
        default="jev-latest",
        validation_alias=AliasChoices("TYPESAFE_MODEL", "TYPESAFE_DEFAULT_MODEL"),
    )
    typesafe_log_level: str = "warning"
    openai_api_key: SecretStr | None = None
    openai_model: str = "gpt-5-mini"

    goal_assistance_timeout_seconds: float = Field(default=10.0, gt=0)
    goal_assistance_max_retries: int = Field(default=2, ge=0, le=5)

    invalid_usable_max: float = Field(default=0.20, ge=0, le=1)
    unclear_term_min: float = Field(default=0.70, ge=0, le=1)
    multiple_goals_min: float = Field(default=0.70, ge=0, le=1)
    usable_confident_min: float = Field(default=0.60, ge=0, le=1)
    clear_specificity_min: float = Field(default=2.50, ge=0, le=3)
    suggestion_specificity_min: float = Field(default=1.50, ge=0, le=3)
    specificity_confidence_min: float = Field(default=0.45, ge=0, le=1)
    generated_result_pass_min: float = Field(default=0.70, ge=0, le=1)

    goal_text_max_length: int = Field(default=200, ge=1)
    clarification_answer_max_length: int = Field(default=500, ge=1)
    clarification_answers_max_count: int = Field(default=20, ge=0)


@lru_cache
def get_settings() -> Settings:
    return Settings()
