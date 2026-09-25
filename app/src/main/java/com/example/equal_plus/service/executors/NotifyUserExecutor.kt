package com.example.equal_plus.service.executors

import android.content.Context
import android.util.Log
import com.example.equal_plus.data.local.entity.ActionEntity
import com.example.equal_plus.service.ActionExecutor
import com.example.equal_plus.service.NotificationHelper

class NotifyUserExecutor(
    private val notificationHelper: NotificationHelper
) : ActionExecutor {
    override suspend fun execute(context: Context, action: ActionEntity): Result<Unit> {
        return try {
            val title = "Action Triggered: ${action.actionType.name}"
            val desc = action.description ?: "Equal Plus processed a system action for call ${action.callId}."
            notificationHelper.showActionExecutedAlert(
                callId = action.callId,
                actionTitle = title,
                description = desc
            )
            Log.i(TAG, "Dispatched notification alert for call ${action.callId}")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send user notification for action ${action.id}", e)
            Result.failure(e)
        }
    }

    companion object {
        private const val TAG = "NotifyUserExecutor"
    }
}
