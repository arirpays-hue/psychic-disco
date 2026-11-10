package com.example.englishtutor

import android.os.Bundle
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
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class AppMode(val titleHe: String) {
    FLASHCARDS("כרטיסיות חכמות"),
    CONVERSATIONS("סימולציות שיחה (Roleplay)"),
    FREE_CHAT("צ'אט חופשי מול AI"),
    SPELLING("מבחן איות והקלדה"),
    SPEED_ATTACK("אתגר 60 שניות ⚡")
}

class MainActivity : ComponentActivity() {
    private lateinit var speechManager: SpeechManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        speechManager = SpeechManager(applicationContext).apply {
            initTts {}
        }

        val wordRepository = WordRepository(applicationContext)
        val conversationRepository = ConversationRepository(applicationContext)

        setContent {
            val scope = rememberCoroutineScope()
            var wordList by remember { mutableStateOf<List<WordCard>?>(null) }
            var scenariosList by remember { mutableStateOf<List<ConversationScenario>>(emptyList()) }
            var currentMode by remember { mutableStateOf(AppMode.FLASHCARDS) }
            val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

            // הגדרת מבטא
            var selectedAccent by remember { mutableStateOf(VoiceAccent.US) }
            var selectedCefr by remember { mutableStateOf(CefrLevel.ALL) }

            LaunchedEffect(Unit) {
                withContext(Dispatchers.IO) {
                    wordList = wordRepository.loadWords()
                    scenariosList = conversationRepository.loadScenarios()
                }
            }

            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                ModalNavigationDrawer(
                    drawerState = drawerState,
                    drawerContent = {
                        ModalDrawerSheet(
                            drawerContainerColor = Color(0xFF0B1218),
                            modifier = Modifier.fillMaxWidth(0.85f)
                        ) {
                            Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
                                Text("English Tutor Pro", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                Spacer(modifier = Modifier.height(16.dp))

                                // בורר מבטאים
                                Text("מבטא דיבור:", color = StarGold, fontSize = 13.sp)
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    VoiceAccent.entries.forEach { accent ->
                                        val isSel = selectedAccent == accent
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(if (isSel) TealDark else CardDark)
                                                .border(1.dp, if (isSel) TealNeon else Color.Gray, RoundedCornerShape(8.dp))
                                                .clickable {
                                                    selectedAccent = accent
                                                    speechManager.currentAccent = accent
                                                }
                                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                        ) {
                                            Text(accent.name, color = if (isSel) TealNeon else Color.White, fontSize = 12.sp)
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(20.dp))

                                // רשימת כל המצבים
                                AppMode.entries.forEach { mode ->
                                    val isSelected = currentMode == mode
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(if (isSelected) TealDark else CardDark)
                                            .border(1.dp, if (isSelected) TealNeon else Color.Transparent, RoundedCornerShape(12.dp))
                                            .clickable {
                                                speechManager.stop()
                                                currentMode = mode
                                                scope.launch { drawerState.close() }
                                            }
                                            .padding(14.dp)
                                    ) {
                                        Text(mode.titleHe, color = if (isSelected) TealNeon else Color.White, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                ) {
                    Surface(modifier = Modifier.fillMaxSize(), color = DarkBg) {
                        val loaded = wordList
                        if (loaded == null) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = TealNeon)
                            }
                        } else {
                            when (currentMode) {
                                AppMode.FLASHCARDS -> FlashcardScreen(
                                    words = loaded,
                                    repository = wordRepository,
                                    isSpeaking = false,
                                    autoSpeak = true,
                                    onToggleAutoSpeak = {},
                                    onSpeakWord = { speechManager.speak(it ?: "") },
                                    onStopSpeaking = { speechManager.stop() },
                                    onOpenDrawer = { scope.launch { drawerState.open() } }
                                )
                                AppMode.CONVERSATIONS -> ConversationScreen(
                                    scenarios = scenariosList,
                                    repository = conversationRepository,
                                    onOpenDrawer = { scope.launch { drawerState.open() } },
                                    onSpeakEnglish = { speechManager.speak(it) }
                                )
                                AppMode.FREE_CHAT -> FreeFlowChatScreen(
                                    conversationRepo = conversationRepository,
                                    speechManager = speechManager,
                                    onOpenDrawer = { scope.launch { drawerState.open() } }
                                )
                                AppMode.SPELLING -> SpellingPracticeScreen(
                                    words = loaded,
                                    speechManager = speechManager,
                                    onOpenDrawer = { scope.launch { drawerState.open() } }
                                )
                                AppMode.SPEED_ATTACK -> SpeedChallengeScreen(
                                    words = loaded,
                                    repo = wordRepository,
                                    onOpenDrawer = { scope.launch { drawerState.open() } }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        speechManager.destroy()
    }
}
