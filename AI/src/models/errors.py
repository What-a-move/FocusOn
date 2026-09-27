class GoalAssistanceError(Exception):
    """Base error carrying only safe, non-user-content metadata."""


class ModelUnavailableError(GoalAssistanceError):
    pass


class ModelConfigurationError(GoalAssistanceError):
    pass


class InvalidModelResponseError(GoalAssistanceError):
    pass


class InputValidationError(GoalAssistanceError):
    def __init__(self, details: list[dict[str, object]]) -> None:
        super().__init__("요청 값이 올바르지 않습니다.")
        self.details = details
