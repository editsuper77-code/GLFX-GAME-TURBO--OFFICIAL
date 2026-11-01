package com.glfx.gameturbo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.glfx.gameturbo.core.design.*

@Composable
fun ScreenHeader(title: String, subtitle: String? = null) {
    Column(Modifier.padding(bottom = 10.dp)) {
        Text(title, style = GType.Headline)
        if (subtitle != null) Text(subtitle, style = GType.Label)
    }
}

@Composable
fun ScreenFrame(title: String, subtitle: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        ScreenHeader(title, subtitle)
        content()
    }
}

@Composable
fun TwoPane(left: @Composable ColumnScope.() -> Unit, right: @Composable ColumnScope.() -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp), content = left)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp), content = right)
    }
}

@Composable
fun FactRow(label: String, value: String, valueColor: Color = Glfx.Text) {
    Column {
        Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
            Text(label, style = GType.Label, modifier = Modifier.padding(end = 12.dp))
            Text(value, style = GType.Body.copy(color = valueColor), textAlign = TextAlign.End, modifier = Modifier.weight(1f))
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.06f)))
    }
}

@Composable
fun FactsCard(title: String, rows: List<Pair<String, String>>) {
    GlassCard(Modifier.fillMaxWidth()) {
        Text(title, style = GType.Title, modifier = Modifier.padding(bottom = 4.dp))
        rows.forEach { FactRow(it.first, it.second) }
    }
}

@Composable
fun AdviceCard(items: List<String>, title: String = "What this means") {
    GlassCard(Modifier.fillMaxWidth()) {
        Text(title, style = GType.Title, modifier = Modifier.padding(bottom = 6.dp))
        items.forEach { Text(it, style = GType.Body.copy(color = Glfx.Muted), modifier = Modifier.padding(vertical = 3.dp)) }
    }
}

@Composable
fun ReadoutCard(label: String, value: String, unit: String, color: Color, values: List<Float>, max: Float = 1f, min: Float = 0f, note: String? = null) {
    GlassCard(Modifier.fillMaxWidth()) {
        Text(label, style = GType.Label)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, style = GType.Display.copy(color = color))
            Text(unit, style = GType.Title.copy(color = Glfx.Muted), modifier = Modifier.padding(start = 6.dp, bottom = 10.dp))
        }
        Sparkline(values, color, Modifier.fillMaxWidth().height(90.dp), min = min, max = max)
        if (note != null) Text(note, style = GType.Label, modifier = Modifier.padding(top = 8.dp))
    }
}

fun mb(bytes: Long) = "${bytes / (1024 * 1024)} MB"
fun pct(f: Float?) = if (f == null) "n/a" else "${(f * 100).toInt()}%"
