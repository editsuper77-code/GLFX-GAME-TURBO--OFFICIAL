package com.glfx.gameturbo.ui

import android.app.Activity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.glfx.gameturbo.MainViewModel
import com.glfx.gameturbo.core.design.*
import com.glfx.gameturbo.core.modules.Category
import com.glfx.gameturbo.core.modules.ModuleCatalog
import com.glfx.gameturbo.core.modules.Status
import com.glfx.gameturbo.feature.benchmark.FrameStats
import com.glfx.gameturbo.feature.optimization.Mode
import com.glfx.gameturbo.feature.optimization.Report
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun AppIcon(pkg: String, size: androidx.compose.ui.unit.Dp) {
    val ctx = LocalContext.current
    val bmp by produceState<ImageBitmap?>(null, pkg) {
        value = withContext(Dispatchers.IO) { runCatching { ctx.packageManager.getApplicationIcon(pkg).toBitmap(96, 96).asImageBitmap() }.getOrNull() }
    }
    val b = bmp
    if (b != null) Image(b, null, Modifier.size(size).clip(RoundedCornerShape(12.dp)))
    else Box(Modifier.size(size).clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.08f)))
}

@Composable
fun GamesScreen(vm: MainViewModel, go: (Screen) -> Unit) {
    val apps by vm.apps.collectAsStateWithLifecycle()
    val ug by vm.userGames.collectAsStateWithLifecycle()
    val modes by vm.gameModes.collectAsStateWithLifecycle()
    val sel by vm.selectedGame.collectAsStateWithLifecycle()
    val def by vm.mode.collectAsStateWithLifecycle()
    var adding by remember { mutableStateOf(false) }
    val library = apps.filter { it.detected || it.pkg in ug }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
            ScreenHeader("Game library", "${library.size} games. Only apps that show in your launcher are visible to GLFX.")
            GhostButton("Add an app", { adding = true })
        }
        if (library.isEmpty()) {
            AdviceCard(listOf("No games were detected. Tap Add an app to choose games yourself."), "Library is empty")
        } else LazyVerticalGrid(GridCells.Adaptive(250.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(library, key = { it.pkg }) { g ->
                val m = modes[g.pkg]?.let { runCatching { Mode.valueOf(it) }.getOrNull() } ?: def
                GlassCard(Modifier.fillMaxWidth(), onClick = { vm.selectGame(if (sel == g.pkg) null else g.pkg) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AppIcon(g.pkg, 48.dp)
                        Column(Modifier.padding(start = 12.dp)) {
                            Text(g.name, style = GType.Title, maxLines = 1)
                            Text(if (sel == g.pkg) "Selected for optimization" else if (g.detected) "Detected game" else "Added by you", style = GType.Label.copy(color = if (sel == g.pkg) Glfx.Ice else Glfx.Muted))
                        }
                    }
                    Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Chip(m.short, true) { vm.setGameMode(g.pkg, Mode.values()[(m.ordinal + 1) % Mode.values().size]) }
                        Chip("Launch", false) { vm.launchGame(g.pkg) }
                        if (!g.detected) Chip("Remove", false) { vm.toggleUserGame(g.pkg) }
                    }
                }
            }
        }
    }

    if (adding) {
        val candidates = apps.filter { !it.detected && it.pkg !in ug }
        AlertDialog(
            onDismissRequest = { adding = false }, containerColor = Glfx.Graphite,
            confirmButton = { TextButton({ adding = false }) { Text("Done", style = GType.Title.copy(color = Glfx.Ice)) } },
            title = { Text("Add an app", style = GType.Headline) },
            text = {
                LazyColumn(Modifier.heightIn(max = 260.dp)) {
                    items(candidates, key = { it.pkg }) { a ->
                        Row(Modifier.fillMaxWidth().clickable { vm.toggleUserGame(a.pkg) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            AppIcon(a.pkg, 36.dp); Text(a.name, style = GType.Body, modifier = Modifier.padding(start = 12.dp))
                        }
                    }
                }
            }
        )
    }
}

