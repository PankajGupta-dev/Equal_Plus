package com.example.equal_plus.data.local

import androidx.room.TypeConverter
import com.example.equal_plus.data.model.ActionStatus
import com.example.equal_plus.data.model.ActionType
import com.example.equal_plus.data.model.CallStatus
import com.example.equal_plus.data.model.CallType
import com.example.equal_plus.data.model.RiskLevel
import com.example.equal_plus.data.model.SpeakerType

class Converters {

    @TypeConverter
    fun fromCallType(value: CallType?): String? = value?.name

    @TypeConverter
    fun toCallType(value: String?): CallType? = value?.let {
        try {
            CallType.valueOf(it)
        } catch (_: Exception) {
            CallType.INCOMING
        }
    }

    @TypeConverter
    fun fromCallStatus(value: CallStatus?): String? = value?.name

    @TypeConverter
    fun toCallStatus(value: String?): CallStatus? = value?.let {
        try {
            CallStatus.valueOf(it)
        } catch (_: Exception) {
            CallStatus.IDLE
        }
    }

    @TypeConverter
    fun fromRiskLevel(value: RiskLevel?): String? = value?.name

    @TypeConverter
    fun toRiskLevel(value: String?): RiskLevel? = value?.let {
        try {
            RiskLevel.valueOf(it)
        } catch (_: Exception) {
            RiskLevel.UNKNOWN
        }
    }

    @TypeConverter
    fun fromSpeakerType(value: SpeakerType?): String? = value?.name

    @TypeConverter
    fun toSpeakerType(value: String?): SpeakerType? = value?.let {
        try {
            SpeakerType.valueOf(it)
        } catch (_: Exception) {
            SpeakerType.CALLER
        }
    }

    @TypeConverter
    fun fromActionType(value: ActionType?): String? = value?.name

    @TypeConverter
    fun toActionType(value: String?): ActionType? = value?.let {
        try {
            ActionType.valueOf(it)
        } catch (_: Exception) {
            ActionType.ALLOW_CALL
        }
    }

    @TypeConverter
    fun fromActionStatus(value: ActionStatus?): String? = value?.name

    @TypeConverter
    fun toActionStatus(value: String?): ActionStatus? = value?.let {
        try {
            ActionStatus.valueOf(it)
        } catch (_: Exception) {
            ActionStatus.PENDING
        }
    }
}
