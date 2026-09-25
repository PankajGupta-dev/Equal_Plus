package com.example.equal_plus.telephony

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Build
import android.telecom.TelecomManager
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay

/**
 * Helper to answer / pick up incoming calls using Android TelecomManager and manage in-call audio.
 */
object CallAnswerHelper {
    private const val TAG = "CallAnswerHelper"

    /**
     * Answers a currently ringing call programmatically using [TelecomManager.acceptRingingCall].
     * Requires [Manifest.permission.ANSWER_PHONE_CALLS].
     *
     * @param context Application or service context.
     * @return true if acceptRingingCall was successfully invoked.
     */
    fun answerRingingCall(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val hasAnswerPermission = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ANSWER_PHONE_CALLS
            ) == PackageManager.PERMISSION_GRANTED

            if (!hasAnswerPermission) {
                Log.w(TAG, "ANSWER_PHONE_CALLS permission not granted, cannot answer call")
                return false
            }

            val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
            if (telecomManager == null) {
                Log.e(TAG, "TelecomManager not available")
                return false
            }

            return try {
                @Suppress("DEPRECATION")
                telecomManager.acceptRingingCall()
                Log.i(TAG, "Successfully invoked telecomManager.acceptRingingCall()")
                isolateMicrophone(context)
                true
            } catch (e: SecurityException) {
                Log.e(TAG, "SecurityException while answering call: ${e.message}", e)
                false
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error answering call: ${e.message}", e)
                false
            }
        } else {
            Log.w(TAG, "Android version below Oreo not supported for acceptRingingCall")
            return false
        }
    }

    /**
     * Answers the call with a short delay (e.g. 500ms) to ensure Telecom has transitioned
     * the call from CallScreening state into Ringing state.
     */
    suspend fun answerRingingCallWithDelay(context: Context, delayMs: Long = 600): Boolean {
        delay(delayMs)
        return answerRingingCall(context)
    }

    /**
     * Mutes user microphone so the caller is screened by AI without hearing the user.
     */
    fun isolateMicrophone(context: Context) {
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            audioManager?.let {
                it.mode = AudioManager.MODE_IN_CALL
                it.isMicrophoneMute = true
                Log.d(TAG, "Microphone muted for AI screening isolation")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to isolate microphone: ${e.localizedMessage}")
        }
    }

    /**
     * Unmutes microphone and routes audio when the call is forwarded to the user.
     */
    fun connectUserAudio(context: Context) {
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            audioManager?.let {
                it.mode = AudioManager.MODE_IN_CALL
                it.isMicrophoneMute = false
                Log.d(TAG, "User microphone unmuted and connected to caller")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to connect user audio: ${e.localizedMessage}")
        }
    }
}
