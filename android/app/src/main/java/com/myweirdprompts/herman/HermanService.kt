package com.myweirdprompts.herman

import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import androidx.core.content.ContextCompat

/** Foreground service that owns the runtime receivers/callbacks and announces events. */
class HermanService : Service() {
    private val main = Handler(Looper.getMainLooper())
    private lateinit var prefs: Prefs
    private val lastFired = HashMap<String, Long>()
    private var startedAt = 0L
    private val cleanup = mutableListOf<() -> Unit>()

    // Battery state; first sticky delivery only initialises.
    private var battInit = false
    private var criticalFired = false
    private var fullFired = false

    // Network state
    private val vpnNetworks = HashSet<Network>()
    private val pendingLost = Runnable { fire("network-lost") }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        startedAt = System.currentTimeMillis()
        startForegroundCompat()
        registerAll()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.getStringExtra(EXTRA_PLAY)?.let { fire(it) }
        return START_STICKY
    }

    override fun onDestroy() {
        cleanup.forEach { runCatching { it() } }
        cleanup.clear()
        main.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    // ---- dispatch -------------------------------------------------------------------------

    /** Plays the clip chosen for event [key] if master and the event are on and not debounced. */
    fun fire(key: String) {
        val ev = Events.all.firstOrNull { it.key == key } ?: return
        if (!prefs.master || !prefs.enabled(ev)) return
        val now = System.currentTimeMillis()
        synchronized(lastFired) {
            val last = lastFired[key] ?: 0L
            if (now - last < DEBOUNCE_MS) return
            lastFired[key] = now
        }
        Player.play(this, prefs.clip(ev), media = prefs.ignoreSilent)
    }

    private fun warmingUp() = System.currentTimeMillis() - startedAt < 2000

    // ---- registration ---------------------------------------------------------------------

    private fun receiver(filter: IntentFilter, onIntent: (Intent) -> Unit) {
        val r = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) = onIntent(intent)
        }
        ContextCompat.registerReceiver(this, r, filter, ContextCompat.RECEIVER_EXPORTED)
        cleanup += { unregisterReceiver(r) }
    }

    private fun registerAll() {
        val kg = getSystemService(KEYGUARD_SERVICE) as KeyguardManager

        // Screen: unlock, or screen on with no keyguard.
        receiver(IntentFilter(Intent.ACTION_USER_PRESENT)) { fire("screen-unlocked") }
        receiver(IntentFilter(Intent.ACTION_SCREEN_ON)) { if (!kg.isKeyguardLocked) fire("screen-unlocked") }

        // Power
        receiver(IntentFilter().apply {
            addAction(Intent.ACTION_POWER_CONNECTED); addAction(Intent.ACTION_POWER_DISCONNECTED)
            addAction(Intent.ACTION_BATTERY_LOW); addAction(Intent.ACTION_SHUTDOWN)
        }) {
            when (it.action) {
                Intent.ACTION_POWER_CONNECTED -> fire("power-plugged")
                Intent.ACTION_POWER_DISCONNECTED -> fire("power-unplugged")
                Intent.ACTION_BATTERY_LOW -> fire("battery-low")
                Intent.ACTION_SHUTDOWN -> fire("shutdown")
            }
        }
        receiver(IntentFilter(Intent.ACTION_BATTERY_CHANGED)) { onBattery(it) }

        // Ringer, airplane, DND
        receiver(IntentFilter(AudioManager.RINGER_MODE_CHANGED_ACTION)) {
            when (it.getIntExtra(AudioManager.EXTRA_RINGER_MODE, -1)) {
                AudioManager.RINGER_MODE_SILENT -> fire("sound-muted")
                AudioManager.RINGER_MODE_NORMAL -> fire("sound-restored")
            }
        }
        receiver(IntentFilter(Intent.ACTION_AIRPLANE_MODE_CHANGED)) {
            val on = it.getBooleanExtra("state", Settings.Global.getInt(contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) != 0)
            fire(if (on) "airplane-on" else "airplane-off")
        }
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        receiver(IntentFilter(NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED)) {
            val off = nm.currentInterruptionFilter.let { f ->
                f == NotificationManager.INTERRUPTION_FILTER_ALL || f == NotificationManager.INTERRUPTION_FILTER_UNKNOWN
            }
            fire(if (off) "dnd-off" else "dnd-on")
        }

        // Bluetooth A2DP connected (needs BLUETOOTH_CONNECT on Android 12+; silently idle without it).
        receiver(IntentFilter(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED)) {
            if (it.getIntExtra(BluetoothProfile.EXTRA_STATE, -1) == BluetoothProfile.STATE_CONNECTED) {
                fire("bluetooth-connected")
            }
        }

        registerHeadset()
        registerNetwork()
    }

    private fun isWired(d: AudioDeviceInfo) = d.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
        d.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES || d.type == AudioDeviceInfo.TYPE_USB_HEADSET

    private fun registerHeadset() {
        val am = getSystemService(AUDIO_SERVICE) as AudioManager
        val cb = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(added: Array<out AudioDeviceInfo>) {
                if (!warmingUp() && added.any { isWired(it) }) fire("headset-connected")
            }
            override fun onAudioDevicesRemoved(removed: Array<out AudioDeviceInfo>) {
                if (!warmingUp() && removed.any { isWired(it) }) fire("headset-disconnected")
            }
        }
        am.registerAudioDeviceCallback(cb, main)
        cleanup += { am.unregisterAudioDeviceCallback(cb) }
    }

    private fun registerNetwork() {
        val cm = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager

        // Default network: connected / lost. Lost is delayed so a Wi-Fi to mobile hand-over is silent.
        val def = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                main.removeCallbacks(pendingLost)
                val caps = cm.getNetworkCapabilities(network)
                if (caps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true) return
                if (!warmingUp()) fire("network-connected")
            }
            override fun onLost(network: Network) {
                if (network in vpnNetworks) return
                main.removeCallbacks(pendingLost)
                main.postDelayed(pendingLost, 2000)
            }
        }
        cm.registerDefaultNetworkCallback(def, main)
        cleanup += { cm.unregisterNetworkCallback(def) }

        // VPN transport up / down.
        val vpnReq = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_VPN)
            .removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
            .build()
        val vpn = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                vpnNetworks += network
                if (!warmingUp()) fire("vpn-connected")
            }
            override fun onLost(network: Network) {
                vpnNetworks -= network
                fire("vpn-disconnected")
            }
        }
        cm.registerNetworkCallback(vpnReq, vpn, main)
        cleanup += { cm.unregisterNetworkCallback(vpn) }
    }

    private fun onBattery(i: Intent) {
        val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) * 100 / i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        val plugged = i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
        val full = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1) == BatteryManager.BATTERY_STATUS_FULL
        val critical = level in 0..5 && !plugged
        val isFull = plugged && full
        if (battInit) {
            if (critical && !criticalFired) fire("battery-critical")
            if (isFull && !fullFired) fire("battery-full")
        }
        criticalFired = critical
        fullFired = isFull
        battInit = true
    }

    // ---- notification ---------------------------------------------------------------------

    private fun startForegroundCompat() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Herman is listening", NotificationManager.IMPORTANCE_LOW)
        )
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val n = Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("Herman Announcer")
            .setContentText("Listening for system events")
            .setContentIntent(open)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else startForeground(1, n)
    }

    companion object {
        const val EXTRA_PLAY = "play"
        const val DEBOUNCE_MS = 3000L
        private const val CHANNEL = "herman_service"

        fun start(ctx: Context, playOnStart: String? = null) {
            val i = Intent(ctx, HermanService::class.java)
            playOnStart?.let { i.putExtra(EXTRA_PLAY, it) }
            ContextCompat.startForegroundService(ctx, i)
        }

        fun stop(ctx: Context) {
            ctx.stopService(Intent(ctx, HermanService::class.java))
        }
    }
}
