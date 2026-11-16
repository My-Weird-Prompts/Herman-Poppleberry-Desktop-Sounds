# Herman Announcer (Android)

Phone companion to the Linux pack in this repo. Kotlin, Jetpack Compose (Material 3), `minSdk 26`, `targetSdk 36`,
package `com.myweirdprompts.herman`, tier small (`android-v1.0`, `android-v1.1`, ...). Written 2026-11-17.

## Never build locally

The laptop has no usable Gradle setup and heavy jobs are barred there. All builds run in GitHub Actions
(`.github/workflows/android.yml`, runner `ubuntu-24.04`, which ships the Android SDK). Iterate on compile errors with
`gh run view <id> --log-failed`.

## Layout

- `app/build.gradle.kts`: task `prepareClips` copies `../clips/*.wav` (repo root) into generated raw resources
  `clip_<id with - as _>.ogg`, converting with `ffmpeg -c:a libopus -b:a 32k` when ffmpeg is on PATH (CI installs it),
  else the WAV is copied. It also copies `clips/clips.json` to `raw/clips_json.json`. The app reads that JSON at runtime,
  so adding a clip to `clips/` needs no code change.
- `Events.kt`: the event list and default clip per event (edit here to add an event or change a default).
- `HermanService.kt`: foreground service (type `specialUse`), runtime receivers and network/audio callbacks, debounce
  (`DEBOUNCE_MS = 3000`). `BootReceiver.kt` starts it and plays `boot`.
- `Player.kt`: MediaPlayer; `USAGE_NOTIFICATION` by default, `USAGE_MEDIA` when "Ignore silent mode" is on.
- `Exporter.kt`: MediaStore export to `Notifications/Herman Poppleberry/` and `Ringtones/` (API 29+ via RELATIVE_PATH;
  API 26-28 writes files directly and needs the storage permission).

## Signing route

Own keystore per app, same pattern as Android-Roaming-Mic and the other apps:

- Created 2026-11-17 by `android/scripts/create-signing-key.sh` (RSA 4096 PKCS12, alias `herman`, store and key
  password identical, valid 10000 days). Local copy `~/.config/android-signing/herman-announcer.jks` plus `.pass`.
- 1Password vault `Development`: Document "Android signing key - Herman Announcer" (the .jks) and Password item
  "Android signing key - Herman Announcer - password".
- GitHub Actions secrets on this repo: `SIGNING_KEYSTORE_B64`, `SIGNING_STORE_PASSWORD`, `SIGNING_KEY_ALIAS`,
  `SIGNING_KEY_PASSWORD`. The workflow decodes the keystore to `$RUNNER_TEMP` and passes `SIGNING_KEYSTORE_PATH`.
  Without the secrets the build falls back to the debug key (not suitable for upgrades).
- Never commit the keystore or passwords (`*.jks` is gitignored). Losing the key means users must uninstall to update.

## Release procedure

```bash
git tag android-v1.1 && git push origin android-v1.1   # version name comes from the tag, versionCode from the run number
gh run watch                                           # then: gh release view android-v1.1 --json assets
```

The release carries `herman-announcer-<version>.apk`. Distribution is GitHub Releases only; this app is not in an
F-Droid channel (the workflow has no F-Droid dispatch step). Pushes to `master` touching `android/` or `clips/` build an
APK artifact without releasing.

## Verification status

See the bottom of this file; update it after on-device testing.

- Confirmed: see the release run for compile and signing result (recorded at release time below).
- Untested on a device as of 2026-11-17: every trigger, the foreground service across reboot, shutdown announcement
  (the system may kill the process before the clip finishes), Bluetooth (needs the runtime Bluetooth permission),
  DND, export and picking the sounds in Settings.
- Notes: when the ringer is silent or DND is on, the notification stream is muted, so `sound-muted` is inaudible unless
  "Ignore silent mode" is on. Vendor battery optimisation can kill the service; exempt the app if events stop.
