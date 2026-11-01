package com.glfx.gameturbo.core.performance

import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.provider.Settings

private fun guards(ctx: Context) = ctx.applicationContext.getSharedPreferences("glfx_guards", Context.MODE_PRIVATE)

/** Do Not Disturb filter changes. The previous value is saved on disk so a crash can still be undone. */
object DndController {
    private const val KEY = "dnd_saved"
    private fun nm(ctx: Context) = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    fun hasAccess(ctx: Context): Boolean = Build.VERSION.SDK_INT >= 23 && nm(ctx).isNotificationPolicyAccessGranted
    fun isActive(ctx: Context): Boolean = guards(ctx).contains(KEY)

    fun enable(ctx: Context): Boolean {
        if (!hasAccess(ctx)) return false
        val n = nm(ctx)
        val g = guards(ctx)
        if (!g.contains(KEY)) g.edit().putInt(KEY, n.currentInterruptionFilter).apply()
        n.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
        return true
    }

    fun restore(ctx: Context) {
        val g = guards(ctx)
        if (!g.contains(KEY)) return
        val prev = g.getInt(KEY, NotificationManager.INTERRUPTION_FILTER_ALL)
        if (hasAccess(ctx)) nm(ctx).setInterruptionFilter(if (prev == NotificationManager.INTERRUPTION_FILTER_UNKNOWN) NotificationManager.INTERRUPTION_FILTER_ALL else prev)
        g.edit().remove(KEY).apply()
    }
}

/** System brightness changes (needs the user to allow "Modify system settings"). */
object BrightnessController {
    private const val KEY = "brightness_saved"

    fun canWrite(ctx: Context): Boolean = Build.VERSION.SDK_INT < 23 || Settings.System.canWrite(ctx)
    fun current(ctx: Context): Int = runCatching { Settings.System.getInt(ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS) }.getOrDefault(128)
    fun isAuto(ctx: Context): Boolean = runCatching {
        Settings.System.getInt(ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE) == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
    }.getOrDefault(false)

    fun set(ctx: Context, value: Int): Boolean {
        if (!canWrite(ctx)) return false
        val g = guards(ctx)
        if (!g.contains(KEY)) g.edit().putInt(KEY, current(ctx)).apply()
        return runCatching { Settings.System.putInt(ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS, value.coerceIn(10, 255)) }.getOrDefault(false)
    }

    fun restore(ctx: Context) {
        val g = guards(ctx)
        if (!g.contains(KEY)) return
        val prev = g.getInt(KEY, -1)
        if (prev >= 0 && canWrite(ctx)) runCatching { Settings.System.putInt(ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS, prev) }
        g.edit().remove(KEY).apply()
    }
}
