package com.glfx.gameturbo.ui

import android.app.Activity
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.glfx.gameturbo.MainViewModel
import com.glfx.gameturbo.core.design.*
import com.glfx.gameturbo.core.monitoring.Snapshot
import com.glfx.gameturbo.feature.optimization.Mode

fun thermalLevel(s: Snapshot?): Level {
    if (s == null) return Level.NA
    val st = s.thermalStatus
    if (st != null) return when { st >= 4 -> Level.BAD; st >= 3 -> Level.WARN; else -> Level.GOOD }
    val t = s.batteryTemp ?: return Level.NA
    return when { t >= 45f -> Level.BAD; t >= 41f -> Level.WARN; else -> Level.GOOD }
}

fun memoryLevel(s: Snapshot?): Level {
    if (s == null) return Level.NA
    val free = 1f - s.ramUsedFrac
    return when { s.lowMemory || free < 0.12f -> Level.BAD; free < 0.25f -> Level.WARN; else -> Level.GOOD }
}

fun batteryLevel(s: Snapshot?): Level {
    if (s == null || s.batteryPct < 0) return Level.NA
    return when { s.charging || s.batteryPct >= 30 -> Level.GOOD; s.batteryPct >= 15 -> Level.WARN; else -> Level.BAD }
}

@Composable
fun HomeScreen(vm: MainViewModel, go: (Screen) -> Unit) {
    val hist by vm.history.collectAsStateWithLifecycle()
    val mode by vm.mode.collectAsStateWithLifecycle()
    val dnd by vm.dndGranted.collectAsStateWithLifecycle()
    val overlay by vm.overlayGranted.collectAsStateWithLifecycle()
    val session by vm.sessionActive.collectAsStateWithLifecycle()
    val dev by vm.device.collectAsStateWithLifecycle()
    val net by vm.netResult.collectAsStateWithLifecycle()
    val activity = LocalContext.current as Activity
    val s = hist.lastOrNull()

    val checks = listOf(thermalLevel(s), memoryLevel(s), batteryLevel(s), if (dnd && overlay) Level.GOOD else Level.WARN)

    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(22.dp)) {
        Column(Modifier.weight(0.85f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            OptimizeRing(checks, session, if (session) "Session active" else "Optimize now", "Mode: ${mode.short}") {
                vm.optimize(activity); go(Screen.Optimize)
            }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                listOf("Thermal", "Memory", "Battery", "Access").forEachIndexed { i, l ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(checks[i].color))
                        Text(" $l", style = GType.Label)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Mode.values().take(2).forEach { Chip(it.short, it == mode) { vm.setMode(it) } }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Mode.values().drop(2).forEach { Chip(it.short, it == mode) { vm.setMode(it) } }
            }
            if (session) {
                Spacer(Modifier.height(14.dp))
                GhostButton("End session and restore", { vm.endSession(activity) })
            }
        }

        Column(Modifier.weight(1.5f).fillMaxHeight().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ScreenHeader("GLFX GAME TURBO", "Real data from this device. Nothing here is simulated.")
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LiveCard(Modifier.weight(1f), if (s?.cpuSystem != null) "CPU load" else "GLFX CPU share", pct(s?.cpuBest), Glfx.Ice,
                    hist.map { it.cpuBest ?: 0f }) { go(Screen.Cpu) }
                LiveCard(Modifier.weight(1f), "Memory used", pct(s?.ramUsedFrac), Glfx.Violet, hist.map { it.ramUsedFrac }) { go(Screen.Ram) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LiveCard(Modifier.weight(1f), "Battery temperature", s?.batteryTemp?.let { "%.1f °C".format(it) } ?: "n/a", Glfx.Warn,
                    hist.map { it.batteryTemp ?: 0f }, min = 20f, max = 50f, sub = s?.thermalLabel) { go(Screen.Thermal) }
                LiveCard(Modifier.weight(1f), "Battery", if ((s?.batteryPct ?: -1) >= 0) "${s?.batteryPct}%" else "n/a", Glfx.Good,
                    hist.map { it.batteryPct.coerceAtLeast(0) / 100f }, sub = if (s?.charging == true) "Charging" else "On battery") { go(Screen.Battery) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LiveCard(Modifier.weight(1f), "Latency", net?.let { if (it.samples.isEmpty()) "failed" else "${it.avgMs.toInt()} ms" } ?: "Run a test", Glfx.Rose, emptyList(), sub = "Network test") { go(Screen.Network) }
                LiveCard(Modifier.weight(1f), "Display", dev?.let { "${it.currentHz.toInt()} Hz" } ?: "…", Glfx.Ice, emptyList(),
                    sub = dev?.let { "${it.displayModes.map { m -> m.hz.toInt() }.distinct().size.coerceAtLeast(1)} refresh rate(s)" }) { go(Screen.Gpu) }
            }
        }
    }
}

@Composable
private fun LiveCard(modifier: Modifier, label: String, value: String, color: Color, values: List<Float>, min: Float = 0f, max: Float = 1f, sub: String? = null, onClick: () -> Unit) {
    GlassCard(modifier, onClick = onClick) {
        Text(label, style = GType.Label)
        Text(value, style = GType.Value.copy(color = color), modifier = Modifier.padding(top = 2.dp))
        if (values.size > 1) Sparkline(values, color, Modifier.fillMaxWidth().height(44.dp).padding(top = 6.dp), min = min, max = max)
        else Spacer(Modifier.height(44.dp))
        if (sub != null) Text(sub, style = GType.Label, modifier = Modifier.padding(top = 4.dp))
    }
}

/** Four arcs = four real checks. The holographic ring turns only while a session is active. */
@Composable
fun OptimizeRing(checks: List<Level>, running: Boolean, title: String, sub: String, onClick: () -> Unit) {
    val rot = if (running) {
        rememberInfiniteTransition(label = "ring").animateFloat(0f, 360f, infiniteRepeatable(tween(3200, easing = LinearEasing), RepeatMode.Restart), label = "rot").value
    } else 0f
    Box(Modifier.size(240.dp).clip(CircleShape).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val sw = 14.dp.toPx()
            val inset = sw / 2 + 4.dp.toPx()
            val box = Size(size.width - 2 * inset, size.height - 2 * inset)
            checks.forEachIndexed { i, lv ->
                drawArc(lv.color, -90f + i * 90f + 8f, 74f, false, Offset(inset, inset), box, style = Stroke(sw, cap = StrokeCap.Round))
            }
            val innerR = size.width / 2 - inset - sw - 12.dp.toPx()
            rotate(rot) { drawCircle(Brush.sweepGradient(Glfx.Holo, center = center), radius = innerR, style = Stroke(2.5.dp.toPx())) }
            drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.09f), Color.Transparent), center = center, radius = innerR), radius = innerR)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, style = GType.Title)
            Text(sub, style = GType.Label)
        }
    }
}
