package com.example.equal_plus.data.network.model

import com.example.equal_plus.data.local.entity.CallEntity
import com.example.equal_plus.data.model.CallStatus
import com.example.equal_plus.data.model.CallType
import com.example.equal_plus.data.model.RiskLevel
import com.google.gson.annotations.SerializedName

data class CallDto(
    @SerializedName("id") val id: String,
    @SerializedName("phone_number") val phoneNumber: String,
    @SerializedName("contact_name") val contactName: String? = null,
    @SerializedName("call_type") val callType: String = "INCOMING",
    @SerializedName("status") val status: String = "RINGING",
    @SerializedName("risk_level") val riskLevel: String = "UNKNOWN",
    @SerializedName("risk_score") val riskScore: Float = 0.0f,
    @SerializedName("category") val category: String? = null,
    @SerializedName("summary") val summary: String? = null,
    @SerializedName("transcription") val transcription: String? = null,
    @SerializedName("recording_path") val recordingPath: String? = null,
    @SerializedName("start_time") val startTime: Long = System.currentTimeMillis(),
    @SerializedName("end_time") val endTime: Long? = null,
    @SerializedName("duration_seconds") val durationSeconds: Long = 0L,
    @SerializedName("is_spam") val isSpam: Boolean = false,
    @SerializedName("created_at") val createdAt: Long = System.currentTimeMillis(),
    @SerializedName("updated_at") val updatedAt: Long = System.currentTimeMillis()
) {
    fun toEntity(): CallEntity {
        return CallEntity(
            id = id,
            phoneNumber = phoneNumber,
            contactName = contactName,
            callType = try { CallType.valueOf(callType.uppercase()) } catch (_: Exception) { CallType.INCOMING },
            status = try { CallStatus.valueOf(status.uppercase()) } catch (_: Exception) { CallStatus.RINGING },
            riskLevel = try { RiskLevel.valueOf(riskLevel.uppercase()) } catch (_: Exception) { RiskLevel.UNKNOWN },
            riskScore = riskScore,
            category = category,
            summary = summary,
            transcription = transcription,
            recordingPath = recordingPath,
            startTime = startTime,
            endTime = endTime,
            durationSeconds = durationSeconds,
            isSpam = isSpam,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }

    companion object {
        fun fromEntity(entity: CallEntity): CallDto {
            return CallDto(
                id = entity.id,
                phoneNumber = entity.phoneNumber,
                contactName = entity.contactName,
                callType = entity.callType.name,
                status = entity.status.name,
                riskLevel = entity.riskLevel.name,
                riskScore = entity.riskScore,
                category = entity.category,
                summary = entity.summary,
                transcription = entity.transcription,
                recordingPath = entity.recordingPath,
                startTime = entity.startTime,
                endTime = entity.endTime,
                durationSeconds = entity.durationSeconds,
                isSpam = entity.isSpam,
                createdAt = entity.createdAt,
                updatedAt = entity.updatedAt
            )
        }
    }
}
