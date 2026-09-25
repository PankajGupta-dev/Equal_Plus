package com.example.equal_plus

import com.example.equal_plus.data.model.RiskLevel
import com.example.equal_plus.telephony.VoipConnectionState
import com.example.equal_plus.telephony.VoipGatewayClient
import com.example.equal_plus.telephony.VoipInboundMessage
import com.example.equal_plus.ui.livecall.LiveAiCallViewModel
import com.example.equal_plus.ui.livecall.LiveCallStatus
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VoipGatewayClientTest {

    private val testDispatcher = StandardTestDispatcher()
    private val gson = Gson()

    class FakeVoipGatewayClient : VoipGatewayClient {
        private val _connectionState = MutableStateFlow<VoipConnectionState>(VoipConnectionState.Disconnected)
        override val connectionState: StateFlow<VoipConnectionState> = _connectionState.asStateFlow()

        private val _inboundMessages = MutableSharedFlow<VoipInboundMessage>(extraBufferCapacity = 64)
        override val inboundMessages: SharedFlow<VoipInboundMessage> = _inboundMessages.asSharedFlow()

        var lastConnectedUrl: String? = null
        var lastConnectedCallId: String? = null
        var sentAudios = mutableListOf<ByteArray>()
        var sentTexts = mutableListOf<String>()
        var takeoverSent = false
        var endCallCalled = false
        var disconnectCalled = false

        fun emitState(state: VoipConnectionState) {
            _connectionState.value = state
        }

        fun emitMessage(msg: VoipInboundMessage) {
            _inboundMessages.tryEmit(msg)
        }

        override fun connect(wsUrl: String, callId: String) {
            lastConnectedUrl = wsUrl
            lastConnectedCallId = callId
            _connectionState.value = VoipConnectionState.Connected
        }

        override fun sendAudio(data: ByteArray) {
            sentAudios.add(data)
        }

        override fun sendText(messageJson: String) {
            sentTexts.add(messageJson)
        }

        override fun sendTakeoverSignal() {
            takeoverSent = true
        }

        override fun endCall() {
            endCallCalled = true
            _connectionState.value = VoipConnectionState.CallEnded
        }

        override fun disconnect() {
            disconnectCalled = true
            _connectionState.value = VoipConnectionState.Disconnected
        }
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `test inbound message json deserialization`() {
        val json = """
            {
                "event": "transcript_update",
                "speaker": "AI",
                "text": "Please provide your authorization code.",
                "statusText": "Challenging caller",
                "riskLevel": "HIGH",
                "riskScore": 0.88,
                "detectedPurpose": "Suspected impersonation"
            }
        """.trimIndent()

        val parsed = gson.fromJson(json, VoipInboundMessage::class.java)
        assertNotNull(parsed)
        assertEquals("transcript_update", parsed.event)
        assertEquals("AI", parsed.speaker)
        assertEquals("Please provide your authorization code.", parsed.text)
        assertEquals("HIGH", parsed.riskLevel)
        assertEquals(0.88f, parsed.riskScore)
        assertEquals("Suspected impersonation", parsed.detectedPurpose)
    }

    @Test
    fun `test LiveAiCallViewModel updates state from VoipGatewayClient connection state`() = runTest(testDispatcher) {
        val fakeClient = FakeVoipGatewayClient()
        val viewModel = LiveAiCallViewModel(fakeClient)
        advanceUntilIdle()

        // Initially disconnected from fake client
        assertEquals(VoipConnectionState.Disconnected, viewModel.liveCallState.value.connectionState)

        // Connecting
        fakeClient.emitState(VoipConnectionState.Connecting)
        advanceUntilIdle()
        assertEquals(VoipConnectionState.Connecting, viewModel.liveCallState.value.connectionState)
        assertTrue(viewModel.liveCallState.value.aiStatusText.contains("Establishing secure VoIP"))

        // Streaming
        fakeClient.emitState(VoipConnectionState.Streaming)
        advanceUntilIdle()
        assertEquals(VoipConnectionState.Streaming, viewModel.liveCallState.value.connectionState)

        // Call ended
        fakeClient.emitState(VoipConnectionState.CallEnded)
        advanceUntilIdle()
        assertEquals(VoipConnectionState.CallEnded, viewModel.liveCallState.value.connectionState)
        assertEquals(LiveCallStatus.ENDED, viewModel.liveCallState.value.status)
    }

    @Test
    fun `test LiveAiCallViewModel updates transcript and risk from inbound streaming messages`() = runTest(testDispatcher) {
        val fakeClient = FakeVoipGatewayClient()
        val viewModel = LiveAiCallViewModel(fakeClient)
        advanceUntilIdle()

        fakeClient.emitMessage(
            VoipInboundMessage(
                event = "transcript_update",
                speaker = "Caller",
                text = "I am calling from the bank security department.",
                riskLevel = "MEDIUM",
                detectedPurpose = "Bank Security Check",
                statusText = "Analyzing bank verification claim"
            )
        )
        advanceUntilIdle()

        val state1 = viewModel.liveCallState.value
        assertTrue(state1.latestTranscript.contains("Caller: I am calling from the bank security department."))
        assertEquals(RiskLevel.MEDIUM, state1.riskLevel)
        assertEquals("Bank Security Check", state1.detectedPurpose)
        assertEquals("Analyzing bank verification claim", state1.aiStatusText)

        // AI reply arrives
        fakeClient.emitMessage(
            VoipInboundMessage(
                event = "transcript_update",
                speaker = "AI",
                text = "Please state your verification badge ID.",
                riskLevel = "HIGH",
                statusText = "Prompting for verification"
            )
        )
        advanceUntilIdle()

        val state2 = viewModel.liveCallState.value
        assertTrue(state2.latestTranscript.contains("AI: Please state your verification badge ID."))
        assertEquals(RiskLevel.HIGH, state2.riskLevel)
    }

    @Test
    fun `test LiveAiCallViewModel endCall signals gateway and marks ended`() = runTest(testDispatcher) {
        val fakeClient = FakeVoipGatewayClient()
        val viewModel = LiveAiCallViewModel(fakeClient)
        advanceUntilIdle()

        viewModel.endCall()
        advanceUntilIdle()

        assertTrue(fakeClient.endCallCalled)
        assertEquals(LiveCallStatus.ENDED, viewModel.liveCallState.value.status)
        assertEquals(VoipConnectionState.CallEnded, viewModel.liveCallState.value.connectionState)
    }

    @Test
    fun `test LiveAiCallViewModel takeOverCall signals gateway and sets connected`() = runTest(testDispatcher) {
        val fakeClient = FakeVoipGatewayClient()
        val viewModel = LiveAiCallViewModel(fakeClient)
        advanceUntilIdle()

        viewModel.takeOverCall()
        advanceUntilIdle()

        assertTrue(fakeClient.takeoverSent)
        assertEquals(LiveCallStatus.CONNECTED, viewModel.liveCallState.value.status)
        assertEquals(VoipConnectionState.Streaming, viewModel.liveCallState.value.connectionState)
    }
}
