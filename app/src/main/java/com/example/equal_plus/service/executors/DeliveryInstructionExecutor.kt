package com.example.equal_plus.service.executors

import android.content.Context
import android.util.Log
import com.example.equal_plus.data.local.AppDatabase
import com.example.equal_plus.data.local.entity.ActionEntity
import com.example.equal_plus.data.local.entity.DeliveryInstructionEntity
import com.example.equal_plus.service.ActionExecutor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

class DeliveryInstructionExecutor : ActionExecutor {
    override suspend fun execute(context: Context, action: ActionEntity): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            var carrier: String? = null
            var instructionText = action.description ?: "Leave parcel by door"

            if (!action.payloadJson.isNullOrBlank()) {
                try {
                    val json = JSONObject(action.payloadJson)
                    carrier = json.optString("carrier", null)
                    instructionText = json.optString("instruction", json.optString("text", instructionText))
                } catch (_: Exception) {}
            }

            val db = AppDatabase.getInstance(context)
            val entity = DeliveryInstructionEntity(
                callId = action.callId,
                carrier = carrier,
                instruction = instructionText,
                createdAt = System.currentTimeMillis()
            )
            val id = db.deliveryInstructionDao().insertInstruction(entity)
            Log.i(TAG, "Saved delivery instruction #$id for call ${action.callId}: $instructionText")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save delivery instruction for call ${action.callId}", e)
            Result.failure(e)
        }
    }

    companion object {
        private const val TAG = "DeliveryInstructionExecutor"
    }
}
