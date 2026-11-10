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
    SKILL_TREE("עץ שלבים ובוסים"),
    CONVERSATIONS("סימולציות שיחה (Roleplay)"),
    FREE_CHAT("צ'אט חופשי + מבחני AI"),
    SPELLING("מבחן איות והקלדה"),
    SPEED_ATTACK("אתגר 60 שניות"),
    SETTINGS("הגדרות והתאמה אישית")
}

class MainActivity : ComponentActivity() {
    private lateinit var speechManager: SpeechManager
    private lateinit var hapticManager: HapticManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        hapticManager = HapticManager(applicationContext)
        speechManager = SpeechManager(applicationContext).apply {
            initTts()
        }

        val wordRepository = WordRepository(applicationContext)
        val conversationRepository = ConversationRepository(applicationContext)

        setContent {
            val scope = rememberCoroutineScope()
            var wordList by remember { mutableStateOf<List<WordCard>?>(null) }
            var scenariosList by remember { mutableStateOf<List<ConversationScenario>>(emptyList()) }
            var currentMode by remember { mutableStateOf(AppMode.FLASHCARDS) }
            val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

            var selectedAccent by remember { mutableStateOf(VoiceAccent.US) }

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
                            drawerContainerColor = Color(0xFF0A1017),
                            modifier = Modifier
                                .fillMaxWidth(0.85f)
                                .border(1.dp, TealNeon.copy(alpha = 0.35f), RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp))
                        ) {
                            Column(modifier = Modifier.fillMaxSize().padding(22.dp)) {
                                Text(
                                    text = "English Tutor Pro",
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color.White
                                )
                                Text(
                                    text = "גרסה 5.0 • מערכת למידה מקיפה",
                                    fontSize = 13.sp,
                                    color = TealNeon
                                )

                                Spacer(modifier = Modifier.height(18.dp))

                                Text("מבטא הקראה באנגלית:", color = StarGold, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    VoiceAccent.entries.forEach { accent ->
                                        val isSel = selectedAccent == accent
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(if (isSel) TealDark else CardDark)
                                                .border(1.5.dp, if (isSel) TealNeon else Color.Gray.copy(0.35f), RoundedCornerShape(10.dp))
                                                .clickable {
                                                    hapticManager.click()
                                                    selectedAccent = accent
                                                    speechManager.currentAccent = accent
                                                }
                                                .padding(horizontal = 12.dp, vertical = 6.dp)
                                        ) {
                                            Text(accent.label, color = if (isSel) TealNeon else Color.LightGray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(20.dp))

                                AppMode.entries.forEach { mode ->
                                    val isSelected = currentMode == mode
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 5.dp)
                                            .clip(RoundedCornerShape(16.dp))
                                            .background(if (isSelected) Color(0xFF0E4944) else CardDark)
                                            .border(
                                                width = if (isSelected) 1.5.dp else 1.dp,
                                                color = if (isSelected) TealNeon else Color.Gray.copy(0.22f),
                                                shape = RoundedCornerShape(16.dp)
                                            )
                                            .clickable {
                                                hapticManager.click()
                                                speechManager.stop()
                                                speechManager.stopListening()
                                                currentMode = mode
                                                scope.launch { drawerState.close() }
                                            }
                                            .padding(horizontal = 14.dp, vertical = 11.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        CanvaIconBadge(
                                            accentColor = if (isSelected) TealNeon else Color.LightGray,
                                            badgeSize = 34.dp,
                                            cornerRadius = 9.dp,
                                            isActive = isSelected
                                        ) {
                                            when (mode) {
                                                AppMode.FLASHCARDS -> CanvaDiamondIcon(tint = if (isSelected) TealNeon else Color.LightGray, modifier = Modifier.size(18.dp))
                                                AppMode.SKILL_TREE -> CanvaBoltIcon(tint = if (isSelected) StarGold else Color.LightGray, modifier = Modifier.size(18.dp))
                                                AppMode.CONVERSATIONS -> CanvaSpeakerBubbleIcon(tint = if (isSelected) TealNeon else Color.LightGray, modifier = Modifier.size(18.dp))
                                                AppMode.FREE_CHAT -> CanvaMicIcon(tint = if (isSelected) TealNeon else Color.LightGray, modifier = Modifier.size(18.dp))
                                                AppMode.SPELLING -> CanvaCheckIcon(tint = if (isSelected) TealNeon else Color.LightGray, modifier = Modifier.size(18.dp))
                                                AppMode.SPEED_ATTACK -> CanvaFlameIcon(tint = if (isSelected) StarGold else Color.LightGray, modifier = Modifier.size(18.dp))
                                                AppMode.SETTINGS -> CanvaSettingsIcon(tint = if (isSelected) StarGold else Color.LightGray, modifier = Modifier.size(18.dp))
                                            }
                                        }
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Text(
                                            text = mode.titleHe,
                                            color = if (isSelected) TealNeon else Color.White,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold
                                        )
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
                                    isSpeaking = speechManager.isCurrentlySpeaking,
                                    autoSpeak = !speechManager.isVoiceMuted,
                                    onToggleAutoSpeak = { 
                                        hapticManager.click()
                                        speechManager.toggleMute() 
                                    },
                                    onSpeakWord = { speechManager.speak(it ?: "", forceEvenIfMuted = true) },
                                    onStopSpeaking = { speechManager.stop() },
                                    onOpenDrawer = { 
                                        hapticManager.click()
                                        scope.launch { drawerState.open() } 
                                    }
                                )
                                AppMode.SKILL_TREE -> SkillTreeScreen(
                                    conversationRepo = conversationRepository,
                                    haptic = hapticManager,
                                    onOpenDrawer = { scope.launch { drawerState.open() } },
                                    onLaunchBossScenario = {
                                        currentMode = AppMode.CONVERSATIONS
                                    }
                                )
                                AppMode.CONVERSATIONS -> ConversationScreen(
                                    scenarios = scenariosList,
                                    repository = conversationRepository,
                                    onOpenDrawer = { 
                                        hapticManager.click()
                                        scope.launch { drawerState.open() } 
                                    },
                                    onSpeakEnglish = { speechManager.speak(it) }
                                )
                                AppMode.FREE_CHAT -> FreeFlowChatScreen(
                                    conversationRepo = conversationRepository,
                                    speechManager = speechManager,
                                    onOpenDrawer = { 
                                        hapticManager.click()
                                        scope.launch { drawerState.open() } 
                                    }
                                )
                                AppMode.SPELLING -> SpellingPracticeScreen(
                                    words = loaded,
                                    speechManager = speechManager,
                                    onOpenDrawer = { 
                                        hapticManager.click()
                                        scope.launch { drawerState.open() } 
                                    }
                                )
                                AppMode.SPEED_ATTACK -> SpeedChallengeScreen(
                                    words = loaded,
                                    repo = wordRepository,
                                    onOpenDrawer = { 
                                        hapticManager.click()
                                        scope.launch { drawerState.open() } 
                                    }
                                )
                                AppMode.SETTINGS -> SettingsScreen(
                                    conversationRepo = conversationRepository,
                                    wordRepo = wordRepository,
                                    speechManager = speechManager,
                                    haptic = hapticManager,
                                    onOpenDrawer = { 
                                        hapticManager.click()
                                        scope.launch { drawerState.open() } 
                                    },
                                    onDeckGenerated = { newWords ->
                                        wordList = (loaded + newWords).distinctBy { it.normalizedKey }
                                    }
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
