package com.example.equal_plus.service.executors

import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import android.util.Log
import com.example.equal_plus.data.local.entity.ActionEntity
import com.example.equal_plus.service.ActionExecutor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

class ReminderExecutor : ActionExecutor {
    override suspend fun execute(context: Context, action: ActionEntity): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            var reminderMessage = action.description ?: "Equal Plus Call Follow-up"
            var hour = 10
            var minutes = 0

            if (!action.payloadJson.isNullOrBlank()) {
                try {
                    val json = JSONObject(action.payloadJson)
                    reminderMessage = json.optString("reminder", json.optString("message", reminderMessage))
                    if (json.has("hour")) hour = json.getInt("hour")
                    if (json.has("minutes")) minutes = json.getInt("minutes")
                } catch (_: Exception) {}
            }

            val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                putExtra(AlarmClock.EXTRA_MESSAGE, reminderMessage)
                putExtra(AlarmClock.EXTRA_HOUR, hour)
                putExtra(AlarmClock.EXTRA_MINUTES, minutes)
                putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }

            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
                Log.i(TAG, "Alarm/Reminder set: $reminderMessage at $hour:$minutes")
            } else {
                Log.w(TAG, "No Alarm Clock application found to handle ACTION_SET_ALARM")
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set reminder for call ${action.callId}", e)
            Result.failure(e)
        }
    }

    companion object {
        private const val TAG = "ReminderExecutor"
    }
}
