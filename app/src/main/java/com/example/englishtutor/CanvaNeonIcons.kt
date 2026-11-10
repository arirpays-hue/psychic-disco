package com.example.englishtutor

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun CanvaIconBadge(
    accentColor: Color,
    badgeSize: Dp = 40.dp,
    cornerRadius: Dp = 12.dp,
    isActive: Boolean = true,
    content: @Composable () -> Unit
) {
    val borderColor = if (isActive) accentColor.copy(alpha = 0.85f) else Color(0xFF3A445E)
    val bgBrush = Brush.verticalGradient(
        colors = if (isActive) {
            listOf(
                accentColor.copy(alpha = 0.28f),
                Color(0xFF101829),
                accentColor.copy(alpha = 0.12f)
            )
        } else {
            listOf(Color(0xFF1C2336), Color(0xFF101522))
        }
    )

    Box(
        modifier = Modifier
            .size(badgeSize)
            .clip(RoundedCornerShape(cornerRadius))
            .background(bgBrush)
            .border(1.5.dp, borderColor, RoundedCornerShape(cornerRadius)),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

// 1. סמל רמקול דו-מצבי מעוצב בקנבה (פעיל עם גלי קול ניאון / מושתק עם X וקו אלכסוני)
@Composable
fun CanvaSoundToggleIcon(
    isMuted: Boolean,
    isSpeakingNow: Boolean = false,
    modifier: Modifier = Modifier.size(22.dp)
) {
    val transition = rememberInfiniteTransition(label = "soundWaveAnim")
    val wavePulse by transition.animateFloat(
        initialValue = 0.85f,
        targetValue = if (isSpeakingNow && !isMuted) 1.25f else 1.0f,
        animationSpec = infiniteRepeatable(tween(420, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "wavePulse"
    )

    val activeColor = if (isMuted) ErrorCrimson else TealNeon
    val secondaryGlow = if (isMuted) StarGold else CheckNeon

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        // הילה פנימית מאחורי הרמקול
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(activeColor.copy(alpha = 0.25f), Color.Transparent)
            ),
            radius = w * 0.48f
        )

        // גוף הרמקול המעוצב
        val speakerPath = Path().apply {
            moveTo(w * 0.10f, h * 0.37f)
            lineTo(w * 0.30f, h * 0.37f)
            lineTo(w * 0.54f, h * 0.16f)
            lineTo(w * 0.54f, h * 0.84f)
            lineTo(w * 0.30f, h * 0.63f)
            lineTo(w * 0.10f, h * 0.63f)
            close()
        }
        drawPath(speakerPath, color = activeColor.copy(alpha = 0.3f))
        drawPath(
            speakerPath,
            color = activeColor,
            style = Stroke(width = w * 0.085f, join = StrokeJoin.Round)
        )

        if (isMuted) {
            // סמל מושתק: X ניאון חד לצד הרמקול
            drawLine(
                color = ErrorCrimson,
                start = Offset(w * 0.66f, h * 0.34f),
                end = Offset(w * 0.92f, h * 0.66f),
                strokeWidth = w * 0.10f,
                cap = StrokeCap.Round
            )
            drawLine(
                color = ErrorCrimson,
                start = Offset(w * 0.92f, h * 0.34f),
                end = Offset(w * 0.66f, h * 0.66f),
                strokeWidth = w * 0.10f,
                cap = StrokeCap.Round
            )
        } else {
            // סמל לא מושתק (פעיל): קשתות גלי קול זוהרות
            drawArc(
                color = activeColor,
                startAngle = -42f,
                sweepAngle = 84f,
                useCenter = false,
                topLeft = Offset(w * 0.44f, h * 0.28f),
                size = Size(w * 0.28f * wavePulse, h * 0.44f),
                style = Stroke(width = w * 0.085f, cap = StrokeCap.Round)
            )
            drawArc(
                color = secondaryGlow,
                startAngle = -48f,
                sweepAngle = 96f,
                useCenter = false,
                topLeft = Offset(w * 0.52f, h * 0.14f),
                size = Size(w * 0.40f * wavePulse, h * 0.72f),
                style = Stroke(width = w * 0.08f, cap = StrokeCap.Round)
            )
        }
    }
}

// 2. כפתור הקלדה קולית מעוצב בקנבה עם אנימציית סונאר ואקוולייזר חי כשהוא מקליט!
@Composable
fun CanvaVoiceOrbButton(
    isRecording: Boolean,
    voiceLevel: Float,
    modifier: Modifier = Modifier.size(52.dp)
) {
    val transition = rememberInfiniteTransition(label = "micOrbFX")
    val sonarRing by transition.animateFloat(
        initialValue = 0.65f,
        targetValue = 1.35f,
        animationSpec = infiniteRepeatable(tween(1100, easing = FastOutSlowInEasing), RepeatMode.Restart),
        label = "sonarRing"
    )
    val orbitAngle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(2200, easing = LinearEasing)),
        label = "orbitAngle"
    )
    val waveBarPhase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(650, easing = LinearEasing)),
        label = "waveBarPhase"
    )

    val mainColor = if (isRecording) ErrorCrimson else TealNeon
    val accentGlow = if (isRecording) StarGold else PurpleNeon

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(52.dp)) {
            val w = size.width
            val h = size.height
            val cx = w / 2f
            val cy = h / 2f

            if (isRecording) {
                // גל סונאר מתפשט בזמן הקלטה
                val sonarAlpha = (1.35f - sonarRing).coerceIn(0f, 0.7f)
                drawCircle(
                    color = ErrorCrimson.copy(alpha = sonarAlpha * 0.5f),
                    radius = (w * 0.38f) * sonarRing,
                    center = Offset(cx, cy),
                    style = Stroke(width = w * 0.05f)
                )

                // הילה שמתרחבת לפי עוצמת הדיבור האמיתית במיקרופון
                val dynamicRadius = w * (0.36f + voiceLevel * 0.14f)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(ErrorCrimson.copy(alpha = 0.45f), Color.Transparent)
                    ),
                    radius = dynamicRadius,
                    center = Offset(cx, cy)
                )

                // טבעת ספקטרום מסתובבת סביב המיקרופון הפעיל
                rotate(degrees = orbitAngle, pivot = Offset(cx, cy)) {
                    drawArc(
                        brush = Brush.sweepGradient(
                            listOf(ErrorCrimson, StarGold, PurpleNeon, ErrorCrimson)
                        ),
                        startAngle = 0f,
                        sweepAngle = 280f,
                        useCenter = false,
                        topLeft = Offset(w * 0.06f, h * 0.06f),
                        size = Size(w * 0.88f, h * 0.88f),
                        style = Stroke(width = w * 0.06f, cap = StrokeCap.Round)
                    )
                }
            } else {
                // מסגרת קנבה יוקרתית במצב המתנה
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        listOf(TealNeon.copy(alpha = 0.25f), Color(0xFF101829))
                    ),
                    topLeft = Offset(w * 0.08f, h * 0.08f),
                    size = Size(w * 0.84f, h * 0.84f),
                    cornerRadius = CornerRadius(w * 0.28f, h * 0.28f)
                )
                drawRoundRect(
                    color = TealNeon.copy(alpha = 0.85f),
                    topLeft = Offset(w * 0.08f, h * 0.08f),
                    size = Size(w * 0.84f, h * 0.84f),
                    cornerRadius = CornerRadius(w * 0.28f, h * 0.28f),
                    style = Stroke(width = w * 0.045f)
                )
            }

            // ציור קפסולת המיקרופון במרכז
            drawRoundRect(
                color = mainColor,
                topLeft = Offset(w * 0.38f, h * 0.24f),
                size = Size(w * 0.24f, h * 0.32f),
                cornerRadius = CornerRadius(w * 0.12f, w * 0.12f),
                style = if (isRecording) androidx.compose.ui.graphics.drawscope.Fill else Stroke(width = w * 0.055f)
            )

            // קשת קליטה תחתונה
            drawArc(
                color = mainColor,
                startAngle = 0f,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = Offset(w * 0.29f, h * 0.34f),
                size = Size(w * 0.42f, h * 0.32f),
                style = Stroke(width = w * 0.055f, cap = StrokeCap.Round)
            )

            // עמוד ובסיס המיקרופון
            drawLine(mainColor, Offset(cx, h * 0.66f), Offset(cx, h * 0.77f), w * 0.055f, StrokeCap.Round)
            drawLine(mainColor, Offset(w * 0.37f, h * 0.77f), Offset(w * 0.63f, h * 0.77f), w * 0.055f, StrokeCap.Round)

            // גלי קול צדדיים קופצים בזמן דיבור
            if (isRecording) {
                val leftWaveH = h * (0.14f + abs(sin(waveBarPhase)) * 0.18f * (0.5f + voiceLevel))
                val rightWaveH = h * (0.14f + abs(cos(waveBarPhase)) * 0.18f * (0.5f + voiceLevel))
                drawLine(
                    color = accentGlow,
                    start = Offset(w * 0.20f, cy - leftWaveH / 2f),
                    end = Offset(w * 0.20f, cy + leftWaveH / 2f),
                    strokeWidth = w * 0.055f,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = accentGlow,
                    start = Offset(w * 0.80f, cy - rightWaveH / 2f),
                    end = Offset(w * 0.80f, cy + rightWaveH / 2f),
                    strokeWidth = w * 0.055f,
                    cap = StrokeCap.Round
                )
            }
        }
    }
}

