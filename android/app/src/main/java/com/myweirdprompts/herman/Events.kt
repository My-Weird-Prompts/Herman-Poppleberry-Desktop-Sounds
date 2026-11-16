package com.myweirdprompts.herman

import android.content.Context
import org.json.JSONObject

/** One announceable system event. [key] is the preference key; [defaultClip] an id in clips.json. */
data class HermanEvent(val key: String, val label: String, val defaultClip: String)

object Events {
    val all = listOf(
        HermanEvent("boot", "Boot completed", "boot"),
        HermanEvent("shutdown", "Shutdown", "shutdown"),
        HermanEvent("screen-unlocked", "Screen on / unlocked", "screen-unlocked"),
        HermanEvent("power-plugged", "Charger connected", "power-plugged"),
        HermanEvent("power-unplugged", "Charger disconnected", "power-unplugged"),
        HermanEvent("battery-low", "Battery low (system warning)", "battery-low"),
        HermanEvent("battery-critical", "Battery at or below 5%", "battery-critical"),
        HermanEvent("battery-full", "Battery full while charging", "battery-full"),
        HermanEvent("headset-connected", "Wired headset connected", "headset-connected"),
        HermanEvent("headset-disconnected", "Wired headset disconnected", "headset-disconnected"),
        HermanEvent("bluetooth-connected", "Bluetooth audio connected", "bluetooth-connected"),
        HermanEvent("network-connected", "Network connected", "network-connected"),
        HermanEvent("network-lost", "Network lost", "network-lost"),
        HermanEvent("vpn-connected", "VPN connected", "vpn-connected"),
        HermanEvent("vpn-disconnected", "VPN disconnected", "vpn-disconnected"),
        HermanEvent("sound-muted", "Ringer set to silent", "sound-muted"),
        HermanEvent("sound-restored", "Ringer back to normal", "sound-restored"),
        HermanEvent("airplane-on", "Airplane mode on", "secure-mode-on"),
        HermanEvent("airplane-off", "Airplane mode off", "secure-mode-off"),
        HermanEvent("dnd-on", "Do Not Disturb on", "mic-muted"),
        HermanEvent("dnd-off", "Do Not Disturb off", "mic-live"),
    )
}

/** Clips bundled as raw resources (see PrepareClips in app/build.gradle.kts). */
object Clips {
    /** id -> spoken line, in clips.json order. */
    fun load(ctx: Context): LinkedHashMap<String, String> {
        val text = ctx.resources.openRawResource(R.raw.clips_json).bufferedReader().use { it.readText() }
        val o = JSONObject(text)
        val m = LinkedHashMap<String, String>()
        for (k in o.keys()) m[k] = o.getString(k)
        return m
    }

    fun resId(ctx: Context, id: String): Int =
        ctx.resources.getIdentifier("clip_" + id.lowercase().replace('-', '_'), "raw", ctx.packageName)
}
