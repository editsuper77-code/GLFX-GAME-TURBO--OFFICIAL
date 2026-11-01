package com.glfx.gameturbo.core.device

import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.display.DisplayManager
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.GLES20
import android.os.Build
import android.os.PowerManager
import android.view.Display

data class DisplayModeInfo(val id: Int, val width: Int, val height: Int, val hz: Float)

data class DeviceProfile(
    val manufacturer: String,
    val model: String,
    val brand: String,
    val apiLevel: Int,
    val release: String,
    val securityPatch: String?,
    val abis: List<String>,
    val cores: Int,
    val hardware: String,
    val board: String,
    val soc: String?,
    val totalRamMb: Long,
    val gpuVendor: String?,
    val gpuRenderer: String?,
    val glEs: String,
    val vulkan: String,
    val displayModes: List<DisplayModeInfo>,
    val currentHz: Float,
    val hasSustainedPerf: Boolean,
    val hasThermalApi: Boolean,
    val hasPerfHintApi: Boolean
)

object DeviceCollector {
    fun collect(ctx: Context): DeviceProfile {
        val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val pm = ctx.getSystemService(Context.POWER_SERVICE) as PowerManager
        val mi = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        val gpu = queryGpu()
        val (modes, hz) = displayInfo(ctx)
        val soc = if (Build.VERSION.SDK_INT >= 31) {
            "${Build.SOC_MANUFACTURER} ${Build.SOC_MODEL}".trim().takeIf { !it.contains("unknown", true) }
        } else null
        return DeviceProfile(
            manufacturer = Build.MANUFACTURER.orEmpty(),
            model = Build.MODEL.orEmpty(),
            brand = Build.BRAND.orEmpty(),
            apiLevel = Build.VERSION.SDK_INT,
            release = Build.VERSION.RELEASE.orEmpty(),
            securityPatch = if (Build.VERSION.SDK_INT >= 23) Build.VERSION.SECURITY_PATCH else null,
            abis = Build.SUPPORTED_ABIS.toList(),
            cores = Runtime.getRuntime().availableProcessors(),
            hardware = Build.HARDWARE.orEmpty(),
            board = Build.BOARD.orEmpty(),
            soc = soc,
            totalRamMb = mi.totalMem / (1024 * 1024),
            gpuVendor = gpu?.first,
            gpuRenderer = gpu?.second,
            glEs = am.deviceConfigurationInfo.glEsVersion ?: "unknown",
            vulkan = vulkanSummary(ctx.packageManager),
            displayModes = modes,
            currentHz = hz,
            hasSustainedPerf = Build.VERSION.SDK_INT >= 24 && pm.isSustainedPerformanceModeSupported,
            hasThermalApi = Build.VERSION.SDK_INT >= 29,
            hasPerfHintApi = Build.VERSION.SDK_INT >= 31
        )
    }

    fun displayInfo(ctx: Context): Pair<List<DisplayModeInfo>, Float> {
        val dm = ctx.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        val d: Display = dm.getDisplay(Display.DEFAULT_DISPLAY) ?: return emptyList<DisplayModeInfo>() to 60f
        return if (Build.VERSION.SDK_INT >= 23) {
            val modes = d.supportedModes.map { DisplayModeInfo(it.modeId, it.physicalWidth, it.physicalHeight, it.refreshRate) }
            modes to d.mode.refreshRate
        } else {
            emptyList<DisplayModeInfo>() to d.refreshRate
        }
    }

    private fun vulkanSummary(pm: PackageManager): String {
        val feats = pm.systemAvailableFeatures
        val ver = feats.firstOrNull { it.name == "android.hardware.vulkan.version" }?.version
            ?: return "Not reported"
        val level = feats.firstOrNull { it.name == "android.hardware.vulkan.level" }?.version
        val major = ver shr 22
        val minor = (ver shr 12) and 0x3ff
        return "Vulkan $major.$minor" + (level?.let { ", hardware level $it" } ?: "")
    }

    /** Reads GPU vendor and renderer through a throw-away 1x1 EGL pbuffer context. */
    private fun queryGpu(): Pair<String?, String?>? = try {
        val d = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        val v = IntArray(2)
        EGL14.eglInitialize(d, v, 0, v, 1)
        val attrs = intArrayOf(
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT, EGL14.EGL_NONE
        )
        val cfgs = arrayOfNulls<EGLConfig>(1)
        val n = IntArray(1)
        EGL14.eglChooseConfig(d, attrs, 0, cfgs, 0, 1, n, 0)
        val ctx = EGL14.eglCreateContext(d, cfgs[0], EGL14.EGL_NO_CONTEXT, intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE), 0)
        val surf = EGL14.eglCreatePbufferSurface(d, cfgs[0], intArrayOf(EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE), 0)
        EGL14.eglMakeCurrent(d, surf, surf, ctx)
        val vendor = GLES20.glGetString(GLES20.GL_VENDOR)
        val renderer = GLES20.glGetString(GLES20.GL_RENDERER)
        EGL14.eglMakeCurrent(d, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
        EGL14.eglDestroySurface(d, surf)
        EGL14.eglDestroyContext(d, ctx)
        EGL14.eglTerminate(d)
        vendor to renderer
    } catch (t: Throwable) {
        null
    }
}
