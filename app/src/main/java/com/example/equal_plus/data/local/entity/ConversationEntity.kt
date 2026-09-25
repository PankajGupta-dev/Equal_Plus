package com.example.equal_plus.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.equal_plus.data.model.RiskLevel
import com.example.equal_plus.data.model.SpeakerType

@Entity(
    tableName = "conversations",
    foreignKeys = [
        ForeignKey(
            entity = CallEntity::class,
            parentColumns = ["id"],
            childColumns = ["callId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["callId"]),
        Index(value = ["timestamp"])
    ]
)
data class ConversationEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0L,

    @ColumnInfo(name = "callId")
    val callId: String,

    @ColumnInfo(name = "speaker")
    val speaker: SpeakerType,

    @ColumnInfo(name = "message")
    val message: String,

    @ColumnInfo(name = "timestamp")
    val timestamp: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "confidence")
    val confidence: Float? = null,

    @ColumnInfo(name = "sentiment")
    val sentiment: String? = null,

    @ColumnInfo(name = "intent")
    val intent: String? = null,

    @ColumnInfo(name = "riskLevel")
    val riskLevel: RiskLevel? = null,

    @ColumnInfo(name = "isFlagged")
    val isFlagged: Boolean = false
)
