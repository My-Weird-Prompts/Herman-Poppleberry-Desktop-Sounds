#!/bin/sh
# Install the Herman Poppleberry desktop sounds for the current user (no root needed).
#   ./install.sh            clips + sound theme + watcher + herman-say
#   ./install.sh --no-watch clips + sound theme + herman-say only
set -e
here="$(cd "$(dirname "$0")" && pwd)"
data="$HOME/.local/share"
mkdir -p "$data/herman-poppleberry/clips" "$data/sounds" "$HOME/.local/bin" "$HOME/.config/systemd/user"
cp "$here"/clips/*.wav "$here"/clips/clips.json "$data/herman-poppleberry/clips/"
rm -rf "$data/sounds/herman-poppleberry" && cp -r "$here/theme/herman-poppleberry" "$data/sounds/"
install -m 755 "$here/scripts/herman-say" "$here/scripts/herman-watch" "$here/scripts/herman-farewell" "$HOME/.local/bin/"
# KDE Plasma: select the sound theme (System Settings > Colours & Themes > System Sounds)
command -v kwriteconfig6 >/dev/null && kwriteconfig6 --file kdeglobals --group Sounds --key Theme herman-poppleberry
# GNOME
command -v gsettings >/dev/null && gsettings set org.gnome.desktop.sound theme-name herman-poppleberry 2>/dev/null || true
if [ "$1" != "--no-watch" ]; then
  cp "$here/scripts/herman-watch.service" "$HOME/.config/systemd/user/"
  systemctl --user daemon-reload
  systemctl --user enable --now herman-watch.service
fi
echo "Installed. Try: herman-say --list ; herman-say secure-mode-on"
