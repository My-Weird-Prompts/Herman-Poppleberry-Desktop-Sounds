# MWP Announcer

Desktop system sounds and spoken announcements in the voices of **Herman Poppleberry**, **Corn Poppleberry** and
**Hilbert Flumingtop** from the [My Weird Prompts](https://myweirdprompts.com) podcast. Boot, shutdown, suspend, screen
lock, each monitor on and off, mic mute, network, VPN, tailnet, battery, device and error events, announced dramatically.

Contents: 84 lines in three voices (252 clips, Ogg Opus 48 kHz mono 64 kbit/s, -16 LUFS, about 6 MB
in total), three freedesktop sound themes (`mwp-herman`, `mwp-corn`, `mwp-hilbert`), an event watcher for what a sound
theme can't cover, and `mwp-say`, a CLI to play any clip. Formerly *Herman-Poppleberry-Desktop-Sounds* (Herman only,
WAV, until v1.1, 2026-11-17).

## Install

**Ubuntu / Debian (amd64 and arm64; the package is `all`)** from the My Weird Prompts APT repo:

```bash
curl -fsSL https://cdn.myweirdprompts.com/apt/setup.sh | sudo sh -s -- mwp-announcer
mwp-announce-voice herman                               # or corn, hilbert: sets the voice and the desktop sound theme
systemctl --user enable --now mwp-announce-watch.service
```

**KDE Plasma:** the themes are also on the KDE Store (System Settings > Notifications > System Sounds > Get New), see
[`docs/kde-store.md`](docs/kde-store.md).

**Any Linux, per user, no root** (needs ffmpeg to build the themes): `./install.sh [herman|corn|hilbert] [--no-watch]`,
`./uninstall.sh`. It also removes the old Herman-only install.

Needs `paplay` (PulseAudio or PipeWire-Pulse) or `pw-play`, and python3 for the watcher.

## Voice and config

`mwp-announce-voice corn` writes `VOICE=corn` to `~/.config/mwp-announcer/config`, selects the `mwp-corn` sound theme
(KDE `kdeglobals [Sounds] Theme`, GNOME `org.gnome.desktop.sound theme-name`) and restarts the watcher. Other keys in
that file (environment `MWP_<KEY>` overrides; full list in the header of `scripts/mwp-announce-watch`):

| key | default | |
|---|---|---|
| `MUTE` | empty | clip ids the watcher never plays, e.g. `mic-live,mic-muted` |
| `SCREENS` | auto | output names left to right, e.g. `DP-1,DP-2,HDMI-A-1`. Auto: by x position when first seen, saved to `~/.local/state/mwp-announcer/screens.json` |
| `SCREEN_CLIPS` | `number` | `number` (screen-1-on ... screen-4-on) or `position` (screen-left/center/right-on; up to three screens) |
| `HOME_SERVER` | empty | host pinged every 30 s for home-server-down/-up |
| `PUBLIC_IP_CHECK` | `off` | `on` = ask api.ipify.org every 10 min, announce a change |
| `WIFI_WEAK` | `30` | signal % for wifi-weak (at most every 15 min); `0` = off |

```bash
mwp-say --list                 # clip ids
mwp-say screen-2-off           # in the configured voice
mwp-say --voice hilbert boot   # in another voice
```

## What triggers what

**Sound theme** (played by the desktop through libcanberra; which events fire depends on the desktop). Theme files are
Ogg Vorbis because libcanberra does not decode Opus; `scripts/build_themes.sh` builds them from the Opus clips.

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

**Watcher** (`mwp-announce-watch`, systemd user unit `mwp-announce-watch.service`):

- **Session:** `boot` if the machine booted less than 5 minutes ago, else `login`; `suspend`/`resume` (logind
  `PrepareForSleep`); `screen-locked`/`screen-unlocked` (`org.freedesktop.ScreenSaver.ActiveChanged`); `mic-muted`/
  `mic-live` (default source, `pactl subscribe`); `shutdown` or `logout` from the unit's ExecStop.
- **Screens:** polls every 2 s, from `kscreen-doctor -j` (KDE), else `wlr-randr --json` (wlroots compositors such as
  labwc on Raspberry Pi OS), else `/sys/class/drm`. A screen counts as on when connected and enabled, so a monitor
  switched off at its power button announces only if it drops its connection (most DisplayPort monitors do; HDMI often
  keeps hot-plug). All screens changing in one poll plays `screens-all-on`/`-off`. Scripts that switch outputs
  themselves can call `mwp-say screen-1-off` directly.
