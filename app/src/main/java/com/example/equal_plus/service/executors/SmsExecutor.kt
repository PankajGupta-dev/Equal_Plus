package com.example.equal_plus.service.executors

import android.content.Context
import android.os.Build
import android.telephony.SmsManager
import android.util.Log
import com.example.equal_plus.data.local.AppDatabase
import com.example.equal_plus.data.local.entity.ActionEntity
import com.example.equal_plus.service.ActionExecutor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

class SmsExecutor : ActionExecutor {
    override suspend fun execute(context: Context, action: ActionEntity): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val db = AppDatabase.getInstance(context)
            val call = db.callDao().getCallByIdDirect(action.callId)
            val recipientNumber = call?.phoneNumber

            if (recipientNumber.isNullOrBlank()) {
                Log.w(TAG, "Cannot send SMS: recipient phone number is null for call ${action.callId}")
                return@withContext Result.failure(IllegalArgumentException("Recipient phone number missing"))
            }

            var smsText = "Equal Plus Auto-Reply: Your call was screened and recorded."
            if (!action.payloadJson.isNullOrBlank()) {
                try {
                    val json = JSONObject(action.payloadJson)
                    smsText = json.optString("message", json.optString("text", smsText))
                } catch (_: Exception) {}
            } else if (!action.description.isNullOrBlank()) {
                smsText = action.description
            }

            val smsManager: SmsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }

            smsManager.sendTextMessage(recipientNumber, null, smsText, null, null)
            Log.i(TAG, "Sent SMS to $recipientNumber: $smsText")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send SMS for call ${action.callId}", e)
            Result.failure(e)
        }
    }

    companion object {
        private const val TAG = "SmsExecutor"
    }
}
