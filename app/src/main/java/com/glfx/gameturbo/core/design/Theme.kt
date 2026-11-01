package com.glfx.gameturbo.core.design

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object Glfx {
    val Ink = Color(0xFF07080B)
    val Graphite = Color(0xFF111319)
    val Text = Color(0xFFEDEFF4)
    val Muted = Color(0xFF8B91A0)
    val Ice = Color(0xFF5CE1E6)
    val Violet = Color(0xFF9B7BFF)
    val Rose = Color(0xFFFF6FB1)
    val Good = Color(0xFF5BE49B)
    val Warn = Color(0xFFFFB454)
    val Bad = Color(0xFFFF5C7A)
    val Holo = listOf(Ice, Violet, Rose, Ice)
}

enum class Level(val color: Color) {
    GOOD(Glfx.Good), WARN(Glfx.Warn), BAD(Glfx.Bad), NA(Glfx.Muted)
}

object GType {
    private val F = FontFamily.SansSerif
    val Display = TextStyle(fontFamily = F, fontWeight = FontWeight.Light, fontSize = 52.sp, letterSpacing = (-1.5).sp, color = Glfx.Text)
    val Value = TextStyle(fontFamily = F, fontWeight = FontWeight.Light, fontSize = 34.sp, letterSpacing = (-0.5).sp, color = Glfx.Text)
    val Headline = TextStyle(fontFamily = F, fontWeight = FontWeight.Light, fontSize = 28.sp, letterSpacing = (-0.5).sp, color = Glfx.Text)
    val Title = TextStyle(fontFamily = F, fontWeight = FontWeight.Medium, fontSize = 17.sp, color = Glfx.Text)
    val Body = TextStyle(fontFamily = F, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp, color = Glfx.Text)
    val Label = TextStyle(fontFamily = F, fontWeight = FontWeight.Medium, fontSize = 12.sp, color = Glfx.Muted)
}

@Composable
fun GlfxTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Glfx.Ice, secondary = Glfx.Violet, background = Glfx.Ink,
            surface = Glfx.Graphite, onBackground = Glfx.Text, onSurface = Glfx.Text
        ),
        content = content
    )
}

fun Modifier.glass(radius: Dp = 22.dp): Modifier {
    val shape = RoundedCornerShape(radius)
    return this
        .clip(shape)
        .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.10f), Color.White.copy(alpha = 0.03f))))
        .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.28f), Color.White.copy(alpha = 0.04f))), shape)
}

@Composable
fun GlfxBackdrop(content: @Composable BoxScope.() -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Glfx.Ink)
            .drawBehind {
                drawRect(
                    Brush.radialGradient(
                        listOf(Glfx.Violet.copy(alpha = 0.22f), Color.Transparent),
                        center = Offset(size.width * 0.88f, size.height * 0.02f), radius = size.width * 0.45f
                    )
                )
                drawRect(
                    Brush.radialGradient(
                        listOf(Glfx.Ice.copy(alpha = 0.14f), Color.Transparent),
                        center = Offset(size.width * 0.08f, size.height * 0.98f), radius = size.width * 0.40f
                    )
                )
            },
        content = content
    )
}

@Composable
fun GlassCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val base = modifier.glass()
    Column(
        (if (onClick != null) base.clickable(onClick = onClick) else base).padding(16.dp),
        content = content
    )
}

@Composable
fun Chip(text: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Box(
        Modifier
            .clip(shape)
            .background(if (selected) Glfx.Ice.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.06f))
            .border(1.dp, if (selected) Glfx.Ice.copy(alpha = 0.7f) else Color.White.copy(alpha = 0.12f), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(text, style = GType.Body.copy(color = if (selected) Glfx.Ice else Glfx.Text))
    }
}

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier
            .clip(shape)
            .background(Brush.horizontalGradient(if (enabled) listOf(Glfx.Ice, Glfx.Violet) else listOf(Color(0xFF3A3D47), Color(0xFF3A3D47))))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 13.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, style = GType.Title.copy(color = Glfx.Ink, fontWeight = FontWeight.SemiBold))
    }
}

@Composable
fun GhostButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier.glass(16.dp).clickable(onClick = onClick).padding(horizontal = 18.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) { Text(text, style = GType.Title) }
}

@Composable
fun Sparkline(values: List<Float>, color: Color, modifier: Modifier = Modifier, min: Float = 0f, max: Float = 1f) {
    Canvas(modifier) {
        if (values.size < 2 || max <= min) return@Canvas
        val w = size.width
        val h = size.height
        val step = w / (values.size - 1)
        val line = Path()
        values.forEachIndexed { i, v ->
            val x = i * step
            val y = h - ((v - min) / (max - min)).coerceIn(0f, 1f) * h
            if (i == 0) line.moveTo(x, y) else line.lineTo(x, y)
        }
        val fill = Path().apply { addPath(line); lineTo(w, h); lineTo(0f, h); close() }
        drawPath(fill, Brush.verticalGradient(listOf(color.copy(alpha = 0.28f), Color.Transparent)))
        drawPath(line, color, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}
