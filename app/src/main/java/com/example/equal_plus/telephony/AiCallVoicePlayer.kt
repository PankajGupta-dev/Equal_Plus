package com.example.equal_plus.telephony

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale

/**
 * Text-to-Speech manager for playing voice prompts directly to answered calls.
 */
class AiCallVoicePlayer(context: Context) {

    companion object {
        private const val TAG = "AiCallVoicePlayer"
        const val DEFAULT_SCREENING_GREETING =
            "Hello. This call is being screened by EQUAL+ AI Assistant. Please state your name and the reason for your call."
    }

    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private var pendingSpeechText: String? = null

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.US
                isInitialized = true
                Log.d(TAG, "TextToSpeech successfully initialized")
                pendingSpeechText?.let { text ->
                    speak(text)
                    pendingSpeechText = null
                }
            } else {
                Log.e(TAG, "TextToSpeech initialization failed with status $status")
            }
        }
    }

    fun speak(text: String) {
        if (!isInitialized) {
            pendingSpeechText = text
            return
        }

        try {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "AI_CALL_PROMPT_${System.currentTimeMillis()}")
            Log.i(TAG, "Speaking prompt to caller: $text")
        } catch (e: Exception) {
            Log.e(TAG, "Error playing TTS prompt: ${e.message}", e)
        }
    }

    fun stop() {
        try {
            tts?.stop()
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping TTS: ${e.message}")
        }
    }

    fun shutdown() {
        try {
            tts?.shutdown()
            tts = null
        } catch (e: Exception) {
            Log.w(TAG, "Error shutting down TTS: ${e.message}")
        }
    }
}
