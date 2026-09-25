import asyncio
import logging
from typing import AsyncGenerator, AsyncIterable, Dict, Any, Optional, Union
from backend.app.ai.elevenlabs import stt_stream
from backend.app.ai.decision import get_decision

logger = logging.getLogger(__name__)

async def process_audio_stream(
    audio_chunk_iterator: AsyncIterable[Union[bytes, str]],
    call_id: Optional[str] = None,
    ws_url: Optional[str] = None
) -> AsyncGenerator[Dict[str, Any], None]:
    """
    Processes live incoming caller audio chunks:
    caller audio chunks -> ElevenLabs Scribe v2 Realtime STT stream -> transcript text -> SLM/Groq decision orchestrator.
    Yields structured decision JSON objects incrementally.
    """
    logger.info(f"Starting audio stream processing for call_id={call_id}")
    
    # 1. Pipe audio chunks into ElevenLabs Scribe Realtime STT
    async for transcript_segment in stt_stream(audio_chunk_iterator, ws_url=ws_url):
        logger.debug(f"Received transcript segment: {transcript_segment}")
        
        # 2. Feed text segment into SLM-first, Groq-fallback reasoning orchestrator
        decision = await get_decision(
            transcript=transcript_segment,
            context={"call_id": call_id}
        )
        
        # 3. Yield decision object to telephony layer / caller
        yield decision
