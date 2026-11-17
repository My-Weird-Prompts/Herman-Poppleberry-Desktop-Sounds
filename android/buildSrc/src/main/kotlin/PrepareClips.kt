import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import groovy.json.JsonOutput
import groovy.json.JsonSlurper

/**
 * Copies the Ogg Opus clips of each voice folder under the repo-root voices directory into generated raw resources named
 * voiceName_idWithDashesAsUnderscores (no conversion). Also writes lines_voiceName.json per voice: the repo-root
 * lines.json merged with that voice's own lines.json overrides.
 */
abstract class PrepareClips : DefaultTask() {
    @get:InputDirectory
    abstract val voicesDir: DirectoryProperty

    @get:InputFile
    abstract val linesFile: RegularFileProperty

    @get:OutputDirectory
    abstract val outDir: DirectoryProperty

    @TaskAction
    fun run() {
        val raw = outDir.get().asFile.resolve("raw")
        raw.deleteRecursively(); raw.mkdirs()
        @Suppress("UNCHECKED_CAST")
        fun read(f: java.io.File) = JsonSlurper().parseText(f.readText()) as Map<String, Any?>
        val base = read(linesFile.get().asFile)
        val voices = voicesDir.get().asFile.listFiles { f -> f.isDirectory }?.sortedBy { it.name } ?: emptyList()
        if (voices.isEmpty()) throw GradleException("No voice folders in ${voicesDir.get().asFile}")
        for (v in voices) {
            val oggs = v.listFiles { f -> f.extension == "ogg" }?.sortedBy { it.name } ?: emptyList()
            if (oggs.isEmpty()) throw GradleException("No ogg clips in $v")
            for (f in oggs) {
                val name = v.name.lowercase() + "_" + f.nameWithoutExtension.lowercase().replace('-', '_')
                f.copyTo(raw.resolve("$name.ogg"), overwrite = true)
            }
            val merged = LinkedHashMap(base)
            val ov = v.resolve("lines.json")
            if (ov.exists()) merged.putAll(read(ov))
            raw.resolve("lines_" + v.name.lowercase() + ".json").writeText(JsonOutput.toJson(merged))
            logger.lifecycle("PrepareClips: ${v.name}: ${oggs.size} clips")
        }
    }
}
