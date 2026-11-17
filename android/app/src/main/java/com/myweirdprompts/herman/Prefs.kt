package com.myweirdprompts.herman

import android.content.Context

class Prefs(ctx: Context) {
    private val app = ctx.applicationContext
    private val sp = app.getSharedPreferences("herman", Context.MODE_PRIVATE)

    init {
        // v2.0 replaced the single-voice clip set: drop per-event clip choices saved by 1.x so they fall back to defaults.
        if (sp.getInt("schema", 1) < 2) {
            val e = sp.edit()
            sp.all.keys.filter { it.startsWith("clip_") }.forEach { e.remove(it) }
            e.putInt("schema", 2).apply()
        }
    }

    var master: Boolean
        get() = sp.getBoolean("master", true)
        set(v) = sp.edit().putBoolean("master", v).apply()

    var ignoreSilent: Boolean
        get() = sp.getBoolean("ignore_silent", false)
        set(v) = sp.edit().putBoolean("ignore_silent", v).apply()

    var voice: Voice
        get() = Voice.from(sp.getString("voice", null))
        set(v) = sp.edit().putString("voice", v.id).apply()

    fun enabled(e: HermanEvent): Boolean = sp.getBoolean("en_" + e.key, true)
    fun setEnabled(e: HermanEvent, v: Boolean) = sp.edit().putBoolean("en_" + e.key, v).apply()

    /** Saved clip id for [e], or its default when nothing is saved or the saved id no longer exists for the voice. */
    fun clip(e: HermanEvent): String {
        val saved = sp.getString("clip_" + e.key, null)
        return if (saved != null && Clips.resId(app, voice, saved) != 0) saved else e.defaultClip
    }
    fun setClip(e: HermanEvent, id: String) = sp.edit().putString("clip_" + e.key, id).apply()
}
