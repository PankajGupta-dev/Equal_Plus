package com.example.equal_plus.telephony

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import java.util.Locale

/**
 * Real-time Speech-to-Text (STT) pipeline that transcribes incoming caller speech,
 * evaluates intent in the background, and forwards the call to the human user when appropriate.
 */
class InCallSpeechToTextPipeline(
    private val context: Context,
    private val onTranscript: (speaker: String, text: String, isFinal: Boolean) -> Unit,
    private val onAiForwardDecision: (reason: String) -> Unit
) {
    companion object {
        private const val TAG = "InCallSTTPipeline"

        // Keywords that trigger automatic AI call forwarding to the human user
        private val FORWARDING_KEYWORDS = listOf(
            "urgent", "emergency", "doctor", "hospital", "delivery",
            "speak to", "talk to", "connect me", "is this", "calling regarding"
        )
    }

    private var speechRecognizer: SpeechRecognizer? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var isListening = false
    private var isForwarded = false
    private var restartRunnable: Runnable? = null

    /**
     * Starts continuous real-time Speech-to-Text recognition on caller's speech.
     */
    fun startListening() {
        mainHandler.post {
            if (isListening) return@post

            if (!SpeechRecognizer.isRecognitionAvailable(context)) {
                Log.w(TAG, "Speech recognition service is not available on this device")
                return@post
            }

            try {
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                    setRecognitionListener(createRecognitionListener())
                }
                isListening = true
                isForwarded = false
                startRecognitionIntent()
                Log.i(TAG, "Continuous in-call Speech-to-Text recognition started")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize SpeechRecognizer", e)
            }
        }
    }

    private fun startRecognitionIntent() {
        if (!isListening) return

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra("android.speech.extra.DICTATION_MODE", true)
        }

        try {
            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            Log.w(TAG, "Error starting speech recognition intent: ${e.message}")
            scheduleRestart(1000)
        }
    }

    private fun createRecognitionListener(): RecognitionListener {
        return object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                Log.d(TAG, "STT ready for speech")
            }

            override fun onBeginningOfSpeech() {
                Log.d(TAG, "STT: Caller began speaking")
            }

            override fun onRmsChanged(rmsdB: Float) {}

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                Log.d(TAG, "STT: Caller paused speaking")
            }

            override fun onError(error: Int) {
                val errorMsg = when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH -> "No speech match"
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Speech timeout"
                    SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                    SpeechRecognizer.ERROR_NETWORK -> "Network error"
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognizer busy"
                    else -> "STT error code $error"
                }
                Log.d(TAG, "STT Notice: $errorMsg. Restarting listener...")
                if (isListening && !isForwarded) {
                    scheduleRestart(500)
                }
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = matches?.firstOrNull()?.trim().orEmpty()

                if (text.isNotBlank()) {
                    Log.i(TAG, "STT Final Result: [Caller] $text")
                    onTranscript("Caller", text, true)
                    evaluateIntentForForwarding(text)
                }

                if (isListening && !isForwarded) {
                    scheduleRestart(300)
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = matches?.firstOrNull()?.trim().orEmpty()

                if (text.isNotBlank()) {
                    Log.d(TAG, "STT Partial: [Caller] $text")
                    onTranscript("Caller", text, false)
                    evaluateIntentForForwarding(text)
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        }
    }

    /**
     * Evaluates caller speech to determine if the AI should forward the call to the human user.
     */
    private fun evaluateIntentForForwarding(callerSpeech: String) {
        if (isForwarded) return

        val lower = callerSpeech.lowercase()

        // Check for urgency or explicit request to speak with user
        val matchedKeyword = FORWARDING_KEYWORDS.firstOrNull { lower.contains(it) }

        if (matchedKeyword != null || lower.length > 25) {
            isForwarded = true
            val reason = if (matchedKeyword != null) "Detected keyword: '$matchedKeyword'" else "Caller identified purpose"
            Log.i(TAG, "AI decision: FORWARD call to user ($reason)")
            onAiForwardDecision(reason)
        }
    }

    private fun scheduleRestart(delayMs: Long) {
        restartRunnable?.let { mainHandler.removeCallbacks(it) }
        restartRunnable = Runnable {
            if (isListening && !isForwarded) {
                startRecognitionIntent()
            }
        }
        mainHandler.postDelayed(restartRunnable!!, delayMs)
    }

    /**
     * Stops continuous Speech-to-Text recognition.
     */
    fun stopListening() {
        mainHandler.post {
            isListening = false
            restartRunnable?.let { mainHandler.removeCallbacks(it) }
            restartRunnable = null

            try {
                speechRecognizer?.stopListening()
                speechRecognizer?.cancel()
                speechRecognizer?.destroy()
            } catch (e: Exception) {
                Log.w(TAG, "Error cleaning up SpeechRecognizer: ${e.message}")
            } finally {
                speechRecognizer = null
            }
            Log.i(TAG, "In-call Speech-to-Text recognition stopped")
        }
    }
}
