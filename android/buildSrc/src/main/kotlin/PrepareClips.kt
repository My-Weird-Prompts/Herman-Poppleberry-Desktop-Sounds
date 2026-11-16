import java.util.concurrent.TimeUnit
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

/**
 * Copies the .wav files in ../clips (repo-root clips/, relative to android/) into generated raw resources, converting to Ogg/Opus
 * when ffmpeg with libopus is on PATH (per clip; falls back to the plain WAV). Also copies
 * clips.json as raw/clips_json.json. Resource names: clip_<id with - replaced by _>.
 */
abstract class PrepareClips : DefaultTask() {
    @get:InputDirectory
    abstract val clipsDir: DirectoryProperty

    @get:OutputDirectory
    abstract val outDir: DirectoryProperty

    @TaskAction
    fun run() {
        val raw = outDir.get().asFile.resolve("raw")
        raw.deleteRecursively(); raw.mkdirs()
        val wavs = clipsDir.get().asFile.listFiles { f -> f.extension == "wav" }?.sortedBy { it.name }
            ?: emptyList()
        if (wavs.isEmpty()) throw GradleException("No wav clips in ${clipsDir.get().asFile}")
        val ffmpeg = try {
            ProcessBuilder("ffmpeg", "-version").redirectErrorStream(true).start().let {
                it.inputStream.readBytes(); it.waitFor() == 0
            }
        } catch (e: Exception) { false }
        var opus = 0; var wav = 0
        for (f in wavs) {
            val name = "clip_" + f.nameWithoutExtension.lowercase().replace('-', '_')
            var done = false
            if (ffmpeg) {
                val dst = raw.resolve("$name.ogg")
                val p = ProcessBuilder("ffmpeg", "-y", "-loglevel", "error", "-i", f.absolutePath,
                    "-c:a", "libopus", "-b:a", "32k", "-ac", "1", dst.absolutePath)
                    .redirectErrorStream(true).start()
                p.inputStream.readBytes()
                if (p.waitFor(60, TimeUnit.SECONDS) && p.exitValue() == 0 && dst.length() > 0) {
                    done = true; opus++
                } else dst.delete()
            }
            if (!done) { f.copyTo(raw.resolve("$name.wav"), overwrite = true); wav++ }
        }
        clipsDir.get().asFile.resolve("clips.json").copyTo(raw.resolve("clips_json.json"), overwrite = true)
        logger.lifecycle("PrepareClips: $opus opus, $wav wav (ffmpeg=$ffmpeg)")
    }
}
