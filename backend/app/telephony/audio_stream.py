import asyncio
import logging
from typing import AsyncGenerator, AsyncIterable, Dict, Any, Optional, Union
from backend.app.ai.elevenlabs import stt_stream
from backend.app.ai.gemini import handle_gemini_intent

logger = logging.getLogger(__name__)

async def process_audio_stream(
    audio_chunk_iterator: AsyncIterable[Union[bytes, str]],
    call_id: Optional[str] = None,
    ws_url: Optional[str] = None
) -> AsyncGenerator[Dict[str, Any], None]:
    """
    Processes live incoming caller audio chunks:
    caller audio chunks -> ElevenLabs Scribe v2 Realtime STT stream -> transcript text -> Gemini intent handler.
    Yields structured decision JSON objects incrementally.
    """
    logger.info(f"Starting audio stream processing for call_id={call_id}")
    
    # 1. Pipe audio chunks into ElevenLabs Scribe Realtime STT
    async for transcript_segment in stt_stream(audio_chunk_iterator, ws_url=ws_url):
        logger.debug(f"Received transcript segment: {transcript_segment}")
        
        # 2. Feed text segment into Gemini intent/decision handler
        decision = await handle_gemini_intent(
            transcript=transcript_segment,
            call_id=call_id
        )
        
        # 3. Yield decision object to telephony layer / caller
        yield decision