- **Network:** `nmcli monitor` for `ethernet-*` (devices `en*`/`eth*`), `wifi-*` (`wl*`), `vpn-*` (VPN, WireGuard,
  `wg*`, `tun*`) and `captive-portal` (NetworkManager connectivity `portal`). Every 30 s a TCP connect to
  `1.1.1.1:443` and a DNS lookup of `myweirdprompts.com` give `internet-down`/`-restored` and `dns-failure`/`-restored`
  (two failed probes in a row before announcing). Every 15 s `tailscale status --json` gives `tailnet-connected`/
  `-disconnected` (`BackendState`) and `exit-node-on`/`-off` (`ExitNodeStatus`). `ssh-login` when `who` shows a new remote
  session.
- **Only from your own scripts or automations** (no built-in trigger): `new-device-on-network`, `network-slow`,
  `intrusion-blocked`, `firewall-on`, `secure-mode-*`, `backup-complete`, `updates-*`, `disk-low`, `system-hot`,
  `memory-pressure`, `app-crashed`, `recording-*`, `webcam-on`, `volume-max`, `bluetooth-*`, `headset-*`.

## Clips

Every voice says the same lines, except `login`, where each introduces himself
(`voices/<voice>/lines.json` overrides `lines.json`).

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
| `screen-1-on` | Screen one on. |
| `screen-1-off` | Screen one off. |
| `screen-2-on` | Screen two on. |
| `screen-2-off` | Screen two off. |
| `screen-3-on` | Screen three on. |
| `screen-3-off` | Screen three off. |
| `screen-4-on` | Screen four on. |
| `screen-4-off` | Screen four off. |
| `screen-left-on` | Left screen on. |
| `screen-left-off` | Left screen off. |
| `screen-center-on` | Centre screen on. |
| `screen-center-off` | Centre screen off. |
| `screen-right-on` | Right screen on. |
| `screen-right-off` | Right screen off. |
| `screens-all-off` | All screens off. Going dark. |
| `screens-all-on` | All screens on. Full panorama. |
| `displays-changed` | Display layout changed. |
| `ethernet-connected` | Ethernet connected. Wired and steady. |
| `ethernet-disconnected` | Ethernet unplugged. Someone pulled the cable. |
| `wifi-connected` | Wi-Fi connected. |
| `wifi-disconnected` | Wi-Fi disconnected. |
| `wifi-weak` | Wi-Fi signal weak. Move closer to the access point. |
| `internet-down` | The internet is down. The network is up, but the world is not answering. |
| `internet-restored` | Internet restored. The world is back. |
| `dns-failure` | DNS is not resolving. The internet has forgotten everyone's names. |
| `dns-restored` | DNS restored. Names are resolving again. |
| `tailnet-connected` | Tailnet connected. We are back on the private network. |
| `tailnet-disconnected` | Tailnet disconnected. We are cut off from home. |
| `exit-node-on` | Exit node engaged. Traffic is leaving by the back door. |
| `exit-node-off` | Exit node released. Traffic is going out the front door. |
| `home-server-down` | The home server is not responding. |
| `home-server-up` | The home server is back online. |
| `public-ip-changed` | Our public address has changed. |
| `captive-portal` | Captive portal detected. Someone wants you to accept their terms and conditions. |
| `ssh-login` | New remote login. Someone has entered the building. |
| `new-device-on-network` | Unknown device on the network. Who invited them? |
| `network-slow` | The network is crawling. Something is hogging the bandwidth. |
| `bluetooth-disconnected` | Bluetooth disconnected. |
| `bluetooth-on` | Bluetooth on. Scanning the airwaves. |
| `bluetooth-off` | Bluetooth off. Radio silence. |

## Android: MWP Announcer

Phone companion to this pack (added 2026-11-17; v2.0 the same day adds the three voices Herman, Corn and Hilbert,
renamed from Herman Announcer). Package `com.myweirdprompts.herman`, source in [`android/`](android/).
It runs a low-importance foreground service and speaks the clips on system events.

