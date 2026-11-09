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
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ConversationScreen(
    scenarios: List<ConversationScenario>,
    repository: ConversationRepository,
    onOpenDrawer: () -> Unit,
    onSpeakEnglish: (String) -> Unit
) {
    var selectedCategory by remember { mutableStateOf("הכל") }
    var activeScenario by remember { mutableStateOf<ConversationScenario?>(null) }
    var currentNodeId by remember { mutableStateOf("start") }
    var showHebrewTranslation by remember { mutableStateOf(false) }
    var lastFeedback by remember { mutableStateOf<String?>(null) }
    var lastChoiceOptimal by remember { mutableStateOf(true) }
    var completionRefresh by remember { mutableIntStateOf(0) }

    val categories = remember(scenarios) {
        listOf("הכל") + scenarios.map { it.categoryHe }.distinct()
    }

    val filteredScenarios = remember(scenarios, selectedCategory) {
        if (selectedCategory == "הכל") scenarios
        else scenarios.filter { it.categoryHe == selectedCategory }
    }

    // הקראה אוטומטית של משפט הדובר בכל שלב חדש בשיחה
    LaunchedEffect(activeScenario?.id, currentNodeId) {
        val node = activeScenario?.nodes?.get(currentNodeId)
        if (node != null) {
            onSpeakEnglish(node.speakerLineEn)
            if (node.isEnding && activeScenario != null) {
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

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = if (activeScenario == null) "סימולציות שיחה באנגלית" else activeScenario!!.titleHe,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )
                    if (activeScenario != null) {
                        Text(
                            text = activeScenario!!.titleEn,
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

            Spacer(modifier = Modifier.height(14.dp))

            if (activeScenario == null) {
                // --- מסך בחירת תרחיש שיחה מתוך עשרות שיחות ---
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
                                            text = "רמה: ${scenario.difficultyHe}",
                                            fontSize = 11.sp,
                                            color = StarGold
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = scenario.titleHe,
                                        fontSize = 18.sp,
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
                // --- מסך ניהול השיחה החיה המבוססת בחירה ---
                val scenario = activeScenario!!
                val node = scenario.nodes[currentNodeId] ?: scenario.nodes.values.first()

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        // משוב על הבחירה הקודמת
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
                                        else StarGold.copy(alpha = 0.15f)
                                    )
                                    .border(
                                        1.dp,
                                        if (lastChoiceOptimal) CheckNeon else StarGold,
                                        RoundedCornerShape(14.dp)
                                    )
                                    .padding(12.dp)
                            ) {
                                Text(
                                    text = lastFeedback ?: "",
                                    fontSize = 13.sp,
                                    color = if (lastChoiceOptimal) CheckNeon else StarGold,
                                    fontWeight = FontWeight.SemiBold
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
                            Column(modifier = Modifier.padding(20.dp)) {
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
                                                text = if (showHebrewTranslation) "הסתר תרגום" else "הצג תרגום",
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

                                Spacer(modifier = Modifier.height(10.dp))

                                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                    Text(
                                        text = node.speakerLineEn,
                                        fontSize = 21.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color.White,
                                        lineHeight = 28.sp,
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
                                            fontSize = 16.sp,
                                            color = TealNeon.copy(alpha = 0.9f),
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        if (node.isEnding) {
                            // סיום מוצלח של השיחה
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(2.dp, CheckNeon, RoundedCornerShape(22.dp)),
                                shape = RoundedCornerShape(22.dp),
                                colors = CardDefaults.cardColors(containerColor = CardDark)
                            ) {
                                Column(
                                    modifier = Modifier.padding(20.dp).fillMaxWidth(),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text("🎉 השלמת את השיחה בהצלחה!", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = CheckNeon)
                                    Spacer(modifier = Modifier.height(14.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        Button(
                                            onClick = {
                                                currentNodeId = "start"
                                                lastFeedback = null
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = TealDark)
                                        ) {
                                            Icon(Icons.Default.Refresh, contentDescription = null, tint = TealNeon)
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("תרגל שוב", color = TealNeon)
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
                                text = "בחר את התגובה שלך באנגלית:",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.LightGray,
                                modifier = Modifier.padding(bottom = 10.dp)
                            )

                            node.options.forEach { option ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 12.dp)
                                        .border(1.5.dp, TealNeon.copy(alpha = 0.55f), RoundedCornerShape(18.dp))
                                        .clickable {
                                            onSpeakEnglish(option.textEn)
                                            lastFeedback = option.feedbackHe
                                            lastChoiceOptimal = option.isOptimal
                                            currentNodeId = option.nextNodeId
                                        },
                                    shape = RoundedCornerShape(18.dp),
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFF182038))
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                            Text(
                                                text = option.textEn,
                                                fontSize = 17.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = TealNeon,
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        }
                                        if (showHebrewTranslation) {
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Text(
                                                text = option.textHe,
                                                fontSize = 13.sp,
                                                color = Color.LightGray
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
