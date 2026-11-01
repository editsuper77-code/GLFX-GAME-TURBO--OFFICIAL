package com.glfx.gameturbo

import com.glfx.gameturbo.core.network.NetResult
import com.glfx.gameturbo.core.performance.Advisor
import org.junit.Assert.assertTrue
import org.junit.Test

class AdvisorTest {
    @Test fun allFailedConnectionsSayOffline() {
        val r = NetResult("x", 10, 10, 0f, 0, 0, 0f, emptyList())
        assertTrue(Advisor.network(r).first().contains("No test connection"))
    }

    @Test fun highLatencyIsFlagged() {
        val r = NetResult("x", 10, 0, 200f, 150, 250, 5f, listOf(150, 250))
        assertTrue(Advisor.network(r).first().contains("Latency is high"))
    }
}
