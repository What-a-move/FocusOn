import logging

from src.utils.logger import log_event


def test_logging_drops_user_and_model_text(caplog):
    logger = logging.getLogger("privacy-test")
    with caplog.at_level(logging.INFO):
        log_event(
            logger,
            "goal_assessed",
            request_id="req-safe",
            clarity_status="CLEAR",
            original_text="private goal",
            raw_model_output="private model output",
            authorization="Bearer secret",
        )

    assert "req-safe" in caplog.text
    assert "private goal" not in caplog.text
    assert "private model output" not in caplog.text
    assert "Bearer secret" not in caplog.text
