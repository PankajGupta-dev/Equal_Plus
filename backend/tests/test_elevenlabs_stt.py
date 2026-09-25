import asyncio
import json
import pytest
import websockets
from typing import AsyncGenerator
from backend.app.ai.elevenlabs import stt_stream
from backend.app.ai.gemini import handle_gemini_intent
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

        # 2. Generator providing test audio chunks
        async def sample_audio_chunks() -> AsyncGenerator[bytes, None]:
            yield b"\x00\x01\x02\x03" * 100
            await asyncio.sleep(0.05)
            yield b"\x04\x05\x06\x07" * 100

        # 3. Consume stt_stream
        received_transcripts = []
        async for text_segment in stt_stream(sample_audio_chunks(), ws_url=ws_url):
            received_transcripts.append(text_segment)

        # 4. Verify incremental transcripts received
        assert len(received_transcripts) >= 2
        assert "FedEx delivery" in received_transcripts[0]
        assert "Where should I leave it?" in received_transcripts[1]

@pytest.mark.asyncio
async def test_telephony_audio_stream_to_gemini_pipeline():
    # 1. Start mock ElevenLabs Scribe WebSocket server
    async with websockets.serve(mock_scribe_ws_server, "127.0.0.1", 8766):
        ws_url = "ws://127.0.0.1:8766"

        async def sample_audio_chunks() -> AsyncGenerator[bytes, None]:
            yield b"\x00\x11\x22\x33" * 50

        # 2. Process audio stream through telephony pipeline
        decisions = []
        async for decision in process_audio_stream(
            audio_chunk_iterator=sample_audio_chunks(),
            call_id="call_test_123",
            ws_url=ws_url
        ):
            decisions.append(decision)

        # 3. Verify decisions processed by Gemini intent handler
        assert len(decisions) >= 1
        fedex_decision = decisions[0]
        assert fedex_decision["call_id"] == "call_test_123"
        assert fedex_decision["intent"] == "DELIVERY_DROP_OFF"
        assert fedex_decision["risk_level"] == "SAFE"
        assert fedex_decision["next_action"] == "RESOLVE"
        assert "front door" in fedex_decision["response"].lower()
