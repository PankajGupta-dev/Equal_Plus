package com.example.equal_plus.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.equal_plus.data.model.ActionStatus
import com.example.equal_plus.data.model.ActionType

@Entity(
    tableName = "actions",
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
        Index(value = ["actionType"]),
        Index(value = ["status"]),
        Index(value = ["timestamp"])
    ]
)
data class ActionEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0L,

    @ColumnInfo(name = "callId")
    val callId: String,

    @ColumnInfo(name = "actionType")
    val actionType: ActionType,

    @ColumnInfo(name = "status")
    val status: ActionStatus = ActionStatus.PENDING,

    @ColumnInfo(name = "timestamp")
    val timestamp: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "description")
    val description: String? = null,

    @ColumnInfo(name = "payloadJson")
    val payloadJson: String? = null,

    @ColumnInfo(name = "executedAt")
    val executedAt: Long? = null
)
