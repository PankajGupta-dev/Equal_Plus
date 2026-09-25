import json
import logging
from typing import Dict, Any, Optional
from groq import AsyncGroq, NotFoundError

from backend.app.config import (
    GROQ_API_KEY,
    GROQ_BASE_URL,
    GROQ_MODEL,
    GROQ_FALLBACK_MODEL
)

logger = logging.getLogger(__name__)

SYSTEM_INSTRUCTION = (
    "Return ONLY valid JSON matching schema: "
    "{intent, risk_level, confidence, conversation_status, next_action, response, entities}. "
    "No prose, no markdown."
)

DEFAULT_FALLBACK_DECISION: Dict[str, Any] = {
    "intent": "general_inquiry",
    "risk_level": "low",
    "confidence": 0.85,
    "conversation_status": "in_progress",
    "next_action": "ask",
    "response": "Hello, I am screening this call for the user. How may I help you today?",
    "entities": {}
}

async def query_groq(transcript: str, context: Optional[Dict[str, Any]] = None) -> Dict[str, Any]:
    """
    Queries Groq API for structured intent resolution with JSON mode.
    Attempts primary model (llama-3.3-70b-versatile) and falls back to GROQ_FALLBACK_MODEL
    (e.g., openai/gpt-oss-120b) if the primary model is not found on the account tier.
    """
    context = context or {}
    api_key = GROQ_API_KEY.strip()

    if not api_key:
        logger.warning("GROQ_API_KEY not set. Returning safe fallback decision.")
        return dict(DEFAULT_FALLBACK_DECISION)

    client = AsyncGroq(api_key=api_key, base_url=GROQ_BASE_URL)

    user_content = (
        f"Caller transcript: \"{transcript}\"\n"
        f"Call Context: {json.dumps(context)}"
    )

    messages = [
        {"role": "system", "content": SYSTEM_INSTRUCTION},
        {"role": "user", "content": user_content}
    ]

    models_to_try = [GROQ_MODEL]
    if GROQ_FALLBACK_MODEL and GROQ_FALLBACK_MODEL != GROQ_MODEL:
        models_to_try.append(GROQ_FALLBACK_MODEL)

    for model_name in models_to_try:
        try:
            completion = await client.chat.completions.create(
                model=model_name,
                messages=messages,
                response_format={"type": "json_object"},
                temperature=0.1
            )
            raw_text = completion.choices[0].message.content or ""
            decision = json.loads(raw_text)

            if isinstance(decision, dict):
                # Ensure all schema fields are present and properly typed
                return {
                    "intent": str(decision.get("intent", "general_inquiry")),
                    "risk_level": str(decision.get("risk_level", "low")),
                    "confidence": float(decision.get("confidence", 0.95)),
                    "conversation_status": str(decision.get("conversation_status", "in_progress")),
                    "next_action": str(decision.get("next_action", "ask")).lower(),
                    "response": str(decision.get("response", "")),
                    "entities": decision.get("entities", {}) if isinstance(decision.get("entities"), dict) else {}
                }
        except NotFoundError:
            logger.info(f"Model '{model_name}' not available on Groq tier, trying fallback...")
            continue
        except Exception as e:
            logger.warning(f"Groq API call failed with model '{model_name}': {e}")
            break

    # Graceful fallback heuristic if all API calls encounter network/quota issues
    lower_t = transcript.lower()
    fallback = dict(DEFAULT_FALLBACK_DECISION)
    if any(k in lower_t for k in ["package", "delivery", "fedex", "ups", "amazon"]):
        fallback["intent"] = "delivery_drop_off"
        fallback["risk_level"] = "safe"
        fallback["next_action"] = "resolve"
        fallback["response"] = "Please leave the package by the front door. Thank you."
        fallback["entities"] = {"service": "delivery"}
    elif any(k in lower_t for k in ["job", "interview", "recruiter", "position"]):
        fallback["intent"] = "job_inquiry"
        fallback["risk_level"] = "safe"
        fallback["next_action"] = "resolve"
        fallback["response"] = "Thank you for calling. Please email the details to the recipient."
    elif any(k in lower_t for k in ["otp", "fraud", "bank", "social security", "credit card"]):
        fallback["intent"] = "financial_verification"
        fallback["risk_level"] = "high"
        fallback["next_action"] = "terminate"
        fallback["response"] = "We cannot provide verification details over an unsecured line."

    return fallback
