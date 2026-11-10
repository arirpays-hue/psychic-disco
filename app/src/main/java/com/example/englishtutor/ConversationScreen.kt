package com.example.englishtutor

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

val ErrorCrimson = Color(0xFFFF4D6D)

class AiOptionsStore {
    private val map = HashMap<String, MutableList<DialogueOption>>()

    fun getOptions(key: String): List<DialogueOption> {
        return map[key]?.toList() ?: emptyList()
    }

    fun addOptions(key: String, newItems: List<DialogueOption>) {
        val current = map.getOrPut(key) { mutableListOf() }
        current.addAll(newItems)
    }
}

@Composable
fun ConversationScreen(
    scenarios: List<ConversationScenario>,
    repository: ConversationRepository,
    onOpenDrawer: () -> Unit,
    onSpeakEnglish: (String) -> Unit
) {
    val scope = rememberCoroutineScope()

    var allScenarios by remember(scenarios) { mutableStateOf(scenarios) }
    var selectedCategory by remember { mutableStateOf("הכל") }
    var activeScenarioId by remember { mutableStateOf("") }
    var currentNodeId by remember { mutableStateOf("start") }
    var stepNumber by remember { mutableIntStateOf(1) }

    var shuffleSeed by remember { mutableIntStateOf(0) }

    val aiStore = remember { AiOptionsStore() }
    var aiStoreVersion by remember { mutableIntStateOf(0) }
    var isGeneratingMoreOptions by remember { mutableStateOf(false) }

    var showAiGeneratorDialog by remember { mutableStateOf(false) }
    var showApiKeyDialog by remember { mutableStateOf(false) }
    var aiTopicInput by remember { mutableStateOf("") }
    var aiDifficultySelected by remember { mutableStateOf("בינוני-מתקדם") }
    var isGeneratingScenario by remember { mutableStateOf(false) }
    var aiStatusMessage by remember { mutableStateOf("") }

    var livesRemaining by remember { mutableIntStateOf(3) }
    var scorePoints by remember { mutableIntStateOf(0) }
    var wrongTextsInCurrentNode by remember { mutableStateOf(emptySet<String>()) }

    var showHebrewTranslation by remember { mutableStateOf(false) }
    var lastFeedback by remember { mutableStateOf("") }
    var lastChoiceOptimal by remember { mutableStateOf(true) }
    var completionRefresh by remember { mutableIntStateOf(0) }

    val activeScenario = remember(allScenarios, activeScenarioId) {
        allScenarios.firstOrNull { it.id == activeScenarioId }
    }

    val shakeOffsetX = remember { Animatable(0f) }

    fun triggerErrorShake() {
        scope.launch {
            for (i in 0..5) {
                shakeOffsetX.animateTo(if (i % 2 == 0) 18f else -18f, tween(40))
            }
            shakeOffsetX.animateTo(0f, spring())
        }
    }

    val categories = remember(allScenarios) {
        listOf("הכל") + allScenarios.map { it.categoryHe }.distinct()
    }

    val filteredScenarios = remember(allScenarios, selectedCategory) {
        if (selectedCategory == "הכל") allScenarios
        else allScenarios.filter { it.categoryHe == selectedCategory }
    }

    LaunchedEffect(activeScenarioId, currentNodeId) {
        wrongTextsInCurrentNode = emptySet()
        shuffleSeed++
        val node = activeScenario?.nodes?.get(currentNodeId)
        if (node != null) {
            onSpeakEnglish(node.speakerLineEn)
            if (node.isEnding && activeScenario != null && livesRemaining > 0) {
                repository.markScenarioCompleted(activeScenario.id)
                completionRefresh++
            }
        }
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(DarkBg, Color(0xFF10172A), DarkBg)))
                .padding(horizontal = 18.dp, vertical = 14.dp)
        ) {
            // סרגל עליון עם סמלי קנבה מותאמים אישית
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.clickable { onOpenDrawer() }) {
                    CanvaIconBadge(accentColor = TealNeon, badgeSize = 44.dp) {
                        CanvaMenuIcon(tint = TealNeon)
                    }
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                ) {
                    Text(
                        text = if (activeScenario == null) "סימולציות שיחה + AI" else activeScenario.titleHe,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )
                    if (activeScenario != null) {
                        Text(
                            text = "שלב $stepNumber • ניקוד: $scorePoints",
                            fontSize = 12.sp,
                            color = TealNeon
                        )
                    }
                }

                if (activeScenario != null) {
                    Box(
                        modifier = Modifier.clickable {
                            activeScenarioId = ""
                            lastFeedback = ""
                        }
                    ) {
                        CanvaIconBadge(accentColor = PurpleNeon, badgeSize = 44.dp) {
                            CanvaBackArrowIcon(tint = PurpleNeon)
                        }
                    }
                } else {
                    Box(modifier = Modifier.clickable { showApiKeyDialog = true }) {
                        CanvaIconBadge(accentColor = StarGold, badgeSize = 44.dp) {
                            CanvaSettingsIcon(tint = StarGold)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (activeScenario == null) {
                Button(
                    onClick = {
                        aiStatusMessage = ""
                        showAiGeneratorDialog = true
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .border(1.5.dp, PurpleNeon, RoundedCornerShape(18.dp)),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1F1638))
                ) {
                    CanvaSparkleIcon(tint = StarGold, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "צור שיחה חדשה בהתאמה אישית עם AI (מקוון)",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                ) {
                    for (category in categories) {
                        val isSelected = selectedCategory == category
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (isSelected) TealDark else CardDark)
                                .border(
                                    1.5.dp,
                                    if (isSelected) TealNeon else Color.Gray.copy(0.25f),
                                    RoundedCornerShape(16.dp)
                                )
                                .clickable { selectedCategory = category }
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = category,
                                fontSize = 13.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) TealNeon else Color.LightGray
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                ) {
                    for (scenario in filteredScenarios) {
                        val isDone = remember(completionRefresh, scenario.id) {
                            repository.isScenarioCompleted(scenario.id)
                        }
                        val stagesCount = (scenario.nodes.size - 1).coerceAtLeast(1)

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(
                                    1.5.dp,
                                    when {
                                        isDone -> CheckNeon
                                        scenario.isAiGenerated -> PurpleNeon
                                        else -> TealNeon.copy(alpha = 0.45f)
                                    },
                                    RoundedCornerShape(20.dp)
                                )
                                .clickable {
                                    activeScenarioId = scenario.id
                                    currentNodeId = "start"
                                    stepNumber = 1
                                    livesRemaining = 3
                                    scorePoints = 0
                                    lastFeedback = ""
                                    showHebrewTranslation = false
                                    shuffleSeed++
                                },
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = CardDark)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = scenario.categoryHe,
                                            fontSize = 11.sp,
                                            color = if (scenario.isAiGenerated) StarGold else PurpleNeon,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(
                                                    if (scenario.isAiGenerated) StarGold.copy(alpha = 0.18f)
                                                    else PurpleNeon.copy(alpha = 0.15f)
                                                )
                                                .padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "רמה: ${scenario.difficultyHe} • $stagesCount שלבים",
                                            fontSize = 11.sp,
                                            color = StarGold
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = scenario.titleHe,
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    Text(
                                        text = scenario.titleEn,
                                        fontSize = 13.sp,
                                        color = TealNeon
                                    )
                                }

                                CanvaIconBadge(
                                    accentColor = if (isDone) CheckNeon else TealNeon,
                                    badgeSize = 42.dp,
                                    cornerRadius = 12.dp
                                ) {
                                    if (isDone) {
                                        CanvaCheckIcon(tint = CheckNeon, modifier = Modifier.size(20.dp))
                                    } else {
                                        CanvaPlayIcon(tint = TealNeon, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
            } else {
                val node = activeScenario.nodes[currentNodeId] ?: activeScenario.nodes.values.first()
                val totalStages = (activeScenario.nodes.size - 1).coerceAtLeast(1)
                val nodeKey = "${activeScenario.id}_${node.id}"

                val shuffledOptions = remember(activeScenario.id, currentNodeId, shuffleSeed, aiStoreVersion) {
                    val combined = ArrayList<DialogueOption>()
                    combined.addAll(node.options)
                    combined.addAll(aiStore.getOptions(nodeKey))
                    combined.distinctBy { optionItem -> optionItem.textEn.lowercase() }.shuffled()
                }

                // שורת לבבות מעוצבים ומד התקדמות
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (i in 1..3) {
                            CanvaHeartIcon(isActive = i <= livesRemaining, modifier = Modifier.size(20.dp))
                        }
                    }

                    LinearProgressIndicator(
                        progress = { (stepNumber.toFloat() / totalStages.toFloat()).coerceIn(0.1f, 1f) },
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 14.dp)
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = TealNeon,
                        trackColor = CardDark
                    )

                    Text(
                        text = "$stepNumber/$totalStages",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.LightGray
                    )
                }

                if (livesRemaining <= 0) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(2.dp, ErrorCrimson, RoundedCornerShape(26.dp)),
                            shape = RoundedCornerShape(26.dp),
                            colors = CardDefaults.cardColors(containerColor = CardDark)
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp).fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                CanvaIconBadge(accentColor = ErrorCrimson, badgeSize = 56.dp, cornerRadius = 16.dp) {
                                    CanvaCloseIcon(tint = ErrorCrimson, modifier = Modifier.size(28.dp))
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "השיחה הסתיימה ללא הצלחה",
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = ErrorCrimson
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = if (lastFeedback.isNotEmpty()) lastFeedback else "ביצעת 3 טעויות. האפשרויות יתערבבו מחדש בניסיון הבא!",
                                    fontSize = 15.sp,
                                    color = Color.LightGray,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(20.dp))
                                Button(
                                    onClick = {
                                        currentNodeId = "start"
                                        stepNumber = 1
                                        livesRemaining = 3
                                        scorePoints = 0
                                        lastFeedback = ""
                                        wrongTextsInCurrentNode = emptySet()
                                        shuffleSeed++
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = TealDark),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    CanvaRefreshIcon(tint = TealNeon, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("נסה שוב (בסדר תשובות מעורבב חדש)", color = TealNeon, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .offset { IntOffset(shakeOffsetX.value.roundToInt(), 0) }
                        ) {
                            if (lastFeedback.isNotEmpty()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 12.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(
                                            if (lastChoiceOptimal) CheckNeon.copy(alpha = 0.15f)
                                            else ErrorCrimson.copy(alpha = 0.18f)
                                        )
                                        .border(
                                            1.5.dp,
                                            if (lastChoiceOptimal) CheckNeon else ErrorCrimson,
                                            RoundedCornerShape(14.dp)
                                        )
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (lastChoiceOptimal) {
                                        CanvaCheckIcon(tint = CheckNeon, modifier = Modifier.size(18.dp))
                                    } else {
                                        CanvaCloseIcon(tint = ErrorCrimson, modifier = Modifier.size(18.dp))
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = lastFeedback.replace("✓", "").replace("✗", "").trim(),
                                        fontSize = 13.sp,
                                        color = if (lastChoiceOptimal) CheckNeon else ErrorCrimson,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            // בועת הדיבור של הדמות
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(2.dp, TealNeon, RoundedCornerShape(24.dp))
                                    .clickable { showHebrewTranslation = !showHebrewTranslation },
                                shape = RoundedCornerShape(24.dp),
                                colors = CardDefaults.cardColors(containerColor = CardDark)
                            ) {
                                Column(modifier = Modifier.padding(18.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            CanvaSpeakerBubbleIcon(tint = PurpleNeon, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = node.speakerName,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = PurpleNeon
                                            )
                                        }

                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(12.dp))
                                                    .background(
                                                        if (showHebrewTranslation) StarGold.copy(alpha = 0.22f)
                                                        else Color(0xFF232B45)
                                                    )
                                                    .border(1.dp, StarGold, RoundedCornerShape(12.dp))
                                                    .clickable { showHebrewTranslation = !showHebrewTranslation }
                                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                                            ) {
                                                CanvaTranslateIcon(tint = StarGold, modifier = Modifier.size(15.dp))
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = if (showHebrewTranslation) "הסתר תרגום" else "תרגם לעברית",
                                                    color = StarGold,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 12.sp
                                                )
                                            }

                                            Spacer(modifier = Modifier.width(8.dp))

                                            IconButton(
                                                onClick = { onSpeakEnglish(node.speakerLineEn) },
                                                modifier = Modifier
                                                    .size(38.dp)
                                                    .clip(CircleShape)
                                                    .background(TealDark)
                                            ) {
                                                SpeakerVectorIcon(
                                                    tint = TealNeon,
                                                    isMuted = false,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))

                                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                        Text(
                                            text = node.speakerLineEn,
                                            fontSize = 20.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = Color.White,
                                            lineHeight = 27.sp,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }

                                    if (showHebrewTranslation) {
                                        Spacer(modifier = Modifier.height(12.dp))
                                        HorizontalDivider(color = StarGold.copy(alpha = 0.4f))
                                        Spacer(modifier = Modifier.height(10.dp))
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(Color(0xFF0E1629))
                                                .border(1.dp, TealNeon.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                                                .padding(12.dp)
                                        ) {
                                            Text(
                                                text = if (node.speakerLineHe.isNotBlank()) node.speakerLineHe else "לחץ על תשובה או המתן לטעינת התרגום...",
                                                fontSize = 16.sp,
                                                color = TealNeon,
                                                fontWeight = FontWeight.Bold,
                                                lineHeight = 23.sp
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            if (node.isEnding) {
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .border(2.dp, CheckNeon, RoundedCornerShape(22.dp)),
                                    shape = RoundedCornerShape(22.dp),
                                    colors = CardDefaults.cardColors(containerColor = CardDark)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(22.dp).fillMaxWidth(),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        TrophyVectorIcon(tint = StarGold, modifier = Modifier.size(54.dp))
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = "השלמת את כל שלבי השיחה!",
                                            fontSize = 21.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = CheckNeon
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = "ניקוד סופי: $scorePoints • לבבות שנותרו: $livesRemaining/3",
                                            fontSize = 14.sp,
                                            color = StarGold
                                        )
                                        Spacer(modifier = Modifier.height(16.dp))
                                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                            Button(
                                                onClick = {
                                                    currentNodeId = "start"
                                                    stepNumber = 1
                                                    livesRemaining = 3
                                                    scorePoints = 0
                                                    lastFeedback = ""
                                                    shuffleSeed++
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = TealDark)
                                            ) {
                                                CanvaRefreshIcon(tint = TealNeon, modifier = Modifier.size(18.dp))
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("שחק שוב (מעורבב)", color = TealNeon)
                                            }
                                            Button(
                                                onClick = {
                                                    activeScenarioId = ""
                                                    lastFeedback = ""
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = PurpleNeon.copy(alpha = 0.25f))
                                            ) {
                                                Text("לכל השיחות", color = Color.White)
                                            }
                                        }
                                    }
                                }
                            } else {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "בחר את התגובה הנכונה (מתערבב בכל ניסיון):",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.LightGray
                                    )

                                    OutlinedButton(
                                        onClick = {
                                            if (!isGeneratingMoreOptions) {
                                                isGeneratingMoreOptions = true
                                                val optimalNextId = node.options.firstOrNull { opt -> opt.isOptimal }?.nextNodeId ?: "start"
                                                scope.launch {
                                                    val res = repository.generateExtraAiOptionsForNode(node, optimalNextId)
                                                    isGeneratingMoreOptions = false
                                                    res.onSuccess { newOpts ->
                                                        aiStore.addOptions(nodeKey, newOpts)
                                                        aiStoreVersion++
                                                        shuffleSeed++
                                                        lastChoiceOptimal = true
                                                        lastFeedback = "מודל ה-AI הוסיף ${newOpts.size} אפשרויות חדשות וערבב את הרשימה"
                                                    }.onFailure {
                                                        lastChoiceOptimal = false
                                                        lastFeedback = "שגיאת חיבור ל-AI המקוון. בדוק חיבור אינטרנט."
                                                    }
                                                }
                                            }
                                        },
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                        modifier = Modifier.height(32.dp),
                                        border = BorderStroke(1.dp, PurpleNeon)
                                    ) {
                                        if (isGeneratingMoreOptions) {
                                            CircularProgressIndicator(
                                                color = PurpleNeon,
                                                strokeWidth = 2.dp,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        } else {
                                            CanvaPlusIcon(tint = PurpleNeon, modifier = Modifier.size(13.dp))
                                            Spacer(modifier = Modifier.width(5.dp))
                                            Text("תשובות AI", fontSize = 11.sp, color = PurpleNeon)
                                        }
                                    }
                                }

                                for (option in shuffledOptions) {
                                    val isAlreadyMarkedWrong = wrongTextsInCurrentNode.contains(option.textEn)

                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(bottom = 11.dp)
                                            .border(
                                                width = 1.5.dp,
                                                color = if (isAlreadyMarkedWrong) ErrorCrimson else TealNeon.copy(alpha = 0.55f),
                                                shape = RoundedCornerShape(18.dp)
                                            )
                                            .clickable(enabled = !isAlreadyMarkedWrong) {
                                                onSpeakEnglish(option.textEn)
                                                lastFeedback = option.feedbackHe
                                                lastChoiceOptimal = option.isOptimal

                                                if (option.isOptimal) {
                                                    scorePoints += if (wrongTextsInCurrentNode.isEmpty()) 25 else 10
                                                    stepNumber++
                                                    currentNodeId = option.nextNodeId
                                                } else {
                                                    val updatedSet = HashSet(wrongTextsInCurrentNode)
                                                    updatedSet.add(option.textEn)
                                                    wrongTextsInCurrentNode = updatedSet
                                                    livesRemaining--
                                                    shuffleSeed++
                                                    triggerErrorShake()
                                                }
                                            },
                                        shape = RoundedCornerShape(18.dp),
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (isAlreadyMarkedWrong) Color(0xFF2B121B) else Color(0xFF182038)
                                        )
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(15.dp).fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                                    Text(
                                                        text = option.textEn,
                                                        fontSize = 16.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = if (isAlreadyMarkedWrong) ErrorCrimson.copy(alpha = 0.7f) else TealNeon,
                                                        modifier = Modifier.fillMaxWidth()
                                                    )
                                                }
                                                if (showHebrewTranslation && option.textHe.isNotBlank()) {
                                                    Spacer(modifier = Modifier.height(6.dp))
                                                    Text(
                                                        text = "תרגום: ${option.textHe}",
                                                        fontSize = 14.sp,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = StarGold
                                                    )
                                                }
                                            }

                                            if (isAlreadyMarkedWrong) {
                                                Spacer(modifier = Modifier.width(8.dp))
                                                CanvaCloseIcon(tint = ErrorCrimson, modifier = Modifier.size(20.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (showAiGeneratorDialog) {
            val quickTopics = listOf(
                "בדיקה ביטחונית ותשאול מעמיק בשדה תעופה",
                "ראיון עבודה לחברת סייבר והייטק",
                "הזמנת אוכל במסעדת שף והחזרת מנה",
                "התמקחות על שכירת דירה בלונדון",
                "פנייה לרופא שיניים בחירום בחו״ל",
                "פתיחת חשבון בנק והנפקת כרטיס אשראי"
            )
            val difficultyOptions = listOf("בינוני", "בינוני-מתקדם", "מתקדם מאוד")

            AlertDialog(
                onDismissRequest = { if (!isGeneratingScenario) showAiGeneratorDialog = false },
                containerColor = CardDark,
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CanvaSparkleIcon(tint = TealNeon, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "מחולל שיחות AI מקוון",
                            color = TealNeon,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 20.sp
                        )
                    }
                },
                text = {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "הקלד כל סיטואציה שתרצה לתרגל, או בחר נושא מהיר. מודל ה-AI יבנה שיחה ארוכה בת 6 שלבים עם תשובה נכונה, תרגום עברי מלא ומלכודות דקדוק:",
                            color = Color.LightGray,
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedTextField(
                            value = aiTopicInput,
                            onValueChange = { newValue -> aiTopicInput = newValue },
                            placeholder = { Text("למשל: החלפת מוצר פגום בחנות בארה״ב...", color = Color.Gray) },
                            singleLine = false,
                            maxLines = 2,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = TealNeon,
                                unfocusedBorderColor = Color.Gray
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(8.dp))
                        Text("או לחץ על נושא מהיר:", color = StarGold, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(6.dp))

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                        ) {
                            for (topic in quickTopics) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(TealDark)
                                        .border(1.dp, TealNeon.copy(0.5f), RoundedCornerShape(10.dp))
                                        .clickable { aiTopicInput = topic }
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Text(text = topic, color = Color.White, fontSize = 11.sp)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            for (diff in difficultyOptions) {
                                val selected = aiDifficultySelected == diff
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (selected) PurpleNeon.copy(0.25f) else DarkBg)
                                        .border(1.dp, if (selected) PurpleNeon else Color.Gray.copy(0.3f), RoundedCornerShape(10.dp))
                                        .clickable { aiDifficultySelected = diff }
                                        .padding(vertical = 6.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = diff,
                                        color = if (selected) PurpleNeon else Color.LightGray,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }

                        if (isGeneratingScenario) {
                            Spacer(modifier = Modifier.height(14.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                CircularProgressIndicator(color = TealNeon, modifier = Modifier.size(22.dp))
                                Spacer(modifier = Modifier.width(10.dp))
                                Text("מודל ה-AI בונה שיחה ארוכה ומתרגם לעברית...", color = TealNeon, fontSize = 13.sp)
                            }
                        }

                        if (aiStatusMessage.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(aiStatusMessage, color = ErrorCrimson, fontSize = 12.sp)
                        }
                    }
                },
                confirmButton = {
                    Button(
                        enabled = !isGeneratingScenario,
                        onClick = {
                            val chosenTopic = aiTopicInput.ifBlank { quickTopics.random() }
                            isGeneratingScenario = true
                            aiStatusMessage = ""
                            scope.launch {
                                val result = repository.generateOnlineAiScenario(chosenTopic, aiDifficultySelected)
                                isGeneratingScenario = false
                                result.onSuccess { newScenario ->
                                    val updatedList = ArrayList<ConversationScenario>()
                                    updatedList.add(newScenario)
                                    updatedList.addAll(allScenarios)
                                    allScenarios = updatedList
                                    showAiGeneratorDialog = false
                                    aiTopicInput = ""
                                    activeScenarioId = newScenario.id
                                    currentNodeId = "start"
                                    stepNumber = 1
                                    livesRemaining = 3
                                    scorePoints = 0
                                    lastFeedback = ""
                                    shuffleSeed++
                                }.onFailure { err ->
                                    aiStatusMessage = "שגיאה ביצירת השיחה (${err.localizedMessage ?: "Error"})."
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = TealDark)
                    ) {
                        Text("צור שיחה עכשיו", color = TealNeon, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(
                        enabled = !isGeneratingScenario,
                        onClick = { showAiGeneratorDialog = false }
                    ) {
                        Text("ביטול", color = Color.LightGray)
                    }
                }
            )
        }

        if (showApiKeyDialog) {
            var apiKeyInput by remember { mutableStateOf(repository.getCustomApiKey()) }
            AlertDialog(
                onDismissRequest = { showApiKeyDialog = false },
                containerColor = CardDark,
                title = {
                    Text("הגדרות מנוע AI מקוון", color = StarGold, fontWeight = FontWeight.Bold)
                },
                text = {
                    Column {
                        Text(
                            text = "האפליקציה מחוברת כברירת מחדל למנוע AI מקוון חינמי ללא צורך במפתח.\nאם תרצה לחבר מפתח אישי של Google Gemini (AIza...) או OpenRouter (sk-or...), תוכל להזין אותו כאן:",
                            color = Color.LightGray,
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedTextField(
                            value = apiKeyInput,
                            onValueChange = { newValue -> apiKeyInput = newValue },
                            placeholder = { Text("השאר ריק לשימוש במנוע החינמי המובנה", color = Color.Gray) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = StarGold,
                                unfocusedBorderColor = Color.Gray
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            repository.saveCustomApiKey(apiKeyInput)
                            showApiKeyDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = TealDark)
                    ) {
                        Text("שמור", color = TealNeon)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showApiKeyDialog = false }) {
                        Text("סגור", color = Color.LightGray)
                    }
                }
            )
        }
    }
}
