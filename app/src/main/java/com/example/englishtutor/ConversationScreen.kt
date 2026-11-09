package com.example.englishtutor

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
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

@Composable
fun ConversationScreen(
    scenarios: List<ConversationScenario>,
    repository: ConversationRepository,
    onOpenDrawer: () -> Unit,
    onSpeakEnglish: (String) -> Unit
) {
    val scope = rememberCoroutineScope()

    var selectedCategory by remember { mutableStateOf("הכל") }
    var activeScenario by remember { mutableStateOf<ConversationScenario?>(null) }
    var currentNodeId by remember { mutableStateOf("start") }
    var stepNumber by remember { mutableIntStateOf(1) }

    var livesRemaining by remember { mutableIntStateOf(3) }
    var scorePoints by remember { mutableIntStateOf(0) }
    var wrongIndicesInCurrentNode by remember { mutableStateOf(setOf<Int>()) }

    var showHebrewTranslation by remember { mutableStateOf(false) }
    var lastFeedback by remember { mutableStateOf<String?>(null) }
    var lastChoiceOptimal by remember { mutableStateOf(true) }
    var completionRefresh by remember { mutableIntStateOf(0) }

    val shakeOffsetX = remember { Animatable(0f) }

    fun triggerErrorShake() {
        scope.launch {
            for (i in 0..5) {
                shakeOffsetX.animateTo(if (i % 2 == 0) 18f else -18f, tween(40))
            }
            shakeOffsetX.animateTo(0f, spring())
        }
    }

    val categories = remember(scenarios) {
        listOf("הכל") + scenarios.map { it.categoryHe }.distinct()
    }

    val filteredScenarios = remember(scenarios, selectedCategory) {
        if (selectedCategory == "הכל") scenarios
        else scenarios.filter { it.categoryHe == selectedCategory }
    }

    // הקראה אוטומטית של משפט הדובר בכל שלב חדש
    LaunchedEffect(activeScenario?.id, currentNodeId) {
        wrongIndicesInCurrentNode = emptySet()
        val node = activeScenario?.nodes?.get(currentNodeId)
        if (node != null) {
            onSpeakEnglish(node.speakerLineEn)
            if (node.isEnding && activeScenario != null && livesRemaining > 0) {
                repository.markScenarioCompleted(activeScenario!!.id)
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
            // סרגל עליון
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onOpenDrawer,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(CardDark)
                        .border(1.5.dp, TealNeon.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                ) {
                    Icon(Icons.Default.Menu, contentDescription = "תפריט", tint = TealNeon)
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f).padding(horizontal = 8.dp)) {
                    Text(
                        text = if (activeScenario == null) "סימולציות שיחה (Roleplay)" else activeScenario!!.titleHe,
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
                    IconButton(
                        onClick = {
                            activeScenario = null
                            lastFeedback = null
                        },
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(CardDark)
                            .border(1.dp, PurpleNeon, RoundedCornerShape(12.dp))
                    ) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "חזור לרשימה", tint = PurpleNeon)
                    }
                } else {
                    Spacer(modifier = Modifier.size(44.dp))
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (activeScenario == null) {
                // --- מסך בחירת תרחיש שיחה ---
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(categories) { category ->
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

                Spacer(modifier = Modifier.height(14.dp))

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(filteredScenarios, key = { it.id }) { scenario ->
                        val isDone = remember(completionRefresh, scenario.id) {
                            repository.isScenarioCompleted(scenario.id)
                        }
                        val stagesCount = (scenario.nodes.size - 1).coerceAtLeast(1)

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(
                                    1.5.dp,
                                    if (isDone) CheckNeon else TealNeon.copy(alpha = 0.45f),
                                    RoundedCornerShape(20.dp)
                                )
                                .clickable {
                                    activeScenario = scenario
                                    currentNodeId = "start"
                                    stepNumber = 1
                                    livesRemaining = 3
                                    scorePoints = 0
                                    lastFeedback = null
                                    showHebrewTranslation = false
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
                                            color = PurpleNeon,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(PurpleNeon.copy(alpha = 0.15f))
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

                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(if (isDone) CheckNeon.copy(0.2f) else TealDark),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isDone) {
                                        Icon(Icons.Default.Check, contentDescription = "הושלם", tint = CheckNeon)
                                    } else {
                                        Text("▶", color = TealNeon, fontSize = 16.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // --- מסך ניהול השיחה החיה ---
                val scenario = activeScenario!!
                val node = scenario.nodes[currentNodeId] ?: scenario.nodes.values.first()
                val totalStages = (scenario.nodes.size - 1).coerceAtLeast(1)

                // מערבב את התשובות אקראית בכל שלב כדי שהתשובה הנכונה לא תהיה תמיד באותו מקום!
                val shuffledOptions = remember(scenario.id, currentNodeId) {
                    node.options.shuffled()
                }

                // שורת לבבות (חיים) ומד התקדמות בשיחה
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        for (i in 1..3) {
                            Text(
                                text = if (i <= livesRemaining) "❤️" else "🖤",
                                fontSize = 18.sp
                            )
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
                    // --- מסך כישלון בשיחה (Game Over) כשנגמרו 3 הלבבות ---
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
                                Text("💔 השיחה הסתיימה ללא הצלחה", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = ErrorCrimson)
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = lastFeedback ?: "בצעת 3 טעויות בשיחה זו. קרא את ההסברים ונסה שוב מהתחלה!",
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
                                        lastFeedback = null
                                        wrongIndicesInCurrentNode = emptySet()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = TealDark),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = null, tint = TealNeon)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("התחל את השיחה מחדש", color = TealNeon, fontWeight = FontWeight.Bold)
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
                            // משוב מפורט על הבחירה האחרונה (ירוק לתשובה נכונה, אדום לשגיאה)
                            AnimatedVisibility(
                                visible = lastFeedback != null,
                                enter = expandVertically() + fadeIn(),
                                exit = shrinkVertically() + fadeOut()
                            ) {
                                Box(
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
                                        .padding(12.dp)
                                ) {
                                    Text(
                                        text = lastFeedback ?: "",
                                        fontSize = 13.sp,
                                        color = if (lastChoiceOptimal) CheckNeon else ErrorCrimson,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            // בועת הדיבור של הדמות מולה משוחחים
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(2.dp, TealNeon, RoundedCornerShape(24.dp)),
                                shape = RoundedCornerShape(24.dp),
                                colors = CardDefaults.cardColors(containerColor = CardDark)
                            ) {
                                Column(modifier = Modifier.padding(18.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "🗣️ ${node.speakerName}",
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = PurpleNeon
                                        )

                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            TextButton(
                                                onClick = { showHebrewTranslation = !showHebrewTranslation }
                                            ) {
                                                Text(
                                                    text = if (showHebrewTranslation) "הסתר תרגום" else "רמז (תרגום)",
                                                    color = StarGold,
                                                    fontSize = 12.sp
                                                )
                                            }

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

                                    Spacer(modifier = Modifier.height(8.dp))

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

                                    AnimatedVisibility(visible = showHebrewTranslation) {
                                        Column {
                                            Spacer(modifier = Modifier.height(10.dp))
                                            HorizontalDivider(color = Color.Gray.copy(alpha = 0.3f))
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text(
                                                text = node.speakerLineHe,
                                                fontSize = 15.sp,
                                                color = TealNeon.copy(alpha = 0.9f),
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(18.dp))

                            if (node.isEnding) {
                                // סיום מוצלח של השיחה הארוכה
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
                                        Text("🏆 השלמת את כל שלבי השיחה!", fontSize = 21.sp, fontWeight = FontWeight.ExtraBold, color = CheckNeon)
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text("ניקוד סופי: $scorePoints • לבבות שנותרו: $livesRemaining/3", fontSize = 14.sp, color = StarGold)
                                        Spacer(modifier = Modifier.height(16.dp))
                                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                            Button(
                                                onClick = {
                                                    currentNodeId = "start"
                                                    stepNumber = 1
                                                    livesRemaining = 3
                                                    scorePoints = 0
                                                    lastFeedback = null
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = TealDark)
                                            ) {
                                                Icon(Icons.Default.Refresh, contentDescription = null, tint = TealNeon)
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("שחק שוב", color = TealNeon)
                                            }
                                            Button(
                                                onClick = {
                                                    activeScenario = null
                                                    lastFeedback = null
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = PurpleNeon.copy(alpha = 0.25f))
                                            ) {
                                                Text("לכל השיחות", color = Color.White)
                                            }
                                        }
                                    }
                                }
                            } else {
                                Text(
                                    text = "בחר את התגובה הנכונה באנגלית (רק תשובה אחת מתאימה!):",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.LightGray,
                                    modifier = Modifier.padding(bottom = 10.dp)
                                )

                                shuffledOptions.forEachIndexed { index, option ->
                                    val isAlreadyMarkedWrong = wrongIndicesInCurrentNode.contains(index)

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
                                                    // תשובה נכונה! מתקדמים לשלב הבא בשיחה
                                                    scorePoints += if (wrongIndicesInCurrentNode.isEmpty()) 20 else 10
                                                    stepNumber++
                                                    currentNodeId = option.nextNodeId
                                                } else {
                                                    // תשובה שגויה! יורד לב, הכרטיס רועד, ונשארים באותו שלב לניסיון נוסף
                                                    wrongIndicesInCurrentNode = wrongIndicesInCurrentNode + index
                                                    livesRemaining--
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
                                                if (showHebrewTranslation) {
                                                    Spacer(modifier = Modifier.height(5.dp))
                                                    Text(
                                                        text = option.textHe,
                                                        fontSize = 13.sp,
                                                        color = Color.LightGray
                                                    )
                                                }
                                            }

                                            if (isAlreadyMarkedWrong) {
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Icon(
                                                    imageVector = Icons.Default.Close,
                                                    contentDescription = "שגוי",
                                                    tint = ErrorCrimson
                                                )
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
    }
}
