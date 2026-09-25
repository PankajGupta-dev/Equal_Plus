import logging
from datetime import datetime
from typing import Optional, Dict, Any
from sqlalchemy import Column, Integer, String, Float, Text, JSON, DateTime
from sqlalchemy.orm import declarative_base
from sqlalchemy.ext.asyncio import create_async_engine, async_sessionmaker, AsyncSession

from backend.app.config import DATABASE_URL

logger = logging.getLogger(__name__)

Base = declarative_base()

class Conversation(Base):
    """
    Conversations table recording call turn interactions and decision outcomes.
    Includes decision_source (slm / groq_fallback) and slm_attempts.
    """
    __tablename__ = "conversations"

    id = Column(Integer, primary_key=True, autoincrement=True)
    call_id = Column(String(64), index=True, nullable=True)
    transcript = Column(Text, nullable=True)
    intent = Column(String(64), nullable=True)
    risk_level = Column(String(32), nullable=True)
    confidence = Column(Float, default=0.0)
    conversation_status = Column(String(64), nullable=True)
    next_action = Column(String(64), nullable=True)
    response = Column(Text, nullable=True)
    entities = Column(JSON, nullable=True)
    decision_source = Column(String(32), nullable=True)  # "slm" or "groq_fallback"
    slm_attempts = Column(Integer, default=0)
    latency_ms = Column(Float, default=0.0)
    created_at = Column(DateTime, default=datetime.utcnow)

class DecisionAttemptLog(Base):
    """
    Decision attempts log tracking each SLM and Groq evaluation attempt.
    """
    __tablename__ = "decision_attempts"

    id = Column(Integer, primary_key=True, autoincrement=True)
    call_id = Column(String(64), index=True, nullable=True)
    attempt_number = Column(Integer, nullable=False)
    source = Column(String(32), nullable=False)  # "slm" or "groq_fallback"
    confidence = Column(Float, nullable=False)
    latency_ms = Column(Float, nullable=False)
    is_confident = Column(Integer, default=0)
    created_at = Column(DateTime, default=datetime.utcnow)

# Engine and session initialization
_engine = None
_session_factory = None

def get_engine():
    global _engine
    if _engine is None:
        _engine = create_async_engine(DATABASE_URL, echo=False)
    return _engine

def get_session_factory():
    global _session_factory
    if _session_factory is None:
        engine = get_engine()
        _session_factory = async_sessionmaker(engine, expire_on_commit=False, class_=AsyncSession)
    return _session_factory

async def init_db():
    """Initializes tables in the SQLite database."""
    try:
        engine = get_engine()
        async with engine.begin() as conn:
            await conn.run_sync(Base.metadata.create_all)
    except Exception as e:
        logger.warning(f"Database initialization warning: {e}")

async def log_decision_attempt(
    call_id: Optional[str],
    attempt_number: int,
    source: str,
    confidence: float,
    latency_ms: float,
    is_confident: bool = False
) -> None:
    """Logs an individual evaluation attempt to the decision attempts / conversations log."""
    try:
        await init_db()
        session_factory = get_session_factory()
        async with session_factory() as session:
            attempt_log = DecisionAttemptLog(
                call_id=call_id,
                attempt_number=attempt_number,
                source=source,
                confidence=float(confidence),
                latency_ms=float(latency_ms),
                is_confident=1 if is_confident else 0
            )
            session.add(attempt_log)
            await session.commit()
    except Exception as e:
        logger.warning(f"Failed to log decision attempt to DB: {e}")

async def save_conversation_decision(
    call_id: Optional[str],
    transcript: str,
    decision: Dict[str, Any],
    decision_source: str,
    slm_attempts: int,
    latency_ms: float
) -> None:
    """Saves final structured decision outcome to conversations table."""
    try:
        await init_db()
        session_factory = get_session_factory()
        async with session_factory() as session:
            record = Conversation(
                call_id=call_id,
                transcript=transcript,
                intent=str(decision.get("intent", "unknown")),
                risk_level=str(decision.get("risk_level", "low")),
                confidence=float(decision.get("confidence", 0.0)),
                conversation_status=str(decision.get("conversation_status", "in_progress")),
                next_action=str(decision.get("next_action", "ask")),
                response=str(decision.get("response", "")),
                entities=decision.get("entities", {}),
                decision_source=decision_source,
                slm_attempts=int(slm_attempts),
                latency_ms=float(latency_ms)
            )
            session.add(record)
            await session.commit()
    except Exception as e:
        logger.warning(f"Failed to save conversation decision to DB: {e}")
