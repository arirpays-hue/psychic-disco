package com.example.englishtutor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// מעטפת תגית מעוצבת בסגנון קנבה (ריבוע מעוגל עם ברק פנימי ומסגרת ניאון)
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

// 1. סמל תפריט צד מעוצב (3 פסי ניאון מדורגים)
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

// 2. סמל יהלום קטגוריה (כמו בתפריט "אולר שווייצרי" בתמונה)
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

// 3. סמל אישור / נלמד (וי ניאון זוהר כפול)
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

// 4. סמל כוכב / מילה קשה (כוכב גיאומטרי מעוצב)
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

// 5. סמל מיקרופון אולפן מעוצב (להקלטת דיבור)
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

// 6. סמל לב אנרגיה (מחליף את אימוג'י הלבבות ❤️ / 🖤)
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

// 7. סמל להבת רצף (מחליף את אימוג'י האש 🔥)
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

// 8. סמל ברק ניאון (מחליף את אימוג'י הברק ⚡)
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

// 9. סמל ניצוץ AI (מחליף את ✨)
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

// 10. סמל חץ חזרה מעוצב
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

// 11. סמל רענון / סבב חדש
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

// 12. סמל סגירה / שגיאה (X ניאון)
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

// 13. סמל שליחת הודעה (חץ ניאון משולש)
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

// 14. סמל הפעלה (משולש Play מעוצב במקום ▶)
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

// 15. סמל הגדרות מעוצב (משושה מכני בסגנון התמונה)
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

// 16. סמל תרגום דו-לשוני (מחליף את דגל התרגום)
@Composable
fun CanvaTranslateIcon(tint: Color = StarGold, modifier: Modifier = Modifier.size(16.dp)) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        drawCircle(tint, radius = w * 0.40f, style = Stroke(width = w * 0.09f))
        drawLine(tint, Offset(w * 0.12f, h * 0.5f), Offset(w * 0.88f, h * 0.5f), w * 0.08f)
        drawLine(tint, Offset(w * 0.5f, h * 0.12f), Offset(w * 0.5f, h * 0.88f), w * 0.08f)
    }
}

// 17. סמל דובר / בועת שיחה (מחליף את 🗣️)
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

// 18. סמל פלוס ליצירת תשובות
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

// 19. סמל שעון עצר מעוצב (מחליף את ⏱️)
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
