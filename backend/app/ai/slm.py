import json
import logging
import re
from typing import Dict, Any, Optional
import httpx

from backend.app.config import OLLAMA_BASE_URL, SLM_MODEL

logger = logging.getLogger(__name__)

SYSTEM_INSTRUCTION = (
    "Return ONLY valid JSON matching schema: "
    "{intent, risk_level, confidence, conversation_status, next_action, response, entities}. "
    "No prose, no markdown."
)

DEFAULT_UNCONFIDENT_DECISION: Dict[str, Any] = {
    "intent": "unknown",
    "risk_level": "unknown",
    "confidence": 0.0,
    "conversation_status": "in_progress",
    "next_action": "escalate",
    "response": "Please hold a moment while I assist you.",
    "entities": {}
}

async def query_slm(transcript: str, context: Optional[Dict[str, Any]] = None) -> Dict[str, Any]:
    """
    Queries local SLM (Phi-3.5-mini-instruct via Ollama) for real-time structured call decision.
    Returns valid dictionary matching the schema.
    """
    context = context or {}
    url = f"{OLLAMA_BASE_URL.rstrip('/')}/api/generate"

    user_prompt = (
        f"Transcript: \"{transcript}\"\n"
        f"Context: {json.dumps(context)}"
    )

    payload = {
        "model": SLM_MODEL,
        "system": SYSTEM_INSTRUCTION,
        "prompt": user_prompt,
        "format": "json",
        "stream": False,
        "options": {
            "temperature": 0.1
        }
    }

    try:
        async with httpx.AsyncClient(timeout=4.0) as client:
            resp = await client.post(url, json=payload)
            if resp.status_code == 200:
                data = resp.json()
                raw_content = data.get("response", "").strip()

                # Clean markdown code fences if model enclosed in ```json ... ```
                cleaned = re.sub(r"^```(?:json)?\s*", "", raw_content)
                cleaned = re.sub(r"\s*```$", "", cleaned)

                decision = json.loads(cleaned)

                # Ensure required fields and normalized types
                if not isinstance(decision, dict):
                    raise ValueError("SLM response is not a valid JSON object")

                return {
                    "intent": str(decision.get("intent", "unknown")),
                    "risk_level": str(decision.get("risk_level", "unknown")),
                    "confidence": float(decision.get("confidence", 0.0)),
                    "conversation_status": str(decision.get("conversation_status", "in_progress")),
                    "next_action": str(decision.get("next_action", "ask")).lower(),
                    "response": str(decision.get("response", "")),
                    "entities": decision.get("entities", {}) if isinstance(decision.get("entities"), dict) else {}
                }
            else:
                logger.warning(f"Ollama SLM returned HTTP {resp.status_code}")
    except (httpx.RequestError, httpx.TimeoutException) as net_err:
        logger.debug(f"Ollama SLM connection/timeout error: {net_err}")
    except (json.JSONDecodeError, ValueError, KeyError) as parse_err:
        logger.warning(f"Ollama SLM response JSON parse error: {parse_err}")
    except Exception as ex:
        logger.warning(f"Unexpected error in query_slm: {ex}")

    # Return safe unconfident structure to allow confidence gate failure and Groq fallback
    return dict(DEFAULT_UNCONFIDENT_DECISION)
