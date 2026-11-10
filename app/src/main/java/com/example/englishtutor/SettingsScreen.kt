package com.example.englishtutor

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    conversationRepo: ConversationRepository,
    wordRepo: WordRepository,
    speechManager: SpeechManager,
    haptic: HapticManager,
    onOpenDrawer: () -> Unit,
    onDeckGenerated: (List<WordCard>) -> Unit,
    onBackToMain: () -> Unit = onOpenDrawer
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
                        onBackToMain()
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
