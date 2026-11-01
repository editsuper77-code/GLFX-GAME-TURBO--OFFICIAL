package com.glfx.gameturbo.feature.panel

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.glfx.gameturbo.core.design.*
import com.glfx.gameturbo.core.monitoring.Snapshot
import com.glfx.gameturbo.core.performance.Advisor
import kotlin.math.abs

enum class Side { LEFT, RIGHT }

class PanelState {
    val expanded = mutableStateOf(false)
    val side = mutableStateOf(Side.LEFT)
    val snap = mutableStateOf<Snapshot?>(null)
    val awake = mutableStateOf(false)
    val dnd = mutableStateOf(false)
    val canDnd = mutableStateOf(false)
    val canWrite = mutableStateOf(false)
    val autoBrightness = mutableStateOf(false)
    val brightness = mutableFloatStateOf(0.5f)
    val volume = mutableFloatStateOf(0.5f)
    val ping = mutableStateOf<String?>(null)
    val pinging = mutableStateOf(false)
}

interface PanelActions {
    fun toggle()
    fun setAwake(on: Boolean)
    fun setDnd(on: Boolean)
    fun setBrightness(f: Float)
    fun setVolume(f: Float)
    fun ping()
    fun openApp()
    fun stop()
    fun grantWrite()
}

@Composable
fun PanelRoot(s: PanelState, a: PanelActions) {
    if (s.expanded.value) Panel(s, a) else Handle(s.side.value, a)
}

@Composable
private fun Handle(side: Side, a: PanelActions) {
    val shape = if (side == Side.LEFT) RoundedCornerShape(topEnd = 14.dp, bottomEnd = 14.dp) else RoundedCornerShape(topStart = 14.dp, bottomStart = 14.dp)
    var drag by remember { mutableFloatStateOf(0f) }
    Box(
        Modifier
            .size(width = 22.dp, height = 96.dp)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(Color(0xE6151822), Color(0xE607080B))))
            .border(1.dp, Brush.verticalGradient(Glfx.Holo), shape)
            .pointerInput(Unit) { detectTapGestures(onTap = { a.toggle() }) }
            .pointerInput(side) {
                detectHorizontalDragGestures(
                    onDragEnd = { if (abs(drag) > 24f && (drag > 0f) == (side == Side.LEFT)) a.toggle(); drag = 0f },
                    onDragCancel = { drag = 0f }
                ) { _, d -> drag += d }
            },
        contentAlignment = Alignment.Center
    ) {
        Box(Modifier.size(width = 3.dp, height = 36.dp).clip(RoundedCornerShape(2.dp)).background(Brush.verticalGradient(listOf(Glfx.Ice, Glfx.Violet))))
    }
}

@Composable
private fun Panel(s: PanelState, a: PanelActions) {
    val shape = RoundedCornerShape(24.dp)
    val snap = s.snap.value
    Column(
        Modifier
            .padding(8.dp)
            .fillMaxSize()
            .clip(shape)
            .background(Brush.verticalGradient(listOf(Color(0xF2151822), Color(0xF207080B))))
            .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.28f), Color.White.copy(alpha = 0.05f))), shape)
            .verticalScroll(rememberScrollState())
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("GLFX", style = GType.Title)
                Text("Game panel", style = GType.Label)
            }
            Chip("Hide", false) { a.toggle() }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Tile("Battery", snap?.let { if (it.batteryPct >= 0) "${it.batteryPct}%" else "n/a" } ?: "…", Modifier.weight(1f))
            Tile("Temp", snap?.batteryTemp?.let { "%.1f °C".format(it) } ?: "n/a", Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Tile("Free memory", snap?.let { "${it.ramAvail / (1024 * 1024)} MB" } ?: "…", Modifier.weight(1f))
            Tile("Thermal", snap?.let { if (it.thermalStatus == null) "n/a" else it.thermalLabel } ?: "…", Modifier.weight(1f))
        }
        if (snap != null) Text(Advisor.thermal(snap).first(), style = GType.Label)

        ToggleRow("Keep screen awake", "Screen will not sleep while this panel is on screen.", s.awake.value) { a.setAwake(it) }
        ToggleRow(
            "Silence notifications",
            if (s.canDnd.value) "Only priority interruptions. Undone when the panel stops." else "Needs Do Not Disturb access. Turn on to open the setting.",
            s.dnd.value
        ) { a.setDnd(it) }

        SliderRow(
            "Brightness", s.brightness.floatValue, 0.05f..1f, s.canWrite.value, "Allow in Settings", { a.grantWrite() }, { a.setBrightness(it) },
            if (s.canWrite.value && s.autoBrightness.value) "Auto-brightness is on, so Android may change this back." else null
        )
        SliderRow("Media volume", s.volume.floatValue, 0f..1f, true, "", {}, { a.setVolume(it) }, null)

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GhostButton(if (s.pinging.value) "Testing…" else "Test connection", { if (!s.pinging.value) a.ping() })
            Text(s.ping.value ?: "", style = GType.Label, modifier = Modifier.weight(1f))
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PrimaryButton("Open GLFX", { a.openApp() }, Modifier.weight(1f))
            GhostButton("Stop", { a.stop() }, Modifier.weight(1f))
        }
        Text("Not possible for an app without root: FPS counter inside games, touch sensitivity, CPU and GPU boost.", style = GType.Label)
    }
}

@Composable
private fun Tile(label: String, value: String, modifier: Modifier) {
    Column(modifier.glass(14.dp).padding(horizontal = 12.dp, vertical = 8.dp)) {
        Text(label, style = GType.Label)
        Text(value, style = GType.Title)
    }
}

@Composable
private fun ToggleRow(title: String, sub: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 8.dp)) {
            Text(title, style = GType.Body)
            Text(sub, style = GType.Label)
        }
        Switch(checked, onChange, colors = SwitchDefaults.colors(checkedTrackColor = Glfx.Ice, checkedThumbColor = Glfx.Ink))
    }
}

@Composable
private fun SliderRow(
    title: String, value: Float, range: ClosedFloatingPointRange<Float>, enabled: Boolean,
    lockedText: String, onGrant: () -> Unit, onChange: (Float) -> Unit, note: String?
) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(title, style = GType.Body)
            if (!enabled) Text(lockedText, style = GType.Label.copy(color = Glfx.Ice), modifier = Modifier.clickable { onGrant() })
        }
        Slider(
            value, onChange, enabled = enabled, valueRange = range,
            colors = SliderDefaults.colors(thumbColor = Glfx.Ice, activeTrackColor = Glfx.Ice, inactiveTrackColor = Color.White.copy(alpha = 0.14f))
        )
        if (note != null) Text(note, style = GType.Label)
    }
}
