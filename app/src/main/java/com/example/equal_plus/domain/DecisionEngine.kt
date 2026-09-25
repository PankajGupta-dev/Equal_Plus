package com.example.equal_plus.domain

import com.example.equal_plus.data.local.entity.ActionEntity
import com.example.equal_plus.data.model.ActionStatus
import com.example.equal_plus.data.model.ActionType
import com.example.equal_plus.data.model.CallStatus
import com.example.equal_plus.data.model.NextAction
import com.example.equal_plus.data.model.RiskLevel
import com.example.equal_plus.data.repository.ActionRepository
import com.example.equal_plus.data.repository.CallRepository
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.annotations.SerializedName
import java.lang.reflect.Type

data class BackendActionItem(
    @SerializedName("type")
    val type: String = "",
    @SerializedName("description")
    val description: String? = null,
    @SerializedName("payload")
    val payload: Map<String, String> = emptyMap()
)

data class BackendDecision(
    @SerializedName("intent")
    val intent: String = "Unknown Inquiry",
    @SerializedName("risk_level", alternate = ["riskLevel"])
    val riskLevel: RiskLevel = RiskLevel.UNKNOWN,
    @SerializedName("risk_score", alternate = ["riskScore"])
    val riskScore: Float = 0.0f,
    @SerializedName("next_action", alternate = ["nextAction"])
    val nextAction: NextAction = NextAction.RESOLVE,
    @SerializedName("response")
    val response: String = "",
    @SerializedName("entities")
    val entities: Map<String, String> = emptyMap(),
    @SerializedName("actions")
    val actions: List<BackendActionItem> = emptyList()
)

data class DecisionResult(
    val callId: String,
    val decision: BackendDecision,
    val executedActions: List<ActionEntity>,
    val updatedCallStatus: CallStatus,
    val updatedRiskLevel: RiskLevel
)

