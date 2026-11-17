# Distributing the sound themes through KDE

Checked 2026-11-17 on Plasma 6.6.6 (Ubuntu 26.04, danieldesktop).

## What KDE offers for sound themes

- **No in-app "Get New" for system sounds.** The System Sounds page (`kcm_soundtheme`, Plasma 6.1+) only lists
  installed themes. `/usr/share/knsrcfiles/` has no sound-theme `.knsrc`, so KNewStuff can't fetch one. The Plasma 6
  sound proposal ([plasma-workspace#47](https://invent.kde.org/plasma/plasma-workspace/-/issues/47)) planned it
  (OCS category 316, a "System Sounds" store page), and it is still an open request
  ([bug 506840](https://bugs.kde.org/show_bug.cgi?id=506840), [bug 511380](https://bugs.kde.org/show_bug.cgi?id=511380)).
- **The KDE Store (store.kde.org, run on opendesktop.org/Pling) hosts sound themes as downloads**, e.g.
  [store.kde.org/p/2134858](https://store.kde.org/p/2134858). Users download the tarball and extract it to
  `~/.local/share/sounds/`, then pick it in System Settings > Colours & Themes > System Sounds.
- **invent.kde.org (KDE's GitLab) is for KDE's own code.** A merge request adding these themes to Plasma would not be
  accepted: they are CC BY-NC 4.0 and KDE ships only free-licensed content (Ocean is the default theme). The upstream
  contribution that would fit is code: a merge request on `plasma/plasma-workspace` adding KNewStuff ("Get New Sound
  Themes…") to `kcm_soundtheme`, which closes bug 506840. Not started.

## Uploading to the KDE Store

Assets: the GitHub release for each desktop tag `v*` carries `mwp-herman.tar.gz`, `mwp-corn.tar.gz` and
`mwp-hilbert.tar.gz` (each one theme directory: `index.theme` + `stereo/*.oga`, Ogg Vorbis because libcanberra
does not decode Opus), built by `scripts/build_themes.sh` in `.github/workflows/desktop-release.yml`.

1. Sign in at <https://store.kde.org> (an opendesktop.org account; needs a person, no API for new products).
2. Add product, category: System Sounds (if it is still hidden, use the nearest "Plasma Themes" subcategory and say so
   in the description). One product with three files, or three products, one per voice.
3. Title "My Weird Prompts: Herman / Corn / Hilbert system sounds", licence CC BY-NC 4.0, link to this repo and to
   <https://myweirdprompts.com>, the screenshot of System Sounds with the theme selected.
4. Record the product URL(s) here and in the README.

Status: not uploaded (2026-11-17).
