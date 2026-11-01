package com.glfx.gameturbo.core.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket
import kotlin.math.abs

data class NetResult(
    val host: String, val sent: Int, val lost: Int, val avgMs: Float, val minMs: Int, val maxMs: Int, val jitterMs: Float,
    val samples: List<Int>
) {
    val lossPct: Float get() = if (sent == 0) 0f else lost * 100f / sent
}

data class NetInfo(
    val type: String, val online: Boolean, val validated: Boolean?, val metered: Boolean?,
    val downKbps: Int?, val upKbps: Int?, val wifiLinkMbps: Int?, val wifiRssi: Int?
)

object NetProbe {
    /** TCP connect time to a public endpoint. This measures the path from this phone, not the game server. */
    suspend fun run(host: String = "1.1.1.1", port: Int = 443, count: Int = 12, timeoutMs: Int = 1500): NetResult =
        withContext(Dispatchers.IO) {
            val ok = ArrayList<Int>()
            var lost = 0
            repeat(count) {
                val t0 = System.nanoTime()
                val success = try {
                    Socket().use { it.connect(InetSocketAddress(host, port), timeoutMs) }
                    true
                } catch (e: Exception) { false }
                if (success) ok += ((System.nanoTime() - t0) / 1_000_000).toInt() else lost++
                delay(150)
            }
            val jitter = if (ok.size > 1) ok.zipWithNext { a, b -> abs(b - a).toFloat() }.average().toFloat() else 0f
            NetResult(
                host, count, lost,
                if (ok.isEmpty()) 0f else ok.average().toFloat(),
                ok.minOrNull() ?: 0, ok.maxOrNull() ?: 0, jitter, ok
            )
        }

    @Suppress("DEPRECATION")
    fun info(ctx: Context): NetInfo {
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        var wifi = false
        val base: NetInfo
        if (Build.VERSION.SDK_INT >= 23) {
            val c = cm.activeNetwork?.let { cm.getNetworkCapabilities(it) }
            if (c == null) return NetInfo("Offline", false, null, null, null, null, null, null)
            wifi = c.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
            val type = when {
                wifi -> "Wi-Fi"
                c.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Mobile data"
                c.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
                c.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH) -> "Bluetooth"
                else -> "Other"
            }
            base = NetInfo(
                type, true, c.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),
                !c.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED),
                c.linkDownstreamBandwidthKbps, c.linkUpstreamBandwidthKbps, null, null
            )
        } else {
            val n = cm.activeNetworkInfo
            if (n == null || !n.isConnected) return NetInfo("Offline", false, null, null, null, null, null, null)
            wifi = n.type == ConnectivityManager.TYPE_WIFI
            base = NetInfo(n.typeName ?: "Unknown", true, null, cm.isActiveNetworkMetered, null, null, null, null)
        }
        if (!wifi) return base
        return try {
            val wm = ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val w = wm.connectionInfo
            base.copy(wifiLinkMbps = w?.linkSpeed?.takeIf { it > 0 }, wifiRssi = w?.rssi?.takeIf { it > -127 })
        } catch (e: Exception) { base }
    }
}
