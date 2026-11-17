#!/bin/sh
# Remove a per-user install (install.sh). For the .deb: sudo apt remove mwp-announcer.
data="$HOME/.local/share"
systemctl --user disable --now mwp-announce-watch.service 2>/dev/null || true
rm -f "$HOME/.config/systemd/user/mwp-announce-watch.service"
for s in mwp-say mwp-announce-watch mwp-announce-farewell mwp-announce-voice; do rm -f "$HOME/.local/bin/$s"; done
rm -rf "$data/mwp-announcer" "$data/sounds/mwp-herman" "$data/sounds/mwp-corn" "$data/sounds/mwp-hilbert"
command -v kwriteconfig6 >/dev/null && kwriteconfig6 --file kdeglobals --group Sounds --key Theme ocean
echo "Removed. Config kept in ~/.config/mwp-announcer."
