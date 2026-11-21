"""Decision gates shared by the local pilot and manual evaluation, after ingress validation."""

from activity import native_editor, validate_route


def beta_decision(payload, result, extraction_status="SUCCESS"):
    validate_route(payload)
    if extraction_status == "EXCLUDED":
        raise ValueError("분석 제외 화면입니다.")
    decision = {
        "label": "UNCERTAIN",
        "confidence": None,
        "reasonCode": "INSUFFICIENT_CONTEXT",
        "decisionSource": "empty",
    }
    if extraction_status not in {"SUCCESS", "PARTIAL"}:
        return {**decision, "reasonCode": "ANALYSIS_UNAVAILABLE", "decisionSource": "extraction_status"}

    source = result["decisionSource"]
    label = result["label"]
    decision["decisionSource"] = source
    if source == "native_editor_policy" and native_editor(payload):
        return {**decision, "label": "STUDY", "reasonCode": "SEMANTIC_GOAL_MATCH"}
    if label == "OFF_TASK" and payload["contentSource"] in {"APP_META", "TAB_META"}:
        return {**decision, "decisionSource": "metadata_needs_content"}
    if label == "UNCERTAIN":
        if result["scores"]:
            decision["reasonCode"] = "AMBIGUOUS_CONTEXT"
        return decision
    confidence = (result["scores"] or {}).get(label)
    if confidence is None or confidence < 0.60:
        return {**decision, "decisionSource": "low_confidence"}
    reason = {"STUDY": "SEMANTIC_GOAL_MATCH", "OFF_TASK": "SEMANTIC_GOAL_MISMATCH"}[label]
    return {**decision, "label": label, "confidence": confidence, "reasonCode": reason}
