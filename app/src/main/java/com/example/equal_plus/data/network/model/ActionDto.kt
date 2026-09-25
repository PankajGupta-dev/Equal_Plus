package com.example.equal_plus.data.network.model

import com.example.equal_plus.data.local.entity.ActionEntity
import com.example.equal_plus.data.model.ActionStatus
import com.example.equal_plus.data.model.ActionType
import com.google.gson.annotations.SerializedName

data class ActionDto(
    @SerializedName("id") val id: Long = 0L,
    @SerializedName("call_id") val callId: String,
    @SerializedName("action_type") val actionType: String,
    @SerializedName("status") val status: String = "PENDING",
    @SerializedName("timestamp") val timestamp: Long = System.currentTimeMillis(),
    @SerializedName("description") val description: String? = null,
    @SerializedName("payload_json") val payloadJson: String? = null,
    @SerializedName("executed_at") val executedAt: Long? = null
) {
    fun toEntity(): ActionEntity {
        return ActionEntity(
            id = id,
            callId = callId,
            actionType = try { ActionType.valueOf(actionType.uppercase()) } catch (_: Exception) { ActionType.NOTIFY_USER },
            status = try { ActionStatus.valueOf(status.uppercase()) } catch (_: Exception) { ActionStatus.PENDING },
            timestamp = timestamp,
            description = description,
            payloadJson = payloadJson,
            executedAt = executedAt
        )
    }

    companion object {
        fun fromEntity(entity: ActionEntity): ActionDto {
            return ActionDto(
                id = entity.id,
                callId = entity.callId,
                actionType = entity.actionType.name,
                status = entity.status.name,
                timestamp = entity.timestamp,
                description = entity.description,
                payloadJson = entity.payloadJson,
                executedAt = entity.executedAt
            )
        }
    }
}