private fun abs(v: Float): Float = if (v < 0f) -v else v

// 3. סמל מבחן אמריקאי / חידון חי בצ'אט
@Composable
fun CanvaQuizIcon(tint: Color = StarGold, modifier: Modifier = Modifier.size(18.dp)) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        drawRoundRect(
            color = tint,
            topLeft = Offset(w * 0.14f, h * 0.12f),
            size = Size(w * 0.72f, h * 0.76f),
            cornerRadius = CornerRadius(w * 0.14f, w * 0.14f),
            style = Stroke(width = w * 0.09f)
        )
        drawCircle(tint, radius = w * 0.06f, center = Offset(w * 0.30f, h * 0.34f))
        drawLine(tint, Offset(w * 0.44f, h * 0.34f), Offset(w * 0.72f, h * 0.34f), w * 0.08f, StrokeCap.Round)

        drawCircle(tint, radius = w * 0.06f, center = Offset(w * 0.30f, h * 0.52f))
        drawLine(tint, Offset(w * 0.44f, h * 0.52f), Offset(w * 0.72f, h * 0.52f), w * 0.08f, StrokeCap.Round)

        drawCircle(tint, radius = w * 0.06f, center = Offset(w * 0.30f, h * 0.70f))
        drawLine(tint, Offset(w * 0.44f, h * 0.70f), Offset(w * 0.64f, h * 0.70f), w * 0.08f, StrokeCap.Round)
    }
}

