package com.glfx.gameturbo.feature.benchmark

import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.ceil

data class FrameStats(
    val frames: Int, val avgFps: Float, val p95Ms: Float, val p99Ms: Float, val low1Fps: Float,
    val dropped: Int, val jitterMs: Float, val refreshHz: Float
) {
    fun toJson(time: Long): JSONObject = JSONObject().apply {
        put("time", time); put("frames", frames); put("avgFps", avgFps.toDouble()); put("p95", p95Ms.toDouble())
        put("p99", p99Ms.toDouble()); put("low1", low1Fps.toDouble()); put("dropped", dropped)
        put("jitter", jitterMs.toDouble()); put("hz", refreshHz.toDouble())
    }

    companion object {
        /** intervalsMs: time between consecutive frames. Pure function so it can be unit tested. */
        fun compute(intervalsMs: List<Float>, refreshHz: Float): FrameStats {
            if (intervalsMs.isEmpty()) return FrameStats(0, 0f, 0f, 0f, 0f, 0, 0f, refreshHz)
            val sorted = intervalsMs.sorted()
            fun pct(p: Float): Float = sorted[(ceil(p * sorted.size).toInt() - 1).coerceIn(0, sorted.size - 1)]
            val avgMs = intervalsMs.average().toFloat()
            val budget = 1000f / refreshHz
            val jitter = if (intervalsMs.size > 1) intervalsMs.zipWithNext { a, b -> abs(b - a) }.average().toFloat() else 0f
            val p99 = pct(0.99f)
            return FrameStats(
                intervalsMs.size, 1000f / avgMs, pct(0.95f), p99, 1000f / p99,
                intervalsMs.count { it > budget * 1.5f }, jitter, refreshHz
            )
        }
    }
}
