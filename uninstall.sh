#!/bin/sh
systemctl --user disable --now herman-watch.service 2>/dev/null || true
rm -f "$HOME/.config/systemd/user/herman-watch.service" "$HOME/.local/bin/herman-say" "$HOME/.local/bin/herman-watch" "$HOME/.local/bin/herman-farewell"
rm -rf "$HOME/.local/share/sounds/herman-poppleberry" "$HOME/.local/share/herman-poppleberry"
command -v kwriteconfig6 >/dev/null && kwriteconfig6 --file kdeglobals --group Sounds --key Theme ocean
echo "Removed."
