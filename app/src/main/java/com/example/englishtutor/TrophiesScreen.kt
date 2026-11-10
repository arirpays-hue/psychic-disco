package com.example.englishtutor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun TrophiesScreen(
    progressionManager: LevelProgressionManager,
    missionManager: DailyMissionManager,
    wordRepo: WordRepository,
    haptic: HapticManager,
    onOpenDrawer: () -> Unit
) {
    val speedHigh = wordRepo.getSpeedHighScore()
    val missionsCompleted = missionManager.getTotalCompletedMissionsEver()
    val highestRecord = progressionManager.highestPointsRecord
    val currentPoints = progressionManager.totalPoints

    val unlockedCount = progressionManager.allTrophies.count {
        progressionManager.isTrophyUnlocked(it, speedHigh, missionsCompleted)
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(DarkBg, Color(0xFF10172A), DarkBg)))
                .padding(horizontal = 18.dp, vertical = 14.dp)
        ) {
            // סרגל עליון נקי ללא כפתורים מיותרים
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
                        text = "היכל הגביעים ושבירת השיאים",
                        color = Color.White,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = "שיא נקודות מצטבר: $highestRecord • נפתחו: $unlockedCount/${progressionManager.allTrophies.size}",
                        color = StarGold,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.size(44.dp))
            }

            Spacer(modifier = Modifier.height(14.dp))

            // כרטיס סיכום שיאים
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.5.dp, StarGold.copy(alpha = 0.7f), RoundedCornerShape(20.dp)),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = CardDark)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("$highestRecord", color = StarGold, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                        Text("שיא נקודות", color = Color.LightGray, fontSize = 11.sp)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("$currentPoints", color = TealNeon, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                        Text("נקודות זמינות לעץ", color = Color.LightGray, fontSize = 11.sp)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("$speedHigh", color = CheckNeon, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                        Text("שיא 60 שניות", color = Color.LightGray, fontSize = 11.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(progressionManager.allTrophies) { trophy ->
                    val unlocked = progressionManager.isTrophyUnlocked(trophy, speedHigh, missionsCompleted)
                    val accent = if (unlocked) StarGold else Color(0xFF45506B)

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                width = if (unlocked) 2.dp else 1.dp,
                                color = if (unlocked) StarGold else Color.Gray.copy(alpha = 0.25f),
                                shape = RoundedCornerShape(22.dp)
                            ),
                        shape = RoundedCornerShape(22.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (unlocked) Color(0xFF1E1910) else CardDark.copy(alpha = 0.65f)
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CanvaIconBadge(
                                accentColor = if (unlocked) StarGold else Color.Gray,
                                badgeSize = 62.dp,
                                cornerRadius = 20.dp,
                                isActive = unlocked
                            ) {
                                CanvaCustomTrophyArt(
                                    type = trophy.visualType,
                                    isUnlocked = unlocked,
                                    modifier = Modifier.size(36.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Text(
                                text = trophy.titleHe,
                                color = if (unlocked) Color.White else Color.Gray,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.ExtraBold,
                                textAlign = TextAlign.Center
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            Text(
                                text = trophy.subtitleHe,
                                color = if (unlocked) Color.LightGray else Color.DarkGray,
                                fontSize = 11.sp,
                                textAlign = TextAlign.Center,
                                lineHeight = 15.sp
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (unlocked) CheckNeon.copy(alpha = 0.18f) else Color(0xFF141A29))
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = if (unlocked) "הושג!" else "יעד: ${trophy.requiredPoints} נק'",
                                    color = if (unlocked) CheckNeon else accent,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// סמל קנבה מותאם אישית לכל גביע ושיא
@Composable
fun CanvaCustomTrophyArt(
    type: TrophyVisualType,
    isUnlocked: Boolean,
    modifier: Modifier = Modifier
) {
    val mainColor = if (isUnlocked) StarGold else Color(0xFF525E7A)
    val secondary = if (isUnlocked) TealNeon else Color(0xFF3A445E)

    when (type) {
        TrophyVisualType.FIRST_SPARK -> CanvaSparkleIcon(tint = mainColor, modifier = modifier)
        TrophyVisualType.SPEED_LIGHTNING -> CanvaBoltIcon(tint = mainColor, modifier = modifier)
        TrophyVisualType.GOLD_CROWN -> CanvaBossCrownIcon(tint = mainColor, modifier = modifier)
        TrophyVisualType.DIAMOND_LEGEND -> CanvaDiamondIcon(tint = if (isUnlocked) TealNeon else mainColor, modifier = modifier)
        TrophyVisualType.SILVER_STAR -> CanvaStarIcon(tint = if (isUnlocked) PurpleNeon else mainColor, isFilled = isUnlocked, modifier = modifier)
        TrophyVisualType.TREE_CONQUEROR -> CanvaSkillTreeIcon(tint = secondary, modifier = modifier)
        TrophyVisualType.BRONZE_SHIELD, TrophyVisualType.MISSION_CHAMPION -> {
            Canvas(modifier = modifier) {
                val w = size.width
                val h = size.height
                val shield = Path().apply {
                    moveTo(w * 0.5f, h * 0.08f)
                    lineTo(w * 0.86f, h * 0.22f)
                    lineTo(w * 0.80f, h * 0.62f)
                    quadraticBezierTo(w * 0.5f, h * 0.92f, w * 0.5f, h * 0.92f)
                    quadraticBezierTo(w * 0.5f, h * 0.92f, w * 0.20f, h * 0.62f)
                    lineTo(w * 0.14f, h * 0.22f)
                    close()
                }
                if (isUnlocked) {
                    drawPath(shield, color = mainColor.copy(alpha = 0.28f))
                }
                drawPath(shield, color = mainColor, style = Stroke(width = w * 0.09f, join = StrokeJoin.Round))
                drawLine(secondary, Offset(w * 0.34f, h * 0.50f), Offset(w * 0.46f, h * 0.64f), w * 0.09f, StrokeCap.Round)
                drawLine(secondary, Offset(w * 0.46f, h * 0.64f), Offset(w * 0.68f, h * 0.36f), w * 0.09f, StrokeCap.Round)
            }
        }
    }
}
