package com.example.equal_plus

import com.example.equal_plus.data.network.ElevenLabsConfig
import com.example.equal_plus.telephony.ElevenLabsConversationEvent
import com.example.equal_plus.telephony.ElevenLabsConversationManager
import com.example.equal_plus.telephony.ElevenLabsSessionState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class ElevenLabsIntegrationTest {

    @Test
    fun testElevenLabsConfigHasValidApiKeyAndEndpoints() {
        assertEquals("sk_14a6926cabe2b9e1e69aa41c1440dbb02b6a63c05da3851c", ElevenLabsConfig.API_KEY)
        assertEquals("https://api.elevenlabs.io/", ElevenLabsConfig.BASE_URL)
        assertEquals("xi-api-key", ElevenLabsConfig.HEADER_API_KEY)
        assertNotNull(ElevenLabsConfig.DEFAULT_VOICE_ID)
    }

    @Test
    fun testElevenLabsConversationManagerInitialState() = runBlocking {
        val manager = ElevenLabsConversationManager()
        val state = manager.sessionState.first()
        assertEquals(ElevenLabsSessionState.Idle, state)
    }
}
