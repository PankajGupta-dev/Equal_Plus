package com.example.equal_plus.service.executors

import android.content.ContentValues
import android.content.Context
import android.provider.BlockedNumberContract
import android.util.Log
import com.example.equal_plus.data.local.AppDatabase
import com.example.equal_plus.data.local.entity.ActionEntity
import com.example.equal_plus.service.ActionExecutor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class BlockNumberExecutor : ActionExecutor {
    override suspend fun execute(context: Context, action: ActionEntity): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val db = AppDatabase.getInstance(context)
            val call = db.callDao().getCallByIdDirect(action.callId)
            val numberToBlock = call?.phoneNumber

            if (numberToBlock.isNullOrBlank()) {
                Log.w(TAG, "Cannot block number: phoneNumber is null or blank for call ${action.callId}")
                return@withContext Result.failure(IllegalArgumentException("No phone number found for call ${action.callId}"))
            }

            if (BlockedNumberContract.canCurrentUserBlockNumbers(context)) {
                val values = ContentValues().apply {
                    put(BlockedNumberContract.BlockedNumbers.COLUMN_ORIGINAL_NUMBER, numberToBlock)
                }
                context.contentResolver.insert(BlockedNumberContract.BlockedNumbers.CONTENT_URI, values)
                Log.i(TAG, "Successfully blocked number: $numberToBlock")
            } else {
                Log.w(TAG, "Current user cannot block numbers or permission missing for $numberToBlock")
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error blocking number for call ${action.callId}", e)
            Result.failure(e)
        }
    }

    companion object {
        private const val TAG = "BlockNumberExecutor"
    }
}
