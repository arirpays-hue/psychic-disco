package com.example.englishtutor

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale
import kotlin.math.max

enum class VoiceAccent(val label: String, val locale: Locale) {
    US("אמריקאי (US)", Locale.US),
    UK("בריטי (UK)", Locale.UK),
    AU("אוסטרלי (AU)", Locale("en", "AU"))
}

class SpeechManager(private val context: Context) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var tts: TextToSpeech? = null
    private var activeRecognizer: SpeechRecognizer? = null

    var isTtsReady by mutableStateOf(false)
        private set

    // מצב השתקה דו-מצבי שמשתקף מיד בכפתור הרמקול
    var isVoiceMuted by mutableStateOf(false)
        private set

    var isCurrentlySpeaking by mutableStateOf(false)
        private set

    var isListeningMic by mutableStateOf(false)
        private set

    // עוצמת הקול החיה מהמיקרופון (0.0 עד 1.0) עבור אנימציית הגלים בקנבה
    var liveVoiceLevel by mutableFloatStateOf(0.2f)
        private set

    var currentAccent: VoiceAccent = VoiceAccent.US
        set(value) {
            field = value
            runCatching { tts?.language = value.locale }
        }

    var speechRate: Float = 0.92f
        set(value) {
            field = value
            runCatching { tts?.setSpeechRate(value) }
        }

    var speechPitch: Float = 1.0f
        set(value) {
            field = value
            runCatching { tts?.setPitch(value) }
        }

    fun initTts(onInitComplete: () -> Unit = {}) {
        runCatching {
            tts = TextToSpeech(context.applicationContext) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    runCatching {
                        val res = tts?.setLanguage(currentAccent.locale)
                        if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
                            tts?.setLanguage(Locale.ENGLISH)
                        }
                        tts?.setSpeechRate(speechRate)
                        tts?.setPitch(speechPitch)
                        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                            override fun onStart(utteranceId: String?) {
                                mainHandler.post { isCurrentlySpeaking = true }
                            }
                            override fun onDone(utteranceId: String?) {
                                mainHandler.post { isCurrentlySpeaking = false }
                            }
                            @Deprecated("Deprecated in Java")
                            override fun onError(utteranceId: String?) {
                                mainHandler.post { isCurrentlySpeaking = false }
                            }
                            override fun onStop(utteranceId: String?, interrupted: Boolean) {
                                mainHandler.post { isCurrentlySpeaking = false }
                            }
                        })
                    }
                    isTtsReady = true
                    onInitComplete()
                }
            }
        }
    }

    // החלפת מצב השתקה / הפעלת קול בלחיצה
    fun toggleMute(): Boolean {
        isVoiceMuted = !isVoiceMuted
        if (isVoiceMuted) {
            stop()
        }
        return isVoiceMuted
    }

    fun setMuted(muted: Boolean) {
        isVoiceMuted = muted
        if (muted) stop()
    }

    fun speak(text: String, forceEvenIfMuted: Boolean = false) {
        if (!isTtsReady || text.isBlank()) return
        if (isVoiceMuted && !forceEvenIfMuted) return

        mainHandler.post {
            runCatching {
                tts?.stop()
                val params = Bundle().apply {
                    putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
                }
                tts?.speak(
                    text.trim(),
                    TextToSpeech.QUEUE_FLUSH,
                    params,
                    "TTS_${System.nanoTime()}"
                )
            }
        }
    }

    fun stop() {
        mainHandler.post {
            isCurrentlySpeaking = false
            runCatching { tts?.stop() }
        }
    }

    fun stopListening() {
        mainHandler.post {
            isListeningMic = false
            liveVoiceLevel = 0.15f
            runCatching {
                activeRecognizer?.stopListening()
                activeRecognizer?.cancel()
                activeRecognizer?.destroy()
            }
            activeRecognizer = null
        }
    }

    fun buildSpeechRecognizerIntent(): Intent {
        return Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, currentAccent.locale.toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "en-US")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak in English...")
        }
    }

    fun startListening(
        onPartialResult: (String) -> Unit = {},
        onResult: (spokenText: String) -> Unit,
        onFallbackIntentNeeded: (Intent) -> Unit,
        onError: (String) -> Unit
    ) {
        mainHandler.post {
            stop() // עוצר הקראה קולית של ה-TTS כדי שלא תקליט את עצמה
            stopListening()

            if (!SpeechRecognizer.isRecognitionAvailable(context)) {
                onFallbackIntentNeeded(buildSpeechRecognizerIntent())
                return@post
            }

            runCatching {
                val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
                activeRecognizer = recognizer
                isListeningMic = true
                liveVoiceLevel = 0.25f

                recognizer.setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        isListeningMic = true
                    }

                    override fun onBeginningOfSpeech() {
                        isListeningMic = true
                        liveVoiceLevel = 0.5f
                    }

                    override fun onRmsChanged(rmsdB: Float) {
                        // ממיר דציבלים לסקאלה של 0.15 עד 1.0 עבור אנימציית הקנבה
                        val normalized = ((rmsdB + 2f) / 12f).coerceIn(0.15f, 1.0f)
                        liveVoiceLevel = normalized
                    }

                    override fun onBufferReceived(buffer: ByteArray?) {}

                    override fun onEndOfSpeech() {
                        liveVoiceLevel = 0.2f
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        val partial = partialResults
                            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            ?.firstOrNull()
                            ?.trim()
                        if (!partial.isNullOrEmpty()) {
                            onPartialResult(partial)
                        }
                    }

                    override fun onResults(results: Bundle?) {
                        isListeningMic = false
                        liveVoiceLevel = 0.15f
                        val spoken = results
                            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            ?.firstOrNull()
                            ?.trim()
                            .orEmpty()

                        runCatching { recognizer.destroy() }
                        activeRecognizer = null

                        if (spoken.isNotEmpty()) {
                            onResult(spoken)
                        } else {
                            onError("לא זוהו מילים. נסה לדבר קרוב יותר למיקרופון.")
                        }
                    }

                    override fun onError(error: Int) {
                        isListeningMic = false
                        liveVoiceLevel = 0.15f
                        runCatching { recognizer.destroy() }
                        activeRecognizer = null

                        // אם שירות הרקע של גוגל חסום במכשיר, פותח אוטומטית את חלונית ההקלטה של המערכת
                        if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ||
                            error == SpeechRecognizer.ERROR_CLIENT ||
                            error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY
                        ) {
                            onFallbackIntentNeeded(buildSpeechRecognizerIntent())
                        } else {
                            val msg = when (error) {
                                SpeechRecognizer.ERROR_NO_MATCH -> "לא זוהה דיבור באנגלית. נסה שוב."
                                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "לא נשמע קול במיקרופון."
                                SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "בעיית רשת בזיהוי הקולי."
                                else -> "הקלטה הסתיימה ללא זיהוי (קוד $error)."
                            }
                            onError(msg)
                        }
                    }

                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })

                recognizer.startListening(buildSpeechRecognizerIntent())
            }.onFailure {
                isListeningMic = false
                onFallbackIntentNeeded(buildSpeechRecognizerIntent())
            }
        }
    }

    fun destroy() {
        stopListening()
        runCatching {
            tts?.stop()
            tts?.shutdown()
        }
    }

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