@Composable
fun CanvaMenuIcon(tint: Color = TealNeon, modifier: Modifier = Modifier.size(22.dp)) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = w * 0.11f
        drawLine(tint, Offset(w * 0.15f, h * 0.26f), Offset(w * 0.85f, h * 0.26f), stroke, StrokeCap.Round)
        drawLine(tint, Offset(w * 0.15f, h * 0.50f), Offset(w * 0.68f, h * 0.50f), stroke, StrokeCap.Round)
        drawLine(tint, Offset(w * 0.15f, h * 0.74f), Offset(w * 0.85f, h * 0.74f), stroke, StrokeCap.Round)
    }
}

@Composable
fun CanvaDiamondIcon(tint: Color = TealNeon, modifier: Modifier = Modifier.size(20.dp)) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val path = Path().apply {
            moveTo(w * 0.5f, h * 0.1f)
            lineTo(w * 0.9f, h * 0.5f)
            lineTo(w * 0.5f, h * 0.9f)
            lineTo(w * 0.1f, h * 0.5f)
            close()
        }
        drawPath(path, color = tint.copy(alpha = 0.25f))
        drawPath(path, color = tint, style = Stroke(width = w * 0.09f, join = StrokeJoin.Round))
        drawLine(tint, Offset(w * 0.35f, h * 0.5f), Offset(w * 0.65f, h * 0.5f), w * 0.08f, StrokeCap.Round)
        drawLine(tint, Offset(w * 0.5f, h * 0.35f), Offset(w * 0.5f, h * 0.65f), w * 0.08f, StrokeCap.Round)
    }
}

