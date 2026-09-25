package com.bridgefy.app.db

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import com.bridgefy.app.model.CallLog
import com.bridgefy.app.model.CallLogStatus
import com.bridgefy.app.model.CallType

object CallLogQueries {
    fun insertCallLog(db: SQLiteDatabase, callLog: CallLog) {
        val cv = ContentValues().apply {
            put("id", callLog.id)
            put("callerId", callLog.callerId)
            put("receiverId", callLog.receiverId)
            put("startTime", callLog.startTime)
            if (callLog.endTime != null) {
                put("endTime", callLog.endTime)
            } else {
                putNull("endTime")
            }
            put("duration", callLog.duration)
            put("status", callLog.status.name)
            put("callType", callLog.callType.name)
        }
        db.insertWithOnConflict("call_logs", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun updateCallEnd(db: SQLiteDatabase, id: String, endTime: Long, duration: Int, status: CallLogStatus) {
        val cv = ContentValues().apply {
            put("endTime", endTime)
            put("duration", duration)
            put("status", status.name)
        }
        db.update("call_logs", cv, "id = ?", arrayOf(id))
    }

    fun getCallLogs(db: SQLiteDatabase): List<CallLog> {
        val callLogs = mutableListOf<CallLog>()
        db.rawQuery("SELECT id, callerId, receiverId, startTime, endTime, duration, status, callType FROM call_logs ORDER BY startTime DESC", emptyArray()).use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getString(0)
                val callerId = cursor.getString(1)
                val receiverId = cursor.getString(2)
                val startTime = cursor.getLong(3)
                val endTime = if (cursor.isNull(4)) null else cursor.getLong(4)
                val duration = cursor.getInt(5)
                val statusStr = cursor.getString(6)
                val status = try {
                    CallLogStatus.valueOf(statusStr)
                } catch (e: Exception) {
                    CallLogStatus.MISSED
                }
                val callTypeStr = if (cursor.columnCount > 7) cursor.getString(7) else "VOICE"
                val callType = try {
                    CallType.valueOf(callTypeStr)
                } catch (e: Exception) {
                    CallType.VOICE
                }
                callLogs.add(CallLog(id, callerId, receiverId, startTime, endTime, duration, status, callType))
            }
        }
        return callLogs
    }
}
