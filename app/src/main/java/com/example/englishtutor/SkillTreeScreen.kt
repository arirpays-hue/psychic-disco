package com.example.englishtutor

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class SkillNode(
    val level: CefrLevel,
    val titleHe: String,
    val descriptionHe: String,
    val isBossBattle: Boolean = false,
    val bossScenarioId: String? = null
)

@Composable
fun SkillTreeScreen(
    words: List<WordCard>,
    haptic: HapticManager,
    onOpenDrawer: () -> Unit,
    onStartLevelPractice: (CefrLevel) -> Unit,
    onLaunchBossScenario: (String) -> Unit
) {
    val totalLearned = remember(words) { words.count { it.isLearned } }

    val nodes = remember {
        listOf(
            SkillNode(
                level = CefrLevel.A1,
                titleHe = "שלב 1: יסודות השפה (A1)",
                descriptionHe = "מילים בסיסיות, היכרות, ברכות והתמצאות ראשונית"
            ),
            SkillNode(
                level = CefrLevel.A2,
                titleHe = "שלב 2: נוסע עצמאי (A2)",
                descriptionHe = "שדה תעופה, תחבורה, קניות ושיחות חולין"
            ),
            SkillNode(
                level = CefrLevel.B1,
                titleHe = "בוס ביניים: מסעדה ומלון בחו״ל",
                descriptionHe = "פתרון תקלות בזמן אמת מול צוות המלון והמסעדה",
                isBossBattle = true,
                bossScenarioId = "restaurant_full_evening"
            ),
            SkillNode(
                level = CefrLevel.B2,
                titleHe = "שלב 4: אנגלית עסקית וטכנולוגיה (B2)",
                descriptionHe = "אוצר מילים מתקדם לעבודה, משא ומתן וטכנולוגיה"
            ),
            SkillNode(
                level = CefrLevel.C1,
                titleHe = "שלב 5: שליטה אקדמית וניבים (C1)",
                descriptionHe = "מילים מורכבות, ביטויים מעמיקים ודיוק לשוני"
            ),
            SkillNode(
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
            // סרגל עליון
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
                        text = "סה״כ מילים שנלמדו: $totalLearned • לחץ על שלב לאימון ממוקד",
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
                        // כרטיס שלב בעץ היכולות
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

                        // צינור אנרגיה אנכי שמחבר בין שלבי העץ
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
