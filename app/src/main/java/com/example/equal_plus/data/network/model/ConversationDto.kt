package com.example.equal_plus.data.network.model

import com.example.equal_plus.data.local.entity.ConversationEntity
import com.example.equal_plus.data.model.RiskLevel
import com.example.equal_plus.data.model.SpeakerType
import com.google.gson.annotations.SerializedName

data class ConversationDto(
    @SerializedName("id") val id: Long = 0L,
    @SerializedName("call_id") val callId: String,
    @SerializedName("speaker") val speaker: String,
    @SerializedName("message") val message: String,
    @SerializedName("timestamp") val timestamp: Long = System.currentTimeMillis(),
    @SerializedName("confidence") val confidence: Float? = null,
    @SerializedName("sentiment") val sentiment: String? = null,
    @SerializedName("intent") val intent: String? = null,
    @SerializedName("risk_level") val riskLevel: String? = null,
    @SerializedName("is_flagged") val isFlagged: Boolean = false
) {
    fun toEntity(): ConversationEntity {
        val mappedSpeaker = when (speaker.uppercase()) {
            "CALLER" -> SpeakerType.CALLER
            "AI", "ASSISTANT" -> SpeakerType.ASSISTANT
            "USER" -> SpeakerType.USER
            "SYSTEM" -> SpeakerType.SYSTEM
            else -> SpeakerType.ASSISTANT
        }
        return ConversationEntity(
            id = id,
            callId = callId,
            speaker = mappedSpeaker,
            message = message,
            timestamp = timestamp,
            confidence = confidence,
            sentiment = sentiment,
            intent = intent,
            riskLevel = riskLevel?.let {
                try { RiskLevel.valueOf(it.uppercase()) } catch (_: Exception) { null }
            },
            isFlagged = isFlagged
        )
    }

    companion object {
        fun fromEntity(entity: ConversationEntity): ConversationDto {
            return ConversationDto(
                id = entity.id,
                callId = entity.callId,
                speaker = entity.speaker.name,
                message = entity.message,
                timestamp = entity.timestamp,
                confidence = entity.confidence,
                sentiment = entity.sentiment,
                intent = entity.intent,
                riskLevel = entity.riskLevel?.name,
                isFlagged = entity.isFlagged
            )
        }
    }
}