@Composable
fun CanvaCheckIcon(tint: Color = CheckNeon, modifier: Modifier = Modifier.size(24.dp)) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val path = Path().apply {
            moveTo(w * 0.18f, h * 0.52f)
            lineTo(w * 0.42f, h * 0.76f)
            lineTo(w * 0.84f, h * 0.24f)
        }
        drawPath(path, color = tint.copy(alpha = 0.3f), style = Stroke(width = w * 0.22f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawPath(path, color = tint, style = Stroke(width = w * 0.12f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

@Composable
fun CanvaStarIcon(tint: Color = StarGold, isFilled: Boolean = true, modifier: Modifier = Modifier.size(24.dp)) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val cy = h / 2f
        val outerR = w * 0.44f
        val innerR = w * 0.20f
        val path = Path()
        for (i in 0 until 10) {
            val r = if (i % 2 == 0) outerR else innerR
            val angle = (i * 36.0 - 90.0) * (PI / 180.0)
            val px = cx + (r * cos(angle)).toFloat()
            val py = cy + (r * sin(angle)).toFloat()
            if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        path.close()
        if (isFilled) {
            drawPath(path, color = tint.copy(alpha = 0.28f))
        }
        drawPath(path, color = tint, style = Stroke(width = w * 0.09f, join = StrokeJoin.Round))
    }
}

@Composable
fun CanvaMicIcon(tint: Color = TealNeon, isRecording: Boolean = false, modifier: Modifier = Modifier.size(24.dp)) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        if (isRecording) {
            drawCircle(color = tint.copy(alpha = 0.25f), radius = w * 0.48f)
        }
        drawRoundRect(
            color = tint,
            topLeft = Offset(w * 0.34f, h * 0.14f),
            size = Size(w * 0.32f, h * 0.44f),
            cornerRadius = CornerRadius(w * 0.16f, w * 0.16f),
            style = if (isRecording) androidx.compose.ui.graphics.drawscope.Fill else Stroke(width = w * 0.09f)
        )
        drawArc(
            color = tint,
            startAngle = 0f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(w * 0.22f, h * 0.28f),
            size = Size(w * 0.56f, h * 0.42f),
            style = Stroke(width = w * 0.09f, cap = StrokeCap.Round)
        )
        drawLine(tint, Offset(w * 0.5f, h * 0.70f), Offset(w * 0.5f, h * 0.86f), w * 0.09f, StrokeCap.Round)
        drawLine(tint, Offset(w * 0.34f, h * 0.86f), Offset(w * 0.66f, h * 0.86f), w * 0.09f, StrokeCap.Round)
    }
}

@Composable
fun CanvaHeartIcon(isActive: Boolean, modifier: Modifier = Modifier.size(20.dp)) {
    val activeColor = Color(0xFFFF3366)
    val inactiveColor = Color(0xFF2B3348)
    val color = if (isActive) activeColor else inactiveColor

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val path = Path().apply {
            moveTo(w * 0.5f, h * 0.86f)
            cubicTo(w * 0.12f, h * 0.62f, w * 0.08f, h * 0.26f, w * 0.30f, h * 0.18f)
            cubicTo(w * 0.42f, h * 0.14f, w * 0.50f, h * 0.26f, w * 0.50f, h * 0.32f)
            cubicTo(w * 0.50f, h * 0.26f, w * 0.58f, h * 0.14f, w * 0.70f, h * 0.18f)
            cubicTo(w * 0.92f, h * 0.26f, w * 0.88f, h * 0.62f, w * 0.5f, h * 0.86f)
            close()
        }
        if (isActive) {
            drawPath(path, color = activeColor.copy(alpha = 0.35f))
        }
        drawPath(path, color = color, style = Stroke(width = w * 0.10f, join = StrokeJoin.Round))
    }
}

