package com.glfx.gameturbo.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.glfx.gameturbo.MainViewModel
import com.glfx.gameturbo.core.design.*
import com.glfx.gameturbo.core.monitoring.Snapshot
import com.glfx.gameturbo.core.performance.Advisor
import java.text.DateFormat
import java.util.Date

@Composable
fun CpuScreen(vm: MainViewModel) {
    val h by vm.history.collectAsStateWithLifecycle()
    val d by vm.device.collectAsStateWithLifecycle()
    val s = h.lastOrNull()
    val source = if (s?.cpuSystem != null) "System-wide load from /proc/stat" else "GLFX's own CPU use. Android 8+ blocks system-wide load for normal apps."
    ScreenFrame("CPU", "Monitoring only. GLFX never changes CPU clocks or governors.") {
        TwoPane(
            left = {
                ReadoutCard(if (s?.cpuSystem != null) "CPU load" else "GLFX CPU share", pct(s?.cpuBest), "", Glfx.Ice, h.map { it.cpuBest ?: 0f }, note = source)
                AdviceCard(Advisor.cpu(h))
            },
            right = {
                FactsCard("Processor", listOf(
                    "Cores" to (d?.cores?.toString() ?: "…"), "SoC" to (d?.soc ?: "Not reported"),
                    "Hardware" to (d?.hardware ?: "…"), "Board" to (d?.board ?: "…"), "ABIs" to (d?.abis?.joinToString(", ") ?: "…")
                ))
                FactsCard("Core frequencies", if (s == null || s.freqMhz.isEmpty()) listOf("Status" to "Not readable on this device")
                else s.freqMhz.mapIndexed { i, f -> "Core $i" to "$f MHz" })
                if (s != null && s.zones.isNotEmpty()) FactsCard("Sensor zones", s.zones.map { it.first to "%.1f °C".format(it.second) })
            }
        )
    }
}

@Composable
fun GpuScreen(vm: MainViewModel) {
    val d by vm.device.collectAsStateWithLifecycle()
    val runs by vm.frameRuns.collectAsStateWithLifecycle()
    val last = runs.firstOrNull()
    ScreenFrame("GPU and display", "GPU load and clocks are not exposed to apps, so GLFX does not show them.") {
        TwoPane(
            left = {
                FactsCard("Graphics", listOf(
                    "Renderer" to (d?.gpuRenderer ?: "Not reported"), "Vendor" to (d?.gpuVendor ?: "Not reported"),
                    "OpenGL ES" to (d?.glEs ?: "…"), "Vulkan" to (d?.vulkan ?: "…")
                ))
                FactsCard("Last frame-pacing test", if (last == null) listOf("Status" to "Run one in Benchmark") else listOf(
                    "Average" to "%.1f fps".format(last.getDouble("avgFps")), "95th percentile" to "%.1f ms".format(last.getDouble("p95")),
                    "Dropped frames" to last.getInt("dropped").toString()
                ))
            },
            right = {
                FactsCard("Display", listOf("Current refresh rate" to (d?.let { "${it.currentHz.toInt()} Hz" } ?: "…")) +
                    (d?.displayModes?.map { "${it.width} × ${it.height}" to "${it.hz.toInt()} Hz" } ?: emptyList()))
                AdviceCard(listOf(
                    "Games choose their own frame rate. GLFX can only ask for a refresh rate for its own windows.",
                    "If a game stutters, lower its in-game graphics before anything else."
                ))
            }
        )
    }
}

@Composable
fun RamScreen(vm: MainViewModel) {
    val h by vm.history.collectAsStateWithLifecycle()
    val s = h.lastOrNull()
    ScreenFrame("Memory", "GLFX reports memory honestly. It does not kill apps.") {
        if (s == null) Text("Collecting the first sample…", style = GType.Body) else TwoPane(
            left = {
                ReadoutCard("Memory used", pct(s.ramUsedFrac), "", Glfx.Violet, h.map { it.ramUsedFrac }, note = "Pressure: ${Advisor.pressure(s)}")
                AdviceCard(Advisor.ram(s))
            },
            right = {
                FactsCard("Details", listOf(
                    "Total" to mb(s.ramTotal), "Available" to mb(s.ramAvail), "Low-memory threshold" to mb(s.ramThreshold),
                    "System says low" to if (s.lowMemory) "Yes" else "No", "GLFX uses" to "${s.appPssKb / 1024} MB"
                ))
            }
        )
    }
}

