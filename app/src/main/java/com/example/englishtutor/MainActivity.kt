package com.example.englishtutor

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
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
    SETTINGS("הגדרות ומחולל מילים AI")
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
            var activeCefrFilter by remember { mutableStateOf(CefrLevel.ALL) }

            // דיאלוג מהיר להוספת מילים ב-AI מכל מקום באפליקציה (עם אפקט טשטוש ברקע)
            var showQuickAiWordsDialog by remember { mutableStateOf(false) }
            var quickAiTopic by remember { mutableStateOf("") }
            var isGeneratingQuickWords by remember { mutableStateOf(false) }

            // חישוב עוצמת הטשטוש הדינמי (Dynamic Glassmorphism Blur) כשתפריט הצד או חלונית פתוחים
            val isOverlayOpen = drawerState.isOpen || drawerState.isAnimationRunning || showQuickAiWordsDialog
            val dynamicBlurRadius by animateDpAsState(
                targetValue = if (isOverlayOpen) 14.dp else 0.dp,
                animationSpec = tween(280),
                label = "glassBlurAnim"
            )

            LaunchedEffect(Unit) {
                withContext(Dispatchers.IO) {
                    wordList = wordRepository.loadWords()
                    scenariosList = conversationRepository.loadScenarios()
                }
            }

            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                ModalNavigationDrawer(
                    drawerState = drawerState,
                    scrimColor = Color(0xAA050811),
                    drawerContent = {
                        ModalDrawerSheet(
                            drawerContainerColor = Color(0xEB0A101A),
                            modifier = Modifier
                                .fillMaxWidth(0.85f)
                                .border(
                                    1.5.dp,
                                    TealNeon.copy(alpha = 0.45f),
                                    RoundedCornerShape(topEnd = 26.dp, bottomEnd = 26.dp)
                                )
                        ) {
                            // גלילה אנכית מלאה כדי שכל הכפתורים וההגדרות תמיד יופיעו בכל גודל מסך!
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(20.dp)
                                    .verticalScroll(rememberScrollState())
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = "English Tutor Pro",
                                            fontSize = 23.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = Color.White
                                        )
                                        Text(
                                            text = "גרסה 5.0 • מערכת למידה ו-AI",
                                            fontSize = 12.sp,
                                            color = TealNeon
                                        )
                                    }

                                    // כפתור גישה מהירה להגדרות גם בראש התפריט
                                    Box(
                                        modifier = Modifier.clickable {
                                            hapticManager.click()
                                            currentMode = AppMode.SETTINGS
                                            scope.launch { drawerState.close() }
                                        }
                                    ) {
                                        CanvaIconBadge(accentColor = StarGold, badgeSize = 40.dp) {
                                            CanvaSettingsIcon(tint = StarGold, modifier = Modifier.size(20.dp))
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(14.dp))

                                // כפתור בולט להוספת מילים באמצעות AI ישירות מהתפריט
                                Button(
                                    onClick = {
                                        hapticManager.click()
                                        scope.launch { drawerState.close() }
                                        showQuickAiWordsDialog = true
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .border(1.5.dp, TealNeon, RoundedCornerShape(14.dp)),
                                    colors = ButtonDefaults.buttonColors(containerColor = TealDark)
                                ) {
                                    CanvaAiDeckIcon(tint = TealNeon, modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "הוסף מילים חדשות עם AI",
                                        color = Color.White,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 14.sp
                                    )
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                Text("מבטא הקראה באנגלית:", color = StarGold, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(6.dp))
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
                                                .padding(horizontal = 10.dp, vertical = 6.dp)
                                        ) {
                                            Text(
                                                text = accent.label,
                                                color = if (isSel) TealNeon else Color.LightGray,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(16.dp))

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
                                                if (mode == AppMode.FLASHCARDS) {
                                                    activeCefrFilter = CefrLevel.ALL
                                                }
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
                                                AppMode.SKILL_TREE -> CanvaSkillTreeIcon(tint = if (isSelected) StarGold else Color.LightGray, modifier = Modifier.size(18.dp))
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
                    // אפקט הטשטוש הדינמי (Blur) שמופעל אוטומטית על התוכן כשהתפריט או החלונית פתוחים
                    Surface(
                        modifier = Modifier
                            .fillMaxSize()
                            .blur(dynamicBlurRadius),
                        color = DarkBg
                    ) {
                        val loaded = wordList
                        if (loaded == null) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = TealNeon)
                            }
                        } else {
                            val wordsForCurrentView = remember(loaded, activeCefrFilter) {
                                if (activeCefrFilter == CefrLevel.ALL) loaded
                                else loaded.filter { it.cefr.equals(activeCefrFilter.name, ignoreCase = true) }.ifEmpty { loaded }
                            }

                            when (currentMode) {
                                AppMode.FLASHCARDS -> FlashcardScreen(
                                    words = wordsForCurrentView,
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
                                    words = loaded,
                                    haptic = hapticManager,
                                    onOpenDrawer = { scope.launch { drawerState.open() } },
                                    onStartLevelPractice = { chosenLevel ->
                                        activeCefrFilter = chosenLevel
                                        currentMode = AppMode.FLASHCARDS
                                    },
                                    onLaunchBossScenario = { bossScenarioId ->
                                        // מעלה את קרב הבוס שנבחר לראש רשימת השיחות ופותח את מסך השיחות
                                        val sorted = scenariosList.sortedByDescending { it.id == bossScenarioId }
                                        scenariosList = sorted
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
                                        wordList = (newWords + loaded).distinctBy { it.normalizedKey }
                                    }
                                )
                            }
                        }
                    }

                    // --- חלונית מהירה להוספת מילים חדשות עם AI (עם רקע מטושטש מאחוריה) ---
                    if (showQuickAiWordsDialog) {
                        AlertDialog(
                            onDismissRequest = { if (!isGeneratingQuickWords) showQuickAiWordsDialog = false },
                            containerColor = CardDark,
                            title = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CanvaAiDeckIcon(tint = TealNeon, modifier = Modifier.size(22.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("הוספת מילים חדשות עם AI", color = TealNeon, fontWeight = FontWeight.ExtraBold, fontSize = 19.sp)
                                }
                            },
                            text = {
                                Column {
                                    Text(
                                        text = "הקלד כל נושא שתרצה (בעברית או באנגלית), וה-AI ייצור עבורך חבילת מילים חדשה ויוסיף אותה מיד לכרטיסיות ולמשחקים:",
                                        color = Color.LightGray,
                                        fontSize = 13.sp
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                    OutlinedTextField(
                                        value = quickAiTopic,
                                        onValueChange = { quickAiTopic = it },
                                        placeholder = { Text("למשל: אבטחת מידע, טיול בלונדון, ספורט...", color = Color.Gray) },
                                        singleLine = true,
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedTextColor = Color.White,
                                            unfocusedTextColor = Color.White,
                                            focusedBorderColor = TealNeon
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    if (isGeneratingQuickWords) {
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            CircularProgressIndicator(color = TealNeon, modifier = Modifier.size(20.dp))
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Text("ה-AI בונה ומתרגם כרטיסיות חדשות...", color = TealNeon, fontSize = 12.sp)
                                        }
                                    }
                                }
                            },
                            confirmButton = {
                                Button(
                                    enabled = !isGeneratingQuickWords,
                                    onClick = {
                                        hapticManager.click()
                                        val topicToCreate = quickAiTopic.ifBlank { "אנגלית מתקדמת לשיחה" }
                                        isGeneratingQuickWords = true
                                        scope.launch {
                                            val created = wordRepository.generateCustomAiDeck(topicToCreate)
                                            isGeneratingQuickWords = false
                                            if (created.isNotEmpty()) {
                                                hapticManager.success()
                                                val current = wordList.orEmpty()
                                                wordList = (created + current).distinctBy { it.normalizedKey }
                                                activeCefrFilter = CefrLevel.ALL
                                                currentMode = AppMode.FLASHCARDS
                                                showQuickAiWordsDialog = false
                                                quickAiTopic = ""
                                            }
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = TealDark)
                                ) {
                                    Text("צור מילים עכשיו", color = TealNeon, fontWeight = FontWeight.Bold)
                                }
                            },
                            dismissButton = {
                                TextButton(
                                    enabled = !isGeneratingQuickWords,
                                    onClick = { showQuickAiWordsDialog = false }
                                ) {
                                    Text("סגור", color = Color.Gray)
                                }
                            }
                        )
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