class DecisionEngine(
    private val actionRepository: ActionRepository,
    private val callRepository: CallRepository? = null,
    private val notificationHelper: com.example.equal_plus.service.NotificationHelper? = null,
    private val context: android.content.Context? = null,
    private val actionExecutorDispatcher: com.example.equal_plus.service.ActionExecutorDispatcher? = null,
    private val gson: Gson = GsonBuilder()
        .registerTypeAdapter(RiskLevel::class.java, RiskLevelDeserializer())
        .registerTypeAdapter(NextAction::class.java, NextActionDeserializer())
        .create()
) {

    private class RiskLevelDeserializer : JsonDeserializer<RiskLevel> {
        override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): RiskLevel {
            return try {
                RiskLevel.valueOf(json.asString.uppercase())
            } catch (_: Exception) {
                RiskLevel.UNKNOWN
            }
        }
    }

    private class NextActionDeserializer : JsonDeserializer<NextAction> {
        override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): NextAction {
            return try {
                NextAction.valueOf(json.asString.uppercase())
            } catch (_: Exception) {
                NextAction.RESOLVE
            }
        }
    }

    fun parseDecisionJson(jsonString: String): BackendDecision {
        return try {
            gson.fromJson(jsonString, BackendDecision::class.java) ?: BackendDecision()
        } catch (_: Exception) {
            BackendDecision()
        }
    }

    suspend fun executeDecision(
        callId: String,
        decisionJson: String
    ): DecisionResult {
        val decision = parseDecisionJson(decisionJson)
        return executeDecision(callId, decision)
    }

    suspend fun executeDecision(
        callId: String,
        decision: BackendDecision
    ): DecisionResult {
        val now = System.currentTimeMillis()
        val generatedActions = mutableListOf<ActionEntity>()

        // 1. Map explicit actions from backend decision
        for (item in decision.actions) {
            val actionType = mapActionType(item.type)
            val desc = item.description ?: defaultDescriptionForAction(actionType, decision)
            val payloadString = if (item.payload.isNotEmpty()) {
                gson.toJson(item.payload)
            } else if (decision.entities.isNotEmpty()) {
                gson.toJson(decision.entities)
            } else null

            val actionEntity = ActionEntity(
                callId = callId,
                actionType = actionType,
                status = ActionStatus.EXECUTED,
                timestamp = now,
                description = desc,
                payloadJson = payloadString,
                executedAt = now
            )
            generatedActions.add(actionEntity)
        }

        // 2. Map next_action to call status & fallback system actions
        val targetCallStatus: CallStatus
        val targetRiskLevel = decision.riskLevel

        when (decision.nextAction) {
            NextAction.TERMINATE -> {
                targetCallStatus = CallStatus.BLOCKED
                if (generatedActions.none { it.actionType == ActionType.BLOCK_CALL || it.actionType == ActionType.BLOCK_NUMBER }) {
                    generatedActions.add(
                        ActionEntity(
                            callId = callId,
                            actionType = ActionType.BLOCK_CALL,
                            status = ActionStatus.EXECUTED,
                            timestamp = now,
                            description = "Automated call termination and number block per decision engine.",
                            executedAt = now
                        )
                    )
                }
            }
            NextAction.ESCALATE -> {
                targetCallStatus = CallStatus.ACTIVE
                if (generatedActions.none { it.actionType == ActionType.NOTIFY_USER || it.actionType == ActionType.WARN_USER }) {
                    generatedActions.add(
                        ActionEntity(
                            callId = callId,
                            actionType = ActionType.NOTIFY_USER,
                            status = ActionStatus.EXECUTED,
                            timestamp = now,
                            description = "High-priority user escalation notification dispatched.",
                            executedAt = now
                        )
                    )
                }
            }
            NextAction.RESOLVE -> {
                targetCallStatus = CallStatus.COMPLETED
            }
            NextAction.ASK -> {
                targetCallStatus = CallStatus.SCREENING
            }
        }

        // 3. Persist actions into ActionRepository
        if (generatedActions.isNotEmpty()) {
            actionRepository.insertActions(generatedActions)
        }

        // 3.5 Dispatch Android system actions via ActionExecutorDispatcher
        context?.let { ctx ->
            val dispatcher = actionExecutorDispatcher ?: com.example.equal_plus.service.ActionExecutorDispatcher(ctx, notificationHelper)
            for (action in generatedActions) {
                try {
                    dispatcher.dispatch(action)
                } catch (e: Exception) {
                    android.util.Log.e("DecisionEngine", "Error executing action ${action.actionType} for call $callId", e)
                }
            }
        }

        // 4. Update CallRepository if provided
        var existingCallEntity: com.example.equal_plus.data.local.entity.CallEntity? = null
        callRepository?.let { repo ->
            val existingCall = repo.getCallByIdDirect(callId)
            existingCallEntity = existingCall
            if (existingCall != null) {
                val updated = existingCall.copy(
                    status = targetCallStatus,
                    riskLevel = targetRiskLevel,
                    riskScore = decision.riskScore,
                    summary = if (decision.response.isNotBlank()) decision.response else existingCall.summary,
                    updatedAt = now
                )
                repo.updateCall(updated)
            }
        }

        // 5. Trigger Notifications for NOTIFY_USER / WARN_USER / High Risk calls
        notificationHelper?.let { helper ->
            val notifyAction = generatedActions.find { it.actionType == ActionType.NOTIFY_USER || it.actionType == ActionType.WARN_USER }
            if (notifyAction != null) {
                helper.showNotifyUserAlert(
                    callId = callId,
                    title = "Equal Plus Action Alert",
                    message = notifyAction.description ?: "Action required on screened call.",
                    riskLevel = targetRiskLevel
                )
            } else if (targetCallStatus == CallStatus.BLOCKED && (targetRiskLevel == RiskLevel.HIGH || targetRiskLevel == RiskLevel.CRITICAL)) {
                helper.showHighRiskTerminatedAlert(
                    callId = callId,
                    callerNumber = existingCallEntity?.phoneNumber ?: "Suspicious Caller",
                    reason = decision.response.ifBlank { "Scam pattern detected." }
                )
            }
        }

        return DecisionResult(
            callId = callId,
            decision = decision,
            executedActions = generatedActions,
            updatedCallStatus = targetCallStatus,
            updatedRiskLevel = targetRiskLevel
        )
    }

    private fun mapActionType(typeStr: String): ActionType {
        return when (typeStr.uppercase().trim()) {
            "CREATE_REMINDER" -> ActionType.CREATE_REMINDER
            "CREATE_CALENDAR_EVENT" -> ActionType.CREATE_CALENDAR_EVENT
            "SAVE_DELIVERY_INSTRUCTION" -> ActionType.SAVE_DELIVERY_INSTRUCTION
            "NOTIFY_USER" -> ActionType.NOTIFY_USER
            "BLOCK_CALL", "BLOCK_NUMBER" -> ActionType.BLOCK_CALL
            "REQUEST_CALLBACK" -> ActionType.REQUEST_CALLBACK
            "WARN_USER" -> ActionType.WARN_USER
            "END_CALL", "TERMINATE_CALL" -> ActionType.END_CALL
            "SCREEN_CALL" -> ActionType.SCREEN_CALL
            "SEND_SMS" -> ActionType.SEND_SMS
            "ANSWER_CALL" -> ActionType.ANSWER_CALL
            else -> ActionType.ALLOW_CALL
        }
    }

    private fun defaultDescriptionForAction(actionType: ActionType, decision: BackendDecision): String {
        return when (actionType) {
            ActionType.CREATE_REMINDER -> "Created reminder from call entities: ${decision.entities}"
            ActionType.CREATE_CALENDAR_EVENT -> "Created calendar appointment: ${decision.entities}"
            ActionType.SAVE_DELIVERY_INSTRUCTION -> "Saved delivery instructions: ${decision.entities}"
            ActionType.NOTIFY_USER -> "Dispatched user alert for intent: ${decision.intent}"
            ActionType.BLOCK_CALL, ActionType.BLOCK_NUMBER -> "Blocked caller per decision risk score ${decision.riskScore}"
            ActionType.REQUEST_CALLBACK -> "Scheduled callback request: ${decision.entities}"
            else -> "Executed action ${actionType.name} for intent: ${decision.intent}"
        }
    }
}
