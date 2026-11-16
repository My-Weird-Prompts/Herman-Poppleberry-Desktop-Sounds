package com.myweirdprompts.herman

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File

/** Copies every clip into MediaStore Notifications/Herman Poppleberry/ and Ringtones/. */
object Exporter {
    private const val SUB = "Herman Poppleberry"

    /** Returns (written, skippedExisting). */
    fun exportAll(ctx: Context): Pair<Int, Int> {
        val lines = Clips.load(ctx)
        var written = 0; var skipped = 0
        for ((id, line) in lines) {
            val res = Clips.resId(ctx, id)
            if (res == 0) continue
            val tv = android.util.TypedValue()
            ctx.resources.getValue(res, tv, true)
            val ogg = tv.string.toString().endsWith(".ogg")
            val ext = if (ogg) "ogg" else "wav"
            val mime = if (ogg) "audio/ogg" else "audio/x-wav"
            val title = "Herman: " + id.replace('-', ' ').replaceFirstChar { it.uppercase() }
            val name = "Herman - $id.$ext"
            for (kind in listOf(Environment.DIRECTORY_NOTIFICATIONS, Environment.DIRECTORY_RINGTONES)) {
                val ok = if (Build.VERSION.SDK_INT >= 29) save29(ctx, res, kind, name, title, mime, line)
                else saveLegacy(ctx, res, kind, name, title, mime)
                if (ok) written++ else skipped++
            }
        }
        return written to skipped
    }

    private fun save29(ctx: Context, res: Int, dir: String, name: String, title: String, mime: String, line: String): Boolean {
        val rel = "$dir/$SUB/"
        val resolver = ctx.contentResolver
        val coll = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        resolver.query(
            coll, arrayOf(MediaStore.Audio.Media._ID),
            "${MediaStore.Audio.Media.RELATIVE_PATH}=? AND ${MediaStore.Audio.Media.DISPLAY_NAME}=?",
            arrayOf(rel, name), null
        )?.use { if (it.moveToFirst()) return false }
        val cv = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, name)
            put(MediaStore.Audio.Media.TITLE, title)
            put(MediaStore.Audio.Media.MIME_TYPE, mime)
            put(MediaStore.Audio.Media.RELATIVE_PATH, rel)
            put(MediaStore.Audio.Media.ARTIST, "Herman Poppleberry")
            put(MediaStore.Audio.Media.IS_PENDING, 1)
            put(MediaStore.Audio.Media.IS_NOTIFICATION, if (dir == Environment.DIRECTORY_NOTIFICATIONS) 1 else 0)
            put(MediaStore.Audio.Media.IS_RINGTONE, if (dir == Environment.DIRECTORY_RINGTONES) 1 else 0)
        }
        val uri = resolver.insert(coll, cv) ?: return false
        resolver.openOutputStream(uri)?.use { out ->
            ctx.resources.openRawResource(res).use { it.copyTo(out) }
        }
        resolver.update(uri, ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) }, null, null)
        return true
    }

    /** API 26-28: write the file directly (needs WRITE_EXTERNAL_STORAGE), then index it. */
    @Suppress("DEPRECATION")
    private fun saveLegacy(ctx: Context, res: Int, dir: String, name: String, title: String, mime: String): Boolean {
        val folder = File(Environment.getExternalStoragePublicDirectory(dir), SUB).apply { mkdirs() }
        val f = File(folder, name)
        if (f.exists()) return false
        ctx.resources.openRawResource(res).use { i -> f.outputStream().use { i.copyTo(it) } }
        val cv = ContentValues().apply {
            put(MediaStore.Audio.Media.DATA, f.absolutePath)
            put(MediaStore.Audio.Media.TITLE, title)
            put(MediaStore.Audio.Media.MIME_TYPE, mime)
            put(MediaStore.Audio.Media.IS_NOTIFICATION, if (dir == Environment.DIRECTORY_NOTIFICATIONS) 1 else 0)
            put(MediaStore.Audio.Media.IS_RINGTONE, if (dir == Environment.DIRECTORY_RINGTONES) 1 else 0)
        }
        ctx.contentResolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, cv)
        return true
    }
}
