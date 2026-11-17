package com.sonasight.app

import android.content.Context

class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("sonasight", Context.MODE_PRIVATE)

    var scene: String
        get() = sp.getString("scene", "streets")!!
        set(v) = sp.edit().putString("scene", v).apply()

    var stock: String
        get() = sp.getString("stock", Films.scene(scene).stockId)!!
        set(v) = sp.edit().putString("stock", v).apply()

    var lens: Int
        get() = sp.getInt("lens", 35)
        set(v) = sp.edit().putInt("lens", v).apply()

    var aspect: Aspect
        get() = runCatching { Aspect.valueOf(sp.getString("aspect", Aspect.R3_2.name)!!) }.getOrDefault(Aspect.R3_2)
        set(v) = sp.edit().putString("aspect", v.name).apply()

    var overlay: Overlay
        get() = runCatching { Overlay.valueOf(sp.getString("overlay", Overlay.PHI.name)!!) }.getOrDefault(Overlay.PHI)
        set(v) = sp.edit().putString("overlay", v.name).apply()

    var turn: Int
        get() = sp.getInt("turn", 0)
        set(v) = sp.edit().putInt("turn", v).apply()

    var ev: Float
        get() = sp.getFloat("ev", 0f)
        set(v) = sp.edit().putFloat("ev", v).apply()

    var level: Boolean
        get() = sp.getBoolean("level", true)
        set(v) = sp.edit().putBoolean("level", v).apply()

    var mode: Mode
        get() = runCatching { Mode.valueOf(sp.getString("mode", Mode.PHOTO.name)!!) }.getOrDefault(Mode.PHOTO)
        set(v) = sp.edit().putString("mode", v.name).apply()

    var front: Boolean
        get() = sp.getBoolean("front", false)
        set(v) = sp.edit().putBoolean("front", v).apply()

    var shots: Int
        get() = sp.getInt("shots", 0)
        set(v) = sp.edit().putInt("shots", v).apply()
}
