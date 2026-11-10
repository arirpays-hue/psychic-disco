package com.example.englishtutor

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.LocalContext
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

data class CanvaSkillNodeItem(
    val level: CefrLevel,
    val titleHe: String,
    val descriptionHe: String,
    val isBossBattle: Boolean = false,
    val bossScenarioId: String? = null
)

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

            var showQuickAiWordsDialog by remember { mutableStateOf(false) }
            var quickAiTopic by remember { mutableStateOf("") }
            var isGeneratingQuickWords by remember { mutableStateOf(false) }

            // אפקט טשטוש דינמי (Glassmorphism Blur) בזמן פתיחת תפריט או חלונית AI
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

                                Text(
                                    text = "מבטא הקראה באנגלית:",
                                    color = StarGold,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    for (accent in VoiceAccent.entries) {
                                        val isSel = selectedAccent == accent
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(if (isSel) TealDark else CardDark)
                                                .border(
                                                    1.5.dp,
                                                    if (isSel) TealNeon else Color.Gray.copy(0.35f),
                                                    RoundedCornerShape(10.dp)
                                                )
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

                                for (mode in AppMode.entries) {
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
                                                AppMode.FLASHCARDS -> CanvaDiamondIcon(
                                                    tint = if (isSelected) TealNeon else Color.LightGray,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                AppMode.SKILL_TREE -> CanvaSkillTreeIcon(
                                                    tint = if (isSelected) StarGold else Color.LightGray,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                AppMode.CONVERSATIONS -> CanvaSpeakerBubbleIcon(
                                                    tint = if (isSelected) TealNeon else Color.LightGray,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                AppMode.FREE_CHAT -> CanvaMicIcon(
                                                    tint = if (isSelected) TealNeon else Color.LightGray,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                AppMode.SPELLING -> CanvaCheckIcon(
                                                    tint = if (isSelected) TealNeon else Color.LightGray,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                AppMode.SPEED_ATTACK -> CanvaFlameIcon(
                                                    tint = if (isSelected) StarGold else Color.LightGray,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                AppMode.SETTINGS -> CanvaSettingsIcon(
                                                    tint = if (isSelected) StarGold else Color.LightGray,
                                                    modifier = Modifier.size(18.dp)
                                                )
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
                                if (activeCefrFilter == CefrLevel.ALL) {
                                    loaded
                                } else {
                                    loaded.filter { it.cefr.equals(activeCefrFilter.name, ignoreCase = true) }
                                        .ifEmpty { loaded }
                                }
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
                                AppMode.SKILL_TREE -> CanvaSkillTreeView(
                                    words = loaded,
                                    haptic = hapticManager,
                                    onOpenDrawer = { scope.launch { drawerState.open() } },
                                    onStartLevelPractice = { chosenLevel: CefrLevel ->
                                        activeCefrFilter = chosenLevel
                                        currentMode = AppMode.FLASHCARDS
                                    },
                                    onLaunchBossScenario = { bossId: String ->
                                        val sorted = scenariosList.sortedByDescending { it.id == bossId }
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
                                AppMode.SETTINGS -> CanvaSettingsView(
                                    conversationRepo = conversationRepository,
                                    wordRepo = wordRepository,
                                    speechManager = speechManager,
                                    haptic = hapticManager,
                                    onOpenDrawer = {
                                        hapticManager.click()
                                        scope.launch { drawerState.open() }
                                    },
                                    onDeckGenerated = { newWords: List<WordCard> ->
                                        wordList = (newWords + loaded).distinctBy { it.normalizedKey }
                                    }
                                )
                            }
                        }
                    }

                    if (showQuickAiWordsDialog) {
                        AlertDialog(
                            onDismissRequest = { if (!isGeneratingQuickWords) showQuickAiWordsDialog = false },
                            containerColor = CardDark,
                            title = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CanvaAiDeckIcon(tint = TealNeon, modifier = Modifier.size(22.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "הוספת מילים חדשות עם AI",
                                        color = TealNeon,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 19.sp
                                    )
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
                                            Text(
                                                text = "ה-AI בונה ומתרגם כרטיסיות חדשות...",
                                                color = TealNeon,
                                                fontSize = 12.sp
                                            )
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

// --- מסך עץ היכולות ומסלול הבוסים (ללא התנגשות עם קבצים ישנים) ---
@Composable
fun CanvaSkillTreeView(
    words: List<WordCard>,
    haptic: HapticManager,
    onOpenDrawer: () -> Unit,
    onStartLevelPractice: (CefrLevel) -> Unit,
    onLaunchBossScenario: (String) -> Unit
) {
    val totalLearned = remember(words) { words.count { it.isLearned } }

    val nodes = remember {
        listOf(
            CanvaSkillNodeItem(
                level = CefrLevel.A1,
                titleHe = "שלב 1: יסודות השפה (A1)",
                descriptionHe = "מילים בסיסיות, היכרות, ברכות והתמצאות ראשונית"
            ),
            CanvaSkillNodeItem(
                level = CefrLevel.A2,
                titleHe = "שלב 2: נוסע עצמאי (A2)",
                descriptionHe = "שדה תעופה, תחבורה, קניות ושיחות חולין"
            ),
            CanvaSkillNodeItem(
                level = CefrLevel.B1,
                titleHe = "בוס ביניים: מסעדה ומלון בחו״ל",
                descriptionHe = "פתרון תקלות בזמן אמת מול צוות המלון והמסעדה",
                isBossBattle = true,
                bossScenarioId = "restaurant_full_evening"
            ),
            CanvaSkillNodeItem(
                level = CefrLevel.B2,
                titleHe = "שלב 4: אנגלית עסקית וטכנולוגיה (B2)",
                descriptionHe = "אוצר מילים מתקדם לעבודה, משא ומתן וטכנולוגיה"
            ),
            CanvaSkillNodeItem(
                level = CefrLevel.C1,
                titleHe = "שלב 5: שליטה אקדמית וניבים (C1)",
                descriptionHe = "מילים מורכבות, ביטויים מעמיקים ודיוק לשוני"
            ),
            CanvaSkillNodeItem(
                level = CefrLevel.C2,
                titleHe = "בוס סופי: ראיון ארכיטקט תוכנה בכיר",
                descriptionHe = "קרב שיחתי מעמיק על פיתוח מערכות, ביצועים ותנאי שכר",
                isBossBattle = true,
                bossScenarioId = "tech_interview_deep_dive"
            )
        )
    }

    val infiniteTransition = rememberInfiniteTransition(label = "treePulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Reverse),
        label = "pulseAlpha"
    )

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(DarkBg, Color(0xFF10172A), DarkBg)))
                .padding(horizontal = 18.dp, vertical = 14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.clickable {
                        haptic.click()
                        onOpenDrawer()
                    }
                ) {
                    CanvaIconBadge(accentColor = TealNeon, badgeSize = 44.dp) {
                        CanvaMenuIcon(tint = TealNeon)
                    }
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "עץ היכולות ומסלול הבוסים",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = "מילים שנלמדו: $totalLearned • לחץ על שלב לאימון",
                        color = TealNeon,
                        fontSize = 12.sp
                    )
                }

                CanvaIconBadge(accentColor = StarGold, badgeSize = 44.dp) {
                    CanvaSkillTreeIcon(tint = StarGold, modifier = Modifier.size(22.dp))
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                itemsIndexed(nodes) { index, node ->
                    val levelWords = remember(words, node.level) {
                        words.filter { it.cefr.equals(node.level.name, ignoreCase = true) }
                    }
                    val learnedInLevel = levelWords.count { it.isLearned }
                    val totalInLevel = levelWords.size.coerceAtLeast(1)
                    val levelProgress = (learnedInLevel.toFloat() / totalInLevel.toFloat()).coerceIn(0f, 1f)

                    val accent = when {
                        node.isBossBattle -> StarGold
                        levelProgress >= 0.8f -> CheckNeon
                        index % 2 == 0 -> TealNeon
                        else -> PurpleNeon
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(
                                    width = if (node.isBossBattle) 2.5.dp else 1.8.dp,
                                    color = accent.copy(alpha = if (node.isBossBattle) pulseAlpha else 0.85f),
                                    shape = RoundedCornerShape(24.dp)
                                )
                                .clickable {
                                    haptic.success()
                                    if (node.isBossBattle && node.bossScenarioId != null) {
                                        onLaunchBossScenario(node.bossScenarioId)
                                    } else {
                                        onStartLevelPractice(node.level)
                                    }
                                },
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (node.isBossBattle) Color(0xFF221A0F) else CardDark
                            )
                        ) {
                            Column(modifier = Modifier.padding(18.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        CanvaIconBadge(
                                            accentColor = accent,
                                            badgeSize = 48.dp,
                                            cornerRadius = 16.dp
                                        ) {
                                            if (node.isBossBattle) {
                                                CanvaBossCrownIcon(tint = StarGold, modifier = Modifier.size(26.dp))
                                            } else {
                                                CanvaDiamondIcon(tint = accent, modifier = Modifier.size(22.dp))
                                            }
                                        }

                                        Spacer(modifier = Modifier.width(14.dp))

                                        Column {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .background(accent.copy(alpha = 0.2f))
                                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                                ) {
                                                    Text(
                                                        text = if (node.isBossBattle) "קרב בוס שיחתי" else "רמה ${node.level.name}",
                                                        color = accent,
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.ExtraBold
                                                    )
                                                }
                                                if (!node.isBossBattle) {
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text(
                                                        text = "$learnedInLevel/${levelWords.size} מילים נלמדו",
                                                        color = Color.LightGray,
                                                        fontSize = 11.sp
                                                    )
                                                }
                                            }

                                            Spacer(modifier = Modifier.height(6.dp))
                                            Text(
                                                text = node.titleHe,
                                                color = Color.White,
                                                fontSize = 17.sp,
                                                fontWeight = FontWeight.ExtraBold
                                            )
                                            Spacer(modifier = Modifier.height(3.dp))
                                            Text(
                                                text = node.descriptionHe,
                                                color = Color.LightGray,
                                                fontSize = 13.sp
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(10.dp))

                                    CanvaIconBadge(
                                        accentColor = accent,
                                        badgeSize = 38.dp,
                                        cornerRadius = 12.dp
                                    ) {
                                        CanvaPlayIcon(tint = accent, modifier = Modifier.size(16.dp))
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                if (!node.isBossBattle) {
                                    LinearProgressIndicator(
                                        progress = { levelProgress.coerceIn(0.06f, 1f) },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(7.dp)
                                            .clip(RoundedCornerShape(4.dp)),
                                        color = accent,
                                        trackColor = Color(0xFF0B101D)
                                    )
                                } else {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(StarGold.copy(alpha = 0.14f))
                                            .padding(horizontal = 12.dp, vertical = 6.dp),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        CanvaBoltIcon(tint = StarGold, modifier = Modifier.size(15.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "לחץ כאן כדי להיכנס לקרב הבוס בסימולציית השיחה!",
                                            color = StarGold,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }

                        if (index < nodes.size - 1) {
                            Canvas(
                                modifier = Modifier
                                    .width(4.dp)
                                    .height(28.dp)
                            ) {
                                drawLine(
                                    brush = Brush.verticalGradient(listOf(accent, TealNeon)),
                                    start = Offset(size.width / 2f, 0f),
                                    end = Offset(size.width / 2f, size.height),
                                    strokeWidth = 4.dp.toPx(),
                                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// --- מסך הגדרות ומחולל מילים AI (ללא התנגשות עם קבצים ישנים) ---
@Composable
fun CanvaSettingsView(
    conversationRepo: ConversationRepository,
    wordRepo: WordRepository,
    speechManager: SpeechManager,
    haptic: HapticManager,
    onOpenDrawer: () -> Unit,
    onDeckGenerated: (List<WordCard>) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var apiKeyInput by remember { mutableStateOf(conversationRepo.getCustomApiKey()) }
    var speechRateSlider by remember { mutableFloatStateOf(speechManager.speechRate) }
    var speechPitchSlider by remember { mutableFloatStateOf(speechManager.speechPitch) }
    var hapticsEnabled by remember { mutableStateOf(haptic.isHapticsEnabled) }

    var customAiTopicInput by remember { mutableStateOf("") }
    var isGeneratingDeck by remember { mutableStateOf(false) }
    var aiDeckStatusBanner by remember { mutableStateOf("") }

    var csvImportInput by remember { mutableStateOf("") }
    var showImportDialog by remember { mutableStateOf(false) }

    val quickDeckTopics = listOf(
        "פיתוח תוכנה והייטק",
        "טיסות ושדה תעופה",
        "אוכל ומסעדות שף",
        "עסקים ומשא ומתן",
        "רפואה וחירום",
        "סלנג אמריקאי שימושי"
    )

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(DarkBg, Color(0xFF10172A), DarkBg)))
                .padding(horizontal = 18.dp, vertical = 14.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.clickable {
                        haptic.click()
                        onOpenDrawer()
                    }
                ) {
                    CanvaIconBadge(accentColor = TealNeon, badgeSize = 44.dp) {
                        CanvaMenuIcon(tint = TealNeon)
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "הגדרות ומחולל מילים AI",
                        color = Color.White,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = "התאמת קול, רטט, גיבוי והוספת כרטיסיות",
                        color = StarGold,
                        fontSize = 11.sp
                    )
                }
                CanvaIconBadge(accentColor = StarGold, badgeSize = 44.dp) {
                    CanvaSettingsIcon(tint = StarGold, modifier = Modifier.size(22.dp))
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(2.dp, TealNeon.copy(alpha = 0.7f), RoundedCornerShape(22.dp)),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = CardDark)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CanvaIconBadge(accentColor = TealNeon, badgeSize = 40.dp, cornerRadius = 12.dp) {
                            CanvaAiDeckIcon(tint = TealNeon, modifier = Modifier.size(22.dp))
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "הוספת מילים חדשות באמצעות AI",
                                color = TealNeon,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 16.sp
                            )
                            Text(
                                text = "הקלד נושא או בחר מהרשימה ליצירת כרטיסיות חדשות",
                                color = Color.LightGray,
                                fontSize = 12.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = customAiTopicInput,
                        onValueChange = { customAiTopicInput = it },
                        placeholder = { Text("למשל: פיתוח אנדרואיד, טיול ביפן, ראיון עבודה...", color = Color.Gray) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = TealNeon,
                            unfocusedBorderColor = Color.Gray.copy(alpha = 0.4f)
                        )
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                    ) {
                        for (topic in quickDeckTopics) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(TealDark)
                                    .border(1.dp, TealNeon.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                                    .clickable {
                                        haptic.click()
                                        customAiTopicInput = topic
                                    }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(text = topic, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Button(
                        enabled = !isGeneratingDeck,
                        onClick = {
                            haptic.click()
                            val chosenTopic = customAiTopicInput.ifBlank { quickDeckTopics.random() }
                            isGeneratingDeck = true
                            aiDeckStatusBanner = "מייצר כרטיסיות חדשות בנושא '$chosenTopic'..."
                            scope.launch {
                                val newDeck = wordRepo.generateCustomAiDeck(chosenTopic)
                                isGeneratingDeck = false
                                if (newDeck.isNotEmpty()) {
                                    haptic.success()
                                    aiDeckStatusBanner = "נוספו ${newDeck.size} מילים חדשות למאגר!"
                                    onDeckGenerated(newDeck)
                                    customAiTopicInput = ""
                                } else {
                                    haptic.error()
                                    aiDeckStatusBanner = "שגיאה ביצירת החפיסה. נסה שוב."
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = TealNeon)
                    ) {
                        if (isGeneratingDeck) {
                            CircularProgressIndicator(color = DarkBg, modifier = Modifier.size(22.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("ה-AI בונה כרטיסיות חדשות...", color = DarkBg, fontWeight = FontWeight.ExtraBold)
                        } else {
                            CanvaSparkleIcon(tint = DarkBg, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("צור והוסף מילים חדשות עם AI", color = DarkBg, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                        }
                    }

                    if (aiDeckStatusBanner.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF0E2F2B))
                                .border(1.dp, CheckNeon, RoundedCornerShape(10.dp))
                                .padding(10.dp)
                        ) {
                            CanvaCheckIcon(tint = CheckNeon, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(aiDeckStatusBanner, color = CheckNeon, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.5.dp, StarGold.copy(alpha = 0.6f), RoundedCornerShape(22.dp)),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = CardDark)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CanvaIconBadge(accentColor = StarGold, badgeSize = 40.dp, cornerRadius = 12.dp) {
                            CanvaSoundWaveSettingsIcon(tint = StarGold, modifier = Modifier.size(22.dp))
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "שמע, מהירות הקראה ורטט",
                                color = StarGold,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 16.sp
                            )
                            Text(
                                text = "כיוון מנוע הקול והפידבק הפיזי במכשיר",
                                color = Color.LightGray,
                                fontSize = 12.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CanvaHapticWaveIcon(tint = StarGold, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("רטט פידבק (Haptics) בלחיצות ובטעויות", color = Color.White, fontSize = 14.sp)
                        }
                        Switch(
                            checked = hapticsEnabled,
                            onCheckedChange = {
                                hapticsEnabled = it
                                haptic.isHapticsEnabled = it
                                if (it) haptic.click()
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "מהירות דיבור באנגלית: ${(speechRateSlider * 100).toInt()}%",
                        color = Color.LightGray,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Slider(
                        value = speechRateSlider,
                        onValueChange = {
                            speechRateSlider = it
                            speechManager.speechRate = it
                        },
                        valueRange = 0.6f..1.4f
                    )

                    Text(
                        text = "גובה צליל (Pitch): ${(speechPitchSlider * 100).toInt()}%",
                        color = Color.LightGray,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Slider(
                        value = speechPitchSlider,
                        onValueChange = {
                            speechPitchSlider = it
                            speechManager.speechPitch = it
                        },
                        valueRange = 0.7f..1.3f
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.5.dp, PurpleNeon.copy(alpha = 0.6f), RoundedCornerShape(22.dp)),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = CardDark)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CanvaIconBadge(accentColor = PurpleNeon, badgeSize = 40.dp, cornerRadius = 12.dp) {
                            CanvaCloudBackupIcon(tint = PurpleNeon, modifier = Modifier.size(22.dp))
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "ניהול נתונים וגיבוי מקומי",
                                color = PurpleNeon,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 16.sp
                            )
                            Text(
                                text = "שמירת התקדמות ב-JSON או ייבוא רשימת CSV",
                                color = Color.LightGray,
                                fontSize = 12.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = {
                                haptic.click()
                                val jsonBackup = wordRepo.exportProgressToJson(wordRepo.loadWords())
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                val clip = ClipData.newPlainText("EnglishTutorBackup", jsonBackup)
                                clipboard?.setPrimaryClip(clip)
                                haptic.success()
                                Toast.makeText(context, "קובץ הגיבוי הועתק ללוח!", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E2640))
                        ) {
                            Text("ייצוא גיבוי (JSON)", color = Color.White, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = {
                                haptic.click()
                                showImportDialog = true
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E2640))
                        ) {
                            Text("ייבוא קובץ CSV", color = TealNeon, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.5.dp, Color.Gray.copy(alpha = 0.35f), RoundedCornerShape(22.dp)),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = CardDark)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CanvaIconBadge(accentColor = StarGold, badgeSize = 40.dp, cornerRadius = 12.dp) {
                            CanvaApiKeyIcon(tint = StarGold, modifier = Modifier.size(22.dp))
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "מפתח API פרטי (אופציונלי)",
                                color = Color.White,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 15.sp
                            )
                            Text(
                                text = "תומך ב-Google Gemini (AIza...) או OpenRouter",
                                color = Color.LightGray,
                                fontSize = 12.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = apiKeyInput,
                        onValueChange = { apiKeyInput = it },
                        placeholder = { Text("השאר ריק לשימוש במנוע החינמי המובנה", color = Color.Gray) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = StarGold
                        )
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = {
                            haptic.click()
                            conversationRepo.saveCustomApiKey(apiKeyInput)
                            haptic.success()
                            Toast.makeText(context, "הגדרות ה-API נשמרו!", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = TealDark)
                    ) {
                        Text("שמור מפתח API", color = TealNeon, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }

        if (showImportDialog) {
            AlertDialog(
                onDismissRequest = { showImportDialog = false },
                containerColor = CardDark,
                title = { Text("ייבוא מילים מקובץ CSV", color = TealNeon, fontWeight = FontWeight.Bold) },
                text = {
                    Column {
                        Text(
                            text = "הדבק שורות בפורמט: מילה באנגלית, תרגום בעברית",
                            color = Color.LightGray,
                            fontSize = 12.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = csvImportInput,
                            onValueChange = { csvImportInput = it },
                            placeholder = { Text("run, לרוץ\njump, לקפוץ") },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            modifier = Modifier.fillMaxWidth().height(140.dp)
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val importedCount = wordRepo.importWordsFromCsv(csvImportInput)
                            showImportDialog = false
                            haptic.success()
                            Toast.makeText(
                                context,
                                "יובאו $importedCount מילים בהצלחה!",
                                Toast.LENGTH_SHORT
                            ).show()
                            onDeckGenerated(wordRepo.loadWords())
                        }
                    ) {
                        Text("ייבא")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showImportDialog = false }) {
                        Text("ביטול", color = Color.Gray)
                    }
                }
            )
        }
    }
}
