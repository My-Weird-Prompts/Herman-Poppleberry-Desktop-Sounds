#!/bin/sh
# Build the freedesktop sound themes mwp-herman, mwp-corn, mwp-hilbert into $1 (default dist/themes) from
# voices/<voice>/*.ogg and theme-map.tsv. Theme files are Ogg Vorbis (.oga): libcanberra decodes Vorbis, not Opus.
# Needs ffmpeg with libvorbis.
set -e
root="$(cd "$(dirname "$0")/.." && pwd)"; out="${1:-$root/dist/themes}"
for v in herman corn hilbert; do
  name="$(echo "$v" | sed 's/^./\U&/')"
  t="$out/mwp-$v"; rm -rf "$t"; mkdir -p "$t/stereo"
  printf '[Sound Theme]\nName=My Weird Prompts: %s\nComment=System sounds announced by %s from the My Weird Prompts podcast\nDirectories=stereo\n\n[stereo]\nOutputProfile=stereo\n' \
    "$name" "$name" > "$t/index.theme"
  while IFS="$(printf '\t')" read -r sound clip; do
    [ -n "$sound" ] || continue
    ffmpeg -nostdin -y -loglevel error -i "$root/voices/$v/$clip.ogg" -c:a libvorbis -q:a 4 -ac 1 "$t/stereo/$sound.oga"
  done < "$root/theme-map.tsv"
done
echo "themes in $out"
