package com.glfx.gameturbo.feature.games

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Build

data class Game(val pkg: String, val name: String, val detected: Boolean)

class GameRepo(private val ctx: Context) {
    /** Every launcher app, flagged when Android or the developer marks it as a game. */
    @Suppress("DEPRECATION")
    fun launchables(): List<Game> {
        val pm = ctx.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(intent, 0)
            .filter { it.activityInfo.packageName != ctx.packageName }
            .map { ri ->
                val ai = ri.activityInfo.applicationInfo
                val isGame = (Build.VERSION.SDK_INT >= 26 && ai.category == ApplicationInfo.CATEGORY_GAME) ||
                    (ai.flags and ApplicationInfo.FLAG_IS_GAME) != 0
                Game(ri.activityInfo.packageName, ri.loadLabel(pm).toString(), isGame)
            }
            .distinctBy { it.pkg }
            .sortedBy { it.name.lowercase() }
    }

    fun launch(pkg: String): Boolean {
        val i = ctx.packageManager.getLaunchIntentForPackage(pkg) ?: return false
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(i)
        return true
    }
}
