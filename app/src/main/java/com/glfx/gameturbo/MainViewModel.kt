package com.glfx.gameturbo

import android.Manifest
import android.app.Activity
import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.glfx.gameturbo.core.device.DeviceCollector
import com.glfx.gameturbo.core.device.DeviceProfile
import com.glfx.gameturbo.core.monitoring.Snapshot
import com.glfx.gameturbo.core.monitoring.SystemMonitor
import com.glfx.gameturbo.core.network.NetInfo
import com.glfx.gameturbo.core.network.NetProbe
import com.glfx.gameturbo.core.network.NetResult
import com.glfx.gameturbo.core.performance.BrightnessController
import com.glfx.gameturbo.core.performance.DndController
import com.glfx.gameturbo.core.storage.Store
import com.glfx.gameturbo.feature.benchmark.FrameStats
import com.glfx.gameturbo.feature.games.Game
import com.glfx.gameturbo.feature.games.GameRepo
import com.glfx.gameturbo.feature.optimization.Act
import com.glfx.gameturbo.feature.optimization.Mode
import com.glfx.gameturbo.feature.optimization.OptimizeEngine
import com.glfx.gameturbo.feature.optimization.Report
import com.glfx.gameturbo.feature.panel.OverlayService
import com.glfx.gameturbo.feature.panel.Side
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject

class MainViewModel(private val app: Application) : AndroidViewModel(app) {
    val store = Store(app)
    private val monitor = SystemMonitor(app)
    private val engine = OptimizeEngine(app, monitor)
    private val games = GameRepo(app)
    private var job: Job? = null

    private val _history = MutableStateFlow<List<Snapshot>>(emptyList())
    val history: StateFlow<List<Snapshot>> = _history
    val device = MutableStateFlow<DeviceProfile?>(null)
    val apps = MutableStateFlow<List<Game>>(emptyList())
    val userGames = MutableStateFlow(store.userGames())
    val gameModes = MutableStateFlow(store.gameModes())
    val selectedGame = MutableStateFlow<String?>(null)
    val mode = MutableStateFlow(runCatching { Mode.valueOf(store.activeMode) }.getOrDefault(Mode.BALANCED))
    val immersive = MutableStateFlow(store.immersive)
    val panelAuto = MutableStateFlow(store.panelAuto)
    val panelSide = MutableStateFlow(runCatching { Side.valueOf(store.panelSide) }.getOrDefault(Side.LEFT))
    val panelRunning: StateFlow<Boolean> = OverlayService.running
    val dndGranted = MutableStateFlow(false)
    val overlayGranted = MutableStateFlow(true)
    val writeGranted = MutableStateFlow(true)
    val notifGranted = MutableStateFlow(true)
    val report = MutableStateFlow<Report?>(null)
    val sessionActive = MutableStateFlow(false)
    val records = MutableStateFlow<List<Report>>(emptyList())
    val frameRuns = MutableStateFlow<List<JSONObject>>(emptyList())
    val netRuns = MutableStateFlow<List<JSONObject>>(emptyList())
    val netResult = MutableStateFlow<NetResult?>(null)
    val netInfo = MutableStateFlow<NetInfo?>(null)
    val netBusy = MutableStateFlow(false)
    val message = MutableStateFlow<String?>(null)

    init {
        viewModelScope.launch(Dispatchers.Default) { device.value = DeviceCollector.collect(app) }
        // Crash recovery: undo anything a previous session left behind.
        if (!OverlayService.running.value) { DndController.restore(app); BrightnessController.restore(app) }
        reloadStored()
        refreshAll()
        viewModelScope.launch { OverlayService.running.collect { updateSession() } }
    }

    override fun onCleared() {
        if (!OverlayService.running.value) { DndController.restore(app); BrightnessController.restore(app) }
    }

    private fun updateSession() { sessionActive.value = engine.isActive || OverlayService.running.value }

    private fun reloadStored() {
        records.value = store.list("records").mapNotNull { runCatching { Report.fromJson(it) }.getOrNull() }
        frameRuns.value = store.list("frame")
        netRuns.value = store.list("net")
        userGames.value = store.userGames(); gameModes.value = store.gameModes()
        mode.value = runCatching { Mode.valueOf(store.activeMode) }.getOrDefault(Mode.BALANCED)
        immersive.value = store.immersive
        panelAuto.value = store.panelAuto
        panelSide.value = runCatching { Side.valueOf(store.panelSide) }.getOrDefault(Side.LEFT)
    }

