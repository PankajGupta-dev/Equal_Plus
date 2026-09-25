package com.example.equal_plus.data.network

/**
 * Configuration and credentials for ElevenLabs Conversational AI and Text-to-Speech APIs.
 */
object ElevenLabsConfig {
    const val API_KEY = "sk_14a6926cabe2b9e1e69aa41c1440dbb02b6a63c05da3851c"

    const val BASE_URL = "https://api.elevenlabs.io/"
    const val HEADER_API_KEY = "xi-api-key"

    /**
     * ElevenLabs Conversational AI WebSocket endpoint for real-time bidirectional call handling.
     */
    const val CONVAI_WS_BASE_URL = "wss://api.elevenlabs.io/v1/convai/conversation"

    /**
     * Default assistant voice: Rachel (clear, polite, professional assistant voice).
     */
    const val DEFAULT_VOICE_ID = "21m00Tcm4TlvDq8ikWAM"

    /**
     * Low-latency turbo model optimized for real-time conversation.
     */
    const val DEFAULT_MODEL_ID = "eleven_turbo_v2_5"
}
