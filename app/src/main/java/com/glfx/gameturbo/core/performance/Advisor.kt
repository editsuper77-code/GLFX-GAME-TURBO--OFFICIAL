package com.glfx.gameturbo.core.performance

import com.glfx.gameturbo.core.monitoring.Snapshot
import com.glfx.gameturbo.core.network.NetResult

object Advisor {
    fun cpu(h: List<Snapshot>): List<String> {
        val v = h.takeLast(10).mapNotNull { it.cpuBest }
        if (v.size < 5) return listOf("Collecting samples. Advice appears after a few seconds.")
        val avg = v.average()
        return when {
            avg > 0.85 -> listOf("CPU load has stayed above 85%. A game running now may stutter. Close heavy apps you are not using.")
            avg > 0.6 -> listOf("CPU load is moderate. Most games will run, but heavy scenes may dip.")
            else -> listOf("CPU load is low. The processor is not a bottleneck right now.")
        }
    }

    fun pressure(s: Snapshot): String {
        if (s.lowMemory) return "Critical"
        val free = 1f - s.ramUsedFrac
        return when { free < 0.12f -> "Critical"; free < 0.25f -> "Tight"; else -> "Comfortable" }
    }

    fun ram(s: Snapshot): List<String> = when (pressure(s)) {
        "Critical" -> listOf("Memory is critically low. Close apps you are not using before starting a game. GLFX does not kill apps for you.")
        "Tight" -> listOf("Free memory is tight. Large games may reload when you switch apps.")
        else -> listOf("Memory is comfortable for most games.")
    }

    fun thermal(s: Snapshot): List<String> {
        val out = ArrayList<String>()
        val st = s.thermalStatus
        if (st != null) {
            out += when {
                st >= 4 -> "The device is at a critical thermal level. Stop playing and let it cool."
                st >= 3 -> "The device is throttling. Use Thermal-aware mode and lower the in-game graphics."
                st >= 2 -> "The device is warm. Expect the system to limit performance soon."
                else -> "Thermal state is fine."
            }
        } else {
            val t = s.batteryTemp
            out += if (t != null && t >= 42f) "Battery is hot at ${"%.1f".format(t)} °C. Take a break." else "Android does not report a thermal level on this version. Battery temperature is used instead."
        }
        if (s.charging) out += "Charging while gaming adds heat. Unplug or use a slower charger for long sessions."
        return out
    }

    fun battery(s: Snapshot): List<String> = when {
        s.batteryPct in 0..14 && !s.charging -> listOf("Battery is very low. Use Battery-friendly mode or plug in.")
        s.batteryPct in 0..29 && !s.charging -> listOf("Battery is under 30%. A long session may not finish.")
        else -> listOf("Battery level is fine for a session.")
    }

    fun network(r: NetResult): List<String> = when {
        r.sent > 0 && r.lost == r.sent -> listOf("No test connection succeeded. Check that you are online.")
        r.lossPct >= 10f -> listOf("Connections are failing. Online games will disconnect or rubber-band. Move closer to the router or switch networks.")
        r.avgMs > 120f -> listOf("Latency is high. Fast online games will feel delayed.")
        r.jitterMs > 30f -> listOf("Latency is unstable. Expect spikes in online matches.")
        else -> listOf("Connection looks good for online games. This measures your path to a public server, not your game's server.")
    }
}
