package com.glfx.gameturbo

import com.glfx.gameturbo.feature.benchmark.FrameStats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FrameStatsTest {
    @Test fun steady60FpsHasNoDrops() {
        val s = FrameStats.compute(List(300) { 16.667f }, 60f)
        assertEquals(60f, s.avgFps, 0.5f)
        assertEquals(0, s.dropped)
        assertEquals(0f, s.jitterMs, 0.001f)
    }

    @Test fun oneLongFrameIsCounted() {
        val data = MutableList(200) { 16.667f }.also { it[100] = 50f }
        val s = FrameStats.compute(data, 60f)
        assertEquals(1, s.dropped)
        assertTrue(s.jitterMs > 0f)
    }

    @Test fun emptyInputIsSafe() = assertEquals(0, FrameStats.compute(emptyList(), 60f).frames)
}