    /** Sampling runs only while the app is visible. Nothing runs in the background. */
    fun setMonitoring(on: Boolean) {
        job?.cancel()
        job = if (on) viewModelScope.launch { monitor.snapshots(1000).collect { s -> _history.value = (_history.value + s).takeLast(120) } } else null
    }

    fun refreshAll() {
        refreshPermissions()
        netInfo.value = NetProbe.info(app)
        viewModelScope.launch(Dispatchers.Default) { apps.value = games.launchables() }
    }

    fun refreshPermissions() {
        dndGranted.value = DndController.hasAccess(app)
        overlayGranted.value = Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(app)
        writeGranted.value = BrightnessController.canWrite(app)
        notifGranted.value = Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(app, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }

    fun setMode(m: Mode) { mode.value = m; store.activeMode = m.name }
    fun setImmersive(on: Boolean) { immersive.value = on; store.immersive = on }
    fun setPanelAuto(on: Boolean) { panelAuto.value = on; store.panelAuto = on }
    fun setPanelSide(s: Side) { panelSide.value = s; store.panelSide = s.name }
    fun selectGame(pkg: String?) { selectedGame.value = pkg }

    fun setGameMode(pkg: String, m: Mode) { store.setGameMode(pkg, m.name); gameModes.value = store.gameModes() }
    fun toggleUserGame(pkg: String) {
        val s = userGames.value.toMutableSet()
        if (!s.add(pkg)) s.remove(pkg)
        store.setUserGames(s); userGames.value = s
    }

    fun optimize(activity: Activity) {
        refreshPermissions()
        val g = selectedGame.value
        val m = g?.let { gameModes.value[it] }?.let { runCatching { Mode.valueOf(it) }.getOrNull() } ?: mode.value
        var r = engine.run(activity, m, g)
        var panelStarted = false
        if (panelAuto.value) {
            if (overlayGranted.value) {
                OverlayService.start(app); panelStarted = true
                r = r.copy(applied = r.applied + Act("Side panel", "A handle on the screen edge opens quick controls over your game. Stop it from its notification or from GLFX."))
            } else {
                r = r.copy(skipped = r.skipped + Act("Side panel", "Needs Display over other apps. Grant it in Access."))
            }
        }
        report.value = r
        sessionActive.value = engine.isActive || panelStarted
        store.push("records", r.toJson(), 50)
        records.value = store.list("records").mapNotNull { runCatching { Report.fromJson(it) }.getOrNull() }
    }

    fun endSession(activity: Activity) {
        engine.restore(activity)
        OverlayService.stop(app)
        sessionActive.value = false
        message.value = "Session ended. All changes were undone."
    }

    fun showPanel() {
        refreshPermissions()
        if (!overlayGranted.value) {
            runCatching {
                app.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${app.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            message.value = "Allow “Display over other apps” for GLFX, then press Show panel again."
            return
        }
        OverlayService.start(app)
    }

    fun hidePanel() { OverlayService.stop(app) }

    fun launchGame(pkg: String) { if (!games.launch(pkg)) message.value = "Could not open this app." }

    fun runNetTest() {
        if (netBusy.value) return
        netBusy.value = true
        viewModelScope.launch {
            val r = NetProbe.run()
            netResult.value = r; netInfo.value = NetProbe.info(app)
            store.push("net", JSONObject().apply {
                put("time", System.currentTimeMillis()); put("avg", r.avgMs.toDouble()); put("jitter", r.jitterMs.toDouble())
                put("loss", r.lossPct.toDouble()); put("min", r.minMs); put("max", r.maxMs)
            }, 30)
            netRuns.value = store.list("net")
            netBusy.value = false
        }
    }

    fun saveFrameRun(s: FrameStats) {
        store.push("frame", s.toJson(System.currentTimeMillis()), 30)
        frameRuns.value = store.list("frame")
    }

    fun export(): String = store.exportJson()
    fun import(text: String): Boolean { val ok = store.importJson(text); if (ok) reloadStored(); return ok }
    fun clearAll() { store.clearAll(); reloadStored(); report.value = null; message.value = "All GLFX data was deleted." }
    fun toast(s: String) { message.value = s }
    fun clearMessage() { message.value = null }
}
