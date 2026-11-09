package com.example.englishtutor

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

val DarkBg = Color(0xFF080B12)
val CardDark = Color(0xFF14192B)
val TealNeon = Color(0xFF00F0FF)
val TealDark = Color(0xFF004452)
val StarGold = Color(0xFFFFC400)
val CheckNeon = Color(0xFF00E676)
val PurpleNeon = Color(0xFFB388FF)

data class ConfettiParticle(
    val angle: Float,
    val speed: Float,
    val radius: Float,
    val color: Color
)

@Composable
fun FlashcardScreen(words: List<WordCard>, repository: WordRepository) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val mainHandler = remember { Handler(Looper.getMainLooper()) }

    val allWords by remember { mutableStateOf(words) }
    var selectedFilter by remember { mutableStateOf(StudyFilter.SMART) }
    var currentWord by remember {
        mutableStateOf(repository.pickNextSmartWord(allWords, selectedFilter, null))
    }

    var uiVersion by remember { mutableIntStateOf(0) }
    val latestWord by rememberUpdatedState(currentWord)

    var isFlipped by remember { mutableStateOf(false) }
    var isTransitioning by remember { mutableStateOf(false) }
    var autoSpeak by remember { mutableStateOf(true) }
    var isSpeaking by remember { mutableStateOf(false) }
    var comboStreak by remember { mutableIntStateOf(0) }

    // --- מנוע הקראה קולית מוגן מקריסות (Crash-Safe TTS) ---
    var ttsReady by remember { mutableStateOf(false) }
    val ttsRef = remember { mutableStateOf<TextToSpeech?>(null) }
    val activeUtteranceId = remember { mutableStateOf("") }

    DisposableEffect(context) {
        var engine: TextToSpeech? = null
        runCatching {
            engine = TextToSpeech(context.applicationContext) { status ->
                runCatching {
                    if (status == TextToSpeech.SUCCESS) {
                        val result = engine?.setLanguage(Locale.US)
                        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                            engine?.setLanguage(Locale.ENGLISH)
                        }
                        engine?.setSpeechRate(0.92f)
                        engine?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                            override fun onStart(utteranceId: String?) {
                                mainHandler.post {
                                    if (utteranceId == activeUtteranceId.value) isSpeaking = true
                                }
                            }

                            override fun onDone(utteranceId: String?) {
                                mainHandler.post {
                                    if (utteranceId == activeUtteranceId.value) isSpeaking = false
                                }
                            }

                            @Deprecated("Deprecated in Java")
                            override fun onError(utteranceId: String?) {
                                mainHandler.post {
                                    if (utteranceId == activeUtteranceId.value) isSpeaking = false
                                }
                            }

                            override fun onStop(utteranceId: String?, interrupted: Boolean) {
                                mainHandler.post {
                                    if (utteranceId == activeUtteranceId.value) isSpeaking = false
                                }
                            }
                        })
                        ttsRef.value = engine
                        ttsReady = true
                    }
                }
            }
        }
        onDispose {
            runCatching {
                engine?.stop()
                engine?.shutdown()
            }
            ttsRef.value = null
        }
    }

    fun stopSpeakingSafely() {
        activeUtteranceId.value = ""
        isSpeaking = false
        runCatching { ttsRef.value?.stop() }
    }

    fun speakWord(wordToSpeak: String?) {
        val cleanWord = wordToSpeak?.trim() ?: return
        val engine = ttsRef.value ?: return
        if (ttsReady && cleanWord.isNotEmpty()) {
            runCatching {
                val newId = "TTS_${cleanWord.hashCode()}_${System.nanoTime()}"
                activeUtteranceId.value = newId
                engine.stop()
                val params = Bundle().apply {
                    putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
                }
                engine.speak(cleanWord, TextToSpeech.QUEUE_FLUSH, params, newId)
            }
        }
    }

    LaunchedEffect(currentWord?.english, ttsReady, autoSpeak) {
        val targetEnglish = currentWord?.english
        if (ttsReady && autoSpeak && !targetEnglish.isNullOrBlank()) {
            stopSpeakingSafely()
            delay(200)
            if (latestWord?.english == targetEnglish && !isTransitioning) {
                speakWord(targetEnglish)
            }
        }
    }

    // --- ניהול גרירה ואנימציות ללא התנגשות קורוטינות (Zero-Crash Gesture State) ---
    var rawDragX by remember { mutableFloatStateOf(0f) }
    var rawDragY by remember { mutableFloatStateOf(0f) }

    val animatedDragX by animateFloatAsState(
        targetValue = rawDragX,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "dragX"
    )
    val animatedDragY by animateFloatAsState(
        targetValue = rawDragY,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "dragY"
    )

    val cardEnterScale = remember { Animatable(1f) }
    val cardShakeX = remember { Animatable(0f) }
    val confettiProgress = remember { Animatable(0f) }
    var showConfetti by remember { mutableStateOf(false) }

    val confettiParticles = remember {
        List(32) {
            ConfettiParticle(
                angle = Random.nextFloat() * 2f * PI.toFloat(),
                speed = Random.nextFloat() * 360f + 120f,
                radius = Random.nextFloat() * 6f + 4f,
                color = listOf(TealNeon, CheckNeon, StarGold, PurpleNeon, Color.White).random()
            )
        }
    }

    fun triggerConfetti() {
        scope.launch {
            runCatching {
                showConfetti = true
                confettiProgress.snapTo(0f)
                confettiProgress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(800, easing = FastOutSlowInEasing)
                )
                showConfetti = false
            }
        }
    }

    fun triggerHardShake() {
        scope.launch {
            runCatching {
                for (i in 0..4) {
                    val target = if (i % 2 == 0) 15f else -15f
                    cardShakeX.animateTo(target, tween(40))
                }
                cardShakeX.animateTo(0f, spring())
            }
        }
    }

    val flipRotation by animateFloatAsState(
        targetValue = if (isFlipped) 180f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "flipRotation"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "infiniteFX")

    val breathingScale by infiniteTransition.animateFloat(
        initialValue = 0.992f,
        targetValue = 1.01f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathingScale"
    )

    val floatingOffsetY by infiniteTransition.animateFloat(
        initialValue = -5f,
        targetValue = 5f,
        animationSpec = infiniteRepeatable(
            animation = tween(2600, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "floatingY"
    )

    val shimmerSweep by infiniteTransition.animateFloat(
        initialValue = -400f,
        targetValue = 1200f,
        animationSpec = infiniteRepeatable(
            animation = tween(3200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerSweep"
    )

    val orbPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(9000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "orbPhase"
    )

    val haloRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(6500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "haloRotation"
    )

    val speakerWaveScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isSpeaking) 1.38f else 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(460, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "speakerWave"
    )

    val eqBar1 by infiniteTransition.animateFloat(0.25f, 1f, infiniteRepeatable(tween(240), RepeatMode.Reverse), "eq1")
    val eqBar2 by infiniteTransition.animateFloat(0.35f, 0.95f, infiniteRepeatable(tween(180), RepeatMode.Reverse), "eq2")
    val eqBar3 by infiniteTransition.animateFloat(0.2f, 1f, infiniteRepeatable(tween(300), RepeatMode.Reverse), "eq3")
    val eqBar4 by infiniteTransition.animateFloat(0.4f, 0.9f, infiniteRepeatable(tween(210), RepeatMode.Reverse), "eq4")
    val eqBar5 by infiniteTransition.animateFloat(0.3f, 1f, infiniteRepeatable(tween(260), RepeatMode.Reverse), "eq5")

    val isCurrentHard = remember(uiVersion, currentWord) { currentWord?.isHard == true }
    val isCurrentLearned = remember(uiVersion, currentWord) { currentWord?.isLearned == true }

    val dynamicBorderColor by animateColorAsState(
        targetValue = when {
            rawDragX > 100f -> CheckNeon
            rawDragX < -100f -> StarGold
            isCurrentHard -> StarGold
            isCurrentLearned -> CheckNeon
            isFlipped -> PurpleNeon
            else -> TealNeon
        },
        animationSpec = tween(220),
        label = "borderColor"
    )

    fun advanceToNextWord(exitDirection: Float = 0f) {
        if (isTransitioning) return
        scope.launch {
            runCatching {
                isTransitioning = true
                stopSpeakingSafely()

                val wasFlipped = isFlipped
                isFlipped = false

                if (exitDirection != 0f) {
                    rawDragX = exitDirection * 850f
                    delay(150)
                } else if (wasFlipped) {
                    delay(150)
                }

                rawDragX = 0f
                rawDragY = 0f
                cardEnterScale.snapTo(0.84f)

                currentWord = repository.pickNextSmartWord(
                    allWords = allWords,
                    filter = selectedFilter,
                    currentEnglish = latestWord?.english
                )
                uiVersion++
                isTransitioning = false

                cardEnterScale.animateTo(
                    targetValue = 1f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessLow
                    )
                )
            }.onFailure {
                rawDragX = 0f
                rawDragY = 0f
                isTransitioning = false
            }
        }
    }

    fun markCurrentWord(learned: Boolean? = null, hard: Boolean? = null) {
        val word = latestWord ?: return
        repository.recordReview(word, learned = learned, hard = hard)

        if (learned == true) {
            comboStreak++
            triggerConfetti()
        } else if (learned == false || hard == true) {
            comboStreak = 0
            if (hard == true) triggerHardShake()
        }
        uiVersion++
    }

    val learnedCount = remember(uiVersion, allWords) { allWords.count { it.isLearned } }
    val hardCount = remember(uiVersion, allWords) { allWords.count { it.isHard } }
    val unlearnedCount = allWords.size - learnedCount
    val progressFraction by animateFloatAsState(
        targetValue = if (allWords.isNotEmpty()) learnedCount.toFloat() / allWords.size else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "progressAnim"
    )

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(DarkBg, Color(0xFF10172A), DarkBg)
                    )
                )
        ) {
            // --- שכבה 1: כדורי אור מרחפים ברקע ---
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height

                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(TealNeon.copy(alpha = 0.12f), Color.Transparent),
                        radius = 380f
                    ),
                    radius = 380f,
                    center = Offset(
                        x = w * 0.2f + cos(orbPhase) * 90f,
                        y = h * 0.3f + sin(orbPhase) * 70f
                    )
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(PurpleNeon.copy(alpha = 0.11f), Color.Transparent),
                        radius = 340f
                    ),
                    radius = 340f,
                    center = Offset(
                        x = w * 0.8f + sin(orbPhase * 0.8f) * 80f,
                        y = h * 0.65f + cos(orbPhase * 0.8f) * 90f
                    )
                )
            }

            // --- שכבה 2: תוכן המסך ---
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // --- חלק עליון ---
                Column(modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(CardDark)
                                .border(
                                    1.5.dp,
                                    if (autoSpeak) TealNeon else Color.Gray.copy(0.3f),
                                    RoundedCornerShape(20.dp)
                                )
                                .clickable { autoSpeak = !autoSpeak }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            SpeakerVectorIcon(
                                tint = if (autoSpeak) TealNeon else Color.Gray,
                                isMuted = !autoSpeak,
                                modifier = Modifier
                                    .size(18.dp)
                                    .scale(if (isSpeaking) speakerWaveScale else 1f)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (autoSpeak) "הקראה אוטומטית" else "הקראה ידנית",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (autoSpeak) TealNeon else Color.Gray
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AnimatedVisibility(
                                visible = comboStreak >= 2,
                                enter = scaleIn(spring(Spring.DampingRatioHighBouncy)) + fadeIn(),
                                exit = scaleOut() + fadeOut()
                            ) {
                                Text(
                                    text = "🔥 x$comboStreak",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = StarGold,
                                    modifier = Modifier
                                        .padding(end = 10.dp)
                                        .scale(breathingScale)
                                )
                            }

                            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(42.dp)) {
                                Canvas(modifier = Modifier.size(38.dp)) {
                                    drawArc(
                                        color = CardDark,
                                        startAngle = 0f,
                                        sweepAngle = 360f,
                                        useCenter = false,
                                        style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
                                    )
                                    drawArc(
                                        color = CheckNeon,
                                        startAngle = -90f,
                                        sweepAngle = progressFraction * 360f,
                                        useCenter = false,
                                        style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
                                    )
                                }
                                Text(
                                    text = "${(progressFraction * 100).roundToInt()}%",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        AnimatedCounterBadge("נלמדו", learnedCount, CheckNeon)
                        AnimatedCounterBadge("לא נלמדו", unlearnedCount, TealNeon)
                        AnimatedCounterBadge("קשות", hardCount, StarGold)
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        StudyFilter.entries.forEach { filter ->
                            val isSelected = selectedFilter == filter
                            val chipScale by animateFloatAsState(
                                targetValue = if (isSelected) 1.05f else 0.96f,
                                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                                label = "chipScale"
                            )
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .scale(chipScale)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(if (isSelected) TealDark else CardDark)
                                    .border(
                                        width = if (isSelected) 1.5.dp else 1.dp,
                                        color = if (isSelected) TealNeon else Color.Gray.copy(0.25f),
                                        shape = RoundedCornerShape(14.dp)
                                    )
                                    .clickable {
                                        stopSpeakingSafely()
                                        rawDragX = 0f
                                        rawDragY = 0f
                                        selectedFilter = filter
                                        isFlipped = false
                                        currentWord = repository.pickNextSmartWord(allWords, filter, null)
                                        uiVersion++
                                    }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = filter.label,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) TealNeon else Color.LightGray
                                )
                            }
                        }
                    }
                }

                // --- חלק מרכזי: הכרטיסייה ---
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (currentWord == null) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            TrophyVectorIcon(
                                tint = StarGold,
                                modifier = Modifier.size(76.dp).scale(breathingScale)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "סיימת את כל המילים בקטגוריה זו!",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = {
                                    selectedFilter = StudyFilter.ALL
                                    currentWord = repository.pickNextSmartWord(allWords, StudyFilter.ALL, null)
                                    uiVersion++
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = TealDark)
                            ) {
                                Text("חזור לכל המילים", color = TealNeon)
                            }
                        }
                    } else {
                        val word = currentWord!!
                        val currentStreak = remember(uiVersion, word) { word.streak }
                        val currentSeen = remember(uiVersion, word) { word.timesSeen }

                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth(0.93f)
                                    .height(345.dp)
                                    .offset {
                                        IntOffset(
                                            x = (animatedDragX + cardShakeX.value).roundToInt(),
                                            y = (animatedDragY + floatingOffsetY).roundToInt()
                                        )
                                    }
                                    .scale(cardEnterScale.value * breathingScale)
                                    .graphicsLayer {
                                        rotationY = flipRotation
                                        rotationZ = (animatedDragX / 25f).coerceIn(-16f, 16f)
                                        rotationX = (-animatedDragY / 35f).coerceIn(-8f, 8f)
                                        cameraDistance = 16f * density
                                    }
                                    .pointerInput(Unit) {
                                        detectDragGestures(
                                            onDragEnd = {
                                                when {
                                                    rawDragX > 170f -> {
                                                        markCurrentWord(learned = true)
                                                        advanceToNextWord(exitDirection = 1f)
                                                    }
                                                    rawDragX < -170f -> {
                                                        markCurrentWord(hard = true)
                                                        advanceToNextWord(exitDirection = -1f)
                                                    }
                                                    else -> {
                                                        rawDragX = 0f
                                                        rawDragY = 0f
                                                    }
                                                }
                                            },
                                            onDragCancel = {
                                                rawDragX = 0f
                                                rawDragY = 0f
                                            },
                                            onDrag = { change, dragAmount ->
                                                change.consume()
                                                if (!isTransitioning) {
                                                    rawDragX = (rawDragX + dragAmount.x).coerceIn(-500f, 500f)
                                                    rawDragY = (rawDragY + dragAmount.y * 0.3f).coerceIn(-200f, 200f)
                                                }
                                            }
                                        )
                                    }
                                    .clickable {
                                        if (!isTransitioning) {
                                            isFlipped = !isFlipped
                                        }
                                    }
                                    .border(2.5.dp, dynamicBorderColor, RoundedCornerShape(28.dp)),
                                shape = RoundedCornerShape(28.dp),
                                colors = CardDefaults.cardColors(containerColor = CardDark),
                                elevation = CardDefaults.cardElevation(defaultElevation = 20.dp)
                            ) {
                                Box(modifier = Modifier.fillMaxSize()) {
                                    Canvas(modifier = Modifier.fillMaxSize()) {
                                        drawLine(
                                            brush = Brush.linearGradient(
                                                colors = listOf(
                                                    Color.Transparent,
                                                    dynamicBorderColor.copy(alpha = 0.14f),
                                                    Color.Transparent
                                                ),
                                                start = Offset(shimmerSweep - 200f, 0f),
                                                end = Offset(shimmerSweep + 200f, size.height)
                                            ),
                                            start = Offset(shimmerSweep - 150f, 0f),
                                            end = Offset(shimmerSweep + 150f, size.height),
                                            strokeWidth = 85f
                                        )
                                    }

                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(22.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (flipRotation <= 90f) {
                                            // --- צד קדמי: אנגלית ---
                                            Column(
                                                modifier = Modifier.fillMaxSize(),
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Box(contentAlignment = Alignment.Center) {
                                                            Box(
                                                                modifier = Modifier
                                                                    .size(50.dp)
                                                                    .rotate(haloRotation)
                                                                    .scale(if (isSpeaking) speakerWaveScale else 1f)
                                                                    .clip(CircleShape)
                                                                    .border(
                                                                        width = 1.5.dp,
                                                                        brush = Brush.sweepGradient(
                                                                            listOf(TealNeon, Color.Transparent, PurpleNeon, Color.Transparent)
                                                                        ),
                                                                        shape = CircleShape
                                                                    )
                                                            )
                                                            IconButton(
                                                                onClick = { speakWord(latestWord?.english) },
                                                                modifier = Modifier
                                                                    .size(42.dp)
                                                                    .clip(CircleShape)
                                                                    .background(TealDark)
                                                            ) {
                                                                SpeakerVectorIcon(
                                                                    tint = TealNeon,
                                                                    isMuted = false,
                                                                    modifier = Modifier.size(22.dp)
                                                                )
                                                            }
                                                        }

                                                        AnimatedVisibility(visible = isSpeaking) {
                                                            Row(
                                                                modifier = Modifier
                                                                    .padding(start = 8.dp)
                                                                    .height(22.dp),
                                                                verticalAlignment = Alignment.CenterVertically,
                                                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                                                            ) {
                                                                listOf(eqBar1, eqBar2, eqBar3, eqBar4, eqBar5).forEach { barHeight ->
                                                                    Box(
                                                                        modifier = Modifier
                                                                            .width(3.dp)
                                                                            .fillMaxHeight(barHeight)
                                                                            .clip(RoundedCornerShape(2.dp))
                                                                            .background(TealNeon)
                                                                    )
                                                                }
                                                            }
                                                        }
                                                    }

                                                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                                                        Text(
                                                            text = when {
                                                                isCurrentHard -> "⚡ מילה מאתגרת"
                                                                isCurrentLearned -> "✓ נלמדה (רצף $currentStreak)"
                                                                currentSeen == 0 -> "✨ מילה חדשה"
                                                                else -> "בתהליך למידה ($currentSeen)"
                                                            },
                                                            fontSize = 13.sp,
                                                            color = dynamicBorderColor,
                                                            fontWeight = FontWeight.SemiBold
                                                        )
                                                    }
                                                }

                                                AnimatedContent(
                                                    targetState = word.english,
                                                    transitionSpec = {
                                                        (fadeIn(tween(200)) + scaleIn(initialScale = 0.9f))
                                                            .togetherWith(fadeOut(tween(120)))
                                                    },
                                                    label = "cleanEnglishWord"
                                                ) { targetEnglish ->
                                                    Text(
                                                        text = targetEnglish,
                                                        fontSize = 42.sp,
                                                        fontWeight = FontWeight.ExtraBold,
                                                        color = TealNeon,
                                                        textAlign = TextAlign.Center,
                                                        modifier = Modifier.fillMaxWidth()
                                                    )
                                                }

                                                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                                                    val swipeStamp = when {
                                                        rawDragX > 90f -> "✓ שחרר לסימון 'נלמד'"
                                                        rawDragX < -90f -> "★ שחרר לסימון 'קשה'"
                                                        else -> "לחץ להיפוך • החלק ימינה (נלמד) או שמאלה (קשה)"
                                                    }
                                                    Text(
                                                        text = swipeStamp,
                                                        fontSize = if (abs(rawDragX) > 90f) 15.sp else 12.sp,
                                                        fontWeight = if (abs(rawDragX) > 90f) FontWeight.ExtraBold else FontWeight.Normal,
                                                        color = if (abs(rawDragX) > 90f) dynamicBorderColor else Color.Gray,
                                                        textAlign = TextAlign.Center
                                                    )
                                                }
                                            }
                                        } else {
                                            // --- צד אחורי: הפירוש בעברית ---
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .graphicsLayer { rotationY = 180f },
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.Center
                                            ) {
                                                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                                                    Text(
                                                        text = word.hebrew,
                                                        fontSize = 38.sp,
                                                        fontWeight = FontWeight.ExtraBold,
                                                        color = Color.White,
                                                        textAlign = TextAlign.Center
                                                    )
                                                }
                                                Spacer(modifier = Modifier.height(14.dp))
                                                Text(
                                                    text = word.english,
                                                    fontSize = 20.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = PurpleNeon
                                                )
                                                Spacer(modifier = Modifier.height(18.dp))
                                                OutlinedButton(
                                                    onClick = { speakWord(latestWord?.english) },
                                                    border = androidx.compose.foundation.BorderStroke(1.dp, TealNeon)
                                                ) {
                                                    SpeakerVectorIcon(
                                                        tint = TealNeon,
                                                        isMuted = false,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text("השמע הגייה באנגלית", color = TealNeon)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        if (showConfetti) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val center = Offset(size.width / 2f, size.height / 2f)
                                val p = confettiProgress.value
                                confettiParticles.forEach { particle ->
                                    val distance = particle.speed * p
                                    val gravityY = 260f * p * p
                                    val x = center.x + cos(particle.angle) * distance
                                    val y = center.y + sin(particle.angle) * distance + gravityY
                                    drawCircle(
                                        color = particle.color.copy(alpha = (1f - p).coerceIn(0f, 1f)),
                                        radius = particle.radius * (1f - p * 0.4f),
                                        center = Offset(x, y)
                                    )
                                }
                            }
                        }
                    }
                }

                // --- חלק תחתון: כפתורי פעולה ---
                Column(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        BouncyActionIcon(
                            label = "נלמד",
                            activeColor = CheckNeon,
                            isActive = isCurrentLearned,
                            onClick = {
                                if (!isTransitioning) {
                                    val newState = !(latestWord?.isLearned ?: false)
                                    markCurrentWord(learned = newState)
                                    if (newState) advanceToNextWord(exitDirection = 1f)
                                }
                            }
                        ) { tint ->
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "נלמד",
                                tint = tint,
                                modifier = Modifier.size(26.dp)
                            )
                        }

                        BouncyActionIcon(
                            label = "הקרא שוב",
                            activeColor = TealNeon,
                            isActive = isSpeaking,
                            onClick = { speakWord(latestWord?.english) }
                        ) { tint ->
                            SpeakerVectorIcon(
                                tint = tint,
                                isMuted = false,
                                modifier = Modifier.size(26.dp)
                            )
                        }

                        BouncyActionIcon(
                            label = "קשה",
                            activeColor = StarGold,
                            isActive = isCurrentHard,
                            onClick = {
                                if (!isTransitioning) {
                                    val newState = !(latestWord?.isHard ?: false)
                                    markCurrentWord(hard = newState)
                                }
                            }
                        ) { tint ->
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = "קשה",
                                tint = tint,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }

                    Button(
                        onClick = {
                            if (!isTransitioning) {
                                markCurrentWord()
                                advanceToNextWord(exitDirection = 0f)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth(0.86f)
                            .height(58.dp)
                            .scale(breathingScale)
                            .clip(RoundedCornerShape(30.dp))
                            .border(2.dp, TealNeon, RoundedCornerShape(30.dp)),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                        contentPadding = PaddingValues()
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.horizontalGradient(
                                        colors = listOf(TealDark, CardDark, TealDark)
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "המילה הבאה בתור החכם",
                                fontSize = 19.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SpeakerVectorIcon(tint: Color, isMuted: Boolean, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val speakerPath = Path().apply {
            moveTo(w * 0.12f, h * 0.36f)
            lineTo(w * 0.32f, h * 0.36f)
            lineTo(w * 0.56f, h * 0.16f)
            lineTo(w * 0.56f, h * 0.84f)
            lineTo(w * 0.32f, h * 0.64f)
            lineTo(w * 0.12f, h * 0.64f)
            close()
        }
        drawPath(speakerPath, color = tint)

        if (isMuted) {
            drawLine(
                color = tint,
                start = Offset(w * 0.66f, h * 0.34f),
                end = Offset(w * 0.92f, h * 0.66f),
                strokeWidth = w * 0.09f,
                cap = StrokeCap.Round
            )
            drawLine(
                color = tint,
                start = Offset(w * 0.92f, h * 0.34f),
                end = Offset(w * 0.66f, h * 0.66f),
                strokeWidth = w * 0.09f,
                cap = StrokeCap.Round
            )
        } else {
            drawArc(
                color = tint,
                startAngle = -45f,
                sweepAngle = 90f,
                useCenter = false,
                topLeft = Offset(w * 0.45f, h * 0.28f),
                size = androidx.compose.ui.geometry.Size(w * 0.32f, h * 0.44f),
                style = Stroke(width = w * 0.08f, cap = StrokeCap.Round)
            )
            drawArc(
                color = tint,
                startAngle = -50f,
                sweepAngle = 100f,
                useCenter = false,
                topLeft = Offset(w * 0.52f, h * 0.14f),
                size = androidx.compose.ui.geometry.Size(w * 0.42f, h * 0.72f),
                style = Stroke(width = w * 0.08f, cap = StrokeCap.Round)
            )
        }
    }
}

@Composable
fun TrophyVectorIcon(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val cupPath = Path().apply {
            moveTo(w * 0.25f, h * 0.15f)
            lineTo(w * 0.75f, h * 0.15f)
            lineTo(w * 0.65f, h * 0.55f)
            lineTo(w * 0.35f, h * 0.55f)
            close()
        }
        drawPath(cupPath, color = tint, style = Stroke(width = w * 0.06f, join = StrokeJoin.Round))
        drawLine(
            color = tint,
            start = Offset(w * 0.5f, h * 0.55f),
            end = Offset(w * 0.5f, h * 0.8f),
            strokeWidth = w * 0.08f
        )
        drawLine(
            color = tint,
            start = Offset(w * 0.3f, h * 0.82f),
            end = Offset(w * 0.7f, h * 0.82f),
            strokeWidth = w * 0.08f,
            cap = StrokeCap.Round
        )
    }
}

@Composable
fun AnimatedCounterBadge(title: String, count: Int, color: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(CardDark)
            .border(1.dp, color.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        AnimatedContent(
            targetState = count,
            transitionSpec = {
                (slideInVertically { -it } + fadeIn()).togetherWith(slideOutVertically { it } + fadeOut())
            },
            label = "counterBadgeAnim"
        ) { targetCount ->
            Text(
                text = "$targetCount",
                fontSize = 15.sp,
                fontWeight = FontWeight.ExtraBold,
                color = color
            )
        }
        Spacer(modifier = Modifier.width(6.dp))
        Text(text = title, fontSize = 12.sp, color = Color.LightGray)
    }
}

@Composable
fun BouncyActionIcon(
    label: String,
    activeColor: Color,
    isActive: Boolean,
    onClick: () -> Unit,
    iconContent: @Composable (Color) -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val pressScale by animateFloatAsState(
        targetValue = when {
            isPressed -> 0.82f
            isActive -> 1.1f
            else -> 1f
        },
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "bouncyIconScale"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .scale(pressScale)
            .clickable(interactionSource = interactionSource, indication = null) { onClick() }
    ) {
        Box(
            modifier = Modifier
                .size(58.dp)
                .clip(CircleShape)
                .background(if (isActive) activeColor.copy(alpha = 0.24f) else CardDark)
                .border(
                    width = 2.dp,
                    color = if (isActive) activeColor else Color.Gray.copy(alpha = 0.35f),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            iconContent(if (isActive) activeColor else Color.LightGray)
        }
        Spacer(modifier = Modifier.height(5.dp))
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
            color = if (isActive) activeColor else Color.Gray
        )
    }
}
