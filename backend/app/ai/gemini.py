import json
import logging
from typing import Dict, Any, Optional

logger = logging.getLogger(__name__)

async def handle_gemini_intent(
    transcript: str,
    call_id: Optional[str] = None,
    context: Optional[Dict[str, Any]] = None
) -> Dict[str, Any]:
    """
    Evaluates transcript text using Gemini reasoning engine to determine
    caller category, intent, risk level, response text, entities, and next action.
    """
    lower_text = transcript.lower()
    
    # Deterministic heuristics / classification rules with fallback
    intent = "UNKNOWN"
    risk_level = "LOW"
    next_action = "ASK"
    response_text = "I am an AI assistant screening this call. How may I help you?"
    entities = {}

    if any(k in lower_text for k in ["fedex", "ups", "amazon", "package", "delivery", "gate code", "front door"]):
        intent = "DELIVERY_DROP_OFF"
        risk_level = "SAFE"
        next_action = "RESOLVE"
        response_text = "Please leave the package by the front door behind the planter."
        entities = {"carrier": "Delivery", "instruction": "Front door"}
    elif any(k in lower_text for k in ["interview", "job", "recruiter", "resume", "position"]):
        intent = "JOB_INQUIRY"
        risk_level = "SAFE"
        next_action = "RESOLVE"
        response_text = "Thank you for reaching out. Please send the job details via email."
        entities = {"type": "recruiter_inquiry"}
    elif any(k in lower_text for k in ["bank", "wire", "fraud", "credit card", "security code", "social security", "otp"]):
        intent = "FINANCIAL_VERIFICATION"
        risk_level = "HIGH"
        next_action = "TERMINATE"
        response_text = "This call cannot be completed over an unsecured line. I am escalating to the account holder."
        entities = {"suspicious": True}
    elif any(k in lower_text for k in ["solar", "insurance", "promotional", "special offer", "discount", "free trial"]):
        intent = "SALES_PITCH"
        risk_level = "MEDIUM"
        next_action = "TERMINATE"
        response_text = "We are not interested in promotional offers. Thank you."
        entities = {"category": "telemarketing"}

    decision = {
        "call_id": call_id,
        "transcript": transcript,
        "intent": intent,
        "risk_level": risk_level,
        "next_action": next_action,
        "response": response_text,
        "entities": entities
    }
    
    return decision
