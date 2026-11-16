package com.myweirdprompts.herman

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer

/**
 * Plays one clip at a time. Default route is the notification stream (USAGE_NOTIFICATION), which
 * the ringer's silent mode mutes. With [media] true it uses the media stream, which silent mode
 * does not mute.
 */
object Player {
    private var current: MediaPlayer? = null

    @Synchronized
    fun play(ctx: Context, clipId: String, media: Boolean) {
        val app = ctx.applicationContext
        val res = Clips.resId(app, clipId)
        if (res == 0) return
        current?.let { runCatching { it.release() } }
        val usage = if (media) AudioAttributes.USAGE_MEDIA else AudioAttributes.USAGE_NOTIFICATION
        val attrs = AudioAttributes.Builder()
            .setUsage(usage)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
        val am = app.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(attrs).build()
        am.requestAudioFocus(focus)
        val mp = MediaPlayer.create(app, res, attrs, am.generateAudioSessionId()) ?: run {
            am.abandonAudioFocusRequest(focus); return
        }
        current = mp
        mp.setOnCompletionListener {
            runCatching { it.release() }
            am.abandonAudioFocusRequest(focus)
            synchronized(Player) { if (current === it) current = null }
        }
        mp.start()
    }
}
