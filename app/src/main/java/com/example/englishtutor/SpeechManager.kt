package com.example.englishtutor

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale
import kotlin.math.max

enum class VoiceAccent(val label: String, val locale: Locale) {
    US("אמריקאי (US)", Locale.US),
    UK("בריטי (UK)", Locale.UK),
    AU("אוסטרלי (AU)", Locale("en", "AU"))
}

class SpeechManager(private val context: Context) {
    private var tts: TextToSpeech? = null
    var isTtsReady = false
        private set

    var currentAccent: VoiceAccent = VoiceAccent.US
        set(value) {
            field = value
            tts?.language = value.locale
        }

    var speechRate: Float = 0.92f
        set(value) {
            field = value
            tts?.setSpeechRate(value)
        }

    var speechPitch: Float = 1.0f
        set(value) {
            field = value
            tts?.setPitch(value)
        }

    fun initTts(onInitComplete: () -> Unit) {
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = currentAccent.locale
                tts?.setSpeechRate(speechRate)
                tts?.setPitch(speechPitch)
                isTtsReady = true
                onInitComplete()
            }
        }
    }

    fun speak(text: String, onStart: () -> Unit = {}, onDone: () -> Unit = {}) {
        if (!isTtsReady || text.isBlank()) return
        tts?.stop()
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) { onStart() }
            override fun onDone(utteranceId: String?) { onDone() }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) { onDone() }
        })
        val params = Bundle().apply {
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
        }
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, "TTS_${System.currentTimeMillis()}")
    }

    fun stop() {
        tts?.stop()
    }

    fun destroy() {
        tts?.stop()
        tts?.shutdown()
    }

    // --- Speech-to-Text & Pronunciation Evaluator ---
    fun startListening(
        onResult: (spokenText: String, accuracyScore: Int) -> Unit,
        onError: (String) -> Unit
    ) {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            onError("זיהוי קולי אינו זמין במכשיר זה")
            return
        }

        val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, currentAccent.locale.toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }

        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val spoken = matches?.firstOrNull() ?: ""
                onResult(spoken, 100)
                recognizer.destroy()
            }

            override fun onError(error: Int) {
                onError("לא נקלט דיבור (קוד $error)")
                recognizer.destroy()
            }

            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        recognizer.startListening(intent)
    }

    // השוואת הגייה וחישוב אחוז דיוק
    fun calculatePronunciationScore(target: String, spoken: String): Int {
        val cleanTarget = target.trim().lowercase().replace(Regex("[^a-z0-9 ]"), "")
        val cleanSpoken = spoken.trim().lowercase().replace(Regex("[^a-z0-9 ]"), "")
        if (cleanTarget.isEmpty() || cleanSpoken.isEmpty()) return 0
        if (cleanTarget == cleanSpoken) return 100

        val targetWords = cleanTarget.split(" ")
        val spokenWords = cleanSpoken.split(" ")
        var matched = 0
        for (w in targetWords) {
            if (spokenWords.contains(w)) matched++
        }
        val wordAccuracy = (matched.toFloat() / max(targetWords.size, spokenWords.size)) * 100
        return wordAccuracy.toInt().coerceIn(0, 100)
    }
}
