package com.example.equal_plus.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.os.bundleOf
import androidx.navigation.NavDeepLinkBuilder
import com.example.equal_plus.MainActivity
import com.example.equal_plus.R
import com.example.equal_plus.data.model.RiskLevel
import com.example.equal_plus.ui.ConversationDetailsFragment

class NotificationHelper(private val context: Context) {

    companion object {
        const val CHANNEL_ID_ALERTS = "equal_plus_call_alerts"
        const val CHANNEL_NAME_ALERTS = "Equal Plus Call Alerts & Security"
        const val NOTIFICATION_ID_BASE = 1000
    }

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID_ALERTS,
                CHANNEL_NAME_ALERTS,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for AI screened calls, security warnings, and user action requests"
                enableLights(true)
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun showNotifyUserAlert(
        callId: String,
        title: String,
        message: String,
        riskLevel: RiskLevel = RiskLevel.MEDIUM
    ): Int {
        val notificationId = NOTIFICATION_ID_BASE + Math.abs(callId.hashCode() % 5000)
        val pendingIntent = createDeepLinkPendingIntent(callId)

        val icon = if (riskLevel == RiskLevel.HIGH || riskLevel == RiskLevel.CRITICAL) {
            R.drawable.ic_block
        } else {
            R.drawable.ic_shield_check
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID_ALERTS)
            .setSmallIcon(icon)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        try {
            notificationManager.notify(notificationId, builder.build())
        } catch (_: SecurityException) {
            // Permission not granted or notifications disabled
        }

        return notificationId
    }

    fun showHighRiskTerminatedAlert(
        callId: String,
        callerNumber: String,
        reason: String
    ): Int {
        val title = "High-Risk Scam Call Blocked"
        val message = "AI terminated and blocked suspicious incoming call from $callerNumber. Reason: $reason"
        return showNotifyUserAlert(
            callId = callId,
            title = title,
            message = message,
            riskLevel = RiskLevel.HIGH
        )
    }

    fun showActionExecutedAlert(
        callId: String,
        actionTitle: String,
        description: String
    ): Int {
        return showNotifyUserAlert(
            callId = callId,
            title = "AI Action: $actionTitle",
            message = description,
            riskLevel = RiskLevel.SAFE
        )
    }

    private fun createDeepLinkPendingIntent(callId: String): PendingIntent {
        return try {
            NavDeepLinkBuilder(context)
                .setGraph(R.navigation.nav_graph)
                .setDestination(R.id.conversationDetailsFragment)
                .setArguments(bundleOf(ConversationDetailsFragment.ARG_CALL_ID to callId))
                .createPendingIntent()
        } catch (_: Exception) {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                putExtra(ConversationDetailsFragment.ARG_CALL_ID, callId)
            }
            PendingIntent.getActivity(
                context,
                callId.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
    }
}
