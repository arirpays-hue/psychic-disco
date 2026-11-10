package com.example.englishtutor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class SkillNode(
    val levelCode: String,
    val titleHe: String,
    val descriptionHe: String,
    val requiredStars: Int,
    val isBossBattle: Boolean = false,
    val scenarioId: String? = null
)

@Composable
fun SkillTreeScreen(
    conversationRepo: ConversationRepository,
    haptic: HapticManager,
    onOpenDrawer: () -> Unit,
    onLaunchBossScenario: (String) -> Unit
) {
    val nodes = remember {
        listOf(
            SkillNode("A1", "יסודות השיחה: היכרות ויומיום", "שליטה בברכות, שאלות הכוונה ומסעדות בסיסיות", 0),
            SkillNode("A2", "נוסע עצמאי: שדה תעופה ותחבורה", "ביקורת דרכונים, רכישת כרטיסים ומציאת מקומות", 3),
            SkillNode("B1", "תקשורת שוטפת: קניות וחירום רפואי", "תיאור תסמינים במרפאה, רכישת מוצרים ואחריות", 7),
            SkillNode("B2", "בוס אמצע: משא ומתן על דירה בלונדון", "קרב שיחתי מול סוכן נדל״ן קשוח", 12, isBossBattle = true, scenarioId = "apartment_rental_viewing"),
            SkillNode("C1", "עולם העסקים: ראיון עבודה להייטק", "הסבר על ארכיטקטורת תוכנה, קורוטינות ו-CI/CD", 18),
            SkillNode("C2", "בוס עליון: מנהל משא ומתן גלובלי", "שיחה עסקית חופשית ברמת שליטה מוחלטת", 25, isBossBattle = true, scenarioId = "tech_interview_deep_dive")
        )
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(DarkBg, Color(0xFF10172A), DarkBg)))
                .padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.clickable { 
                    haptic.click()
                    onOpenDrawer() 
                }) {
                    CanvaIconBadge(accentColor = TealNeon, badgeSize = 44.dp) {
                        CanvaMenuIcon(tint = TealNeon)
                    }
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("מסלול שלבים ועץ מיומנויות", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                    Text("פתיחת בוסים והתקדמות לפי רמות CEFR", color = TealNeon, fontSize = 12.sp)
                }

                CanvaIconBadge(accentColor = StarGold, badgeSize = 44.dp) {
                    CanvaStarIcon(tint = StarGold, isFilled = true, modifier = Modifier.size(22.dp))
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                itemsIndexed(nodes) { index, node ->
                    val isUnlocked = true // פתוח לתרגול חופשי
                    val accent = if (node.isBossBattle) StarGold else if (index % 2 == 0) TealNeon else PurpleNeon

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                width = if (node.isBossBattle) 2.5.dp else 1.5.dp,
                                color = accent,
                                shape = RoundedCornerShape(22.dp)
                            )
                            .clickable {
                                haptic.click()
                                if (node.isBossBattle && node.scenarioId != null) {
                                    onLaunchBossScenario(node.scenarioId)
                                }
                            },
                        shape = RoundedCornerShape(22.dp),
                        colors = CardDefaults.cardColors(containerColor = if (node.isBossBattle) Color(0xFF241C10) else CardDark)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(18.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(accent.copy(alpha = 0.2f))
                                            .padding(horizontal = 8.dp, vertical = 3.dp)
                                    ) {
                                        Text(
                                            text = if (node.isBossBattle) "קרב בוס" else node.levelCode,
                                            color = accent,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(node.titleHe, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(node.descriptionHe, color = Color.LightGray, fontSize = 13.sp)
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            CanvaIconBadge(accentColor = accent, badgeSize = 44.dp, cornerRadius = 22.dp) {
                                if (node.isBossBattle) {
                                    CanvaBoltIcon(tint = StarGold, modifier = Modifier.size(24.dp))
                                } else {
                                    CanvaCheckIcon(tint = accent, modifier = Modifier.size(20.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
