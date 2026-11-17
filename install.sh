#!/bin/sh
# Per-user install from a checkout (no root). The .deb from the MWP APT repo is the usual route; this is for
# non-Debian systems. Removes the old Herman-only install (herman-watch, herman-poppleberry theme) if present.
#   ./install.sh [herman|corn|hilbert] [--no-watch]      needs ffmpeg (builds the sound themes)
set -e
here="$(cd "$(dirname "$0")" && pwd)"; data="$HOME/.local/share"; voice=herman; watch=1
for a in "$@"; do case "$a" in --no-watch) watch=0 ;; herman|corn|hilbert) voice="$a" ;; esac; done
# old Herman-only pack (before 2026-11-17 v2.0)
systemctl --user disable --now herman-watch.service 2>/dev/null || true
rm -rf "$HOME/.config/systemd/user/herman-watch.service" "$data/herman-poppleberry" "$data/sounds/herman-poppleberry" \
       "$HOME/.local/bin/herman-say" "$HOME/.local/bin/herman-watch" "$HOME/.local/bin/herman-farewell"
mkdir -p "$data/mwp-announcer" "$data/sounds" "$HOME/.local/bin" "$HOME/.config/systemd/user"
rm -rf "$data/mwp-announcer/voices"; cp -r "$here/voices" "$here/lines.json" "$data/mwp-announcer/"
"$here/scripts/build_themes.sh" "$data/sounds" >/dev/null
for s in mwp-say mwp-announce-watch mwp-announce-farewell mwp-announce-voice; do install -m 755 "$here/scripts/$s" "$HOME/.local/bin/"; done
"$HOME/.local/bin/mwp-announce-voice" "$voice"
if [ "$watch" = 1 ]; then
  cp "$here/scripts/mwp-announce-watch.service" "$HOME/.config/systemd/user/"
  systemctl --user daemon-reload; systemctl --user enable --now mwp-announce-watch.service
fi
echo "Installed. Try: mwp-say --list ; mwp-say screen-1-on ; mwp-announce-voice corn"
