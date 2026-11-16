import java.util.concurrent.TimeUnit

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Release signing comes from CI secrets (SIGNING_*); without them the debug key is used.
val keystorePath: String? = System.getenv("SIGNING_KEYSTORE_PATH")

android {
    namespace = "com.myweirdprompts.herman"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.myweirdprompts.herman"
        minSdk = 26
        targetSdk = 36
        versionCode = (System.getenv("VERSION_CODE") ?: "1").toInt()
        versionName = System.getenv("VERSION_NAME") ?: "0.1"
    }

    signingConfigs {
        if (keystorePath != null) {
            create("release") {
                storeFile = file(keystorePath)
                storePassword = System.getenv("SIGNING_STORE_PASSWORD")
                keyAlias = System.getenv("SIGNING_KEY_ALIAS")
                keyPassword = System.getenv("SIGNING_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = if (keystorePath != null) signingConfigs.getByName("release")
            else signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
}

/**
 * Copies ../clips/*.wav (repo-root clips/, relative to android/) into generated raw resources, converting to Ogg/Opus
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

val prepareClips = tasks.register<PrepareClips>("prepareClips") {
    clipsDir.set(rootProject.layout.projectDirectory.dir("../clips"))
    outDir.set(layout.buildDirectory.dir("generated/herman/res"))
}

android.sourceSets.getByName("main").res.srcDir(prepareClips.map { it.outDir })

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.06.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.core:core-ktx:1.16.0")
}
