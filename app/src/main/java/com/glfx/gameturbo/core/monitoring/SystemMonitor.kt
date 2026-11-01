package com.glfx.gameturbo.core.monitoring

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.os.Process
import android.os.SystemClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File

data class Snapshot(
    val t: Long,
    val cpuSystem: Float?,
    val cpuApp: Float?,
    val freqMhz: List<Int>,
    val zones: List<Pair<String, Float>>,
    val ramTotal: Long,
    val ramAvail: Long,
    val ramThreshold: Long,
    val lowMemory: Boolean,
    val appPssKb: Long,
    val batteryPct: Int,
    val charging: Boolean,
    val plug: String,
    val batteryTemp: Float?,
    val batteryHealth: String,
    val voltageMv: Int,
    val tech: String,
    val thermalStatus: Int?,
    val thermalLabel: String,
    val headroom: Float?
) {
    val ramUsedFrac: Float get() = if (ramTotal > 0) 1f - ramAvail.toFloat() / ramTotal else 0f
    val cpuBest: Float? get() = cpuSystem ?: cpuApp
}

class SystemMonitor(private val ctx: Context) {
    private val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    private val pm = ctx.getSystemService(Context.POWER_SERVICE) as PowerManager
    private val cores = Runtime.getRuntime().availableProcessors()
    private var primed = false
    private var prevTotal = 0L
    private var prevIdle = 0L
    private var prevCpuMs = 0L
    private var prevWall = 0L
    private val zoneIdx: List<Pair<Int, String>> by lazy { discoverZones() }

    fun snapshots(periodMs: Long = 1000L): Flow<Snapshot> =
        flow { while (true) { emit(read()); delay(periodMs) } }.flowOn(Dispatchers.Default)

    @Synchronized
    fun read(): Snapshot {
        val mi = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        val pss = runCatching { am.getProcessMemoryInfo(intArrayOf(Process.myPid()))[0].totalPss.toLong() }.getOrDefault(0L)

        val sys = readProcStat()
        val nowCpu = Process.getElapsedCpuTime()
        val nowWall = SystemClock.elapsedRealtime()
        val app = if (primed && nowWall > prevWall)
            ((nowCpu - prevCpuMs).toFloat() / ((nowWall - prevWall) * cores)).coerceIn(0f, 1f) else null
        prevCpuMs = nowCpu; prevWall = nowWall; primed = true

        val b: Intent? = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = b?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = b?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val status = b?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val plugged = b?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        val tRaw = b?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE) ?: Int.MIN_VALUE
        val health = b?.getIntExtra(BatteryManager.EXTRA_HEALTH, 0) ?: 0

        val ts = if (Build.VERSION.SDK_INT >= 29) pm.currentThermalStatus else null
        val head = if (Build.VERSION.SDK_INT >= 30) pm.getThermalHeadroom(10).takeIf { !it.isNaN() } else null

        return Snapshot(
            t = System.currentTimeMillis(),
            cpuSystem = sys,
            cpuApp = app,
            freqMhz = readFreqs(),
            zones = readZones(),
            ramTotal = mi.totalMem,
            ramAvail = mi.availMem,
            ramThreshold = mi.threshold,
            lowMemory = mi.lowMemory,
            appPssKb = pss,
            batteryPct = if (level >= 0 && scale > 0) level * 100 / scale else -1,
            charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL,
            plug = when {
                plugged and BatteryManager.BATTERY_PLUGGED_AC != 0 -> "AC charger"
                plugged and BatteryManager.BATTERY_PLUGGED_USB != 0 -> "USB"
                plugged and BatteryManager.BATTERY_PLUGGED_WIRELESS != 0 -> "Wireless"
                else -> "Not plugged in"
            },
            batteryTemp = if (tRaw == Int.MIN_VALUE) null else tRaw / 10f,
            batteryHealth = when (health) {
                BatteryManager.BATTERY_HEALTH_GOOD -> "Good"
                BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheated"
                BatteryManager.BATTERY_HEALTH_DEAD -> "Dead"
                BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Over voltage"
                BatteryManager.BATTERY_HEALTH_COLD -> "Cold"
                BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "Failure"
                else -> "Not reported"
            },
            voltageMv = b?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0,
            tech = b?.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY).orEmpty().ifBlank { "Not reported" },
            thermalStatus = ts,
            thermalLabel = if (ts == null) "Not reported (Android 10+)" else THERMAL_LABELS.getOrElse(ts) { "Unknown" },
            headroom = head
        )
    }

    private fun readProcStat(): Float? = try {
        val line = File("/proc/stat").bufferedReader().use { it.readLine() }
        val p = line?.trim()?.split(Regex("\\s+"))?.drop(1)?.mapNotNull { it.toLongOrNull() }
        if (p == null || p.size < 5) null else {
            val idle = p[3] + p[4]
            val total = p.sum()
            val first = prevTotal == 0L
            val dT = total - prevTotal
            val dI = idle - prevIdle
            prevTotal = total; prevIdle = idle
            if (first || dT <= 0) null else (1f - dI.toFloat() / dT).coerceIn(0f, 1f)
        }
    } catch (t: Throwable) { null }

    private fun readFreqs(): List<Int> {
        val out = ArrayList<Int>()
        for (i in 0 until cores) {
            val v = runCatching { File("/sys/devices/system/cpu/cpu$i/cpufreq/scaling_cur_freq").readText().trim().toLong() }.getOrNull()
            if (v != null) out += (v / 1000).toInt()
        }
        return out
    }

    private fun discoverZones(): List<Pair<Int, String>> {
        val out = ArrayList<Pair<Int, String>>()
        for (i in 0 until 40) {
            val t = runCatching { File("/sys/class/thermal/thermal_zone$i/temp").readText().trim().toFloat() }.getOrNull() ?: continue
            val name = runCatching { File("/sys/class/thermal/thermal_zone$i/type").readText().trim() }.getOrDefault("zone$i")
            val c = if (t > 1000) t / 1000f else t
            if (c in 0f..130f) out += i to name
        }
        return out.take(8)
    }

    private fun readZones(): List<Pair<String, Float>> = zoneIdx.mapNotNull { (i, name) ->
        val t = runCatching { File("/sys/class/thermal/thermal_zone$i/temp").readText().trim().toFloat() }.getOrNull() ?: return@mapNotNull null
        name to (if (t > 1000) t / 1000f else t)
    }

    companion object {
        val THERMAL_LABELS = listOf("None", "Light", "Moderate", "Severe", "Critical", "Emergency", "Shutdown")
    }
}