@Composable
fun CanvaFlameIcon(tint: Color = StarGold, modifier: Modifier = Modifier.size(18.dp)) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val path = Path().apply {
            moveTo(w * 0.52f, h * 0.08f)
            cubicTo(w * 0.82f, h * 0.35f, w * 0.88f, h * 0.68f, w * 0.50f, h * 0.92f)
            cubicTo(w * 0.12f, h * 0.68f, w * 0.20f, h * 0.40f, w * 0.38f, h * 0.28f)
            cubicTo(w * 0.36f, h * 0.42f, w * 0.46f, h * 0.44f, w * 0.52f, h * 0.08f)
            close()
        }
        drawPath(path, color = tint.copy(alpha = 0.35f))
        drawPath(path, color = tint, style = Stroke(width = w * 0.09f, join = StrokeJoin.Round))
    }
}

@Composable
fun CanvaBoltIcon(tint: Color = StarGold, modifier: Modifier = Modifier.size(18.dp)) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val path = Path().apply {
            moveTo(w * 0.58f, h * 0.08f)
            lineTo(w * 0.22f, h * 0.54f)
            lineTo(w * 0.50f, h * 0.54f)
            lineTo(w * 0.42f, h * 0.92f)
            lineTo(w * 0.78f, h * 0.46f)
            lineTo(w * 0.50f, h * 0.46f)
            close()
        }
        drawPath(path, color = tint.copy(alpha = 0.3f))
        drawPath(path, color = tint, style = Stroke(width = w * 0.08f, join = StrokeJoin.Round))
    }
}

@Composable
fun CanvaSparkleIcon(tint: Color = PurpleNeon, modifier: Modifier = Modifier.size(18.dp)) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val path = Path().apply {
            moveTo(w * 0.5f, h * 0.08f)
            quadraticBezierTo(w * 0.5f, h * 0.5f, w * 0.92f, h * 0.5f)
            quadraticBezierTo(w * 0.5f, h * 0.5f, w * 0.5f, h * 0.92f)
            quadraticBezierTo(w * 0.5f, h * 0.5f, w * 0.08f, h * 0.5f)
            quadraticBezierTo(w * 0.5f, h * 0.5f, w * 0.5f, h * 0.08f)
            close()
        }
        drawPath(path, color = tint.copy(alpha = 0.35f))
        drawPath(path, color = tint, style = Stroke(width = w * 0.08f, join = StrokeJoin.Round))
    }
}

@Composable
fun CanvaBackArrowIcon(tint: Color = PurpleNeon, modifier: Modifier = Modifier.size(22.dp)) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = w * 0.11f
        drawLine(tint, Offset(w * 0.22f, h * 0.5f), Offset(w * 0.82f, h * 0.5f), stroke, StrokeCap.Round)
        drawLine(tint, Offset(w * 0.52f, h * 0.22f), Offset(w * 0.82f, h * 0.5f), stroke, StrokeCap.Round)
        drawLine(tint, Offset(w * 0.52f, h * 0.78f), Offset(w * 0.82f, h * 0.5f), stroke, StrokeCap.Round)
    }
}

@Composable
fun CanvaRefreshIcon(tint: Color = TealNeon, modifier: Modifier = Modifier.size(20.dp)) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        drawArc(
            color = tint,
            startAngle = 35f,
            sweepAngle = 285f,
            useCenter = false,
            topLeft = Offset(w * 0.16f, h * 0.16f),
            size = Size(w * 0.68f, h * 0.68f),
            style = Stroke(width = w * 0.11f, cap = StrokeCap.Round)
        )
        val head = Path().apply {
            moveTo(w * 0.62f, h * 0.12f)
            lineTo(w * 0.88f, h * 0.24f)
            lineTo(w * 0.68f, h * 0.42f)
            close()
        }
        drawPath(head, color = tint)
    }
}

@Composable
fun CanvaCloseIcon(tint: Color = ErrorCrimson, modifier: Modifier = Modifier.size(20.dp)) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = w * 0.12f
        drawLine(tint, Offset(w * 0.22f, h * 0.22f), Offset(w * 0.78f, h * 0.78f), stroke, StrokeCap.Round)
        drawLine(tint, Offset(w * 0.78f, h * 0.22f), Offset(w * 0.22f, h * 0.78f), stroke, StrokeCap.Round)
    }
}

