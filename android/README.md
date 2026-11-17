# MWP Announcer (Android, formerly Herman Announcer)

Phone companion to the Linux pack in this repo. Kotlin, Jetpack Compose (Material 3), `minSdk 26`, `targetSdk 36`,
package `com.myweirdprompts.herman`, tier small (`android-v1.0`, `android-v1.1`, `android-v2.0`, ...). Written 2026-11-17.

## Never build locally

The laptop has no usable Gradle setup and heavy jobs are barred there. All builds run in GitHub Actions
(`.github/workflows/android.yml`, runner `ubuntu-24.04`, which ships the Android SDK). Iterate on compile errors with
`gh run view <id> --log-failed`.

## Layout

- `buildSrc/.../PrepareClips.kt` (wired in `app/build.gradle.kts`): task `prepareClips` copies `../voices/<voice>/*.ogg`
  (repo root; already Ogg Opus, no ffmpeg) into generated raw resources `<voice>_<id with - as _>.ogg` (voices: herman,
  corn, hilbert; 84 ids each) and writes `lines_<voice>.json` = root `lines.json` merged with `voices/<voice>/lines.json`
  overrides. Adding a clip or voice folder needs no code change except listing a new voice in `Voice` (Events.kt).
- `Events.kt`: the `Voice` enum, `Clips` loader, the event list and default clip per event (edit here to add an event or change a default).
- `HermanService.kt`: foreground service (type `specialUse`), runtime receivers and network/audio callbacks, debounce
  (`DEBOUNCE_MS = 3000`). `BootReceiver.kt` starts it and plays `boot`.
- `Prefs.kt`: settings incl. `voice`. A `schema` marker (2) clears saved `clip_*` choices from 1.x on first run, and `clip()`
  falls back to the event default when a saved id does not exist in the selected voice.
- `Player.kt`: plays in the voice from Prefs; MediaPlayer; `USAGE_NOTIFICATION` by default, `USAGE_MEDIA` when "Ignore silent mode" is on.
- `Exporter.kt`: exports the selected voice to `Notifications/MWP Announcer/<Voice>/` and `Ringtones/MWP Announcer/<Voice>/`
  as `<Voice> - <id>.ogg` (API 29+ via RELATIVE_PATH; API 26-28 writes files directly and needs the storage permission).
  First it deletes the app's own earlier exports: MediaStore rows with `OWNER_PACKAGE_NAME` = our package whose path is
  `Notifications|Ringtones/Herman Poppleberry` with names `Herman - *` (v1.x) or under `.../MWP Announcer/`. Files of other
  apps and files left from before a reinstall (owner lost) are not touched; delete those by hand. Empty folders may remain.
- The user-visible name is "MWP Announcer" (`strings.xml`); the package and applicationId stay `com.myweirdprompts.herman`
  so updates over 1.x work. Settings: Events tab, top row "Voice" (Herman / Corn / Hilbert).

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
git tag android-v2.0 && git push origin android-v2.0   # version name comes from the tag, versionCode from the run number
gh run watch                                           # then: gh release view android-v2.0 --json assets
```

The release carries `mwp-announcer-<version>.apk`. Distribution: GitHub Releases and the public MWP F-Droid repo
(`My-Weird-Prompts/fdroid`, `apps.yml` entry for this repo with `tag_prefix: android-v`); after a release run
`gh workflow run publish.yml -R My-Weird-Prompts/fdroid`. Pushes to `master` touching `android/`, `voices/` or `lines.json`
build an APK artifact without releasing.

### Release notes

- 2.0 (2026-11-17): breaking. Three voices (Herman, Corn, Hilbert) with a picker; 84 clips per voice incl. screen, network and
  tailnet lines; app renamed MWP Announcer (package unchanged); export goes to `MWP Announcer/<Voice>/` and removes the
  app's earlier exports; old clip prefs reset; ffmpeg dropped from CI; clips are the repo's Ogg Opus files unconverted.
  versionCode is the CI run number, so it keeps rising over 1.x.

## Gotchas hit on 2026-11-17

- Kotlin block comments nest: a KDoc containing a glob such as `clips/` followed by `*.wav` opens a second comment, swallows
  the rest of the file and gives baffling symptoms (in a `.gradle.kts`: the `dependencies {}` block silently vanished, so
  all `androidx` imports were unresolved). Keep globs out of comments.
- The clip task lives in `buildSrc` (not inline in the build script) and is wired with
  `androidComponents.onVariants { variant.sources.res?.addGeneratedSourceDirectory(...) }`.
- `org.json` is not on the buildSrc classpath; PrepareClips uses Groovy's `JsonSlurper`/`JsonOutput`.
- Maven Central can return HTTP 429 on a runner; re-run the job (`gh run rerun <id>`).
- The default branch is `master`; the workflow triggers on `main` and `master`.

## Verification status

See the bottom of this file; update it after on-device testing.

- Confirmed: see the release run for compile and signing result (recorded at release time below).
- Untested on a device as of 2026-11-17: every trigger, the foreground service across reboot, shutdown announcement
  (the system may kill the process before the clip finishes), Bluetooth (needs the runtime Bluetooth permission),
  DND, export and picking the sounds in Settings.
- Notes: when the ringer is silent or DND is on, the notification stream is muted, so `sound-muted` is inaudible unless
  "Ignore silent mode" is on. Vendor battery optimisation can kill the service; exempt the app if events stop.

### Status at release (2026-11-17)

- Verified: CI build green on `android-v2.0` (run 44111026304), release `android-v2.0` carries signed `mwp-announcer-2.0.apk`
  (versionCode 17); the public MWP F-Droid index-v2.json lists `com.myweirdprompts.herman` "MWP Announcer" at 2.0 (code 17,
  next to 1.1) and the APK URL returns HTTP 200.
- Untested on a device: voice picker, per-voice soundboard, export cleanup of real 1.x files (the MediaStore owner query
  and deletes), the 1.x to 2.0 prefs reset, and all items listed above.
