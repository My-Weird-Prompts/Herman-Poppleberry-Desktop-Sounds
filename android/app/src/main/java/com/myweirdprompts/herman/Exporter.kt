package com.myweirdprompts.herman

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File

/**
 * Exports the selected voice into MediaStore Notifications/MWP Announcer/<Voice>/ and Ringtones/MWP Announcer/<Voice>/.
 * Before exporting it deletes what this app exported earlier: v1.x files (folder "Herman Poppleberry", names "Herman - ...")
 * and any earlier v2 export (folder "MWP Announcer"). Only files created by this app are removed.
 */
object Exporter {
    private const val ROOT = "MWP Announcer"
    private const val OLD_ROOT = "Herman Poppleberry"
    private val DIRS = listOf(Environment.DIRECTORY_NOTIFICATIONS, Environment.DIRECTORY_RINGTONES)

    class Result(val written: Int, val removed: Int)

    fun exportAll(ctx: Context, voice: Voice): Result {
        val removed = removePrevious(ctx)
        val lines = Clips.load(ctx, voice)
        var written = 0
        for (id in lines.keys) {
            val res = Clips.resId(ctx, voice, id)
            if (res == 0) continue
            val title = voice.label + ": " + id.replace('-', ' ').replaceFirstChar { it.uppercase() }
            val name = "${voice.label} - $id.ogg"
            for (kind in DIRS) {
                val ok = if (Build.VERSION.SDK_INT >= 29) save29(ctx, res, kind, voice, name, title)
                else saveLegacy(ctx, res, kind, voice, name, title)
                if (ok) written++
            }
        }
        return Result(written, removed)
    }

    /** True when a file in [relPath] named [name] is one of ours: old "Herman - " names, or anything under the new root. */
    private fun isOurs(relPath: String, name: String): Boolean {
        val p = relPath.trimEnd('/')
        val inOld = DIRS.any { p == "$it/$OLD_ROOT" } && name.startsWith("Herman - ")
        val inNew = DIRS.any { p == "$it/$ROOT" || p.startsWith("$it/$ROOT/") }
        return inOld || inNew
    }

    private fun removePrevious(ctx: Context): Int {
        var n = 0
        if (Build.VERSION.SDK_INT >= 29) {
            val resolver = ctx.contentResolver
            val coll = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            val rows = ArrayList<Long>()
            resolver.query(
                coll,
                arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.RELATIVE_PATH, MediaStore.Audio.Media.DISPLAY_NAME),
                "${MediaStore.Audio.Media.OWNER_PACKAGE_NAME}=?", arrayOf(ctx.packageName), null
            )?.use { c ->
                while (c.moveToNext()) {
                    val rel = c.getString(1) ?: continue
                    if (isOurs(rel, c.getString(2) ?: "")) rows += c.getLong(0)
                }
            }
            for (id in rows) {
                val uri = android.content.ContentUris.withAppendedId(coll, id)
                n += runCatching { resolver.delete(uri, null, null) }.getOrDefault(0)
            }
        } else {
            @Suppress("DEPRECATION")
            for (kind in DIRS) {
                val base = Environment.getExternalStoragePublicDirectory(kind)
                File(base, OLD_ROOT).listFiles { f -> f.name.startsWith("Herman - ") }?.forEach { n += rmLegacy(ctx, it) }
                File(base, ROOT).walkBottomUp().filter { it.isFile }.forEach { n += rmLegacy(ctx, it) }
            }
        }
        return n
    }

    @Suppress("DEPRECATION")
    private fun rmLegacy(ctx: Context, f: File): Int {
        ctx.contentResolver.delete(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, "${MediaStore.Audio.Media.DATA}=?", arrayOf(f.absolutePath))
        return if (f.delete()) 1 else 0
    }

    private fun save29(ctx: Context, res: Int, dir: String, voice: Voice, name: String, title: String): Boolean {
        val rel = "$dir/$ROOT/${voice.label}/"
        val resolver = ctx.contentResolver
        val coll = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val cv = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, name)
            put(MediaStore.Audio.Media.TITLE, title)
            put(MediaStore.Audio.Media.MIME_TYPE, "audio/ogg")
            put(MediaStore.Audio.Media.RELATIVE_PATH, rel)
            put(MediaStore.Audio.Media.ARTIST, voice.label)
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
    private fun saveLegacy(ctx: Context, res: Int, dir: String, voice: Voice, name: String, title: String): Boolean {
        val folder = File(File(Environment.getExternalStoragePublicDirectory(dir), ROOT), voice.label).apply { mkdirs() }
        val f = File(folder, name)
        ctx.resources.openRawResource(res).use { i -> f.outputStream().use { i.copyTo(it) } }
        val cv = ContentValues().apply {
            put(MediaStore.Audio.Media.DATA, f.absolutePath)
            put(MediaStore.Audio.Media.TITLE, title)
            put(MediaStore.Audio.Media.MIME_TYPE, "audio/ogg")
            put(MediaStore.Audio.Media.IS_NOTIFICATION, if (dir == Environment.DIRECTORY_NOTIFICATIONS) 1 else 0)
            put(MediaStore.Audio.Media.IS_RINGTONE, if (dir == Environment.DIRECTORY_RINGTONES) 1 else 0)
        }
        ctx.contentResolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, cv)
        return true
    }
}
