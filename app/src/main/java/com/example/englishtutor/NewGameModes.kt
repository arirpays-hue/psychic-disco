package com.example.englishtutor

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

data class ChatMessage(
    val id: Long = System.nanoTime(),
    val text: String,
    val isUser: Boolean,
    val grammarCorrection: String? = null,
    val interactiveQuiz: InteractiveChatQuiz? = null,
    val translatedHe: String? = null
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FreeFlowChatScreen(
    conversationRepo: ConversationRepository,
    speechManager: SpeechManager,
    onOpenDrawer: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    var messages by remember {
        mutableStateOf(
            listOf(
                ChatMessage(
                    text = "Hello! I am your AI conversational partner. Tap the microphone to speak directly inside the app, long-press any message to translate it, or tap a quiz button above!",
                    isUser = false
                )
            )
        )
    }
    var inputText by remember { mutableStateOf("") }
    var isSending by remember { mutableStateOf(false) }
    var voiceStatusBanner by remember { mutableStateOf("") }

    var longPressedMsgId by remember { mutableLongStateOf(-1L) }
    var translatingMsgId by remember { mutableLongStateOf(-1L) }

    val isMuted = speechManager.isVoiceMuted
    val isSpeakingNow = speechManager.isCurrentlySpeaking
    val isListeningMic = speechManager.isListeningMic
    val liveVoiceLevel = speechManager.liveVoiceLevel

    LaunchedEffect(messages.size, isSending) {
        val totalItems = messages.size + (if (isSending) 1 else 0)
        if (totalItems > 0) {
            runCatching { listState.animateScrollToItem(totalItems - 1) }
        }
    }

    fun sendMessage(userText: String) {
        val cleanText = userText.trim()
        if (cleanText.isBlank() || isSending) return
        val userMsg = ChatMessage(text = cleanText, isUser = true)
        messages = messages + userMsg
        inputText = ""
        isSending = true
        voiceStatusBanner = ""

        scope.launch {
            val response = conversationRepo.generateFreeChatReply(cleanText, messages)
            isSending = false
            val aiMsg = ChatMessage(
                text = response.replyEn,
                isUser = false,
                grammarCorrection = response.correctionHe,
                interactiveQuiz = response.interactiveQuiz
            )
            messages = messages + aiMsg
            speechManager.speak(
                response.interactiveQuiz?.questionEn ?: response.replyEn
            )
        }
    }

    fun triggerInteractiveAiActivity(typeKey: String, labelHe: String) {
        if (isSending) return
        val userMsg = ChatMessage(text = "Please create a live interactive $labelHe for me!", isUser = true)
        messages = messages + userMsg
        isSending = true

        scope.launch {
            val response = conversationRepo.generateInteractiveChatChallenge(typeKey, inputText)
            isSending = false
            val aiMsg = ChatMessage(
                text = response.replyEn,
                isUser = false,
                interactiveQuiz = response.interactiveQuiz
            )
            messages = messages + aiMsg
            speechManager.speak(response.interactiveQuiz?.questionEn ?: response.replyEn)
        }
    }

    // הפעלת הקלטה קולית שקטה בתוך האפליקציה בלבד (ללא חלון קופץ של גוגל!)
    fun beginInAppVoiceRecording() {
        voiceStatusBanner = "מקשיב באנגלית... דבר כעת (לחץ שוב על המיקרופון לסיום)"
        speechManager.startListeningInApp(
            activityContext = context,
            onPartialResult = { partial ->
                inputText = partial
            },
            onResult = { finalSpoken ->
                voiceStatusBanner = ""
                inputText = finalSpoken
                sendMessage(finalSpoken)
            },
            onError = { errMsg ->
                voiceStatusBanner = errMsg
            }
        )
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            beginInAppVoiceRecording()
        } else {
            voiceStatusBanner = "נדרשת הרשאת מיקרופון כדי להשתמש בהקלדה הקולית"
        }
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(DarkBg, Color(0xFF10172A), DarkBg)))
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.clickable { onOpenDrawer() }) {
                    CanvaIconBadge(accentColor = TealNeon, badgeSize = 44.dp) {
                        CanvaMenuIcon(tint = TealNeon)
                    }
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "צ'אט דיבור חופשי + מבחני AI",
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = when {
                            isListeningMic -> "מקליט קול חי באפליקציה..."
                            isSending -> "ה-AI בונה תגובה..."
                            isMuted -> "קול מושתק (לחץ על הרמקול להפעלה)"
                            else -> "קול פעיל • לחיצה ארוכה על הודעה לתרגום"
                        },
                        color = when {
                            isListeningMic -> ErrorCrimson
                            isMuted -> StarGold
                            else -> TealNeon
                        },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Box(
                    modifier = Modifier.clickable {
                        val nowMuted = speechManager.toggleMute()
                        voiceStatusBanner = if (nowMuted) {
                            "ההקראה הקולית הושתקה"
                        } else {
                            "ההקראה הקולית הופעלה"
                        }
                    }
                ) {
                    CanvaIconBadge(
                        accentColor = if (isMuted) ErrorCrimson else TealNeon,
                        badgeSize = 44.dp,
                        isActive = true
                    ) {
                        CanvaSoundToggleIcon(
                            isMuted = isMuted,
                            isSpeakingNow = isSpeakingNow,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
            ) {
                InteractiveChipButton(
                    title = "מבחן אמריקאי חי",
                    accentColor = StarGold,
                    onClick = { triggerInteractiveAiActivity("MULTIPLE_CHOICE", "מבחן אמריקאי") }
                )
                InteractiveChipButton(
                    title = "השלמת משפט חסר",
                    accentColor = TealNeon,
                    onClick = { triggerInteractiveAiActivity("FILL_BLANK", "אתגר השלמת משפט") }
                )
                InteractiveChipButton(
                    title = "זיהוי משפט תקין",
                    accentColor = PurpleNeon,
                    onClick = { triggerInteractiveAiActivity("GRAMMAR_FIX", "חידון דקדוק") }
                )
                InteractiveChipButton(
                    title = "אתגר אוצר מילים",
                    accentColor = CheckNeon,
                    onClick = { triggerInteractiveAiActivity("VOCAB_CHALLENGE", "אתגר אוצר מילים") }
                )
            }

            AnimatedVisibility(
                visible = voiceStatusBanner.isNotEmpty(),
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(CardDark)
                        .border(1.dp, if (isListeningMic) ErrorCrimson else TealNeon, RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    CanvaSparkleIcon(tint = if (isListeningMic) ErrorCrimson else TealNeon, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = voiceStatusBanner,
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).padding(vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                itemsIndexed(messages, key = { _, m -> m.id }) { index, msg ->
                    val isMenuOpen = longPressedMsgId == msg.id
                    val isTranslatingThis = translatingMsgId == msg.id

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = if (msg.isUser) Alignment.End else Alignment.Start
                    ) {
                        Box(
                            modifier = Modifier
                                .widthIn(max = 315.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .background(if (msg.isUser) TealDark else CardDark)
                                .border(
                                    width = if (isMenuOpen) 2.dp else 1.5.dp,
                                    color = when {
                                        isMenuOpen -> StarGold
                                        msg.isUser -> TealNeon
                                        else -> PurpleNeon.copy(alpha = 0.75f)
                                    },
                                    shape = RoundedCornerShape(18.dp)
                                )
                                .combinedClickable(
                                    onClick = {
                                        speechManager.speak(msg.text, forceEvenIfMuted = false)
                                    },
                                    onLongClick = {
                                        longPressedMsgId = if (longPressedMsgId == msg.id) -1L else msg.id
                                    }
                                )
                                .padding(14.dp)
                        ) {
                            Column {
                                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                    Text(
                                        text = msg.text,
                                        color = Color.White,
                                        fontSize = 16.sp,
                                        lineHeight = 22.sp
                                    )
                                }

                                if (!msg.translatedHe.isNullOrBlank()) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    HorizontalDivider(color = StarGold.copy(alpha = 0.35f))
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        CanvaTranslateIcon(tint = StarGold, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = msg.translatedHe,
                                            color = StarGold,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            lineHeight = 20.sp
                                        )
                                    }
                                }
                            }
                        }

                        AnimatedVisibility(
                            visible = isMenuOpen,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut()
                        ) {
                            Row(
                                modifier = Modifier
                                    .padding(top = 6.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(Color(0xFF1A2238))
                                    .border(1.5.dp, StarGold, RoundedCornerShape(14.dp))
                                    .clickable(enabled = !isTranslatingThis) {
                                        translatingMsgId = msg.id
                                        scope.launch {
                                            val translated = conversationRepo.translateTextOnline(msg.text)
                                            val updated = messages.toMutableList()
                                            updated[index] = msg.copy(translatedHe = translated)
                                            messages = updated
                                            translatingMsgId = -1L
                                            longPressedMsgId = -1L
                                        }
                                    }
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CanvaIconBadge(accentColor = StarGold, badgeSize = 28.dp, cornerRadius = 8.dp) {
                                    CanvaTranslateIcon(tint = StarGold, modifier = Modifier.size(16.dp))
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (isTranslatingThis) "מתרגם דרך שרת גוגל..." else "תרגם הודעה זו לעברית (Google Translate)",
                                    color = StarGold,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.ExtraBold
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

                        if (msg.interactiveQuiz != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            InChatInteractiveQuizCard(
                                quiz = msg.interactiveQuiz,
                                onSpeakOption = { speechManager.speak(it) },
                                onRequestAnotherQuiz = {
                                    triggerInteractiveAiActivity("MULTIPLE_CHOICE", "מבחן אמריקאי נוסף")
                                }
                            )
                        }
                    }
                }

                if (isSending) {
                    item {
                        AiThinkingBubble()
                    }
                }
            }

            // שורת הקלדה וכפתור מיקרופון מונפש בקנבה (ללא שום חלונית חיצונית של גוגל!)
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.clickable(enabled = !isSending) {
                        if (isListeningMic) {
                            speechManager.stopListening()
                            voiceStatusBanner = ""
                            if (inputText.isNotBlank()) {
                                sendMessage(inputText)
                            }
                        } else {
                            val hasMicPerm = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.RECORD_AUDIO
                            ) == PackageManager.PERMISSION_GRANTED

                            if (hasMicPerm) {
                                beginInAppVoiceRecording()
                            } else {
                                micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        }
                    }
                ) {
                    CanvaVoiceOrbButton(
                        isRecording = isListeningMic,
                        voiceLevel = liveVoiceLevel,
                        modifier = Modifier.size(52.dp)
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    enabled = !isSending,
                    placeholder = {
                        Text(
                            text = when {
                                isListeningMic -> "מקשיב לך... (רואים את המילים נכתבות כאן)"
                                isSending -> "ה-AI חושב..."
                                else -> "הקלד או לחץ על המיקרופון לדיבור חי..."
                            },
                            color = if (isListeningMic) ErrorCrimson else Color.Gray,
                            fontSize = 13.sp
                        )
                    },
                    modifier = Modifier.weight(1f),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        disabledTextColor = Color.Gray,
                        focusedBorderColor = if (isListeningMic) ErrorCrimson else TealNeon,
                        unfocusedBorderColor = Color.Gray.copy(alpha = 0.4f)
                    )
                )

                Spacer(modifier = Modifier.width(8.dp))

                Box(
                    modifier = Modifier.clickable(enabled = !isSending && inputText.isNotBlank()) {
                        if (isListeningMic) {
                            speechManager.stopListening()
                        }
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

@Composable
fun InteractiveChipButton(
    title: String,
    accentColor: Color,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(CardDark)
            .border(1.2.dp, accentColor.copy(alpha = 0.7f), RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .padding(horizontal = 11.dp, vertical = 7.dp)
    ) {
        CanvaQuizIcon(tint = accentColor, modifier = Modifier.size(15.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = title,
            color = accentColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun InChatInteractiveQuizCard(
    quiz: InteractiveChatQuiz,
    onSpeakOption: (String) -> Unit,
    onRequestAnotherQuiz: () -> Unit
) {
    var selectedIdx by remember { mutableIntStateOf(-1) }
    var showHebrewHint by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(2.dp, StarGold.copy(alpha = 0.85f), RoundedCornerShape(22.dp)),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF161E35))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CanvaQuizIcon(tint = StarGold, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = quiz.typeTitleHe,
                        color = StarGold,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF232B45))
                        .border(1.dp, TealNeon.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                        .clickable { showHebrewHint = !showHebrewHint }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    CanvaTranslateIcon(tint = TealNeon, modifier = Modifier.size(13.dp))
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = if (showHebrewHint) "הסתר תרגום" else "תרגם שאלה",
                        color = TealNeon,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Text(
                    text = quiz.questionEn,
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.ExtraBold,
                    lineHeight = 24.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSpeakOption(quiz.questionEn) }
                )
            }

            if (showHebrewHint) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = quiz.questionHe,
                    color = StarGold,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            quiz.optionsEn.forEachIndexed { idx, optText ->
                val isSelected = selectedIdx == idx
                val isCorrect = idx == quiz.correctIndex

                val bg = when {
                    selectedIdx == -1 -> Color(0xFF1E2746)
                    isCorrect -> Color(0xFF0E3B2E)
                    isSelected -> Color(0xFF3B121E)
                    else -> Color(0xFF141A2E)
                }

                val borderCol = when {
                    selectedIdx == -1 -> TealNeon.copy(alpha = 0.45f)
                    isCorrect -> CheckNeon
                    isSelected -> ErrorCrimson
                    else -> Color.Gray.copy(alpha = 0.25f)
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(bg)
                        .border(1.5.dp, borderCol, RoundedCornerShape(14.dp))
                        .clickable(enabled = selectedIdx == -1) {
                            selectedIdx = idx
                            onSpeakOption(optText)
                        }
                        .padding(horizontal = 14.dp, vertical = 11.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                            Text(
                                text = "${('A' + idx)}. $optText",
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        if (selectedIdx != -1) {
                            if (isCorrect) {
                                CanvaCheckIcon(tint = CheckNeon, modifier = Modifier.size(18.dp))
                            } else if (isSelected) {
                                CanvaCloseIcon(tint = ErrorCrimson, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }

            if (selectedIdx != -1) {
                Spacer(modifier = Modifier.height(10.dp))
                val won = selectedIdx == quiz.correctIndex
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (won) CheckNeon.copy(alpha = 0.15f) else ErrorCrimson.copy(alpha = 0.15f))
                        .border(1.dp, if (won) CheckNeon else ErrorCrimson, RoundedCornerShape(12.dp))
                        .padding(10.dp)
                ) {
                    Column {
                        Text(
                            text = if (won) "תשובה נכונה!" else "לא מדויק — התשובה הנכונה מודגשת בירוק:",
                            color = if (won) CheckNeon else ErrorCrimson,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = quiz.explanationHe,
                            color = Color.White,
                            fontSize = 13.sp,
                            lineHeight = 19.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = onRequestAnotherQuiz,
                    colors = ButtonDefaults.buttonColors(containerColor = TealDark),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    CanvaRefreshIcon(tint = TealNeon, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("בקש מה-AI שאלה אמריקאית נוספת", color = TealNeon, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

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

// -------------------------------------------------------------
// 2. מבחן איות והקלדה אינסופי (ה-AI מוסיף שאלות ברקע באופן שקט!)
// -------------------------------------------------------------
@Composable
fun SpellingPracticeScreen(
    words: List<WordCard>,
    speechManager: SpeechManager,
    onOpenDrawer: () -> Unit
) {
    val context = LocalContext.current
    val wordRepo = remember { WordRepository(context.applicationContext) }

    // רשימה דינמית שמתרחבת בשקט ברקע על ידי ה-AI כך שלעולם לא ייגמרו השאלות!
    var dynamicPool by remember { mutableStateOf(words.shuffled()) }
    var currentIndex by remember { mutableIntStateOf(0) }
    val currentWord = dynamicPool.getOrNull(currentIndex) ?: WordCard("Resilience", "חוסן נפשי")

    var typedInput by remember { mutableStateOf("") }
    var resultState by remember { mutableIntStateOf(0) }
    var score by remember { mutableIntStateOf(0) }

    // בכל פעם שמתקרבים ל-4 השאלות האחרונות, ה-AI שואב ברקע מילים חדשות ומוסיף אותן בשקט לתור!
    LaunchedEffect(currentIndex, dynamicPool.size) {
        if (currentIndex >= dynamicPool.size - 4) {
            val newAiWords = wordRepo.fetchInfiniteAiWordsSilently(dynamicPool)
            if (newAiWords.isNotEmpty()) {
                dynamicPool = dynamicPool + newAiWords
            } else {
                // אם כולן כבר נלמדו, מערבב תור חדש ברקע כדי שהמשחק ימשיך לנצח
                dynamicPool = dynamicPool + words.shuffled()
            }
        }
    }

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
                    Box(modifier = Modifier.clickable { speechManager.speak(currentWord.english, forceEvenIfMuted = true) }) {
                        CanvaIconBadge(accentColor = TealNeon, badgeSize = 60.dp, cornerRadius = 30.dp) {
                            SpeakerVectorIcon(tint = TealNeon, isMuted = false, modifier = Modifier.size(28.dp))
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(currentWord.hebrew, fontSize = 28.sp, color = Color.White, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("הקשב והקלד את המילה באנגלית", fontSize = 13.sp, color = Color.Gray)
                }
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                OutlinedTextField(
                    value = typedInput,
                    onValueChange = {
                        typedInput = it
                        resultState = 0
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
                        resultState = if (isCorrect) 1 else 2
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

                if (resultState != 0) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (resultState == 1) {
                            CanvaCheckIcon(tint = CheckNeon, modifier = Modifier.size(20.dp))
                        } else {
                            CanvaCloseIcon(tint = ErrorCrimson, modifier = Modifier.size(20.dp))
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            if (resultState == 1) "איות מושלם!" else "שגוי! המילה הנכונה: ${currentWord.english}",
                            color = if (resultState == 1) CheckNeon else ErrorCrimson,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = {
                        typedInput = ""
                        resultState = 0
                        currentIndex++
                    }) {
                        Text("המילה הבאה")
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// 3. אתגר 60 שניות אינסופי (ה-AI מזריק שאלות חדשות ברקע בלי שיראו!)
// -------------------------------------------------------------
@Composable
fun SpeedChallengeScreen(
    words: List<WordCard>,
    repo: WordRepository,
    onOpenDrawer: () -> Unit
) {
    val scope = rememberCoroutineScope()

    // מאגר שאלות אינסופי שמתמלא ברקע על ידי ה-AI בזמן המשחק!
    var infiniteQueue by remember { mutableStateOf(words.shuffled()) }
    var currentIdx by remember { mutableIntStateOf(0) }

    var timeLeft by remember { mutableIntStateOf(60) }
    var isRunning by remember { mutableStateOf(false) }
    var score by remember { mutableIntStateOf(0) }
    var comboStreak by remember { mutableIntStateOf(0) }
    var correctCount by remember { mutableIntStateOf(0) }
    var wrongCount by remember { mutableIntStateOf(0) }
    var highScore by remember { mutableIntStateOf(repo.getSpeedHighScore()) }

    var selectedAnswer by remember { mutableStateOf("") }
    var isAnswerLocked by remember { mutableStateOf(false) }
    var feedbackBanner by remember { mutableStateOf("") }
    var lastWasCorrect by remember { mutableStateOf(true) }

    val cardShakeX = remember { Animatable(0f) }

    val currentWord = infiniteQueue.getOrNull(currentIdx) ?: WordCard("Run", "לרוץ")

    // שאיבה שקטה ברקע של מילים ושאלות חדשות מה-AI בכל פעם שמתקדמים בשאלות
    LaunchedEffect(currentIdx, isRunning) {
        if (currentIdx >= infiniteQueue.size - 6) {
            val aiNewWords = repo.fetchInfiniteAiWordsSilently(infiniteQueue)
            if (aiNewWords.isNotEmpty()) {
                infiniteQueue = infiniteQueue + aiNewWords.shuffled()
            } else {
                infiniteQueue = infiniteQueue + words.shuffled()
            }
        }
    }

    val options = remember(currentIdx, infiniteQueue.size) {
        val wrong = infiniteQueue
            .filter { it.hebrew != currentWord.hebrew }
            .shuffled()
            .take(3)
            .map { it.hebrew }
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
                delay(240)
                selectedAnswer = ""
                feedbackBanner = ""
                currentIdx++
                isAnswerLocked = false
            }
        } else {
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
                delay(520)
                selectedAnswer = ""
                feedbackBanner = ""
                currentIdx++
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
                                currentIdx++
                                isRunning = true
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = TealDark),
                            modifier = Modifier.fillMaxWidth(0.8f).height(50.dp)
                        ) {
                            CanvaRefreshIcon(tint = TealNeon, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("שחק שוב (עם שאלות חדשות)", color = TealNeon, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                    }
                }
            } else {
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

                    AnimatedVisibility(
                        visible = feedbackBanner.isNotEmpty(),
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
                                text = feedbackBanner,
                                color = if (lastWasCorrect) CheckNeon else ErrorCrimson,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 13.sp
                            )
                        }
                    }
                }

                val cardBorderColor = when {
                    selectedAnswer.isEmpty() -> TealNeon
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

                Column(modifier = Modifier.fillMaxWidth()) {
                    for (opt in options) {
                        val isThisSelected = selectedAnswer == opt
                        val isThisCorrectOption = opt == currentWord.hebrew

                        val buttonBgColor = when {
                            selectedAnswer.isEmpty() -> Color(0xFF182038)
                            isThisCorrectOption -> Color(0xFF0E3B2E)
                            isThisSelected && !isThisCorrectOption -> Color(0xFF3B121E)
                            else -> Color(0xFF14192B)
                        }

                        val buttonBorderColor = when {
                            selectedAnswer.isEmpty() -> TealNeon.copy(alpha = 0.45f)
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

                                if (selectedAnswer.isNotEmpty()) {
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
