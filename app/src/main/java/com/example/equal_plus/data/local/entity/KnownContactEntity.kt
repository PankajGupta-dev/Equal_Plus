package com.example.equal_plus.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Local cache of verified user contacts synced from device address book.
 * Used by CallScreeningService to distinguish known callers from unknown/screened callers.
 */
@Entity(
    tableName = "known_contacts",
    indices = [
        Index(value = ["number"], unique = false)
    ]
)
data class KnownContactEntity(
    @PrimaryKey
    val id: String = java.util.UUID.randomUUID().toString(),

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "number")
    val number: String,

    @ColumnInfo(name = "synced_at")
    val syncedAt: Long = System.currentTimeMillis()
)
