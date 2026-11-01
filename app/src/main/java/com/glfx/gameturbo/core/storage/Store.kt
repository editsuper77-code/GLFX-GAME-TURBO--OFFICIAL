package com.glfx.gameturbo.core.storage

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Small local store (SharedPreferences + JSON). Nothing leaves the device. */
class Store(ctx: Context) {
    private val sp = ctx.getSharedPreferences("glfx_store", Context.MODE_PRIVATE)

    var activeMode: String
        get() = sp.getString("mode", "BALANCED") ?: "BALANCED"
        set(v) { sp.edit().putString("mode", v).apply() }

    var immersive: Boolean
        get() = sp.getBoolean("immersive", false)
        set(v) { sp.edit().putBoolean("immersive", v).apply() }

    var panelAuto: Boolean
        get() = sp.getBoolean("panel_auto", true)
        set(v) { sp.edit().putBoolean("panel_auto", v).apply() }

    var panelSide: String
        get() = sp.getString("panel_side", "LEFT") ?: "LEFT"
        set(v) { sp.edit().putString("panel_side", v).apply() }

    fun gameModes(): Map<String, String> {
        val o = JSONObject(sp.getString("game_modes", "{}") ?: "{}")
        return o.keys().asSequence().associateWith { o.getString(it) }
    }

    fun setGameMode(pkg: String, mode: String) {
        val o = JSONObject(sp.getString("game_modes", "{}") ?: "{}")
        o.put(pkg, mode)
        sp.edit().putString("game_modes", o.toString()).apply()
    }

    fun userGames(): Set<String> = sp.getStringSet("user_games", emptySet())?.toSet() ?: emptySet()

    fun setUserGames(s: Set<String>) { sp.edit().putStringSet("user_games", s.toSet()).apply() }

    fun list(key: String): List<JSONObject> {
        val a = JSONArray(sp.getString(key, "[]") ?: "[]")
        return (0 until a.length()).map { a.getJSONObject(it) }
    }

    fun push(key: String, o: JSONObject, max: Int = 50) {
        val cur = list(key).toMutableList()
        cur.add(0, o)
        val a = JSONArray()
        cur.take(max).forEach { a.put(it) }
        sp.edit().putString(key, a.toString()).apply()
    }

    fun exportJson(): String = JSONObject().apply {
        put("app", "GLFX GAME TURBO"); put("version", 1)
        put("mode", activeMode); put("immersive", immersive)
        put("panel_auto", panelAuto); put("panel_side", panelSide)
        put("game_modes", JSONObject(gameModes() as Map<*, *>))
        put("user_games", JSONArray(userGames().toList()))
        put("records", JSONArray(list("records")))
        put("frame", JSONArray(list("frame")))
        put("net", JSONArray(list("net")))
    }.toString(2)

    /** Returns true when the file looked like a GLFX backup and was applied. */
    fun importJson(text: String): Boolean = try {
        val o = JSONObject(text)
        if (o.optString("app") != "GLFX GAME TURBO") false else {
            val e = sp.edit()
            e.putString("mode", o.optString("mode", "BALANCED"))
            e.putBoolean("immersive", o.optBoolean("immersive", false))
            e.putBoolean("panel_auto", o.optBoolean("panel_auto", true))
            e.putString("panel_side", o.optString("panel_side", "LEFT"))
            e.putString("game_modes", o.optJSONObject("game_modes")?.toString() ?: "{}")
            val ug = o.optJSONArray("user_games")
            e.putStringSet("user_games", (0 until (ug?.length() ?: 0)).map { ug!!.getString(it) }.toSet())
            for (k in listOf("records", "frame", "net")) e.putString(k, (o.optJSONArray(k) ?: JSONArray()).toString())
            e.apply()
            true
        }
    } catch (t: Throwable) { false }

    fun clearAll() { sp.edit().clear().apply() }
}