@Composable
fun ReportView(r: Report) {
    val fmt = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
    GlassCard(Modifier.fillMaxWidth()) {
        Text("${r.mode.label} mode, ${fmt.format(Date(r.time))}", style = GType.Title)
        if (r.mode != r.requested) Text("You asked for ${r.requested.label}. GLFX switched to protect the device.", style = GType.Label.copy(color = Glfx.Warn), modifier = Modifier.padding(top = 4.dp))
        Text("Done (${r.applied.size})", style = GType.Label.copy(color = Glfx.Good), modifier = Modifier.padding(top = 12.dp))
        if (r.applied.isEmpty()) Text("Nothing was changed.", style = GType.Body.copy(color = Glfx.Muted))
        r.applied.forEach { Text(it.title, style = GType.Body.copy(fontWeight = FontWeight.Medium), modifier = Modifier.padding(top = 6.dp)); Text(it.detail, style = GType.Label) }
        if (r.skipped.isNotEmpty()) {
            Text("Not done (${r.skipped.size})", style = GType.Label.copy(color = Glfx.Warn), modifier = Modifier.padding(top = 12.dp))
            r.skipped.forEach { Text(it.title, style = GType.Body.copy(fontWeight = FontWeight.Medium), modifier = Modifier.padding(top = 6.dp)); Text(it.detail, style = GType.Label) }
        }
    }
    FactsCard("Before and after", listOf(
        "Available memory" to "${r.ramBeforeMb} MB to ${r.ramAfterMb} MB", "Thermal" to "${r.thermalBefore} to ${r.thermalAfter}",
        "Battery" to "${r.battBefore}% to ${r.battAfter}%"
    ))
    AdviceCard(r.advice, "Advice")
    AdviceCard(r.unsupported, "Not possible without root or a system API")
}

@Composable
fun OptimizeScreen(vm: MainViewModel) {
    val activity = LocalContext.current as Activity
    val report by vm.report.collectAsStateWithLifecycle()
    val mode by vm.mode.collectAsStateWithLifecycle()
    val session by vm.sessionActive.collectAsStateWithLifecycle()
    val apps by vm.apps.collectAsStateWithLifecycle()
    val ug by vm.userGames.collectAsStateWithLifecycle()
    val sel by vm.selectedGame.collectAsStateWithLifecycle()
    val library = apps.filter { it.detected || it.pkg in ug }
    val panelOn by vm.panelRunning.collectAsStateWithLifecycle()

    ScreenFrame("Optimization center", "Runs only supported, reversible actions. The report lists what was done and what was not.") {
        TwoPane(
            left = {
                GlassCard(Modifier.fillMaxWidth()) {
                    Text("Mode", style = GType.Title)
                    Mode.values().forEach { m ->
                        Column(Modifier.fillMaxWidth().clickable { vm.setMode(m) }.padding(vertical = 8.dp)) {
                            Text(m.label, style = GType.Body.copy(color = if (m == mode) Glfx.Ice else Glfx.Text, fontWeight = FontWeight.Medium))
                            Text(m.blurb, style = GType.Label)
                        }
                    }
                }
                GlassCard(Modifier.fillMaxWidth()) {
                    Text("Game", style = GType.Title)
                    Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Chip("None", sel == null) { vm.selectGame(null) }
                        library.forEach { Chip(it.name, sel == it.pkg) { vm.selectGame(it.pkg) } }
                    }
                }
                GlassCard(Modifier.fillMaxWidth()) {
                    Text("Game side panel", style = GType.Title)
                    Text("A slim handle on the screen edge opens quick controls on top of any game: live stats, keep awake, notifications, brightness, volume and a connection test.", style = GType.Label, modifier = Modifier.padding(vertical = 6.dp))
                    if (panelOn) GhostButton("Hide panel", { vm.hidePanel() }) else GhostButton("Show panel", { vm.showPanel() })
                }
                PrimaryButton("Optimize now", { vm.optimize(activity) }, Modifier.fillMaxWidth())
                if (session) GhostButton("End session and restore", { vm.endSession(activity) }, Modifier.fillMaxWidth())
                sel?.let { pkg -> GhostButton("Launch ${library.firstOrNull { it.pkg == pkg }?.name ?: "game"}", { vm.launchGame(pkg) }, Modifier.fillMaxWidth()) }
                if (session) Text("Changes stay on until you return here and end the session. GLFX cannot see when a game closes.", style = GType.Label)
            },
            right = {
                if (report == null) AdviceCard(listOf("Press Optimize now. GLFX checks thermal state, memory, battery and permissions, then applies only what is safe and supported."), "No report yet")
                else ReportView(report!!)
            }
        )
    }
}

@Composable
fun ProfilesScreen(vm: MainViewModel) {
    val mode by vm.mode.collectAsStateWithLifecycle()
    ScreenFrame("Gaming profiles", "Each profile is a fixed set of app-level actions. Pick a default here, and set a mode per game in the library.") {
        Mode.values().forEach { m ->
            GlassCard(Modifier.fillMaxWidth(), onClick = { vm.setMode(m) }) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(m.label, style = GType.Title)
                    if (m == mode) Text("Default", style = GType.Label.copy(color = Glfx.Ice))
                }
                Text(m.blurb, style = GType.Body.copy(color = Glfx.Muted), modifier = Modifier.padding(top = 4.dp))
            }
        }
        AdviceCard(listOf("Performance switches to Thermal-aware on its own when Android reports a severe thermal state."), "Safety rule")
    }
}

