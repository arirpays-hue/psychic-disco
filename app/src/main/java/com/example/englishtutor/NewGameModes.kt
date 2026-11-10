package com.example.englishtutor

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

data class ChatMessage(val text: String, val isUser: Boolean, val grammarCorrection: String? = null)

@Composable
fun FreeFlowChatScreen(
    conversationRepo: ConversationRepository,
    speechManager: SpeechManager,
    onOpenDrawer: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    var messages by remember {
        mutableStateOf(
            listOf(
                ChatMessage(
                    "Hello! I am your AI conversational partner. What would you like to talk about today?",
                    false
                )
            )
        )
    }
    var inputText by remember { mutableStateOf("") }
    var isSending by remember { mutableStateOf(false) }
    var isRecording by remember { mutableStateOf(false) }

    // גלילה אוטומטית לתחתית הצ'אט בכל הודעה חדשה או כשמופיעה בועת המחשבה של ה-AI
    LaunchedEffect(messages.size, isSending) {
        val totalItems = messages.size + (if (isSending) 1 else 0)
        if (totalItems > 0) {
            runCatching { listState.animateScrollToItem(totalItems - 1) }
        }
    }

    fun sendMessage(userText: String) {
        val cleanText = userText.trim()
        if (cleanText.isBlank() || isSending) return
        val userMsg = ChatMessage(cleanText, true)
        messages = messages + userMsg
        inputText = ""
        isSending = true

        scope.launch {
            val response = conversationRepo.generateFreeChatReply(cleanText, messages)
            isSending = false
            messages = messages + ChatMessage(response.replyEn, false, response.correctionHe)
            speechManager.speak(response.replyEn)
        }
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(DarkBg, Color(0xFF10172A), DarkBg)))
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.clickable { onOpenDrawer() }) {
                    CanvaIconBadge(accentColor = TealNeon, badgeSize = 42.dp) {
                        CanvaMenuIcon(tint = TealNeon)
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "צ'אט דיבור חופשי מול AI",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = if (isSending) "ה-AI מנתח ומנסח תשובה..." else "מוכן לשיחה באנגלית",
                        color = if (isSending) PurpleNeon else TealNeon,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Box(modifier = Modifier.clickable { speechManager.stop() }) {
                    CanvaIconBadge(accentColor = StarGold, badgeSize = 42.dp) {
                        SpeakerVectorIcon(tint = StarGold, isMuted = true, modifier = Modifier.size(20.dp))
                    }
                }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).padding(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(messages) { msg ->
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = if (msg.isUser) Alignment.End else Alignment.Start
                    ) {
                        Box(
                            modifier = Modifier
                                .widthIn(max = 310.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .background(if (msg.isUser) TealDark else CardDark)
                                .border(
                                    1.5.dp,
                                    if (msg.isUser) TealNeon else PurpleNeon.copy(alpha = 0.7f),
                                    RoundedCornerShape(18.dp)
                                )
                                .clickable { if (!msg.isUser) speechManager.speak(msg.text) }
                                .padding(14.dp)
                        ) {
                            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                Text(
                                    text = msg.text,
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    lineHeight = 22.sp
                                )
                            }
                        }
                        if (msg.grammarCorrection != null) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(StarGold.copy(alpha = 0.14f))
                                    .border(1.dp, StarGold.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                CanvaSparkleIcon(tint = StarGold, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "תיקון דקדוק: ${msg.grammarCorrection}",
                                    color = StarGold,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }

                // --- בועת אנימציית "ה-AI חושב..." עם סמל הקנבה המונפש ---
                if (isSending) {
                    item {
                        AiThinkingBubble()
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.clickable(enabled = !isSending) {
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
                    }
                ) {
                    CanvaIconBadge(
                        accentColor = if (isRecording) ErrorCrimson else TealNeon,
                        badgeSize = 48.dp,
                        cornerRadius = 24.dp,
                        isActive = !isSending
                    ) {
                        CanvaMicIcon(
                            tint = if (isRecording) ErrorCrimson else TealNeon,
                            isRecording = isRecording,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    enabled = !isSending,
                    placeholder = {
                        Text(
                            if (isSending) "ה-AI חושב..." else "הקלד או הקלט באנגלית...",
                            color = Color.Gray
                        )
                    },
                    modifier = Modifier.weight(1f),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        disabledTextColor = Color.Gray,
                        focusedBorderColor = TealNeon,
                        unfocusedBorderColor = Color.Gray.copy(alpha = 0.4f)
                    )
                )

                Spacer(modifier = Modifier.width(8.dp))

                Box(
                    modifier = Modifier.clickable(enabled = !isSending && inputText.isNotBlank()) {
                        sendMessage(inputText)
                    }
                ) {
                    CanvaIconBadge(
                        accentColor = TealNeon,
                        badgeSize = 48.dp,
                        cornerRadius = 24.dp,
                        isActive = !isSending && inputText.isNotBlank()
                    ) {
                        CanvaSendIcon(
                            tint = if (!isSending && inputText.isNotBlank()) TealNeon else Color.Gray,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

// קומפוננטת האנימציה של ה-AI בזמן מחשבה וניתוח דקדוק
@Composable
fun AiThinkingBubble() {
    val infiniteTransition = rememberInfiniteTransition(label = "thinkingBars")
    val bar1 by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(420, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "b1"
    )
    val bar2 by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(320, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "b2"
    )
    val bar3 by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(520, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "b3"
    )
    val borderAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "borderPulse"
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(
                    Brush.horizontalGradient(
                        listOf(CardDark, Color(0xFF1F1638), CardDark)
                    )
                )
                .border(
                    width = 1.5.dp,
                    color = PurpleNeon.copy(alpha = borderAlpha),
                    shape = RoundedCornerShape(20.dp)
                )
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CanvaAiThinkingIcon(
                    primaryColor = TealNeon,
                    secondaryColor = PurpleNeon,
                    modifier = Modifier.size(34.dp)
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = "ה-AI חושב ומנתח דקדוק...",
                        color = TealNeon,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        modifier = Modifier.height(14.dp)
                    ) {
                        listOf(bar1, bar2, bar3, bar2).forEach { factor ->
                            Box(
                                modifier = Modifier
                                    .width(18.dp * factor)
                                    .height(5.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(
                                        Brush.horizontalGradient(listOf(TealNeon, PurpleNeon))
                                    )
                            )
                        }
                    }
                }
            }
        }
    }
}

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
                Box(modifier = Modifier.clickable { onOpenDrawer() }) {
                    CanvaIconBadge(accentColor = TealNeon, badgeSize = 42.dp) {
                        CanvaMenuIcon(tint = TealNeon)
                    }
                }
                Text("מבחן איות והקלדה", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                Text("ניקוד: $score", color = StarGold, fontWeight = FontWeight.Bold)
            }

            Card(
                modifier = Modifier.fillMaxWidth().height(260.dp).border(2.dp, TealNeon, RoundedCornerShape(24.dp)),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = CardDark)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(modifier = Modifier.clickable { speechManager.speak(currentWord.english) }) {
                        CanvaIconBadge(accentColor = TealNeon, badgeSize = 60.dp, cornerRadius = 30.dp) {
                            SpeakerVectorIcon(tint = TealNeon, isMuted = false, modifier = Modifier.size(28.dp))
                        }
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (resultStatus == true) {
                            CanvaCheckIcon(tint = CheckNeon, modifier = Modifier.size(20.dp))
                        } else {
                            CanvaCloseIcon(tint = ErrorCrimson, modifier = Modifier.size(20.dp))
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            if (resultStatus == true) "איות מושלם!" else "שגוי! המילה הנכונה: ${currentWord.english}",
                            color = if (resultStatus == true) CheckNeon else ErrorCrimson,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
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
// 3. אתגר 60 שניות מהיר עם ענישה וחיווי מיידי על טעויות
// -------------------------------------------------------------
@Composable
fun SpeedChallengeScreen(
    words: List<WordCard>,
    repo: WordRepository,
    onOpenDrawer: () -> Unit
) {
    val scope = rememberCoroutineScope()

    var timeLeft by remember { mutableIntStateOf(60) }
    var isRunning by remember { mutableStateOf(false) }
    var score by remember { mutableIntStateOf(0) }
    var comboStreak by remember { mutableIntStateOf(0) }
    var correctCount by remember { mutableIntStateOf(0) }
    var wrongCount by remember { mutableIntStateOf(0) }

    var currentIdx by remember { mutableIntStateOf(0) }
    var highScore by remember { mutableIntStateOf(repo.getSpeedHighScore()) }

    // מצב חיווי וענישה לאחר בחירה
    var selectedAnswer by remember { mutableStateOf<String?>(null) }
    var isAnswerLocked by remember { mutableStateOf(false) }
    var feedbackBanner by remember { mutableStateOf<String?>(null) }
    var lastWasCorrect by remember { mutableStateOf(true) }

    val cardShakeX = remember { Animatable(0f) }

    val currentWord = words.getOrNull(currentIdx) ?: WordCard("Run", "לרוץ")

    val options = remember(currentIdx, words) {
        val wrong = words.filter { it.hebrew != currentWord.hebrew }.shuffled().take(3).map { it.hebrew }
        (wrong + currentWord.hebrew).shuffled()
    }

    LaunchedEffect(isRunning) {
        if (isRunning) {
            while (timeLeft > 0) {
                delay(1000)
                timeLeft = (timeLeft - 1).coerceAtLeast(0)
            }
            isRunning = false
            repo.saveSpeedHighScore(score)
            highScore = repo.getSpeedHighScore()
        }
    }

    fun handleOptionClick(chosenHebrew: String) {
        if (isAnswerLocked || !isRunning) return
        isAnswerLocked = true
        selectedAnswer = chosenHebrew

        val isCorrect = chosenHebrew == currentWord.hebrew
        lastWasCorrect = isCorrect

        if (isCorrect) {
            correctCount++
            comboStreak++
            val bonus = if (comboStreak >= 3) 15 else 10
            score += bonus
            feedbackBanner = "+$bonus נקודות! תשובה נכונה"
            scope.launch {
                delay(260)
                selectedAnswer = null
                feedbackBanner = null
                currentIdx = (currentIdx + 1) % words.size
                isAnswerLocked = false
            }
        } else {
            // ענישה על תשובה שגויה: הורדת 5 נקודות, קנס של 2 שניות בשעון, איפוס רצף ורעידת הכרטיס!
            wrongCount++
            comboStreak = 0
            score = (score - 5).coerceAtLeast(0)
            timeLeft = (timeLeft - 2).coerceAtLeast(0)
            feedbackBanner = "טעות! קנס: 5- נקודות ו-2- שניות • התשובה: ${currentWord.hebrew}"

            scope.launch {
                for (i in 0..5) {
                    cardShakeX.animateTo(if (i % 2 == 0) 20f else -20f, tween(35))
                }
                cardShakeX.animateTo(0f, spring())
                delay(550) // משאיר את התשובה הנכונה מסומנת בירוק והשגויה באדום כדי שהמשתמש יראה וילמד
                selectedAnswer = null
                feedbackBanner = null
                currentIdx = (currentIdx + 1) % words.size
                isAnswerLocked = false
            }
        }
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(DarkBg, Color(0xFF10172A), DarkBg)))
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.clickable { onOpenDrawer() }) {
                    CanvaIconBadge(accentColor = TealNeon, badgeSize = 42.dp) {
                        CanvaMenuIcon(tint = TealNeon)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CanvaBoltIcon(tint = StarGold, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("אתגר 60 שניות", color = StarGold, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
                }
                Text("שיא: $highScore", color = Color.White, fontWeight = FontWeight.Bold)
            }

            if (!isRunning && timeLeft == 60) {
                Card(
                    modifier = Modifier.fillMaxWidth().border(2.dp, TealNeon, RoundedCornerShape(24.dp)),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = CardDark)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp).fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CanvaIconBadge(accentColor = StarGold, badgeSize = 64.dp, cornerRadius = 20.dp) {
                            CanvaBoltIcon(tint = StarGold, modifier = Modifier.size(34.dp))
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "כמה מילים תצליח לזהות ב-60 שניות?",
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.ExtraBold,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "תשובה נכונה: +10 נקודות (רצף מזכה ב-+15)\nתשובה שגויה: קנס של 5- נקודות ו-2- שניות מהשעון!",
                            color = Color.LightGray,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                            lineHeight = 20.sp
                        )
                        Spacer(modifier = Modifier.height(22.dp))
                        Button(
                            onClick = {
                                score = 0
                                comboStreak = 0
                                correctCount = 0
                                wrongCount = 0
                                timeLeft = 60
                                isRunning = true
                            },
                            modifier = Modifier.height(54.dp).fillMaxWidth(0.8f),
                            colors = ButtonDefaults.buttonColors(containerColor = TealNeon)
                        ) {
                            Text("התחל אתגר!", color = DarkBg, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                        }
                    }
                }
            } else if (!isRunning && timeLeft == 0) {
                Card(
                    modifier = Modifier.fillMaxWidth().border(2.dp, StarGold, RoundedCornerShape(26.dp)),
                    shape = RoundedCornerShape(26.dp),
                    colors = CardDefaults.cardColors(containerColor = CardDark)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp).fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        TrophyVectorIcon(tint = StarGold, modifier = Modifier.size(64.dp))
                        Spacer(modifier = Modifier.height(10.dp))
                        Text("הזמן נגמר!", color = StarGold, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("ניקוד סופי: $score", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            AnimatedCounterBadge("נכונות", correctCount, CheckNeon)
                            AnimatedCounterBadge("שגויות", wrongCount, ErrorCrimson)
                        }
                        Spacer(modifier = Modifier.height(20.dp))
                        Button(
                            onClick = {
                                timeLeft = 60
                                score = 0
                                comboStreak = 0
                                correctCount = 0
                                wrongCount = 0
                                isRunning = true
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = TealDark),
                            modifier = Modifier.fillMaxWidth(0.8f).height(50.dp)
                        ) {
                            CanvaRefreshIcon(tint = TealNeon, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("שחק שוב", color = TealNeon, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                    }
                }
            } else {
                // סרגל זמן, ניקוד חי ורצף
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CanvaTimerIcon(tint = if (timeLeft <= 10) ErrorCrimson else TealNeon)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "$timeLeft שניות",
                                color = if (timeLeft <= 10) ErrorCrimson else TealNeon,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }

                        if (comboStreak >= 2) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CanvaFlameIcon(tint = StarGold)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("רצף x$comboStreak", color = StarGold, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                            }
                        }

                        Text(
                            text = "ניקוד: $score",
                            color = CheckNeon,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    LinearProgressIndicator(
                        progress = { (timeLeft / 60f).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                        color = if (timeLeft <= 10) ErrorCrimson else TealNeon,
                        trackColor = CardDark
                    )

                    // באנר משוב מיידי על הצלחה או ענישה
                    AnimatedVisibility(
                        visible = feedbackBanner != null,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 10.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (lastWasCorrect) CheckNeon.copy(alpha = 0.18f)
                                    else ErrorCrimson.copy(alpha = 0.22f)
                                )
                                .border(
                                    1.5.dp,
                                    if (lastWasCorrect) CheckNeon else ErrorCrimson,
                                    RoundedCornerShape(12.dp)
                                )
                                .padding(vertical = 8.dp, horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            if (lastWasCorrect) {
                                CanvaCheckIcon(tint = CheckNeon, modifier = Modifier.size(18.dp))
                            } else {
                                CanvaCloseIcon(tint = ErrorCrimson, modifier = Modifier.size(18.dp))
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = feedbackBanner ?: "",
                                color = if (lastWasCorrect) CheckNeon else ErrorCrimson,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 13.sp
                            )
                        }
                    }
                }

                // כרטיס המילה באנגלית (רועד באדום במקרה של תשובה שגויה)
                val cardBorderColor = when {
                    selectedAnswer == null -> TealNeon
                    lastWasCorrect -> CheckNeon
                    else -> ErrorCrimson
                }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                        .offset { IntOffset(cardShakeX.value.roundToInt(), 0) }
                        .border(2.5.dp, cardBorderColor, RoundedCornerShape(22.dp)),
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = CardDark)
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                            Text(
                                text = currentWord.english,
                                fontSize = 38.sp,
                                color = Color.White,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                }

                // כפתורי האפשרויות עם סימון ירוק לתשובה הנכונה ואדום לתשובה השגויה
                Column(modifier = Modifier.fillMaxWidth()) {
                    for (opt in options) {
                        val isThisSelected = selectedAnswer == opt
                        val isThisCorrectOption = opt == currentWord.hebrew

                        val buttonBgColor = when {
                            selectedAnswer == null -> Color(0xFF182038)
                            isThisCorrectOption -> Color(0xFF0E3B2E) // מדגיש תמיד את התשובה הנכונה בירוק
                            isThisSelected && !isThisCorrectOption -> Color(0xFF3B121E) // מדגיש את הטעות שנבחרה באדום
                            else -> Color(0xFF14192B)
                        }

                        val buttonBorderColor = when {
                            selectedAnswer == null -> TealNeon.copy(alpha = 0.45f)
                            isThisCorrectOption -> CheckNeon
                            isThisSelected && !isThisCorrectOption -> ErrorCrimson
                            else -> Color.Gray.copy(alpha = 0.2f)
                        }

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 5.dp)
                                .height(54.dp)
                                .border(1.5.dp, buttonBorderColor, RoundedCornerShape(16.dp))
                                .clickable(enabled = !isAnswerLocked) {
                                    handleOptionClick(opt)
                                },
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = buttonBgColor)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = opt,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )

                                if (selectedAnswer != null) {
                                    if (isThisCorrectOption) {
                                        CanvaCheckIcon(tint = CheckNeon, modifier = Modifier.size(20.dp))
                                    } else if (isThisSelected) {
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