@Composable
fun CanvaSendIcon(tint: Color = DarkBg, modifier: Modifier = Modifier.size(20.dp)) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val path = Path().apply {
            moveTo(w * 0.85f, h * 0.18f)
            lineTo(w * 0.15f, h * 0.50f)
            lineTo(w * 0.85f, h * 0.82f)
            lineTo(w * 0.68f, h * 0.50f)
            close()
        }
        drawPath(path, color = tint)
    }
}

@Composable
fun CanvaPlayIcon(tint: Color = TealNeon, modifier: Modifier = Modifier.size(18.dp)) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val path = Path().apply {
            moveTo(w * 0.75f, h * 0.20f)
            lineTo(w * 0.22f, h * 0.50f)
            lineTo(w * 0.75f, h * 0.80f)
            close()
        }
        drawPath(path, color = tint.copy(alpha = 0.3f))
        drawPath(path, color = tint, style = Stroke(width = w * 0.10f, join = StrokeJoin.Round))
    }
}

@Composable
fun CanvaSettingsIcon(tint: Color = StarGold, modifier: Modifier = Modifier.size(22.dp)) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val cy = h / 2f
        val r = w * 0.38f
        val hex = Path()
        for (i in 0 until 6) {
            val a = (i * 60.0) * (PI / 180.0)
            val px = cx + (r * cos(a)).toFloat()
            val py = cy + (r * sin(a)).toFloat()
            if (i == 0) hex.moveTo(px, py) else hex.lineTo(px, py)
        }
        hex.close()
        drawPath(hex, color = tint, style = Stroke(width = w * 0.10f, join = StrokeJoin.Round))
        drawCircle(color = tint, radius = w * 0.13f, center = Offset(cx, cy))
    }
}

// 4. סמל תרגום גוגל מעוצב בקנבה (כדור הארץ + חילופי אותיות A/א)
@Composable
fun CanvaTranslateIcon(tint: Color = StarGold, modifier: Modifier = Modifier.size(18.dp)) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        drawRoundRect(
            color = tint.copy(alpha = 0.22f),
            topLeft = Offset(w * 0.08f, h * 0.08f),
            size = Size(w * 0.54f, h * 0.54f),
            cornerRadius = CornerRadius(w * 0.14f, w * 0.14f)
        )
        drawRoundRect(
            color = tint,
            topLeft = Offset(w * 0.08f, h * 0.08f),
            size = Size(w * 0.54f, h * 0.54f),
            cornerRadius = CornerRadius(w * 0.14f, w * 0.14f),
            style = Stroke(width = w * 0.08f)
        )
        drawRoundRect(
            color = TealNeon,
            topLeft = Offset(w * 0.38f, h * 0.38f),
            size = Size(w * 0.54f, h * 0.54f),
            cornerRadius = CornerRadius(w * 0.14f, w * 0.14f),
            style = Stroke(width = w * 0.08f)
        )
        drawLine(tint, Offset(w * 0.24f, h * 0.46f), Offset(w * 0.35f, h * 0.22f), w * 0.08f, StrokeCap.Round)
        drawLine(tint, Offset(w * 0.35f, h * 0.22f), Offset(w * 0.46f, h * 0.46f), w * 0.08f, StrokeCap.Round)
        drawLine(TealNeon, Offset(w * 0.54f, h * 0.56f), Offset(w * 0.76f, h * 0.76f), w * 0.08f, StrokeCap.Round)
        drawLine(TealNeon, Offset(w * 0.76f, h * 0.56f), Offset(w * 0.54f, h * 0.76f), w * 0.08f, StrokeCap.Round)
    }
}

@Composable
fun CanvaSpeakerBubbleIcon(tint: Color = PurpleNeon, modifier: Modifier = Modifier.size(18.dp)) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        drawRoundRect(
            color = tint,
            topLeft = Offset(w * 0.12f, h * 0.16f),
            size = Size(w * 0.76f, h * 0.56f),
            cornerRadius = CornerRadius(w * 0.16f, w * 0.16f),
            style = Stroke(width = w * 0.10f)
        )
        drawLine(tint, Offset(w * 0.28f, h * 0.36f), Offset(w * 0.72f, h * 0.36f), w * 0.09f, StrokeCap.Round)
        drawLine(tint, Offset(w * 0.28f, h * 0.52f), Offset(w * 0.56f, h * 0.52f), w * 0.09f, StrokeCap.Round)
    }
}

