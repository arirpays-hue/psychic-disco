package com.example.englishtutor

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// -------------------------------------------------------------
// 1. שיחה חופשית לחלוטין מול AI (Free Flow Chat)
// -------------------------------------------------------------
data class ChatMessage(val text: String, val isUser: Boolean, val grammarCorrection: String? = null)

@Composable
fun FreeFlowChatScreen(
    conversationRepo: ConversationRepository,
    speechManager: SpeechManager,
    onOpenDrawer: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var messages by remember {
        mutableStateOf(listOf(ChatMessage("Hello! I am your AI conversational partner. What would you like to talk about today?", false)))
    }
    var inputText by remember { mutableStateOf("") }
    var isSending by remember { mutableStateOf(false) }
    var isRecording by remember { mutableStateOf(false) }

    fun sendMessage(userText: String) {
        if (userText.isBlank() || isSending) return
        val userMsg = ChatMessage(userText, true)
        messages = messages + userMsg
        inputText = ""
        isSending = true

        scope.launch {
            val response = conversationRepo.generateFreeChatReply(userText, messages)
            isSending = false
            messages = messages + ChatMessage(response.replyEn, false, response.correctionHe)
            speechManager.speak(response.replyEn)
        }
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(DarkBg)
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onOpenDrawer) {
                    Icon(Icons.Default.Menu, contentDescription = null, tint = TealNeon)
                }
                Text("צ'אט דיבור חופשי מול AI", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                IconButton(onClick = { speechManager.stop() }) {
                    Icon(Icons.Default.VolumeOff, contentDescription = null, tint = StarGold)
                }
            }

            LazyColumn(
                modifier = Modifier.weight(1f).padding(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(messages) { msg ->
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = if (msg.isUser) Alignment.End else Alignment.Start
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (msg.isUser) TealDark else CardDark)
                                .border(1.dp, if (msg.isUser) TealNeon else PurpleNeon, RoundedCornerShape(16.dp))
                                .padding(14.dp)
                        ) {
                            Text(msg.text, color = Color.White, fontSize = 16.sp)
                        }
                        if (msg.grammarCorrection != null) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("💡 תיקון: ${msg.grammarCorrection}", color = StarGold, fontSize = 12.sp)
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        if (!isRecording) {
                            isRecording = true
                            speechManager.startListening(
                                onResult = { spoken, _ ->
                                    isRecording = false
                                    sendMessage(spoken)
                                },
                                onError = { isRecording = false }
                            )
                        }
                    },
                    modifier = Modifier.background(if (isRecording) ErrorCrimson else TealDark, CircleShape)
                ) {
                    Icon(Icons.Default.Mic, contentDescription = null, tint = Color.White)
                }

                Spacer(modifier = Modifier.width(8.dp))

                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    placeholder = { Text("הקלד באנגלית...", color = Color.Gray) },
                    modifier = Modifier.weight(1f),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )

                Spacer(modifier = Modifier.width(8.dp))

                IconButton(
                    onClick = { sendMessage(inputText) },
                    modifier = Modifier.background(TealNeon, CircleShape)
                ) {
                    Icon(Icons.Default.Send, contentDescription = null, tint = DarkBg)
                }
            }
        }
    }
}