@Composable
fun BenchmarkScreen(vm: MainViewModel) {
    var tab by remember { mutableStateOf(0) }
    ScreenFrame("Benchmark center", "Measured on GLFX's own test surfaces. Results compare only against this device.") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip("Frame timing", tab == 0) { tab = 0 }
            Chip("Touch lab", tab == 1) { tab = 1 }
        }
        if (tab == 0) FrameTest(vm) else TouchLab()
    }
}

@Composable
private fun FrameTest(vm: MainViewModel) {
    val dev by vm.device.collectAsStateWithLifecycle()
    val runs by vm.frameRuns.collectAsStateWithLifecycle()
    var running by remember { mutableStateOf(false) }
    var tick by remember { mutableLongStateOf(0L) }
    var elapsed by remember { mutableLongStateOf(0L) }
    var result by remember { mutableStateOf<FrameStats?>(null) }

    LaunchedEffect(running) {
        if (!running) return@LaunchedEffect
        val iv = ArrayList<Float>()
        var last = 0L
        var start = 0L
        while (true) {
            val t = withFrameNanos { it }
            if (start == 0L) start = t
            if (last != 0L) iv.add((t - last) / 1_000_000f)
            last = t; tick = t; elapsed = (t - start) / 1_000_000
            if (elapsed >= 5000) break
        }
        val s = FrameStats.compute(iv.drop(10), dev?.currentHz ?: 60f)
        result = s; vm.saveFrameRun(s); running = false
    }

    TwoPane(
        left = {
            Canvas(Modifier.fillMaxWidth().height(210.dp).glass()) {
                val t = tick / 1_000_000_000f
                if (running) for (i in 0 until 400) {
                    val x = (sin(t * 0.7f + i * 0.37f) * 0.5f + 0.5f) * size.width
                    val y = (cos(t * 0.9f + i * 0.53f) * 0.5f + 0.5f) * size.height
                    drawCircle(Glfx.Holo[i % 4].copy(alpha = 0.8f), 5f, Offset(x, y))
                }
            }
            PrimaryButton(if (running) "Running… ${(5 - elapsed / 1000).coerceAtLeast(0)} s left" else "Start 5-second test", { result = null; elapsed = 0; running = true }, enabled = !running)
            AdviceCard(listOf(
                "Close other apps and keep the phone still for repeatable results.",
                "This measures how smoothly this device draws GLFX's test scene. It says nothing about FPS in your games."
            ), "Limits")
        },
        right = {
            result?.let { s ->
                FactsCard("Result", listOf(
                    "Average" to "%.1f fps".format(s.avgFps), "Display refresh" to "${s.refreshHz.toInt()} Hz",
                    "95th percentile frame" to "%.1f ms".format(s.p95Ms), "99th percentile frame" to "%.1f ms".format(s.p99Ms),
                    "1% low" to "%.1f fps".format(s.low1Fps), "Dropped frames" to "${s.dropped} of ${s.frames}", "Jitter" to "%.2f ms".format(s.jitterMs)
                ))
            }
            if (runs.size > 1) GlassCard(Modifier.fillMaxWidth()) {
                Text("Average fps across your runs", style = GType.Title)
                Sparkline(runs.reversed().map { it.getDouble("avgFps").toFloat() }, Glfx.Ice, Modifier.fillMaxWidth().height(70.dp).padding(top = 8.dp), min = 0f, max = (runs.maxOf { it.getDouble("avgFps") }.toFloat() * 1.1f).coerceAtLeast(1f))
            }
            if (result == null && runs.isEmpty()) AdviceCard(listOf("No runs yet."), "Results")
        }
    )
}

