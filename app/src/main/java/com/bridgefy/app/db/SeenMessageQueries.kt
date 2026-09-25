package com.bridgefy.app.db

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase

object SeenMessageQueries {
    fun insertSeen(db: SQLiteDatabase, id: String, seenAt: Long = System.currentTimeMillis()) {
        val cv = ContentValues().apply {
            put("id", id)
            put("seenAt", seenAt)
        }
        db.insertWithOnConflict("seen_messages", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun hasSeen(db: SQLiteDatabase, id: String): Boolean {
        db.rawQuery("SELECT COUNT(*) FROM seen_messages WHERE id = ?", arrayOf(id)).use { cursor ->
            if (cursor.moveToFirst()) {
                return cursor.getInt(0) > 0
            }
        }
        return false
    }

    fun getAllSeen(db: SQLiteDatabase): List<Pair<String, Long>> {
        val result = mutableListOf<Pair<String, Long>>()
        db.rawQuery("SELECT id, seenAt FROM seen_messages", emptyArray()).use { cursor ->
            while (cursor.moveToNext()) {
                result.add(Pair(cursor.getString(0), cursor.getLong(1)))
            }
        }
        return result
    }

    fun deleteOlderThan(db: SQLiteDatabase, before: Long) {
        db.delete("seen_messages", "seenAt < ?", arrayOf(before.toString()))
    }

    fun count(db: SQLiteDatabase): Int {
        db.rawQuery("SELECT COUNT(*) FROM seen_messages", emptyArray()).use { cursor ->
            if (cursor.moveToFirst()) {
                return cursor.getInt(0)
            }
        }
        return 0
    }
}