**Install:** download `mwp-announcer-<version>.apk` from the
[Releases](https://github.com/My-Weird-Prompts/MWP-Announcer/releases) entry tagged `android-v*` and
open it (allow installs from your browser or file manager), or add the MWP F-Droid repo
(`myweirdprompts.com/fdroid`) and install "MWP Announcer" there. Upgrading from Herman Announcer 1.x keeps the same package. On first launch allow notifications and Bluetooth. The Permissions card on the Events tab shows what is granted and has Allow buttons, including "Unrestricted battery" (battery-optimisation exemption) so Android does not stop the service; 2.1, 2026-11-17.

**What triggers what** (each event can be switched off or given another clip in the app; a 3 second debounce applies per event):

| Android event | clip |
|---|---|
| boot completed | `boot` |
| shutdown | `shutdown` |
| screen unlocked (or screen on with no lock) | `screen-unlocked` |
| charger connected / disconnected | `power-plugged` / `power-unplugged` |
| battery low (system warning, about 15%) | `battery-low` |
| battery at or below 5%, not charging | `battery-critical` |
| battery full while charging | `battery-full` |
| wired or USB headset plugged / unplugged | `headset-connected` / `headset-disconnected` |
| Bluetooth turned on / off (adapter state, no runtime permission on Android 12+) | `bluetooth-on` / `bluetooth-off` |
| Bluetooth audio (A2DP) connected / disconnected (needs Nearby devices) | `bluetooth-connected` / `bluetooth-disconnected` |
| default network available / lost | `network-connected` / `network-lost` |
| VPN up / down | `vpn-connected` / `vpn-disconnected` |
| ringer silent / normal | `sound-muted` / `sound-restored` |
| airplane mode on / off | `secure-mode-on` / `secure-mode-off` |
| Do Not Disturb on / off | `mic-muted` / `mic-live` |

Pick the voice (Herman, Corn or Hilbert) at the top of the Events tab; events and the Soundboard use it. The announcer plays on
the notification stream, so the ringer's silent mode mutes it; "Ignore silent mode" moves it to the media stream. The
Soundboard tab plays every clip of the selected voice.

**Export as notification sounds:** the button copies the selected voice into `Notifications/MWP Announcer/<Voice>/` and
`Ringtones/MWP Announcer/<Voice>/` (MediaStore), so they appear in Settings > Sound. Before exporting it deletes what the
app exported earlier (v1.x `Herman Poppleberry` folders and earlier `MWP Announcer` exports), so only the new clips remain.

Clips are not duplicated in the app source: the build copies `voices/<voice>/*.ogg` as they are and merges `lines.json`
with each voice's overrides, so a re-rendered clip needs only a new `android-v*` tag. Build, signing and release:
[`android/README.md`](android/README.md).

## How they were made

Rendered on 2026-11-17 by `scripts/render_voices.py`, which calls the deployed My Weird Prompts OmniVoice worker
(`tts_worker_omnivoice` in Modal app `mwp-recording-app`, profile `myweirdprompts`) with adapter set `v3`: Herman
[`My-Weird-Prompts/Herman-LoRA-V3`](https://huggingface.co/My-Weird-Prompts/Herman-LoRA-V3) `31a7d05`, Corn
[`My-Weird-Prompts/Corn-LoRA-V3`](https://huggingface.co/My-Weird-Prompts/Corn-LoRA-V3) `fa1cc4e`, Hilbert
`My-Weird-Prompts/Hilbert-OmniVoice-LoRA-Chatterbox` `ac68971`. The voice is in the LoRA weights, no reference clip;
the render-time creak guard is on `retry`. Output is loudness-normalised (`loudnorm=I=-16:TP=-1.5`) and encoded to Ogg
Opus. The whole set of 252 clips took under two minutes on one L40S (well under $0.25).

Add or change a line: edit `lines.json` (or `voices/<voice>/lines.json`), then

```bash
MODAL_PROFILE=myweirdprompts ~/.local/share/uv/tools/modal/bin/python scripts/render_voices.py [--only id1,id2] [--force]
```

renders whatever is missing into `voices/`. The MWP text normaliser is not applied, so write numbers as words.

## Licence

Non-commercial use: [CC BY-NC 4.0](https://creativecommons.org/licenses/by-nc/4.0/). Herman Poppleberry, Corn
Poppleberry and Hilbert Flumingtop are characters from My Weird Prompts.
