package com.myweirdprompts.herman

import android.content.Context

class Prefs(ctx: Context) {
    private val sp = ctx.applicationContext.getSharedPreferences("herman", Context.MODE_PRIVATE)

    var master: Boolean
        get() = sp.getBoolean("master", true)
        set(v) = sp.edit().putBoolean("master", v).apply()

    var ignoreSilent: Boolean
        get() = sp.getBoolean("ignore_silent", false)
        set(v) = sp.edit().putBoolean("ignore_silent", v).apply()

    fun enabled(e: HermanEvent): Boolean = sp.getBoolean("en_" + e.key, true)
    fun setEnabled(e: HermanEvent, v: Boolean) = sp.edit().putBoolean("en_" + e.key, v).apply()

    fun clip(e: HermanEvent): String = sp.getString("clip_" + e.key, null) ?: e.defaultClip
    fun setClip(e: HermanEvent, id: String) = sp.edit().putString("clip_" + e.key, id).apply()
}
