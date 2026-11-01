package com.glfx.gameturbo.feature.optimization

import android.app.Activity
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.view.WindowManager
import com.glfx.gameturbo.core.device.DeviceCollector
import com.glfx.gameturbo.core.monitoring.Snapshot
import com.glfx.gameturbo.core.monitoring.SystemMonitor
import com.glfx.gameturbo.core.performance.Advisor
import com.glfx.gameturbo.core.performance.DndController
import org.json.JSONArray
import org.json.JSONObject

enum class Mode(val label: String, val short: String, val blurb: String) {
    PERFORMANCE("Performance", "Performance", "Highest refresh rate for GLFX windows, sustained performance where supported, notifications silenced."),
    BALANCED("Balanced", "Balanced", "Keeps the screen awake while GLFX is open and leaves everything else at system defaults."),
    BATTERY("Battery-friendly", "Battery", "Prefers a 60 Hz window and skips keep-awake to save charge."),
    THERMAL("Thermal-aware", "Thermal", "Prefers a 60 Hz window, asks for sustained performance and silences notifications. Used automatically when the device is hot.")
}

data class Act(val title: String, val detail: String)

data class Report(
    val time: Long, val mode: Mode, val requested: Mode, val game: String?,
    val applied: List<Act>, val skipped: List<Act>, val unsupported: List<String>, val advice: List<String>,
    val ramBeforeMb: Long, val ramAfterMb: Long, val thermalBefore: String, val thermalAfter: String,
    val battBefore: Int, val battAfter: Int
) {
    fun toJson(): JSONObject {
        fun acts(l: List<Act>) = JSONArray().also { a -> l.forEach { a.put(JSONObject().put("t", it.title).put("d", it.detail)) } }
        return JSONObject().apply {
            put("time", time); put("mode", mode.name); put("requested", requested.name); put("game", game ?: JSONObject.NULL)
            put("applied", acts(applied)); put("skipped", acts(skipped))
            put("unsupported", JSONArray(unsupported)); put("advice", JSONArray(advice))
            put("ramB", ramBeforeMb); put("ramA", ramAfterMb); put("thB", thermalBefore); put("thA", thermalAfter)
            put("batB", battBefore); put("batA", battAfter)
        }
    }

    companion object {
        fun fromJson(o: JSONObject): Report {
            fun acts(a: JSONArray?) = (0 until (a?.length() ?: 0)).map { Act(a!!.getJSONObject(it).getString("t"), a.getJSONObject(it).getString("d")) }
            fun strs(a: JSONArray?) = (0 until (a?.length() ?: 0)).map { a!!.getString(it) }
            return Report(
                o.getLong("time"), Mode.valueOf(o.getString("mode")), Mode.valueOf(o.getString("requested")),
                if (o.isNull("game")) null else o.getString("game"),
                acts(o.optJSONArray("applied")), acts(o.optJSONArray("skipped")), strs(o.optJSONArray("unsupported")), strs(o.optJSONArray("advice")),
                o.getLong("ramB"), o.getLong("ramA"), o.getString("thB"), o.getString("thA"), o.getInt("batB"), o.getInt("batA")
            )
        }
    }
}

class OptimizeEngine(private val ctx: Context, private val monitor: SystemMonitor) {
    private var active = false
    private var dndByEngine = false
    private var prevDisplayMode: Int? = null
    private var sustainedOn = false
    private var keepAwakeOn = false

    val isActive: Boolean get() = active

    fun run(activity: Activity, requested: Mode, game: String?): Report {
        if (active) restore(activity)
        val before = monitor.read()
        val applied = ArrayList<Act>()
        val skipped = ArrayList<Act>()
        var mode = requested

        if (requested == Mode.PERFORMANCE && (before.thermalStatus ?: 0) >= 3) {
            mode = Mode.THERMAL
            skipped += Act("Performance mode", "Thermal status is ${before.thermalLabel}. Switched to Thermal-aware mode to protect the device.")
        }

        val w = activity.window

        // 1. Keep screen awake (GLFX window only, fully reversible)
        if (mode == Mode.BALANCED || mode == Mode.PERFORMANCE) {
            w.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON); keepAwakeOn = true
            applied += Act("Keep screen awake", "The screen will not sleep while GLFX is open.")
        } else skipped += Act("Keep screen awake", "Not used in ${mode.label} mode to save battery.")

