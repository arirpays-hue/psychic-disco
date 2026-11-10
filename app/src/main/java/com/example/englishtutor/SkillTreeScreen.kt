package com.example.englishtutor

import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

@Composable
fun SkillTreeScreen(
    words: List<WordCard>,
    progressionManager: LevelProgressionManager,
    missionManager: DailyMissionManager,
    haptic: HapticManager,
    onOpenDrawer: () -> Unit,
    onStartLevelPractice: (CefrLevel) -> Unit,
    onLaunchScenarioCategory: (String) -> Unit
) {
    val context = LocalContext.current
    val availablePoints = progressionManager.totalPoints
    val treeVersion = progressionManager.treeVersion
    val missionVersion = missionManager.missionVersion

    val todayMissions = remember(missionVersion) {
        missionManager.getTodayMissions()
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(DarkBg, Color(0xFF10172A), DarkBg)))
                .padding(horizontal = 18.dp, vertical = 14.dp)
        ) {
            // סרגל עליון עם תצוגת נקודות היכולת (ללא כפתורי דמה שלא מובילים לכלום!)
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
                        text = "עץ היכולות והמשימות היומיות",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = "פתח שורשים כדי לפתוח ענפים, מילים ושיחות חדשות",
                        color = TealNeon,
                        fontSize = 11.sp
                    )
                }

                // תג נקודות יכולת חי
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF241D0C))
                        .border(1.5.dp, StarGold, RoundedCornerShape(14.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    CanvaStarIcon(tint = StarGold, isFilled = true, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = "$availablePoints נק'",
                        color = StarGold,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 28.dp)
            ) {
                // --- חלק 1: כרטיס משימות יומיות פרוצדורליות (מועשרות ב-AI ברקע) ---
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 18.dp)
                            .border(2.dp, PurpleNeon.copy(alpha = 0.75f), RoundedCornerShape(22.dp)),
                        shape = RoundedCornerShape(22.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF17132B))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CanvaIconBadge(accentColor = PurpleNeon, badgeSize = 36.dp, cornerRadius = 10.dp) {
                                        CanvaSparkleIcon(tint = PurpleNeon, modifier = Modifier.size(18.dp))
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = "משימות יומיות (AI + פרוצדורלי)",
                                            color = Color.White,
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.ExtraBold
                                        )
                                        Text(
                                            text = "השלם משימות כדי לצבור נקודות לפתיחת העץ",
                                            color = PurpleNeon,
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            todayMissions.forEach { mission ->
                                val progress = (mission.currentCount.toFloat() / mission.targetCount.coerceAtLeast(1)).coerceIn(0f, 1f)
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(if (mission.isCompleted) Color(0xFF0E3228) else CardDark)
                                        .border(
                                            1.dp,
                                            if (mission.isCompleted) CheckNeon else TealNeon.copy(alpha = 0.35f),
                                            RoundedCornerShape(14.dp)
                                        )
                                        .padding(12.dp)
                                ) {
                                    Column {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                if (mission.isCompleted) {
                                                    CanvaCheckIcon(tint = CheckNeon, modifier = Modifier.size(18.dp))
                                                } else {
                                                    CanvaBoltIcon(tint = StarGold, modifier = Modifier.size(16.dp))
                                                }
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = mission.titleHe,
                                                    color = if (mission.isCompleted) CheckNeon else Color.White,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    fontSize = 14.sp
                                                )
                                            }
                                            Text(
                                                text = if (mission.isCompleted) "הושלם! (+${mission.rewardPoints})" else "+${mission.rewardPoints} נק'",
                                                color = StarGold,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "${mission.descriptionHe} (${mission.currentCount}/${mission.targetCount})",
                                            color = Color.LightGray,
                                            fontSize = 12.sp
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        LinearProgressIndicator(
                                            progress = { progress },
                                            modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)),
                                            color = if (mission.isCompleted) CheckNeon else TealNeon,
                                            trackColor = DarkBg
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // --- חלק 2: שורשים וענפים של עץ היכולות ---
                itemsIndexed(progressionManager.allTreeNodes, key = { _, n -> n.id }) { index, node ->
                    val isUnlocked = remember(treeVersion, node.id) { progressionManager.isNodeUnlocked(node.id) }
                    val canUnlock = remember(treeVersion, availablePoints, node.id) { progressionManager.canUnlockNode(node) }
                    val parentUnlocked = remember(treeVersion, node.parentId) {
                        node.parentId == null || progressionManager.isNodeUnlocked(node.parentId)
                    }

                    val levelWords = remember(words, node.level) {
                        words.filter { it.cefr.equals(node.level.name, ignoreCase = true) }
                    }
                    val learnedInLevel = levelWords.count { it.isLearned }

                    val nodeColor = when {
                        isUnlocked && node.isRoot -> TealNeon
                        isUnlocked && !node.isRoot -> CheckNeon
                        canUnlock -> StarGold
                        else -> Color(0xFF4A5572)
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = if (node.isRoot) 0.dp else 28.dp) // הזחה לענפים שמתפצלים מהשורש!
                    ) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(
                                    width = if (node.isRoot) 2.2.dp else 1.5.dp,
                                    color = nodeColor,
                                    shape = RoundedCornerShape(if (node.isRoot) 22.dp else 18.dp)
                                )
                                .clickable {
                                    when {
                                        isUnlocked -> {
                                            haptic.click()
                                            if (node.unlocksScenarioCategory != null) {
                                                onLaunchScenarioCategory(node.unlocksScenarioCategory)
                                            } else {
                                                onStartLevelPractice(node.level)
                                            }
                                        }
                                        canUnlock -> {
                                            val success = progressionManager.unlockNode(node)
                                            if (success) {
                                                haptic.success()
                                                Toast.makeText(
                                                    context,
                                                    "נפתחה יכולת חדשה: ${node.titleHe}!",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                        }
                                        !parentUnlocked -> {
                                            haptic.error()
                                            Toast.makeText(
                                                context,
                                                "יש לפתוח קודם את השורש הקודם בעץ!",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                        else -> {
                                            haptic.error()
                                            Toast.makeText(
                                                context,
                                                "חסרות לך ${node.costPoints - availablePoints} נקודות לפתיחת יכולת זו",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    }
                                },
                            shape = RoundedCornerShape(if (node.isRoot) 22.dp else 18.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = when {
                                    isUnlocked -> CardDark
                                    canUnlock -> Color(0xFF231D10)
                                    else -> Color(0xFF0E1320)
                                }
                            )
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
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
                                            accentColor = nodeColor,
                                            badgeSize = if (node.isRoot) 48.dp else 40.dp,
                                            cornerRadius = 14.dp,
                                            isActive = isUnlocked || canUnlock
                                        ) {
                                            CanvaAbilityNodeIcon(
                                                type = node.iconType,
                                                tint = nodeColor,
                                                modifier = Modifier.size(22.dp)
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(12.dp))

                                        Column {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(6.dp))
                                                        .background(nodeColor.copy(alpha = 0.2f))
                                                        .padding(horizontal = 7.dp, vertical = 2.dp)
                                                ) {
                                                    Text(
                                                        text = if (node.isRoot) "שורש מרכזי • ${node.level.name}" else "ענף יכולת • ${node.level.name}",
                                                        color = nodeColor,
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.ExtraBold
                                                    )
                                                }
                                                if (node.isRoot) {
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text(
                                                        text = "$learnedInLevel/${levelWords.size} מילים נלמדו",
                                                        color = Color.LightGray,
                                                        fontSize = 11.sp
                                                    )
                                                }
                                            }

                                            Spacer(modifier = Modifier.height(5.dp))
                                            Text(
                                                text = node.titleHe,
                                                color = if (isUnlocked || canUnlock) Color.White else Color.Gray,
                                                fontSize = if (node.isRoot) 16.sp else 15.sp,
                                                fontWeight = FontWeight.ExtraBold
                                            )
                                            Text(
                                                text = node.subtitleHe,
                                                color = Color.LightGray,
                                                fontSize = 12.sp
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(8.dp))

                                    // תג מצב: פתוח לאימון / כפתור פתיחה בנקודות / נעול
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(
                                                when {
                                                    isUnlocked -> TealDark
                                                    canUnlock -> StarGold
                                                    else -> Color(0xFF1A2133)
                                                }
                                            )
                                            .padding(horizontal = 10.dp, vertical = 7.dp)
                                    ) {
                                        Text(
                                            text = when {
                                                isUnlocked -> "התאמן כעת"
                                                canUnlock -> "פתח (${node.costPoints})"
                                                else -> "${node.costPoints} נק'"
                                            },
                                            color = when {
                                                isUnlocked -> TealNeon
                                                canUnlock -> DarkBg
                                                else -> Color.Gray
                                            },
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.ExtraBold
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "בונוס פתיחה: ${node.unlockRewardHe}",
                                    color = if (isUnlocked) CheckNeon else StarGold.copy(alpha = 0.85f),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        // קו חיבור אנכי של עץ היכולות
                        if (index < progressionManager.allTreeNodes.size - 1) {
                            Canvas(
                                modifier = Modifier
                                    .width(4.dp)
                                    .height(22.dp)
                            ) {
                                drawLine(
                                    brush = Brush.verticalGradient(listOf(nodeColor, TealNeon.copy(alpha = 0.4f))),
                                    start = Offset(size.width / 2f, 0f),
                                    end = Offset(size.width / 2f, size.height),
                                    strokeWidth = 3.5.dp.toPx(),
                                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 6f), 0f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CanvaAbilityNodeIcon(
    type: AbilityIconType,
    tint: Color,
    modifier: Modifier = Modifier
) {
    when (type) {
        AbilityIconType.ROOT_SEED -> CanvaSkillTreeIcon(tint = tint, modifier = modifier)
        AbilityIconType.VOCAB_EXPANSION -> CanvaAiDeckIcon(tint = tint, modifier = modifier)
        AbilityIconType.DIALOGUE_BRANCH -> CanvaSpeakerBubbleIcon(tint = tint, modifier = modifier)
        AbilityIconType.DIFFICULTY_BOOST -> CanvaBoltIcon(tint = tint, modifier = modifier)
        AbilityIconType.SPEED_MASTERY -> CanvaFlameIcon(tint = tint, modifier = modifier)
        AbilityIconType.BOSS_CROWN -> CanvaBossCrownIcon(tint = tint, modifier = modifier)
    }
}