@Composable
private fun TouchLab() {
    var points by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var rate by remember { mutableIntStateOf(0) }
    var maxPointers by remember { mutableIntStateOf(0) }
    val stamps = remember { ArrayDeque<Long>() }
    TwoPane(
        left = {
            Box(
                Modifier.fillMaxWidth().height(240.dp).glass().pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown()
                        do {
                            val e = awaitPointerEvent()
                            val now = e.changes.first().uptimeMillis
                            stamps.addLast(now)
                            while (stamps.isNotEmpty() && stamps.first() < now - 1000) stamps.removeFirst()
                            rate = stamps.size
                            val down = e.changes.filter { it.pressed }
                            if (down.size > maxPointers) maxPointers = down.size
                            points = (points + down.map { it.position }).takeLast(400)
                        } while (e.changes.any { it.pressed })
                    }
                }
            ) {
                Canvas(Modifier.fillMaxSize()) { points.forEachIndexed { i, p -> drawCircle(Glfx.Ice.copy(alpha = 0.15f + 0.6f * i / points.size), 9f, p) } }
                if (points.isEmpty()) Text("Touch here", style = GType.Label, modifier = Modifier.align(Alignment.Center))
            }
            GhostButton("Clear", { points = emptyList(); maxPointers = 0; rate = 0; stamps.clear() })
        },
        right = {
            FactsCard("Touch results", listOf("Events in the last second" to "$rate", "Most fingers at once" to "$maxPointers"))
            AdviceCard(listOf("Event rate shows how many touch events reached GLFX per second. Many phones report touches at the display refresh rate.", "GLFX cannot change touch sensitivity. Android offers no supported way to do that."), "What this means")
        }
    )
}

@Composable
fun HistoryScreen(vm: MainViewModel) {
    val recs by vm.records.collectAsStateWithLifecycle()
    var open by remember { mutableStateOf<Long?>(null) }
    val fmt = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Session history", "The last 50 optimization runs on this device.")
        if (recs.isEmpty()) AdviceCard(listOf("Run Optimize now and the report will be saved here."), "No sessions yet")
        else LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(recs, key = { it.time }) { r ->
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    GlassCard(Modifier.fillMaxWidth(), onClick = { open = if (open == r.time) null else r.time }) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("${r.mode.label}${r.game?.let { " · $it" } ?: ""}", style = GType.Title)
                            Text(fmt.format(Date(r.time)), style = GType.Label)
                        }
                        Text("${r.applied.size} done, ${r.skipped.size} not done", style = GType.Label)
                    }
                    if (open == r.time) ReportView(r)
                }
            }
        }
    }
}

@Composable
fun ModulesScreen(vm: MainViewModel) {
    val dev by vm.device.collectAsStateWithLifecycle()
    val dnd by vm.dndGranted.collectAsStateWithLifecycle()
    val overlay by vm.overlayGranted.collectAsStateWithLifecycle()
    val write by vm.writeGranted.collectAsStateWithLifecycle()
    val granted = buildSet {
        if (dnd) add(ModuleCatalog.DND)
        if (overlay) add(ModuleCatalog.OVERLAY)
        if (write) add(ModuleCatalog.WRITE)
    }
    var cat by remember { mutableStateOf<Category?>(null) }
    var open by remember { mutableStateOf<String?>(null) }
    val list = ModuleCatalog.all.filter { cat == null || it.category == cat }
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Module catalog", "${ModuleCatalog.all.size} of ${ModuleCatalog.TARGET} modules are built so far. Only real, working modules are listed.")
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.width(230.dp).glass(18.dp).verticalScroll(rememberScrollState()).padding(8.dp)) {
                CatRow("All", "${ModuleCatalog.all.size} / ${ModuleCatalog.TARGET}", cat == null) { cat = null }
                Category.values().forEach { c -> CatRow(c.label, "${ModuleCatalog.count(c)} / ${c.target}", cat == c) { cat = c } }
            }
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(list, key = { it.id }) { m ->
                    val st = ModuleCatalog.effectiveStatus(m, dev, granted)
                    val color = when (st) { Status.FULL -> Glfx.Good; Status.PARTIAL -> Glfx.Ice; Status.MONITOR -> Glfx.Violet; Status.PERMISSION -> Glfx.Warn; Status.UNSUPPORTED -> Glfx.Muted }
                    GlassCard(Modifier.fillMaxWidth(), onClick = { open = if (open == m.id) null else m.id }) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(m.name, style = GType.Title, modifier = Modifier.weight(1f))
                            Text("${st.code} · ${st.label}", style = GType.Label.copy(color = color))
                        }
                        Text(m.description, style = GType.Body.copy(color = Glfx.Muted), modifier = Modifier.padding(top = 4.dp))
                        if (open == m.id) {
                            Column(Modifier.padding(top = 8.dp)) {
                                FactRow("Module ID", m.id); FactRow("Category", m.category.label); FactRow("How it works", m.implementation)
                                FactRow("Needs Android", "API ${m.minApi}+"); FactRow("Permission", m.permission ?: "None")
                                FactRow("Undo", m.rollback ?: "Nothing to undo")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CatRow(label: String, count: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(if (selected) Glfx.Ice.copy(alpha = 0.14f) else Color.Transparent).clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 9.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = GType.Body.copy(color = if (selected) Glfx.Ice else Glfx.Text), modifier = Modifier.weight(1f))
        Text(count, style = GType.Label, modifier = Modifier.padding(start = 6.dp))
    }
}