        // 2. Preferred display mode for GLFX windows
        if (Build.VERSION.SDK_INT >= 23) {
            val (modes, hz) = DeviceCollector.displayInfo(ctx)
            val cur = modes.firstOrNull { it.hz == hz }
            val same = if (cur != null) modes.filter { it.width == cur.width && it.height == cur.height } else modes
            val target = when (mode) {
                Mode.PERFORMANCE -> same.maxByOrNull { it.hz }
                Mode.BATTERY, Mode.THERMAL -> same.filter { it.hz >= 55f }.minByOrNull { it.hz }
                Mode.BALANCED -> null
            }
            if (target != null && same.size > 1) {
                val lp = w.attributes
                prevDisplayMode = lp.preferredDisplayModeId
                lp.preferredDisplayModeId = target.id
                w.attributes = lp
                applied += Act("Refresh rate preference", "GLFX windows now ask for ${target.hz.toInt()} Hz. Other apps are not changed.")
            } else skipped += Act("Refresh rate preference", if (mode == Mode.BALANCED) "Left at the system default." else "This display offers only one suitable mode.")
        } else skipped += Act("Refresh rate preference", "Needs Android 6 or newer.")

        // 3. Sustained performance
        if (mode == Mode.PERFORMANCE || mode == Mode.THERMAL) {
            val pm = ctx.getSystemService(Context.POWER_SERVICE) as PowerManager
            if (Build.VERSION.SDK_INT >= 24 && pm.isSustainedPerformanceModeSupported) {
                w.setSustainedPerformanceMode(true); sustainedOn = true
                applied += Act("Sustained performance window", "Requested steady performance for the GLFX window.")
            } else skipped += Act("Sustained performance window", "This device does not support it.")
        }

        // 4. Silence notifications (needs the user's Do Not Disturb access)
        if (mode == Mode.PERFORMANCE || mode == Mode.THERMAL) {
            if (Build.VERSION.SDK_INT < 23) skipped += Act("Silence notifications", "Needs Android 6 or newer.")
            else if (DndController.enable(ctx)) {
                dndByEngine = true
                applied += Act("Silence notifications", "Only priority interruptions will come through. Ends when you end the session.")
            } else skipped += Act("Silence notifications", "Needs Do Not Disturb access. Grant it in Access.")
        }

        active = applied.isNotEmpty()
        val after = monitor.read()
        val advice = ArrayList<String>().apply {
            addAll(Advisor.ram(after)); addAll(Advisor.thermal(after)); addAll(Advisor.battery(after))
        }
        if (applied.isEmpty()) advice.add(0, "No change was made on this device. GLFX does not report success when nothing was done.")

        return Report(
            System.currentTimeMillis(), mode, requested, game, applied, skipped, UNSUPPORTED, advice,
            before.ramAvail / MB, after.ramAvail / MB, before.thermalLabel, after.thermalLabel, before.batteryPct, after.batteryPct
        )
    }

    /** Undoes every change made by [run]. Safe to call when nothing is active. */
    fun restore(activity: Activity) {
        val w = activity.window
        if (keepAwakeOn) w.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (Build.VERSION.SDK_INT >= 23) prevDisplayMode?.let { val lp = w.attributes; lp.preferredDisplayModeId = it; w.attributes = lp }
        if (sustainedOn && Build.VERSION.SDK_INT >= 24) w.setSustainedPerformanceMode(false)
        if (dndByEngine) { DndController.restore(ctx); dndByEngine = false }
        keepAwakeOn = false; prevDisplayMode = null; sustainedOn = false; active = false
    }

    companion object {
        private const val MB = 1024L * 1024L
        val UNSUPPORTED = listOf(
            "CPU frequency or governor control: needs root.",
            "GPU clock control and FPS unlocking: no public API.",
            "Global touch sensitivity: no supported API.",
            "Killing background apps: intentionally not done.",
            "Carrier or ISP routing: not possible from an app.",
            "Thermal limit override: never done."
        )
    }
}
