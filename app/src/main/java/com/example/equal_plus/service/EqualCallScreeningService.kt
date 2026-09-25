package com.example.equal_plus.service

import android.telecom.Call
import android.telecom.CallScreeningService
import android.util.Log
import com.example.equal_plus.data.local.AppDatabase
import com.example.equal_plus.data.local.PolicyDataStore
import com.example.equal_plus.data.local.entity.ActionEntity
import com.example.equal_plus.data.local.entity.CallEntity
import com.example.equal_plus.data.model.ActionStatus
import com.example.equal_plus.data.model.ActionType
import com.example.equal_plus.data.model.CallStatus
import com.example.equal_plus.data.model.CallType
import com.example.equal_plus.data.model.RiskLevel
import com.example.equal_plus.data.repository.ActionRepositoryImpl
import com.example.equal_plus.data.repository.CallRepositoryImpl
import com.example.equal_plus.data.repository.PolicyRepositoryImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.UUID

class EqualCallScreeningService : CallScreeningService() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val database by lazy { AppDatabase.getInstance(applicationContext) }
    private val policyDataStore by lazy { PolicyDataStore(applicationContext) }

    private val callRepository by lazy { CallRepositoryImpl(database.callDao()) }
    private val actionRepository by lazy { ActionRepositoryImpl(database.actionDao()) }
    private val policyRepository by lazy { PolicyRepositoryImpl(policyDataStore) }

    private val policyEvaluator by lazy {
        PolicyEvaluator(policyRepository, callRepository)
    }

    companion object {
        private const val TAG = "EqualCallScreening"
    }

    override fun onScreenCall(callDetails: Call.Details) {
        val rawHandle = callDetails.handle?.schemeSpecificPart ?: ""
        val callerName = callDetails.callerDisplayName

        serviceScope.launch {
            try {
                val decision = policyEvaluator.evaluateIncomingCall(rawHandle, callerName)
                Log.d(TAG, "Screening incoming call from $rawHandle ($callerName) -> Decision: $decision")

                val responseBuilder = CallResponse.Builder()
                val callId = UUID.randomUUID().toString()
                val now = System.currentTimeMillis()

                when (decision) {
                    ScreeningDecision.BLOCK -> {
                        responseBuilder.setDisallowCall(true)
                            .setRejectCall(true)
                            .setSkipCallLog(false)
                            .setSkipNotification(true)

                        recordCallAndAction(
                            callId = callId,
                            phoneNumber = rawHandle,
                            contactName = callerName,
                            status = CallStatus.BLOCKED,
                            riskLevel = RiskLevel.HIGH,
                            isSpam = true,
                            actionType = ActionType.BLOCK_NUMBER,
                            actionDescription = "Auto-blocked scam caller per Equal Plus AI Policy.",
                            timestamp = now
                        )
                    }
                    ScreeningDecision.SILENCE, ScreeningDecision.SCREEN -> {
                        responseBuilder.setDisallowCall(false)
                            .setSilenceCall(true)

                        recordCallAndAction(
                            callId = callId,
                            phoneNumber = rawHandle,
                            contactName = callerName,
                            status = CallStatus.SCREENING,
                            riskLevel = RiskLevel.MEDIUM,
                            isSpam = false,
                            actionType = ActionType.SCREEN_CALL,
                            actionDescription = "Silenced ringer and activated AI screening assistant.",
                            timestamp = now
                        )
                    }
                    ScreeningDecision.ALLOW -> {
                        responseBuilder.setDisallowCall(false)

                        recordCallAndAction(
                            callId = callId,
                            phoneNumber = rawHandle,
                            contactName = callerName,
                            status = CallStatus.RINGING,
                            riskLevel = RiskLevel.SAFE,
                            isSpam = false,
                            actionType = ActionType.ALLOW_CALL,
                            actionDescription = "Call allowed per user whitelist / personal policy.",
                            timestamp = now
                        )
                    }
                }

                respondToCall(callDetails, responseBuilder.build())
            } catch (e: Exception) {
                Log.e(TAG, "Error screening call", e)
                // Fallback safe allow
                val fallbackResponse = CallResponse.Builder().setDisallowCall(false).build()
                respondToCall(callDetails, fallbackResponse)
            }
        }
    }

    private suspend fun recordCallAndAction(
        callId: String,
        phoneNumber: String,
        contactName: String?,
        status: CallStatus,
        riskLevel: RiskLevel,
        isSpam: Boolean,
        actionType: ActionType,
        actionDescription: String,
        timestamp: Long
    ) {
        val callEntity = CallEntity(
            id = callId,
            phoneNumber = if (phoneNumber.isBlank()) "Unknown Number" else phoneNumber,
            contactName = contactName,
            callType = CallType.INCOMING,
            status = status,
            riskLevel = riskLevel,
            isSpam = isSpam,
            startTime = timestamp,
            createdAt = timestamp
        )
        callRepository.insertCall(callEntity)

        val actionEntity = ActionEntity(
            callId = callId,
            actionType = actionType,
            status = ActionStatus.EXECUTED,
            timestamp = timestamp,
            description = actionDescription,
            executedAt = timestamp
        )
        actionRepository.insertAction(actionEntity)
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}
