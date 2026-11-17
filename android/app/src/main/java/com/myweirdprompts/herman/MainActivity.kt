package com.myweirdprompts.herman

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    /** Bumped in onResume so the permissions panel re-reads state after the user comes back from Settings. */
    private val resumed = mutableIntStateOf(0)

    override fun onResume() {
        super.onResume()
        resumed.intValue++
    }

    private fun granted(p: String) = checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED

    private fun batteryExempt() = (getSystemService(POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(packageName)

    private fun openAppSettings() = startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = Prefs(this)
        if (prefs.master) HermanService.start(this)
        setContent { MaterialTheme { Screen(prefs) } }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun Screen(prefs: Prefs) {
        var voice by remember { mutableStateOf(prefs.voice) }
        val clips = remember(voice) { Clips.load(this, voice) }
        var tab by remember { mutableIntStateOf(0) }
        val perms = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {}
        LaunchedEffect(Unit) {
            val want = mutableListOf<String>()
            if (Build.VERSION.SDK_INT >= 33) want += Manifest.permission.POST_NOTIFICATIONS
            if (Build.VERSION.SDK_INT >= 31) want += Manifest.permission.BLUETOOTH_CONNECT
            if (want.isNotEmpty()) perms.launch(want.toTypedArray())
        }
        Scaffold(topBar = { TopAppBar(title = { Text("MWP Announcer") }) }) { pad ->
            Column(Modifier.padding(pad)) {
                TabRow(selectedTabIndex = tab) {
                    Tab(tab == 0, { tab = 0 }, text = { Text("Events") })
                    Tab(tab == 1, { tab = 1 }, text = { Text("Soundboard") })
                }
                if (tab == 0) EventsTab(prefs, clips, voice) { voice = it; prefs.voice = it } else Soundboard(clips)
            }
        }
    }

    @Composable
    fun EventsTab(prefs: Prefs, clips: Map<String, String>, voice: Voice, onVoice: (Voice) -> Unit) {
        var master by remember { mutableStateOf(prefs.master) }
        var ignore by remember { mutableStateOf(prefs.ignoreSilent) }
        val writePerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
            if (ok) export(voice)
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Text("Voice", style = MaterialTheme.typography.titleMedium)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Voice.entries.forEach { v ->
                        FilterChip(selected = v == voice, onClick = { onVoice(v) }, label = { Text(v.label) })
                    }
                }
            }
            item { PermissionsPanel() }
            item {
                SwitchRow("Announcer is on", "Master switch. Off stops the background service.", master) {
                    master = it; prefs.master = it
                    if (it) HermanService.start(this@MainActivity) else HermanService.stop(this@MainActivity)
                }
            }
            item {
                SwitchRow("Ignore silent mode", "Play on the media stream so the ringer's silent mode does not mute the announcer.", ignore) {
                    ignore = it; prefs.ignoreSilent = it
                }
            }
            item {
                Button(onClick = {
                    if (Build.VERSION.SDK_INT < 29) writePerm.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE) else export(voice)
                }, Modifier.fillMaxWidth()) { Text("Export ${voice.label} as notification sounds") }
            }
            item { Text("Events", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp)) }
            items(Events.all, key = { it.key }) { ev -> EventRow(prefs, ev, clips) }
        }
    }

    /** What the announcer needs, each with its state and a button to fix it. */
    @Composable
    fun PermissionsPanel() {
        @Suppress("UNUSED_VARIABLE") val tick = resumed.intValue  // recompose on resume
        val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { resumed.intValue++ }
        val rows = buildList {
            if (Build.VERSION.SDK_INT >= 33) add(Triple("Notifications", "Keeps the background service alive and visible.",
                granted(Manifest.permission.POST_NOTIFICATIONS) to { ask.launch(Manifest.permission.POST_NOTIFICATIONS) }))
            if (Build.VERSION.SDK_INT >= 31) add(Triple("Nearby devices (Bluetooth)", "Bluetooth audio connected and disconnected.",
                granted(Manifest.permission.BLUETOOTH_CONNECT) to { ask.launch(Manifest.permission.BLUETOOTH_CONNECT) }))
            add(Triple("Unrestricted battery", "Stops Android from killing the announcer in the background.",
                batteryExempt() to {
                    @Suppress("BatteryLife")
                    startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
                }))
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Permissions", style = MaterialTheme.typography.titleMedium)
                rows.forEach { (title, why, st) ->
                    val (ok, fix) = st
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text((if (ok) "✓ " else "✗ ") + title, style = MaterialTheme.typography.titleSmall)
                            Text(why, style = MaterialTheme.typography.bodySmall)
                        }
                        if (!ok) TextButton(onClick = fix) { Text("Allow") }
                    }
                }
                TextButton(onClick = { openAppSettings() }) { Text("Open app settings") }
            }
        }
    }

    private fun export(voice: Voice) {
        Thread {
            val msg = runCatching {
                val r = Exporter.exportAll(this, voice)
                "Removed ${r.removed} old files, exported ${r.written}. Pick them in Settings > Sound."
            }.getOrElse { "Export failed: ${it.message}" }
            runOnUiThread { Toast.makeText(this, msg, Toast.LENGTH_LONG).show() }
        }.start()
    }

    @Composable
    fun SwitchRow(title: String, sub: String, checked: Boolean, onChange: (Boolean) -> Unit) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(sub, style = MaterialTheme.typography.bodySmall)
            }
            Switch(checked, onChange)
        }
    }

    @Composable
    fun EventRow(prefs: Prefs, ev: HermanEvent, clips: Map<String, String>) {
        var on by remember { mutableStateOf(prefs.enabled(ev)) }
        var clip by remember { mutableStateOf(prefs.clip(ev)) }
        var open by remember { mutableStateOf(false) }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(ev.label, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                    Switch(on, { on = it; prefs.setEnabled(ev, it) })
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) {
                        OutlinedButton(onClick = { open = true }) { Text(clip) }
                        DropdownMenu(open, { open = false }) {
                            clips.keys.forEach { id ->
                                DropdownMenuItem(text = { Text(id) }, onClick = {
                                    clip = id; prefs.setClip(ev, id); open = false
                                    Player.play(this@MainActivity, id, media = true)
                                })
                            }
                        }
                    }
                    TextButton(onClick = { Player.play(this@MainActivity, clip, media = true) }) { Text("Play") }
                }
                Text(clips[clip] ?: "", style = MaterialTheme.typography.bodySmall)
            }
        }
    }

    @Composable
    fun Soundboard(clips: Map<String, String>) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(clips.entries.toList(), key = { it.key }) { (id, line) ->
                Card(Modifier.fillMaxWidth().clickable { Player.play(this@MainActivity, id, media = true) }) {
                    Column(Modifier.padding(12.dp)) {
                        Text(id, style = MaterialTheme.typography.titleSmall)
                        Text(line, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}
