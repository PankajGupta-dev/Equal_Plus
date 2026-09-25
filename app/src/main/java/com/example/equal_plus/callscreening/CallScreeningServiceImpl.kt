package com.example.equal_plus.callscreening

import android.telecom.Call
import android.telecom.CallScreeningService
import android.util.Log
import com.example.equal_plus.data.local.AppDatabase
import com.example.equal_plus.data.local.dao.KnownContactDao
import com.example.equal_plus.data.local.entity.KnownContactEntity
import com.example.equal_plus.telephony.LiveCallSessionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * System CallScreeningService registered with Android Telecom / RoleManager.
 * Inspects incoming calls against local Room DB table `known_contacts`, records
 * call details into Room DB in real-time, and automatically answers unknown calls for AI screening.
 */
class CallScreeningServiceImpl(
    private val contactDaoProvider: (android.content.Context) -> KnownContactDao = { context ->
        AppDatabase.getInstance(context).knownContactDao()
    },
    private val aiHandoffTrigger: AiCallHandoffTrigger = AiCallHandoffTriggerImpl()
) : CallScreeningService() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    companion object {
        private const val TAG = "CallScreeningService"

        fun normalizePhoneNumber(rawNumber: String): String {
            val digitsOnly = rawNumber.filter { it.isDigit() }
            return if (rawNumber.startsWith("+")) "+$digitsOnly" else digitsOnly
        }
    }

    override fun onCreate() {
        super.onCreate()
        if (AppContextProvider.applicationContext == null) {
            AppContextProvider.applicationContext = this.applicationContext
        }
    }

    override fun onScreenCall(callDetails: Call.Details) {
        val rawHandle = callDetails.handle?.schemeSpecificPart.orEmpty()
        val callerName = callDetails.callerDisplayName.orEmpty()
        val appContext = this.applicationContext ?: AppContextProvider.applicationContext ?: this

        Log.i(TAG, "Incoming call received for screening: rawHandle='$rawHandle', displayName='$callerName'")

        serviceScope.launch {
            val callId = UUID.randomUUID().toString()
            val decision = evaluateCall(rawHandle, appContext)
            applyScreeningDecision(callDetails, decision, callId, callerName)
        }
    }

    /**
     * Matches incoming number against `known_contacts` table and emits a [CallDecision].
     */
    suspend fun evaluateCall(rawNumber: String, context: android.content.Context): CallDecision {
        if (rawNumber.isBlank()) {
            return CallDecision.UnknownCaller(rawNumber)
        }

        val normalized = normalizePhoneNumber(rawNumber)
        val contactDao = try {
            contactDaoProvider(context)
        } catch (e: Exception) {
            Log.w(TAG, "Unable to access KnownContactDao: ${e.localizedMessage}")
            null
        }

        // 1. Direct match on normalized number
        var matchedContact: KnownContactEntity? = contactDao?.findContactByNumber(normalized)

        // 2. Suffix match fallback (last 7-10 digits)
        if (matchedContact == null && normalized.length >= 7) {
            val suffix = normalized.takeLast(7)
            matchedContact = contactDao?.findContactBySuffix(suffix)
        }

        return if (matchedContact != null) {
            Log.i(TAG, "MATCH FOUND in known_contacts: '${matchedContact.name}' (${matchedContact.number}). Allowing normal ring.")
            CallDecision.KnownCaller(matchedContact)
        } else {
            Log.i(TAG, "NO MATCH in known_contacts for: '$rawNumber'. Triggering AI screening.")
            CallDecision.UnknownCaller(rawNumber)
        }
    }

    /**
     * Translates [CallDecision] into Android Telecom [CallResponse], logs into Room DB,
     * updates live session state, and picks up call if unknown.
     */
    private suspend fun applyScreeningDecision(
        callDetails: Call.Details,
        decision: CallDecision,
        callId: String,
        callerName: String
    ) {
        val responseBuilder = CallResponse.Builder()
        val appContext = this.applicationContext ?: AppContextProvider.applicationContext ?: this

        when (decision) {
            is CallDecision.KnownCaller -> {
                // MATCH found: allow call normally, let it ring for user
                responseBuilder
                    .setDisallowCall(false)
                    .setSilenceCall(false)

                // Log into Room DB and broadcast state
                LiveCallSessionManager.onCallEntering(
                    context = appContext,
                    phoneNumber = decision.contact.number,
                    callerName = decision.contact.name,
                    isKnownCaller = true,
                    callId = callId
                )
            }

            is CallDecision.UnknownCaller -> {
                // NO MATCH found: Silence ringer and skip native notification so call is suppressed from phone screen
                responseBuilder
                    .setDisallowCall(false)
                    .setSilenceCall(true)
                    .setSkipNotification(true)
                    .setSkipCallLog(false)

                // Log into Room DB and broadcast active session to Live Call screen
                LiveCallSessionManager.onCallEntering(
                    context = appContext,
                    phoneNumber = decision.rawNumber,
                    callerName = callerName.ifBlank { "Unknown Caller" },
                    isKnownCaller = false,
                    callId = callId
                )

                // Trigger AI Call Handoff stub & auto-answer
                aiHandoffTrigger.triggerHandoff(
                    incomingNumber = decision.rawNumber,
                    callId = callId
                )

                // Answer the call programmatically so AI can screen it
                LiveCallSessionManager.answerCallAndBeginAiScreening(appContext, callId)
            }
        }

        try {
            respondToCall(callDetails, responseBuilder.build())
            Log.d(TAG, "Successfully responded to call screening request for $callId")
        } catch (e: Exception) {
            Log.e(TAG, "Error building or responding with CallResponse, falling back to safe response", e)
            val fallback = CallResponse.Builder().setDisallowCall(false).build()
            respondToCall(callDetails, fallback)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}

/**
 * Internal helper to hold Application context when service is instantiated by Android system.
 */
object AppContextProvider {
    var applicationContext: android.content.Context? = null
}