@Composable
fun CanvaPlusIcon(tint: Color = PurpleNeon, modifier: Modifier = Modifier.size(14.dp)) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val s = w * 0.13f
        drawLine(tint, Offset(w * 0.18f, h * 0.5f), Offset(w * 0.82f, h * 0.5f), s, StrokeCap.Round)
        drawLine(tint, Offset(w * 0.5f, h * 0.18f), Offset(w * 0.5f, h * 0.82f), s, StrokeCap.Round)
    }
}

@Composable
fun CanvaTimerIcon(tint: Color = TealNeon, modifier: Modifier = Modifier.size(18.dp)) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        drawCircle(tint, radius = w * 0.38f, center = Offset(w * 0.5f, h * 0.55f), style = Stroke(width = w * 0.10f))
        drawLine(tint, Offset(w * 0.36f, h * 0.10f), Offset(w * 0.64f, h * 0.10f), w * 0.10f, StrokeCap.Round)
        drawLine(tint, Offset(w * 0.5f, h * 0.55f), Offset(w * 0.66f, h * 0.40f), w * 0.10f, StrokeCap.Round)
    }
}

@Composable
fun CanvaAiThinkingIcon(
    primaryColor: Color = TealNeon,
    secondaryColor: Color = PurpleNeon,
    modifier: Modifier = Modifier.size(34.dp)
) {
    val transition = rememberInfiniteTransition(label = "aiCoreThinking")
    val outerAngle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(2400, easing = LinearEasing)),
        label = "outerOrbit"
    )
    val innerAngle by transition.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(1800, easing = LinearEasing)),
        label = "innerOrbit"
    )
    val corePulse by transition.animateFloat(
        initialValue = 0.65f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(tween(750, easing = LinearEasing), RepeatMode.Reverse),
        label = "corePulse"
    )

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val cy = h / 2f

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(primaryColor.copy(alpha = 0.38f * corePulse.coerceAtMost(1f)), Color.Transparent)
            ),
            radius = w * 0.48f,
            center = Offset(cx, cy)
        )

        rotate(degrees = outerAngle, pivot = Offset(cx, cy)) {
            drawArc(
                color = primaryColor,
                startAngle = 0f,
                sweepAngle = 110f,
                useCenter = false,
                topLeft = Offset(w * 0.08f, h * 0.08f),
                size = Size(w * 0.84f, h * 0.84f),
                style = Stroke(width = w * 0.08f, cap = StrokeCap.Round)
            )
            drawArc(
                color = secondaryColor,
                startAngle = 180f,
                sweepAngle = 110f,
                useCenter = false,
                topLeft = Offset(w * 0.08f, h * 0.08f),
                size = Size(w * 0.84f, h * 0.84f),
                style = Stroke(width = w * 0.08f, cap = StrokeCap.Round)
            )
        }

        rotate(degrees = innerAngle, pivot = Offset(cx, cy)) {
            val hexPath = Path()
            val r = w * 0.26f
            for (i in 0 until 6) {
                val rad = (i * 60.0) * (PI / 180.0)
                val px = cx + (r * cos(rad)).toFloat()
                val py = cy + (r * sin(rad)).toFloat()
                if (i == 0) hexPath.moveTo(px, py) else hexPath.lineTo(px, py)
            }
            hexPath.close()
            drawPath(
                path = hexPath,
                color = secondaryColor,
                style = Stroke(width = w * 0.07f, join = StrokeJoin.Round)
            )
        }

        val starR = w * 0.14f * corePulse
        val starPath = Path().apply {
            moveTo(cx, cy - starR)
            quadraticBezierTo(cx, cy, cx + starR, cy)
            quadraticBezierTo(cx, cy, cx, cy + starR)
            quadraticBezierTo(cx, cy, cx - starR, cy)
            quadraticBezierTo(cx, cy, cx, cy - starR)
            close()
        }
        drawPath(path = starPath, color = primaryColor)
    }
}
