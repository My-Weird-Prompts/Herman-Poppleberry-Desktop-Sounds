package com.myweirdprompts.herman

import android.content.Context
import org.json.JSONObject

/** One announceable system event. [key] is the preference key; [defaultClip] an id in lines.json. */
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
        HermanEvent("bluetooth-on", "Bluetooth turned on", "bluetooth-on"),
        HermanEvent("bluetooth-off", "Bluetooth turned off", "bluetooth-off"),
        HermanEvent("bluetooth-connected", "Bluetooth audio connected", "bluetooth-connected"),
        HermanEvent("bluetooth-disconnected", "Bluetooth audio disconnected", "bluetooth-disconnected"),
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

/** The three bundled voices. [id] is the folder name under voices/ and the raw resource prefix. */
enum class Voice(val id: String, val label: String) {
    HERMAN("herman", "Herman"), CORN("corn", "Corn"), HILBERT("hilbert", "Hilbert");

    companion object {
        fun from(id: String?): Voice = entries.firstOrNull { it.id == id } ?: HERMAN
    }
}

/** Clips bundled as raw resources (see PrepareClips in buildSrc): voice_id.ogg and lines_voice.json. */
object Clips {
    /** id -> spoken line for [voice] (shared lines merged with the voice's overrides at build time). */
    fun load(ctx: Context, voice: Voice): LinkedHashMap<String, String> {
        val res = ctx.resources.getIdentifier("lines_" + voice.id, "raw", ctx.packageName)
        val text = ctx.resources.openRawResource(res).bufferedReader().use { it.readText() }
        val o = JSONObject(text)
        val m = LinkedHashMap<String, String>()
        for (k in o.keys()) m[k] = o.getString(k)
        return m
    }

    fun resId(ctx: Context, voice: Voice, id: String): Int =
        ctx.resources.getIdentifier(voice.id + "_" + id.lowercase().replace('-', '_'), "raw", ctx.packageName)
}
