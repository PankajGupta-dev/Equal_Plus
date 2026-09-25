package com.example.equal_plus.service

import android.content.Context
import android.util.Log
import com.example.equal_plus.data.local.entity.ActionEntity
import com.example.equal_plus.data.model.ActionType
import com.example.equal_plus.service.executors.BlockNumberExecutor
import com.example.equal_plus.service.executors.CalendarEventExecutor
import com.example.equal_plus.service.executors.DeliveryInstructionExecutor
import com.example.equal_plus.service.executors.NotifyUserExecutor
import com.example.equal_plus.service.executors.ReminderExecutor
import com.example.equal_plus.service.executors.SmsExecutor

interface ActionExecutor {
    suspend fun execute(context: Context, action: ActionEntity): Result<Unit>
}

class ActionExecutorDispatcher(
    private val context: Context,
    private val notificationHelper: NotificationHelper? = null
) {
    suspend fun dispatch(action: ActionEntity): Result<Unit> {
        val helper = notificationHelper ?: NotificationHelper(context)
        val executor: ActionExecutor = when (action.actionType) {
            ActionType.CREATE_REMINDER -> ReminderExecutor()
            ActionType.CREATE_CALENDAR_EVENT -> CalendarEventExecutor()
            ActionType.SAVE_DELIVERY_INSTRUCTION -> DeliveryInstructionExecutor()
            ActionType.BLOCK_CALL, ActionType.BLOCK_NUMBER -> BlockNumberExecutor()
            ActionType.SEND_SMS -> SmsExecutor()
            ActionType.NOTIFY_USER, ActionType.WARN_USER -> NotifyUserExecutor(helper)
            else -> {
                Log.d(TAG, "Action type ${action.actionType} requires no system execution.")
                return Result.success(Unit)
            }
        }
        return executor.execute(context, action)
    }

    companion object {
        private const val TAG = "ActionExecutorDispatcher"
    }
}
