#!/bin/sh
# Build dist/mwp-announcer_<version>_all.deb.   scripts/build_deb.sh 2.0
# Layout: /usr/share/mwp-announcer/{voices,lines.json}, /usr/share/sounds/mwp-{herman,corn,hilbert},
# /usr/bin/mwp-*, /usr/lib/systemd/user/mwp-announce-watch.service (users enable it themselves, see postinst).
set -e
ver="${1:?version}"; root="$(cd "$(dirname "$0")/.." && pwd)"; pkg="$root/dist/deb"
rm -rf "$pkg"; mkdir -p "$pkg/DEBIAN" "$pkg/usr/share/mwp-announcer" "$pkg/usr/share/sounds" "$pkg/usr/bin" \
  "$pkg/usr/lib/systemd/user" "$pkg/usr/share/doc/mwp-announcer"
cp -r "$root/voices" "$root/lines.json" "$pkg/usr/share/mwp-announcer/"
"$root/scripts/build_themes.sh" "$pkg/usr/share/sounds" >/dev/null
for s in mwp-say mwp-announce-watch mwp-announce-farewell mwp-announce-voice; do install -m 755 "$root/scripts/$s" "$pkg/usr/bin/"; done
install -m 644 "$root/scripts/mwp-announce-watch.service" "$pkg/usr/lib/systemd/user/"
cp "$root/README.md" "$pkg/usr/share/doc/mwp-announcer/"
size=$(du -sk "$pkg/usr" | cut -f1)
cat > "$pkg/DEBIAN/control" <<CTL
Package: mwp-announcer
Version: $ver
Architecture: all
Maintainer: My Weird Prompts <show@myweirdprompts.com>
Installed-Size: $size
Depends: python3, pulseaudio-utils | pipewire-bin
Recommends: dbus-bin | dbus, network-manager, iputils-ping
Section: sound
Priority: optional
Homepage: https://github.com/My-Weird-Prompts/MWP-Announcer
Description: Desktop announcements by Herman, Corn and Hilbert from My Weird Prompts
 System sounds and spoken announcements (boot, lock, suspend, screens on and off,
 network, VPN, tailnet, battery, devices) in the voices of the My Weird Prompts
 podcast cast. Ships three freedesktop sound themes, the mwp-say CLI and a
 user-session watcher service.
CTL
cat > "$pkg/DEBIAN/postinst" <<'PI'
#!/bin/sh
set -e
if [ "$1" = configure ]; then
  echo "mwp-announcer: per user, run  mwp-announce-voice herman  (or corn, hilbert)"
  echo "               and  systemctl --user enable --now mwp-announce-watch.service"
fi
PI
chmod 755 "$pkg/DEBIAN/postinst"
out="$root/dist/mwp-announcer_${ver}_all.deb"
dpkg-deb --root-owner-group -Zxz --build "$pkg" "$out" >/dev/null
echo "$out"
