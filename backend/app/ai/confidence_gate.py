from typing import Dict, Any
from backend.app.config import SLM_CONFIDENCE_THRESHOLD

ALLOWED_ACTIONS = {"ask", "respond", "continue", "resolve", "terminate", "escalate"}
REQUIRED_FIELDS = {
    "intent",
    "risk_level",
    "confidence",
    "conversation_status",
    "next_action",
    "response",
    "entities"
}

def is_confident(decision: Dict[str, Any]) -> bool:
    """
    Evaluates whether an SLM decision passes the confidence gate.
    
    Criteria:
    - Must be a valid dictionary containing all required schema fields
    - confidence >= SLM_CONFIDENCE_THRESHOLD (0.75)
    - intent != "unknown"
    - next_action in [ask, respond, continue, resolve, terminate, escalate]
    """
    if not isinstance(decision, dict):
        return False

    # Check required fields exist
    if not REQUIRED_FIELDS.issubset(decision.keys()):
        return False

    # 1. Confidence check
    try:
        confidence = float(decision.get("confidence", 0.0))
        if confidence < SLM_CONFIDENCE_THRESHOLD:
            return False
    except (ValueError, TypeError):
        return False

    # 2. Intent check
    intent = str(decision.get("intent", "")).strip().lower()
    if not intent or intent == "unknown":
        return False

    # 3. Next action check
    next_action = str(decision.get("next_action", "")).strip().lower()
    if next_action not in ALLOWED_ACTIONS:
        return False

    return True
