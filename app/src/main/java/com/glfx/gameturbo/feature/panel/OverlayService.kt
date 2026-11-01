package com.glfx.gameturbo.feature.panel

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.ui.platform.ComposeView
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.ViewTreeSavedStateRegistryOwner
import com.glfx.gameturbo.MainActivity
import com.glfx.gameturbo.R
import com.glfx.gameturbo.core.design.GlfxTheme
import com.glfx.gameturbo.core.monitoring.SystemMonitor
import com.glfx.gameturbo.core.network.NetProbe
import com.glfx.gameturbo.core.performance.BrightnessController
import com.glfx.gameturbo.core.performance.DndController
import com.glfx.gameturbo.core.storage.Store
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Visible foreground service that draws the GLFX side panel over other apps.
 * It starts only when the user asks, shows a notification with a Stop button, and undoes its changes when it ends.
 */
class OverlayService : Service(), LifecycleOwner, SavedStateRegistryOwner {
    private val registry = LifecycleRegistry(this)
    private val ssc = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle get() = registry
    override val savedStateRegistry: SavedStateRegistry get() = ssc.savedStateRegistry

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val state = PanelState()
    private val wm by lazy { getSystemService(Context.WINDOW_SERVICE) as WindowManager }
    private val audio by lazy { getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    private lateinit var monitor: SystemMonitor
    private var view: ComposeView? = null
    private var sampling: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ssc.performAttach()
        ssc.performRestore(null)
        registry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        monitor = SystemMonitor(this)
        state.side.value = runCatching { Side.valueOf(Store(this).panelSide) }.getOrDefault(Side.LEFT)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startAsForeground()
        if (intent?.action == ACTION_STOP || !overlayAllowed()) { stopSelf(); return START_NOT_STICKY }
        if (view == null) addPanel()
        running.value = true
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        sampling?.cancel()
        scope.cancel()
        view?.let { runCatching { wm.removeView(it) } }
        view = null
        DndController.restore(this)
        BrightnessController.restore(this)
        registry.currentState = Lifecycle.State.DESTROYED
        running.value = false
        super.onDestroy()
    }

    private fun overlayAllowed() = Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(this)

    private fun addPanel() {
        refreshPerms()
        val v = ComposeView(this)
        v.setViewTreeLifecycleOwner(this)
        ViewTreeSavedStateRegistryOwner.set(v, this)
        v.setContent { GlfxTheme { PanelRoot(state, actions) } }
        registry.currentState = Lifecycle.State.RESUMED
        wm.addView(v, params())
        view = v
    }

    private fun params(): WindowManager.LayoutParams {
        val dm = resources.displayMetrics
        val expanded = state.expanded.value
        @Suppress("DEPRECATION")
        val type = if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE
        var flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
        if (state.awake.value) flags = flags or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        val width = if (expanded) minOf((338 * dm.density).toInt(), (dm.widthPixels * 0.6f).toInt()) else WindowManager.LayoutParams.WRAP_CONTENT
        val height = if (expanded) WindowManager.LayoutParams.MATCH_PARENT else WindowManager.LayoutParams.WRAP_CONTENT
        return WindowManager.LayoutParams(width, height, type, flags, PixelFormat.TRANSLUCENT).apply {
            gravity = (if (state.side.value == Side.LEFT) Gravity.START else Gravity.END) or Gravity.CENTER_VERTICAL
        }
    }

    private fun applyLayout() { view?.let { runCatching { wm.updateViewLayout(it, params()) } } }

    private fun refreshPerms() {
        state.canDnd.value = DndController.hasAccess(this)
        state.dnd.value = DndController.isActive(this)
        state.canWrite.value = BrightnessController.canWrite(this)
        state.autoBrightness.value = BrightnessController.isAuto(this)
        state.brightness.floatValue = (BrightnessController.current(this) / 255f).coerceIn(0.05f, 1f)
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        state.volume.floatValue = audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / max
    }

    private fun setExpanded(on: Boolean) {
        state.expanded.value = on
        applyLayout()
        sampling?.cancel()
        sampling = null
        if (on) {
            refreshPerms()
            // Sampling runs only while the panel is open.
            sampling = scope.launch { monitor.snapshots(1000).collect { state.snap.value = it } }
        }
    }

    private fun openSettings(action: String, withPackage: Boolean) {
        val i = Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (withPackage) i.data = Uri.parse("package:$packageName")
        runCatching { startActivity(i) }
        setExpanded(false)
    }

    private val actions = object : PanelActions {
        override fun toggle() = setExpanded(!state.expanded.value)

        override fun setAwake(on: Boolean) { state.awake.value = on; applyLayout() }

        override fun setDnd(on: Boolean) {
            if (on) {
                if (DndController.enable(this@OverlayService)) state.dnd.value = true
                else openSettings(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS, false)
            } else {
                DndController.restore(this@OverlayService); state.dnd.value = false
            }
        }

        override fun setBrightness(f: Float) {
            state.brightness.floatValue = f
            BrightnessController.set(this@OverlayService, (f * 255).roundToInt())
        }

        override fun setVolume(f: Float) {
            state.volume.floatValue = f
            val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            runCatching { audio.setStreamVolume(AudioManager.STREAM_MUSIC, (f * max).roundToInt(), 0) }
        }

        override fun ping() {
            scope.launch {
                state.pinging.value = true
                val r = NetProbe.run(count = 6)
                state.ping.value = if (r.samples.isEmpty()) "No connection" else "${r.avgMs.toInt()} ms, jitter ${r.jitterMs.toInt()}, lost ${r.lost} of ${r.sent}"
                state.pinging.value = false
            }
        }

        override fun openApp() {
            runCatching {
                startActivity(Intent(this@OverlayService, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP))
            }
            setExpanded(false)
        }

        override fun stop() { stopSelf() }

        override fun grantWrite() { openSettings(Settings.ACTION_MANAGE_WRITE_SETTINGS, true) }
    }

    private fun startAsForeground() {
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Game panel", NotificationManager.IMPORTANCE_LOW))
        }
        val immutable = if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or immutable)
        val stop = PendingIntent.getService(this, 1, Intent(this, OverlayService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_UPDATE_CURRENT or immutable)
        val n: Notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("GLFX game panel is on")
            .setContentText("Tap to open GLFX. Stop removes the panel and undoes its changes.")
            .setOngoing(true)
            .setContentIntent(open)
            .addAction(0, "Stop", stop)
            .build()
        ServiceCompat.startForeground(this, NOTIFICATION_ID, n, if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0)
    }

    companion object {
        val running = MutableStateFlow(false)
        private const val CHANNEL = "glfx_panel"
        private const val NOTIFICATION_ID = 1001
        private const val ACTION_STOP = "com.glfx.gameturbo.STOP_PANEL"

        fun start(ctx: Context) { ContextCompat.startForegroundService(ctx, Intent(ctx, OverlayService::class.java)) }
        fun stop(ctx: Context) { ctx.stopService(Intent(ctx, OverlayService::class.java)) }
    }
}
