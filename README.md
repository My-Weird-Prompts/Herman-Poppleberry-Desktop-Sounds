# Herman Poppleberry Desktop Sounds

Desktop system sounds and spoken announcements in the voice of **Herman Poppleberry**, co-host of the
[My Weird Prompts](https://myweirdprompts.com) podcast. Boot, shutdown, suspend, screen lock, mic mute, VPN,
battery, device and error events, all announced by Herman, dramatically.

Contents: 46 WAV clips (24 kHz mono 16-bit, loudness-normalised to -16 LUFS), a freedesktop sound theme
(`herman-poppleberry`, Ogg Vorbis), an event watcher for what the theme can't cover, and `herman-say`, a CLI
to play any clip.

## Install (Linux, per user, no root)

```bash
git clone https://github.com/My-Weird-Prompts/Herman-Poppleberry-Desktop-Sounds
cd Herman-Poppleberry-Desktop-Sounds
./install.sh             # clips + sound theme + watcher service + herman-say
./install.sh --no-watch  # without the watcher
./uninstall.sh
```

Or download the zip from [Releases](https://github.com/My-Weird-Prompts/Herman-Poppleberry-Desktop-Sounds/releases)
and use the WAVs on any OS.

The installer copies the clips to `~/.local/share/herman-poppleberry/clips/` and the theme to
`~/.local/share/sounds/herman-poppleberry/`. On KDE Plasma it selects the theme (`kdeglobals [Sounds] Theme`),
and on GNOME it sets `org.gnome.desktop.sound theme-name`. It also installs the user unit `herman-watch.service`.

Needs `paplay` (PulseAudio or PipeWire-Pulse). The watcher also uses `dbus-monitor`, `pactl` and `nmcli`.

## What triggers what

**Sound theme** (played by the desktop through libcanberra; which events fire depends on the desktop):

| freedesktop sound name | clip |
|---|---|
| `system-bootup` | `boot` |
| `system-shutdown` | `shutdown` |
| `system-ready` | `boot` |
| `device-added` | `device-added` |
| `device-removed` | `device-removed` |
| `power-plug` | `power-plugged` |
| `power-unplug` | `power-unplugged` |
| `battery-low` | `battery-low` |
| `battery-caution` | `battery-critical` |
| `battery-full` | `battery-full` |
| `dialog-error` | `error` |
| `screen-capture` | `screenshot` |
| `complete` | `task-complete` |
| `complete-download` | `download-complete` |
| `network-connectivity-established` | `network-connected` |
| `network-connectivity-lost` | `network-lost` |

**Watcher** (`scripts/herman-watch`, a systemd user service):

- **Session start:** `boot` if the machine booted less than 5 minutes ago, otherwise `login`.
- **Suspend and resume:** `suspend` and `resume`, from logind `PrepareForSleep`.
- **Screen lock:** `screen-locked` and `screen-unlocked`, from `org.freedesktop.ScreenSaver.ActiveChanged`.
- **Default microphone:** `mic-muted` and `mic-live`, from `pactl subscribe`.
- **NetworkManager VPN or WireGuard:** `vpn-connected` and `vpn-disconnected`, from `nmcli monitor`.
- **Service stop:** `shutdown` while the system is going down, otherwise `logout` (`scripts/herman-farewell`).

Mute an event with `HERMAN_MUTE=mic-live,mic-muted` (set it with `systemctl --user edit herman-watch`).

**Everything else** is played from your own scripts, hotkeys or automations:

```bash
herman-say --list
herman-say secure-mode-on
```

## Clips

| id | line |
|---|---|
| `boot` | Attention. System boot sequence initiated. All systems coming online. |
| `shutdown` | Shutting down. All systems going dark. Goodbye, for now. |
| `secure-mode-on` | Secure mode enabled. This workstation is now locked down. |
| `mic-muted` | Microphone muted. Nobody can hear you now. |
| `mic-live` | Microphone live. You are on the air. |
| `battery-critical` | Warning. Battery critically low. Find a power source immediately. |
| `login` | Good morning. Herman Poppleberry reporting for duty. Systems online. |
| `logout` | Logging you out. The workstation will remember you fondly. |
| `suspend` | Entering sleep mode. Do not disturb the machine. |
| `resume` | And we're back. Resuming operations. |
| `screen-locked` | Screen locked. Nobody gets in without the password. |
| `screen-unlocked` | Welcome back. Access granted. |
| `restart` | Restart initiated. I shall return momentarily. |
| `power-plugged` | Power connected. Charging commenced. |
| `power-unplugged` | Running on battery. Conserve your energy. |
| `battery-low` | Battery low. Plug me in at your earliest convenience. |
| `battery-full` | Battery fully charged. |
| `volume-max` | Volume at maximum. Brace yourself. |
| `sound-muted` | Sound muted. Silence, at last. |
| `sound-restored` | Sound restored. |
| `headset-connected` | Headset connected. |
| `headset-disconnected` | Headset disconnected. |
| `device-added` | New device detected. |
| `device-removed` | Device removed. |
| `webcam-on` | Webcam activated. You are being watched. By yourself. |
| `bluetooth-connected` | Bluetooth connected. |
| `network-connected` | Network connected. We are online. |
| `network-lost` | Network connection lost. We are on our own now. |
| `vpn-connected` | VPN connected. Your traffic is now incognito. |
| `vpn-disconnected` | VPN disconnected. We are exposed. |
| `secure-mode-off` | Secure mode disabled. Standing down. |
| `intrusion-blocked` | Intrusion attempt blocked. Nice try. |
| `firewall-on` | Firewall engaged. The walls are up. |
| `disk-low` | Warning. Disk space running low. |
| `system-hot` | Warning. The system is running hot. |
| `memory-pressure` | Memory pressure detected. Something is eating all the RAM. |
| `updates-available` | Updates are available. When you have a moment. |
| `updates-installed` | Updates installed. A restart is recommended. |
| `app-crashed` | An application has crashed. My condolences. |
| `error` | Error. Something has gone terribly wrong. |
| `task-complete` | Task complete. |
| `backup-complete` | Backup complete. Your data is safe. |
| `download-complete` | Download complete. |
| `screenshot` | Screenshot captured. |
| `recording-started` | Recording started. |
| `recording-stopped` | Recording stopped. |

## How they were made

Rendered on 2026-11-17 with the My Weird Prompts Chatterbox voice-clone TTS (the podcast's production `tts_worker`
on Modal, Herman's reference voice and per-character EQ), then loudness-normalised with ffmpeg
`loudnorm=I=-16:TP=-1.5`. Re-render or add a clip: add it to `clips/clips.json`, then run the private MWP
cast-TTS tooling (`render_cast.py --voice herman --file clips/clips.json --out clips`). Existing WAVs are skipped.

## Licence

Non-commercial use: [CC BY-NC 4.0](https://creativecommons.org/licenses/by-nc/4.0/). Herman Poppleberry is a
character from My Weird Prompts.