@Composable
fun ThermalScreen(vm: MainViewModel) {
    val h by vm.history.collectAsStateWithLifecycle()
    val s = h.lastOrNull()
    ScreenFrame("Thermal", "GLFX never disables or overrides thermal protection.") {
        if (s == null) Text("Collecting the first sample…", style = GType.Body) else TwoPane(
            left = {
                ReadoutCard("Battery temperature", s.batteryTemp?.let { "%.1f".format(it) } ?: "n/a", "°C", thermalLevel(s).color,
                    h.map { it.batteryTemp ?: 0f }, min = 20f, max = 50f, note = "Thermal status: ${s.thermalLabel}")
                AdviceCard(Advisor.thermal(s))
            },
            right = {
                FactsCard("Readings", listOf(
                    "Thermal status" to s.thermalLabel,
                    "Headroom" to (s.headroom?.let { "%.2f (1.0 means throttling)".format(it) } ?: "Not reported"),
                    "Charging" to if (s.charging) "Yes" else "No"
                ))
                if (s.zones.isNotEmpty()) FactsCard("Sensor zones", s.zones.map { it.first to "%.1f °C".format(it.second) })
            }
        )
    }
}

@Composable
fun BatteryScreen(vm: MainViewModel) {
    val h by vm.history.collectAsStateWithLifecycle()
    val s = h.lastOrNull()
    val first = h.firstOrNull()
    ScreenFrame("Battery", "GLFX cannot increase battery capacity. It only reports and advises.") {
        if (s == null) Text("Collecting the first sample…", style = GType.Body) else TwoPane(
            left = {
                ReadoutCard("Charge", if (s.batteryPct >= 0) "${s.batteryPct}" else "n/a", "%", batteryLevel(s).color,
                    h.map { it.batteryPct.coerceAtLeast(0) / 100f }, note = if (s.charging) "Charging (${s.plug})" else "On battery")
                AdviceCard(Advisor.battery(s))
            },
            right = {
                FactsCard("Details", listOf(
                    "Temperature" to (s.batteryTemp?.let { "%.1f °C".format(it) } ?: "Not reported"),
                    "Health" to s.batteryHealth, "Voltage" to if (s.voltageMv > 0) "${s.voltageMv} mV" else "Not reported",
                    "Technology" to s.tech, "Power source" to s.plug,
                    "Change since GLFX opened" to if (first != null && first.batteryPct >= 0) "${s.batteryPct - first.batteryPct} points" else "n/a"
                ))
            }
        )
    }
}

@Composable
fun NetworkScreen(vm: MainViewModel) {
    val info by vm.netInfo.collectAsStateWithLifecycle()
    val r by vm.netResult.collectAsStateWithLifecycle()
    val busy by vm.netBusy.collectAsStateWithLifecycle()
    val runs by vm.netRuns.collectAsStateWithLifecycle()
    val fmt = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
    ScreenFrame("Network", "Measures your connection from this phone. It cannot change carrier routing.") {
        TwoPane(
            left = {
                FactsCard("Connection", listOf(
                    "Type" to (info?.type ?: "…"),
                    "Internet validated" to (info?.validated?.let { if (it) "Yes" else "No" } ?: "Not reported"),
                    "Metered" to (info?.metered?.let { if (it) "Yes" else "No" } ?: "Not reported"),
                    "Android bandwidth estimate" to (info?.downKbps?.let { "${it / 1000} Mbps down, ${(info?.upKbps ?: 0) / 1000} Mbps up" } ?: "Not reported"),
                    "Wi-Fi link speed" to (info?.wifiLinkMbps?.let { "$it Mbps" } ?: "n/a"),
                    "Wi-Fi signal" to (info?.wifiRssi?.let { "$it dBm" } ?: "n/a")
                ))
                PrimaryButton(if (busy) "Testing…" else "Run network test", { vm.runNetTest() }, enabled = !busy)
            },
            right = {
                if (r != null) {
                    FactsCard("Result (${r!!.host})", listOf(
                        "Average" to if (r!!.samples.isEmpty()) "failed" else "%.0f ms".format(r!!.avgMs),
                        "Fastest / slowest" to "${r!!.minMs} / ${r!!.maxMs} ms", "Jitter" to "%.1f ms".format(r!!.jitterMs),
                        "Failed connections" to "${r!!.lost} of ${r!!.sent} (%.0f%%)".format(r!!.lossPct)
                    ))
                    AdviceCard(Advisor.network(r!!))
                } else AdviceCard(listOf("Run the test to measure latency, jitter and failed connections. It makes 12 short connections to a public server."), "No result yet")
                if (runs.isNotEmpty()) FactsCard("Recent tests", runs.take(5).map { fmt.format(Date(it.getLong("time"))) to "%.0f ms, jitter %.0f".format(it.getDouble("avg"), it.getDouble("jitter")) })
            }
        )
    }
}