// -------------------------------------------------------------
// 2. מבחן הקלדה / כתיבה (Spelling Practice)
// -------------------------------------------------------------
@Composable
fun SpellingPracticeScreen(
    words: List<WordCard>,
    speechManager: SpeechManager,
    onOpenDrawer: () -> Unit
) {
    var currentIndex by remember { mutableIntStateOf(0) }
    val currentWord = words.getOrNull(currentIndex) ?: WordCard("Apple", "תפוח")
    var typedInput by remember { mutableStateOf("") }
    var resultStatus by remember { mutableStateOf<Boolean?>(null) }
    var score by remember { mutableIntStateOf(0) }

    LaunchedEffect(currentWord.english) {
        speechManager.speak(currentWord.english)
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Column(
            modifier = Modifier.fillMaxSize().background(DarkBg).padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onOpenDrawer) { Icon(Icons.Default.Menu, null, tint = TealNeon) }
                Text("מבחן איות והקלדה", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                Text("ניקוד: $score", color = StarGold, fontWeight = FontWeight.Bold)
            }

            Card(
                modifier = Modifier.fillMaxWidth().height(260.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = CardDark)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    IconButton(
                        onClick = { speechManager.speak(currentWord.english) },
                        modifier = Modifier.size(56.dp).background(TealDark, CircleShape)
                    ) {
                        Icon(Icons.Default.VolumeUp, null, tint = TealNeon, modifier = Modifier.size(32.dp))
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(currentWord.hebrew, fontSize = 28.sp, color = Color.White, fontWeight = FontWeight.Bold)
                    Text("הקשב והקלד את המילה באנגלית", fontSize = 13.sp, color = Color.Gray)
                }
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                OutlinedTextField(
                    value = typedInput,
                    onValueChange = {
                        typedInput = it
                        resultStatus = null
                    },
                    label = { Text("הקלד באנגלית כאן...") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = TealNeon
                    ),
                    modifier = Modifier.fillMaxWidth(0.85f)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        val isCorrect = typedInput.trim().equals(currentWord.english.trim(), ignoreCase = true)
                        resultStatus = isCorrect
                        if (isCorrect) {
                            score += 10
                            speechManager.speak("Correct!")
                        } else {
                            speechManager.speak("Incorrect, the word is ${currentWord.english}")
                        }
                    },
                    modifier = Modifier.fillMaxWidth(0.85f).height(50.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = TealDark)
                ) {
                    Text("בדוק איות", color = TealNeon, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }

                if (resultStatus != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        if (resultStatus == true) "✓ איות מושלם!" else "✗ שגוי! המילה הנכונה: ${currentWord.english}",
                        color = if (resultStatus == true) CheckNeon else ErrorCrimson,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = {
                        typedInput = ""
                        resultStatus = null
                        currentIndex = (currentIndex + 1) % words.size
                    }) {
                        Text("המילה הבאה")
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// 3. אתגר 60 שניות מהיר (Speed Challenge / Time Attack)
// -------------------------------------------------------------
@Composable
fun SpeedChallengeScreen(
    words: List<WordCard>,
    repo: WordRepository,
    onOpenDrawer: () -> Unit
) {
    var timeLeft by remember { mutableIntStateOf(60) }
    var isRunning by remember { mutableStateOf(false) }
    var score by remember { mutableIntStateOf(0) }
    var currentIdx by remember { mutableIntStateOf(0) }
    var highScore by remember { mutableIntStateOf(repo.getSpeedHighScore()) }

    val currentWord = words.getOrNull(currentIdx) ?: WordCard("Run", "לרוץ")

    // אפשרויות בחירה אמריקאיות (מתוכן 1 נכונה ו-3 מסיחות)
    val options = remember(currentIdx) {
        val wrong = words.filter { it.hebrew != currentWord.hebrew }.shuffled().take(3).map { it.hebrew }
        (wrong + currentWord.hebrew).shuffled()
    }

    LaunchedEffect(isRunning) {
        if (isRunning) {
            while (timeLeft > 0) {
                delay(1000)
                timeLeft--
            }
            isRunning = false
            repo.saveSpeedHighScore(score)
            highScore = repo.getSpeedHighScore()
        }
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Column(
            modifier = Modifier.fillMaxSize().background(DarkBg).padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onOpenDrawer) { Icon(Icons.Default.Menu, null, tint = TealNeon) }
                Text("אתגר 60 שניות ⚡", color = StarGold, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
                Text("שיא: $highScore", color = Color.White)
            }

            if (!isRunning && timeLeft == 60) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("כמה מילים תצליח לזהות ב-60 שניות?", color = Color.White, fontSize = 20.sp)
                    Spacer(modifier = Modifier.height(20.dp))
                    Button(
                        onClick = { isRunning = true },
                        modifier = Modifier.height(56.dp).fillMaxWidth(0.7f),
                        colors = ButtonDefaults.buttonColors(containerColor = TealNeon)
                    ) {
                        Text("התחל אתגר!", color = DarkBg, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    }
                }
            } else if (!isRunning && timeLeft == 0) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("הזמן נגמר! 🏁", color = StarGold, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                    Text("הניקוד שלך: $score", color = Color.White, fontSize = 20.sp)
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = {
                        timeLeft = 60
                        score = 0
                        isRunning = true
                    }) {
                        Text("נסה שוב")
                    }
                }
            } else {
                Text("⏱️ זמן נותר: $timeLeft שניות", color = if (timeLeft < 10) ErrorCrimson else TealNeon, fontSize = 18.sp, fontWeight = FontWeight.Bold)

                Card(
                    modifier = Modifier.fillMaxWidth().height(160.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = CardDark)
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(currentWord.english, fontSize = 36.sp, color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }

                Column(modifier = Modifier.fillMaxWidth()) {
                    options.forEach { opt ->
                        Button(
                            onClick = {
                                if (opt == currentWord.hebrew) score += 10
                                currentIdx = (currentIdx + 1) % words.size
                            },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).height(50.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E2640))
                        ) {
                            Text(opt, fontSize = 17.sp, color = Color.White)
                        }
                    }
                }
            }
        }
    }
}
