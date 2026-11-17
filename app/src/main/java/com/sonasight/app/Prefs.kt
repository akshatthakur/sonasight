package com.sonasight.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("sonasight", Context.MODE_PRIVATE)

    private fun str(k: String, d: String) = sp.getString(k, d) ?: d
    private fun put(k: String, v: String) = sp.edit().putString(k, v).apply()
    private fun put(k: String, v: Int) = sp.edit().putInt(k, v).apply()
    private fun put(k: String, v: Float) = sp.edit().putFloat(k, v).apply()
    private fun put(k: String, v: Boolean) = sp.edit().putBoolean(k, v).apply()

    var scene: String
        get() = str("scene", "streets")
        set(v) = put("scene", v)

    var stock: String
        get() = str("stock", Films.scene(scene).stockId)
        set(v) = put("stock", v)

    /** Recipe in use, or "" for none. */
    var recipe: String
        get() = str("recipe", "")
        set(v) = put("recipe", v)

    var adjust: Adjust
        get() = Adjust.fromJson(runCatching { JSONObject(str("adjust", "{}")) }.getOrNull())
        set(v) = put("adjust", v.toJson().toString())

    var lens: Int
        get() = sp.getInt("lens", 35)
        set(v) = put("lens", v)

    var aspect: Aspect
        get() = runCatching { Aspect.valueOf(str("aspect", Aspect.R3_2.name)) }.getOrDefault(Aspect.R3_2)
        set(v) = put("aspect", v.name)

    var overlay: Overlay
        get() = runCatching { Overlay.valueOf(str("overlay", Overlay.PHI.name)) }.getOrDefault(Overlay.PHI)
        set(v) = put("overlay", v.name)

    var turn: Int
        get() = sp.getInt("turn", 0)
        set(v) = put("turn", v)

    var ev: Float
        get() = sp.getFloat("ev", 0f)
        set(v) = put("ev", v)

    var level: Boolean
        get() = sp.getBoolean("level", true)
        set(v) = put("level", v)

    var mode: Mode
        get() = runCatching { Mode.valueOf(str("mode", Mode.PHOTO.name)) }.getOrDefault(Mode.PHOTO)
        set(v) = put("mode", v.name)

    var front: Boolean
        get() = sp.getBoolean("front", false)
        set(v) = put("front", v)

    var shots: Int
        get() = sp.getInt("shots", 0)
        set(v) = put("shots", v)

    var dateStamp: Boolean
        get() = sp.getBoolean("date", false)
        set(v) = put("date", v)

    var timer: Int
        get() = sp.getInt("timer", 0)
        set(v) = put("timer", v)

    var location: Boolean
        get() = sp.getBoolean("location", false)
        set(v) = put("location", v)

    var zebra: Boolean
        get() = sp.getBoolean("zebra", false)
        set(v) = put("zebra", v)

    /** Roll size: 0 when no roll is loaded, otherwise 24 or 36. */
    var rollSize: Int
        get() = sp.getInt("rollSize", 0)
        set(v) = put("rollSize", v)

    var recipes: List<Recipe>
        get() = runCatching {
            val arr = JSONArray(str("recipes", "[]"))
            (0 until arr.length()).map {
                val o = arr.getJSONObject(it)
                Recipe(o.getString("id"), o.getString("name"), o.getString("stock"), Adjust.fromJson(o.optJSONObject("adjust")))
            }
        }.getOrDefault(emptyList())
        set(v) {
            val arr = JSONArray()
            v.forEach {
                arr.put(JSONObject().put("id", it.id).put("name", it.name).put("stock", it.stockId).put("adjust", it.adjust.toJson()))
            }
            put("recipes", arr.toString())
        }
}
