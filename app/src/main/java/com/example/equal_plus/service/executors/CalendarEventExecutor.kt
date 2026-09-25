package com.example.equal_plus.service.executors

import android.content.ContentValues
import android.content.Context
import android.provider.CalendarContract
import android.util.Log
import com.example.equal_plus.data.local.entity.ActionEntity
import com.example.equal_plus.service.ActionExecutor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.TimeZone

class CalendarEventExecutor : ActionExecutor {
    override suspend fun execute(context: Context, action: ActionEntity): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            var title = action.description ?: "Equal Plus Event"
            var description = "Created automatically from AI call screening"
            var startTime = System.currentTimeMillis() + 3600000L
            var endTime = startTime + 3600000L

            if (!action.payloadJson.isNullOrBlank()) {
                try {
                    val json = JSONObject(action.payloadJson)
                    title = json.optString("title", title)
                    description = json.optString("description", description)
                    if (json.has("startTime")) startTime = json.getLong("startTime")
                    if (json.has("endTime")) endTime = json.getLong("endTime")
                } catch (_: Exception) {}
            }

            val values = ContentValues().apply {
                put(CalendarContract.Events.DTSTART, startTime)
                put(CalendarContract.Events.DTEND, endTime)
                put(CalendarContract.Events.TITLE, title)
                put(CalendarContract.Events.DESCRIPTION, description)
                put(CalendarContract.Events.CALENDAR_ID, 1)
                put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
            }

            val uri = context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
            Log.i(TAG, "Calendar event created: $title at $uri")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to insert calendar event for call ${action.callId}", e)
            Result.failure(e)
        }
    }

    companion object {
        private const val TAG = "CalendarEventExecutor"
    }
}
