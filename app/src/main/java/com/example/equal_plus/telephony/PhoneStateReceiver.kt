package com.example.equal_plus.telephony

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import android.util.Log
import com.example.equal_plus.data.local.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * BroadcastReceiver listening for TelephonyManager.ACTION_PHONE_STATE_CHANGED.
 * Provides a reliable dual-detection channel for all incoming calls.
 */
class PhoneStateReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "PhoneStateReceiver"
        private var lastProcessedState: String? = null
        private var currentActiveCallId: String? = null
    }

    private val receiverScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return

        val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
        val incomingNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER).orEmpty()

        Log.d(TAG, "Phone state changed: state=$state, incomingNumber=$incomingNumber")

        if (state == lastProcessedState && state != TelephonyManager.EXTRA_STATE_RINGING) {
            return
        }
        lastProcessedState = state

        when (state) {
            TelephonyManager.EXTRA_STATE_RINGING -> {
                val callId = UUID.randomUUID().toString()
                currentActiveCallId = callId

                receiverScope.launch {
                    val db = AppDatabase.getInstance(context)
                    val normalized = incomingNumber.filter { it.isDigit() || it == '+' }
                    val matchedContact = if (normalized.isNotBlank()) {
                        db.knownContactDao().findContactByNumber(normalized)
                            ?: if (normalized.length >= 7) db.knownContactDao().findContactBySuffix(normalized.takeLast(7)) else null
                    } else null

                    val isKnown = matchedContact != null
                    val contactName = matchedContact?.name

                    Log.i(TAG, "Ringing call detected via PhoneStateReceiver: $incomingNumber (Known: $isKnown)")

                    // Notify session manager to update Room DB and Live UI
                    LiveCallSessionManager.onCallEntering(
                        context = context,
                        phoneNumber = incomingNumber,
                        callerName = contactName,
                        isKnownCaller = isKnown,
                        callId = callId
                    )

                    // If unknown caller, automatically pick up the call
                    if (!isKnown) {
                        Log.i(TAG, "Unknown caller ringing: Triggering auto-pickup...")
                        LiveCallSessionManager.answerCallAndBeginAiScreening(context, callId)
                    }
                }
            }

            TelephonyManager.EXTRA_STATE_IDLE -> {
                Log.d(TAG, "Phone state is IDLE (call finished)")
                LiveCallSessionManager.onCallEnded(context)
                currentActiveCallId = null
            }
        }
    }
}
