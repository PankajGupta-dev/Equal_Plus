import asyncio
import json
import pytest
import websockets
from typing import AsyncGenerator
from unittest.mock import patch

from backend.app.ai.elevenlabs import stt_stream
from backend.app.ai.confidence_gate import is_confident
from backend.app.ai.groq_client import query_groq
from backend.app.ai.decision import get_decision
from backend.app.telephony.audio_stream import process_audio_stream

# Mock WebSocket Server for ElevenLabs Scribe v2 Realtime
async def mock_scribe_ws_server(websocket):
    try:
        async for message in websocket:
            if isinstance(message, str):
                try:
                    data = json.loads(message)
                    if data.get("type") == "close":
                        break
                except json.JSONDecodeError:
                    pass
            # Simulate ElevenLabs Scribe v2 Realtime responding with transcripts
            await websocket.send(json.dumps({
                "message_type": "transcript",
                "text": "Hi this is FedEx delivery with a package for you",
                "is_final": True
            }))
            await websocket.send(json.dumps({
                "message_type": "transcript",
                "text": "Where should I leave it?",
                "is_final": True
            }))
            break
    except websockets.exceptions.ConnectionClosed:
        pass

@pytest.mark.asyncio
async def test_elevenlabs_stt_stream_incremental_transcripts():
    # 1. Start mock ElevenLabs Scribe WebSocket server on localhost
    async with websockets.serve(mock_scribe_ws_server, "127.0.0.1", 8765):
        ws_url = "ws://127.0.0.1:8765"

        async def sample_audio_chunks() -> AsyncGenerator[bytes, None]:
            yield b"\x00\x01\x02\x03" * 100
            await asyncio.sleep(0.05)
            yield b"\x04\x05\x06\x07" * 100

        received_transcripts = []
        async for text_segment in stt_stream(sample_audio_chunks(), ws_url=ws_url):
            received_transcripts.append(text_segment)

        assert len(received_transcripts) >= 2
        assert "FedEx delivery" in received_transcripts[0]
        assert "Where should I leave it?" in received_transcripts[1]

def test_confidence_gate_validation():
    # Confident decision
    valid_confident = {
        "intent": "delivery_drop_off",
        "risk_level": "safe",
        "confidence": 0.88,
        "conversation_status": "in_progress",
        "next_action": "resolve",
        "response": "Please leave it at the door.",
        "entities": {"carrier": "FedEx"}
    }
    assert is_confident(valid_confident) is True

    # Low confidence (< 0.75)
    low_conf = dict(valid_confident, confidence=0.70)
    assert is_confident(low_conf) is False

    # Unknown intent
    unknown_intent = dict(valid_confident, intent="unknown")
    assert is_confident(unknown_intent) is False

    # Disallowed action
    disallowed_action = dict(valid_confident, next_action="invalid_action_name")
    assert is_confident(disallowed_action) is False

    # Incomplete schema
    incomplete = {"intent": "delivery_drop_off", "confidence": 0.9}
    assert is_confident(incomplete) is False

@pytest.mark.asyncio
async def test_decision_orchestrator_slm_confident():
    """When SLM returns confident decision on attempt 1, return SLM decision."""
    mock_slm_decision = {
        "intent": "delivery_drop_off",
        "risk_level": "safe",
        "confidence": 0.92,
        "conversation_status": "in_progress",
        "next_action": "resolve",
        "response": "Please leave the package at the front porch.",
        "entities": {"carrier": "FedEx"}
    }

    with patch("backend.app.ai.decision.query_slm", return_value=mock_slm_decision):
        decision = await get_decision("Hi, I have a package from FedEx", context={"call_id": "call_123"})
        assert decision["source"] == "slm"
        assert decision["slm_attempts"] == 1
        assert decision["intent"] == "delivery_drop_off"
        assert decision["next_action"] == "resolve"

@pytest.mark.asyncio
async def test_decision_orchestrator_fallback_to_groq():
    """When SLM is unconfident after 2 retries, fallback to Groq."""
    unconfident_slm = {
        "intent": "unknown",
        "risk_level": "unknown",
        "confidence": 0.2,
        "conversation_status": "in_progress",
        "next_action": "ask",
        "response": "Could you repeat that?",
        "entities": {}
    }
    groq_mock = {
        "intent": "financial_verification",
        "risk_level": "high",
        "confidence": 0.95,
        "conversation_status": "in_progress",
        "next_action": "terminate",
        "response": "We cannot share banking details.",
        "entities": {}
    }

    with patch("backend.app.ai.decision.query_slm", return_value=unconfident_slm) as mock_slm:
        with patch("backend.app.ai.decision.query_groq", return_value=groq_mock) as mock_groq:
            decision = await get_decision("Urgent: confirm your bank account OTP", context={"call_id": "call_999"})
            assert mock_slm.call_count == 2
            assert mock_groq.call_count == 1
            assert decision["source"] == "groq_fallback"
            assert decision["slm_attempts"] == 2
            assert decision["intent"] == "financial_verification"
            assert decision["next_action"] == "terminate"

@pytest.mark.asyncio
async def test_groq_client_live_query():
    """Tests live Groq query with JSON schema validation."""
    decision = await query_groq("Hello, this is DHL delivery at the front gate.", context={"call_id": "call_live"})
    assert isinstance(decision, dict)
    assert "intent" in decision
    assert "risk_level" in decision
    assert "confidence" in decision
    assert "conversation_status" in decision
    assert "next_action" in decision
    assert "response" in decision
    assert "entities" in decision
    assert decision["confidence"] >= 0.0

@pytest.mark.asyncio
async def test_telephony_audio_stream_to_decision_pipeline():
    """Verifies audio_stream integration with get_decision orchestrator."""
    async with websockets.serve(mock_scribe_ws_server, "127.0.0.1", 8767):
        ws_url = "ws://127.0.0.1:8767"

        async def sample_audio_chunks() -> AsyncGenerator[bytes, None]:
            yield b"\x00\x11\x22\x33" * 50

        mock_slm_decision = {
            "intent": "delivery_drop_off",
            "risk_level": "safe",
            "confidence": 0.89,
            "conversation_status": "in_progress",
            "next_action": "resolve",
            "response": "Please leave the parcel by the front door.",
            "entities": {"carrier": "FedEx"}
        }

        with patch("backend.app.ai.decision.query_slm", return_value=mock_slm_decision):
            decisions = []
            async for decision in process_audio_stream(
                audio_chunk_iterator=sample_audio_chunks(),
                call_id="call_stream_test",
                ws_url=ws_url
            ):
                decisions.append(decision)

            assert len(decisions) >= 1
            assert decisions[0]["source"] == "slm"
            assert decisions[0]["intent"] == "delivery_drop_off"
            assert decisions[0]["next_action"] == "resolve"
