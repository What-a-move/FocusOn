import json
import logging
from typing import Any


ALLOWED_METADATA = {
    "request_id",
    "clarity_status",
    "model",
    "latency_ms",
    "retry_count",
    "token_usage",
    "error_code",
    "http_status",
    "path",
}


def configure_logging(level: int = logging.INFO) -> None:
    logging.basicConfig(level=level, format="%(message)s")


def log_event(logger: logging.Logger, event: str, **metadata: Any) -> None:
    """Log allowlisted operational metadata; user/model text is dropped by design."""

    safe = {key: value for key, value in metadata.items() if key in ALLOWED_METADATA}
    logger.info(json.dumps({"event": event, **safe}, ensure_ascii=False, default=str))
