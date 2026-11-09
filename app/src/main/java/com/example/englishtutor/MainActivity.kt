package com.example.englishtutor

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

enum class AppMode(val titleHe: String, val subtitleHe: String) {
    FLASHCARDS("כרטיסיות מילים חכמות", "לימוד מילים בשיטת הפתקים ואלגוריתם זיכרון"),
    CONVERSATIONS("סימולציות שיחה (Roleplay)", "עשרות שיחות ארוכות מבוססות בחירה")
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val wordRepository = WordRepository(applicationContext)
        val conversationRepository = ConversationRepository(applicationContext)

        setContent {
            val context = LocalContext.current
            val lifecycleOwner = LocalLifecycleOwner.current
            val scope = rememberCoroutineScope()
            val mainHandler = remember { Handler(Looper.getMainLooper()) }

            var wordList by remember { mutableStateOf<List<WordCard>?>(null) }
            var scenariosList by remember { mutableStateOf<List<ConversationScenario>>(emptyList()) }
            var currentMode by remember { mutableStateOf(AppMode.FLASHCARDS) }
            val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

            // מנוע TTS מרכזי משותף לכרטיסיות ולשיחות
            var ttsReady by remember { mutableStateOf(false) }
            var isSpeaking by remember { mutableStateOf(false) }
            var autoSpeak by remember { mutableStateOf(true) }
            val ttsRef = remember { mutableStateOf<TextToSpeech?>(null) }
            val activeUtteranceId = remember { mutableStateOf("") }

            fun stopSpeakingSafely() {
                activeUtteranceId.value = ""
                isSpeaking = false
                runCatching { ttsRef.value?.stop() }
            }

            fun speakEnglish(text: String?) {
                val clean = text?.trim() ?: return
                val engine = ttsRef.value ?: return
                if (ttsReady && clean.isNotEmpty()) {
                    runCatching {
                        val newId = "TTS_${clean.hashCode()}_${System.nanoTime()}"
                        activeUtteranceId.value = newId
                        engine.stop()
                        val params = Bundle().apply {
                            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
                        }
                        engine.speak(clean, TextToSpeech.QUEUE_FLUSH, params, newId)
                    }
                }
            }

            DisposableEffect(context) {
                var engine: TextToSpeech? = null
                runCatching {
                    engine = TextToSpeech(context.applicationContext) { status ->
                        runCatching {
                            if (status == TextToSpeech.SUCCESS) {
                                val res = engine?.setLanguage(Locale.US)
                                if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
                                    engine?.setLanguage(Locale.ENGLISH)
                                }
                                engine?.setSpeechRate(0.92f)
                                engine?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                                    override fun onStart(utteranceId: String?) {
                                        mainHandler.post {
                                            if (utteranceId == activeUtteranceId.value) isSpeaking = true
                                        }
                                    }
                                    override fun onDone(utteranceId: String?) {
                                        mainHandler.post {
                                            if (utteranceId == activeUtteranceId.value) isSpeaking = false
                                        }
                                    }
                                    @Deprecated("Deprecated in Java")
                                    override fun onError(utteranceId: String?) {
                                        mainHandler.post {
                                            if (utteranceId == activeUtteranceId.value) isSpeaking = false
                                        }
                                    }
                                    override fun onStop(utteranceId: String?, interrupted: Boolean) {
                                        mainHandler.post {
                                            if (utteranceId == activeUtteranceId.value) isSpeaking = false
                                        }
                                    }
                                })
                                ttsRef.value = engine
                                ttsReady = true
                            }
                        }
                    }
                }
                onDispose {
                    runCatching {
                        engine?.stop()
                        engine?.shutdown()
                    }
                    ttsRef.value = null
                }
            }

            DisposableEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                        stopSpeakingSafely()
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
            }

            LaunchedEffect(Unit) {
                withContext(Dispatchers.IO) {
                    val loadedWords = wordRepository.loadWords()
                    val loadedScenarios = conversationRepository.loadScenarios()
                    wordList = loadedWords
                    scenariosList = loadedScenarios
                }
            }

            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                ModalNavigationDrawer(
                    drawerState = drawerState,
                    drawerContent = {
                        ModalDrawerSheet(
                            drawerContainerColor = Color(0xFF0B1218),
                            modifier = Modifier
                                .fillMaxWidth(0.82f)
                                .border(1.dp, TealNeon.copy(alpha = 0.3f), RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp))
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(20.dp)
                            ) {
                                Text(
                                    text = "English Tutor Pro",
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color.White,
                                    modifier = Modifier.padding(vertical = 8.dp)
                                )
                                Text(
                                    text = "בחר מצב למידה ותרגול",
                                    fontSize = 14.sp,
                                    color = TealNeon,
                                    modifier = Modifier.padding(bottom = 24.dp)
                                )

                                AppMode.entries.forEach { mode ->
                                    val selected = currentMode == mode
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(bottom = 14.dp)
                                            .clip(RoundedCornerShape(18.dp))
                                            .background(if (selected) Color(0xFF0E4944) else CardDark)
                                            .border(
                                                width = if (selected) 2.dp else 1.dp,
                                                color = if (selected) TealNeon else Color.Gray.copy(0.25f),
                                                shape = RoundedCornerShape(18.dp)
                                            )
                                            .clickable {
                                                stopSpeakingSafely()
                                                currentMode = mode
                                                scope.launch { drawerState.close() }
                                            }
                                            .padding(16.dp)
                                    ) {
                                        Column {
                                            Text(
                                                text = mode.titleHe,
                                                fontSize = 18.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (selected) TealNeon else Color.White
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = mode.subtitleHe,
                                                fontSize = 12.sp,
                                                color = Color.LightGray
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                ) {
                    Surface(modifier = Modifier.fillMaxSize(), color = DarkBg) {
                        val loaded = wordList
                        if (loaded == null) {
                            Box(
                                modifier = Modifier.fillMaxSize().background(DarkBg),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(color = TealNeon)
                            }
                        } else {
                            when (currentMode) {
                                AppMode.FLASHCARDS -> FlashcardScreen(
                                    words = loaded,
                                    repository = wordRepository,
                                    isSpeaking = isSpeaking,
                                    autoSpeak = autoSpeak,
                                    onToggleAutoSpeak = { autoSpeak = !autoSpeak },
                                    onSpeakWord = { speakEnglish(it) },
                                    onStopSpeaking = { stopSpeakingSafely() },
                                    onOpenDrawer = { scope.launch { drawerState.open() } }
                                )
                                AppMode.CONVERSATIONS -> ConversationScreen(
                                    scenarios = scenariosList,
                                    repository = conversationRepository,
                                    onOpenDrawer = { scope.launch { drawerState.open() } },
                                    onSpeakEnglish = { speakEnglish(it) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
