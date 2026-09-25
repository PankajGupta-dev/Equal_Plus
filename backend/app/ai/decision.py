import time
import logging
from typing import Dict, Any, Optional

from backend.app.config import SLM_MAX_RETRIES
from backend.app.ai.slm import query_slm
from backend.app.ai.confidence_gate import is_confident
from backend.app.ai.groq_client import query_groq
from backend.app.db.conversations import log_decision_attempt, save_conversation_decision

logger = logging.getLogger(__name__)

async def get_decision(
    transcript: str,
    context: Optional[Dict[str, Any]] = None
) -> Dict[str, Any]:
    """
    SLM-first, LLM-fallback reasoning orchestrator.
    
    1. Tries local SLM (Phi-3.5-mini-instruct via Ollama) up to SLM_MAX_RETRIES (2).
    2. Runs each SLM output through the confidence gate:
       - If confident on attempt 1 or 2 -> returns SLM decision tagged with source="slm".
    3. If SLM fails or lacks confidence after retries -> falls back to Groq API.
       - Returns Groq decision tagged with source="groq_fallback".
    4. Logs each attempt (attempt_number, source, confidence, latency_ms) to conversations table.
    """
    context = context or {}
    call_id = context.get("call_id")
    slm_attempts = 0

    # 1. Attempt SLM up to SLM_MAX_RETRIES (2 attempts)
    for attempt in range(1, SLM_MAX_RETRIES + 1):
        slm_attempts += 1
        start_time = time.perf_counter()
        
        slm_decision = await query_slm(transcript, context)
        latency_ms = (time.perf_counter() - start_time) * 1000.0
        confidence = float(slm_decision.get("confidence", 0.0))
        confident = is_confident(slm_decision)

        logger.info(
            f"SLM attempt {attempt}/{SLM_MAX_RETRIES}: "
            f"intent={slm_decision.get('intent')}, confidence={confidence:.2f}, "
            f"latency={latency_ms:.1f}ms, is_confident={confident}"
        )

        # Log individual attempt
        await log_decision_attempt(
            call_id=call_id,
            attempt_number=attempt,
            source="slm",
            confidence=confidence,
            latency_ms=latency_ms,
            is_confident=confident
        )

        if confident:
            slm_decision["source"] = "slm"
            slm_decision["slm_attempts"] = slm_attempts
            slm_decision["latency_ms"] = round(latency_ms, 2)
            
            # Save to conversations table
            await save_conversation_decision(
                call_id=call_id,
                transcript=transcript,
                decision=slm_decision,
                decision_source="slm",
                slm_attempts=slm_attempts,
                latency_ms=latency_ms
            )
            return slm_decision

    # 2. Both SLM attempts failed or were unconfident -> Fallback to Groq API
    logger.info(f"SLM unconfident after {slm_attempts} attempts. Falling back to Groq API...")
    start_time = time.perf_counter()
    
    groq_decision = await query_groq(transcript, context)
    latency_ms = (time.perf_counter() - start_time) * 1000.0
    confidence = float(groq_decision.get("confidence", 0.95))

    logger.info(
        f"Groq fallback decision: intent={groq_decision.get('intent')}, "
        f"confidence={confidence:.2f}, latency={latency_ms:.1f}ms"
    )

    # Log fallback attempt
    await log_decision_attempt(
        call_id=call_id,
        attempt_number=slm_attempts + 1,
        source="groq_fallback",
        confidence=confidence,
        latency_ms=latency_ms,
        is_confident=True
    )

    groq_decision["source"] = "groq_fallback"
    groq_decision["slm_attempts"] = slm_attempts
    groq_decision["latency_ms"] = round(latency_ms, 2)

    # Save to conversations table
    await save_conversation_decision(
        call_id=call_id,
        transcript=transcript,
        decision=groq_decision,
        decision_source="groq_fallback",
        slm_attempts=slm_attempts,
        latency_ms=latency_ms
    )

    return groq_decision
