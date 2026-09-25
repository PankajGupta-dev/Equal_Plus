package com.example.equal_plus.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.equal_plus.data.model.CallStatus
import com.example.equal_plus.data.model.CallType
import com.example.equal_plus.data.model.RiskLevel
import java.util.UUID

@Entity(
    tableName = "calls",
    indices = [
        Index(value = ["phoneNumber"]),
        Index(value = ["status"]),
        Index(value = ["riskLevel"]),
        Index(value = ["createdAt"])
    ]
)
data class CallEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String = UUID.randomUUID().toString(),

    @ColumnInfo(name = "phoneNumber")
    val phoneNumber: String,

    @ColumnInfo(name = "contactName")
    val contactName: String? = null,

    @ColumnInfo(name = "callType")
    val callType: CallType = CallType.INCOMING,

    @ColumnInfo(name = "status")
    val status: CallStatus = CallStatus.RINGING,

    @ColumnInfo(name = "riskLevel")
    val riskLevel: RiskLevel = RiskLevel.UNKNOWN,

    @ColumnInfo(name = "riskScore")
    val riskScore: Float = 0.0f,

    @ColumnInfo(name = "category")
    val category: String? = null,

    @ColumnInfo(name = "summary")
    val summary: String? = null,

    @ColumnInfo(name = "transcription")
    val transcription: String? = null,

    @ColumnInfo(name = "recordingPath")
    val recordingPath: String? = null,

    @ColumnInfo(name = "startTime")
    val startTime: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "endTime")
    val endTime: Long? = null,

    @ColumnInfo(name = "durationSeconds")
    val durationSeconds: Long = 0L,

    @ColumnInfo(name = "isSpam")
    val isSpam: Boolean = false,

    @ColumnInfo(name = "createdAt")
    val createdAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "updatedAt")
    val updatedAt: Long = System.currentTimeMillis()
)
