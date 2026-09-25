package com.example.equal_plus.callscreening

import android.util.Log

/**
 * Interface to trigger AI call screening handoff for unknown or unverified callers.
 */
interface AiCallHandoffTrigger {
    /**
     * Hand off the active incoming call to the AI conversational assistant.
     *
     * @param incomingNumber The raw or normalized phone number of the incoming caller.
     * @param callId Unique identifier for this call session.
     */
    suspend fun triggerHandoff(incomingNumber: String, callId: String)
}

/**
 * Scaffolded stub implementation for AiCallHandoffTrigger.
 * Logs the handoff event and provides the extension hook for the VoIP gateway.
 */
class AiCallHandoffTriggerStub : AiCallHandoffTrigger {

    companion object {
        private const val TAG = "AiCallHandoffTrigger"
    }

    override suspend fun triggerHandoff(incomingNumber: String, callId: String) {
        Log.i(TAG, "Triggering AI call handoff for unknown caller: $incomingNumber (Call ID: $callId)")

        // TODO: Connect to telephony / VoIP gateway client (e.g. VoipGatewayClient / ElevenLabs)
        // TODO: Initiate audio streaming session, answer call via telecom connection, and begin real-time conversational screening.
    }
}
