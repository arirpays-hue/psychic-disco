package com.example.englishtutor

import android.widget.Toast
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
    onDeckGenerated: (List<WordCard>) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var apiKeyInput by remember { mutableStateOf(conversationRepo.getCustomApiKey()) }
    var speechRateSlider by remember { mutableFloatStateOf(speechManager.speechRate) }
    var speechPitchSlider by remember { mutableFloatStateOf(speechManager.speechPitch) }
    var hapticsEnabled by remember { mutableStateOf(haptic.isHapticsEnabled) }

    // יצירת חפיסת AI מותאמת
    var customAiTopicInput by remember { mutableStateOf("") }
    var isGeneratingDeck by remember { mutableStateOf(false) }

    // ייבוא מילים
    var csvImportInput by remember { mutableStateOf("") }
    var showImportDialog by remember { mutableStateOf(false) }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(DarkBg, Color(0xFF10172A), DarkBg)))
                .padding(20.dp)
                .verticalScroll(rememberScrollState())
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
                Text("הגדרות והתאמה אישית", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                CanvaIconBadge(accentColor = StarGold, badgeSize = 44.dp) {
                    CanvaSettingsIcon(tint = StarGold, modifier = Modifier.size(22.dp))
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // --- 1. מחולל חפיסות מילים ב-AI ---
            Text("מחולל כרטיסיות מותאם אישית (AI Deck)", color = TealNeon, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = customAiTopicInput,
                onValueChange = { customAiTopicInput = it },
                placeholder = { Text("הקלד נושא: למשל 'פיתוח אנדרואיד', 'קולינריה איטלקית'...") },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                enabled = !isGeneratingDeck && customAiTopicInput.isNotBlank(),
                onClick = {
                    haptic.click()
                    isGeneratingDeck = true
                    scope.launch {
                        val newDeck = wordRepo.generateCustomAiDeck(customAiTopicInput)
                        isGeneratingDeck = false
                        if (newDeck.isNotEmpty()) {
                            haptic.success()
                            Toast.makeText(context, "נוצרו ${newDeck.size} כרטיסיות חדשות בהצלחה!", Toast.LENGTH_SHORT).show()
                            onDeckGenerated(newDeck)
                            customAiTopicInput = ""
                        } else {
                            haptic.error()
                            Toast.makeText(context, "שגיאה ביצירת החפיסה. נסה שוב.", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = TealDark)
            ) {
                if (isGeneratingDeck) {
                    CircularProgressIndicator(color = TealNeon, modifier = Modifier.size(20.dp))
                } else {
                    Text("צור חפיסה חדשה עכשיו", color = TealNeon, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // --- 2. בקרת שמע ורטט (Haptics & TTS) ---
            Text("הגדרות שמע ורטט (Audio & Haptics)", color = StarGold, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("רטט פידבק מותאם בלחיצות ובטעויות", color = Color.White, fontSize = 14.sp)
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
            Text("מהירות דיבור: ${String.format("%.2f", speechRateSlider)}x", color = Color.LightGray, fontSize = 13.sp)
            Slider(
                value = speechRateSlider,
                onValueChange = {
                    speechRateSlider = it
                    speechManager.speechRate = it
                },
                valueRange = 0.6f..1.4f
            )

            Text("גובה צליל (Pitch): ${String.format("%.2f", speechPitchSlider)}", color = Color.LightGray, fontSize = 13.sp)
            Slider(
                value = speechPitchSlider,
                onValueChange = {
                    speechPitchSlider = it
                    speechManager.speechPitch = it
                },
                valueRange = 0.7f..1.3f
            )

            Spacer(modifier = Modifier.height(24.dp))

            // --- 3. גיבוי, ייצוא וייבוא נתונים ---
            Text("ניהול נתונים וגיבוי מקומי (Backup & Restore)", color = PurpleNeon, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Spacer(modifier = Modifier.height(10.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = {
                        haptic.click()
                        val jsonBackup = wordRepo.exportProgressToJson(wordRepo.loadWords())
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        val clip = android.content.ClipData.newPlainText("EnglishTutorBackup", jsonBackup)
                        clipboard.setPrimaryClip(clip)
                        haptic.success()
                        Toast.makeText(context, "קובץ הגיבוי הועתק ללוח!", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = CardDark)
                ) {
                    Text("ייצוא גיבוי (JSON)", color = Color.White)
                }

                Button(
                    onClick = { 
                        haptic.click()
                        showImportDialog = true 
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = CardDark)
                ) {
                    Text("ייבוא קובץ CSV", color = Color.White)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // --- 4. הגדרת מפתח API חיצוני ---
            Text("מפתח API פרטי (Gemini / OpenRouter)", color = Color.LightGray, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = apiKeyInput,
                onValueChange = { apiKeyInput = it },
                placeholder = { Text("השאר ריק לשימוש במנוע החינמי המובנה") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = {
                    haptic.click()
                    conversationRepo.saveCustomApiKey(apiKeyInput)
                    haptic.success()
                    Toast.makeText(context, "המפתח נשמר בהצלחה!", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = TealDark)
            ) {
                Text("שמור מפתח API", color = TealNeon)
            }
        }

        if (showImportDialog) {
            AlertDialog(
                onDismissRequest = { showImportDialog = false },
                containerColor = CardDark,
                title = { Text("ייבוא מילים מקובץ CSV", color = TealNeon, fontWeight = FontWeight.Bold) },
                text = {
                    Column {
                        Text("הדבק שורות בפורמט: מילה באנגלית, תרגום בעברית", color = Color.LightGray, fontSize = 12.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = csvImportInput,
                            onValueChange = { csvImportInput = it },
                            placeholder = { Text("run, לרוץ\njump, לקפוץ") },
                            modifier = Modifier.fillMaxWidth().height(140.dp)
                        )
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        val importedCount = wordRepo.importWordsFromCsv(csvImportInput)
                        showImportDialog = false
                        haptic.success()
                        Toast.makeText(context, "יובאו $importedCount מילים בהצלחה!", Toast.LENGTH_SHORT).show()
                        onDeckGenerated(wordRepo.loadWords())
                    }) {
                        Text("ייבא")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showImportDialog = false }) { Text("ביטול", color = Color.Gray) }
                }
            )
        }
    }
}
