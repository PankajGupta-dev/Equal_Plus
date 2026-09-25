import asyncio
import json
import logging
from typing import AsyncGenerator, AsyncIterable, Union, Optional
import websockets
from backend.app.config import ELEVENLABS_API_KEY

logger = logging.getLogger(__name__)

ELEVENLABS_SCRIBE_REALTIME_WS_URL = "wss://api.elevenlabs.io/v1/speech-to-text/stream?model_id=scribe_v2_realtime"
ELEVENLABS_TTS_API_URL = "https://api.elevenlabs.io/v1/text-to-speech"

async def tts_stream(text: str, voice_id: str = "21m00Tcm4TlvDq8ikWAM") -> bytes:
    """
    Synthesize text to speech audio via ElevenLabs TTS API.
    """
    import httpx
    url = f"{ELEVENLABS_TTS_API_URL}/{voice_id}"
    headers = {
        "xi-api-key": ELEVENLABS_API_KEY,
        "Content-Type": "application/json"
    }
    payload = {
        "text": text,
        "model_id": "eleven_turbo_v2_5"
    }
    async with httpx.AsyncClient() as client:
        response = await client.post(url, json=payload, headers=headers)
        response.raise_for_status()
        return response.content

async def stt_stream(
    audio_chunk_iterator: AsyncIterable[Union[bytes, str]],
    ws_url: Optional[str] = None
) -> AsyncGenerator[str, None]:
    """
    Opens a WebSocket to ElevenLabs Scribe v2 Realtime (model scribe_v2_realtime),
    streams incoming audio chunks, and yields transcript segments incrementally as they arrive.
    
    :param audio_chunk_iterator: Async generator yielding audio chunks (PCM bytes or base64/json string).
    :param ws_url: Optional custom WebSocket URL (defaults to ElevenLabs Scribe v2 Realtime endpoint).
    """
    target_url = ws_url or ELEVENLABS_SCRIBE_REALTIME_WS_URL
    headers = {
        "xi-api-key": ELEVENLABS_API_KEY
    }
    
    transcript_queue: asyncio.Queue[Optional[str]] = asyncio.Queue()
    is_finished = False

    # websockets 14+ uses additional_headers, older versions use extra_headers
    connect_kwargs = {}
    if ELEVENLABS_API_KEY:
        try:
            connect_kwargs["additional_headers"] = headers
        except TypeError:
            connect_kwargs["extra_headers"] = headers

    async with websockets.connect(target_url, **connect_kwargs) as ws:
        async def send_audio():
            nonlocal is_finished
            try:
                async for chunk in audio_chunk_iterator:
                    if isinstance(chunk, bytes):
                        await ws.send(chunk)
                    elif isinstance(chunk, str):
                        await ws.send(chunk)
                # Send EOS marker if supported, or close frame
                try:
                    await ws.send(json.dumps({"type": "close"}))
                except Exception:
                    pass
            except Exception as e:
                logger.error(f"Error in stt_stream send_audio: {e}")
            finally:
                # Brief grace period to allow final transcript packets to be consumed
                await asyncio.sleep(0.15)
                is_finished = True

        async def receive_transcripts():
            try:
                while True:
                    try:
                        message = await asyncio.wait_for(ws.recv(), timeout=0.2)
                    except asyncio.TimeoutError:
                        if is_finished:
                            break
                        continue
                    except (websockets.exceptions.ConnectionClosed, Exception):
                        break

                    if isinstance(message, bytes):
                        message = message.decode("utf-8")

                    try:
                        data = json.loads(message)
                        # Extract transcript from various possible payload formats
                        # Scribe v2 realtime: {"text": "...", ...} or {"transcript": "...", ...} or {"words": ...}
                        transcript_text = ""
                        if "text" in data and data["text"]:
                            transcript_text = data["text"]
                        elif "transcript" in data and data["transcript"]:
                            transcript_text = data["transcript"]
                        elif "message_type" in data and data.get("message_type") == "transcript":
                            transcript_text = data.get("text", "")
                        
                        if transcript_text.strip():
                            await transcript_queue.put(transcript_text.strip())
                    except json.JSONDecodeError:
                        if message.strip():
                            await transcript_queue.put(message.strip())
            except Exception as e:
                logger.error(f"Error in stt_stream receive_transcripts: {e}")
            finally:
                await transcript_queue.put(None)

        send_task = asyncio.create_task(send_audio())
        recv_task = asyncio.create_task(receive_transcripts())

        try:
            while True:
                item = await transcript_queue.get()
                if item is None:
                    break
                yield item
        finally:
            send_task.cancel()
            recv_task.cancel()
            await asyncio.gather(send_task, recv_task, return_exceptions=True)
