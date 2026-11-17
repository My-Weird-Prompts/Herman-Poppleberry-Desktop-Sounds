"""Render the announcement clips for one or more cast voices through the MWP OmniVoice LoRA adapters.

Calls the deployed `tts_worker_omnivoice` in Modal app `mwp-recording-app` (profile `myweirdprompts`) with an
adapter set (default `v3`: Herman-LoRA-V3, Corn-LoRA-V3, Hilbert-OmniVoice-LoRA-Chatterbox; see the MWP monorepo
modal_app/stages/tts_omnivoice.py ADAPTER_SETS). No reference clip: the voice is in the adapter weights.

Output: <out>/<voice>/<id>.ogg, Ogg Opus 48 kHz mono 64 kbit/s, loudnorm I=-16 TP=-1.5.

Lines are plain English with no numbers or acronyms, so the MWP text normaliser is not applied.

Usage (Modal's uv-tool interpreter):
  MODAL_PROFILE=myweirdprompts ~/.local/share/uv/tools/modal/bin/python scripts/render_voices.py \
      --voices herman,corn,hilbert [--only boot,login] [--set v3] [--out voices] [--force]
"""
import argparse
import json
import shutil
import subprocess
import sys
import tempfile
import uuid
from pathlib import Path

APP, FN, VOLUME = "mwp-recording-app", "tts_worker_omnivoice", "mwp-working-files"
ROOT = Path(__file__).resolve().parent.parent


def lines_for(voice: str) -> dict:
    """lines.json, with voices/<voice>/lines.json overriding per-voice lines (e.g. the login self-intro)."""
    base = json.loads((ROOT / "lines.json").read_text())
    over = ROOT / "voices" / voice / "lines.json"
    if over.exists():
        base.update(json.loads(over.read_text()))
    return base


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--voices", default="herman,corn,hilbert")
    ap.add_argument("--only", help="comma-separated clip ids")
    ap.add_argument("--set", default="v3", help="adapter set name")
    ap.add_argument("--out", default=str(ROOT / "voices"))
    ap.add_argument("--force", action="store_true")
    a = ap.parse_args()

    import modal

    out = Path(a.out)
    only = set(a.only.split(",")) if a.only else None
    todo = []
    for v in a.voices.split(","):
        for cid, text in lines_for(v).items():
            if only and cid not in only:
                continue
            if a.force or not (out / v / f"{cid}.ogg").exists():
                todo.append((v, cid, text))
    if not todo:
        print("nothing to render")
        return

    items = [{"segment_idx": n, "speaker": v, "voice": v, "text": t} for n, (v, _, t) in enumerate(todo)]
    vol_dir = f"private/mwp-desktop-sounds/{uuid.uuid4().hex[:8]}"
    mod = [sys.executable, "-m", "modal"]
    work = Path(tempfile.mkdtemp(prefix="mwp-sounds-", dir=Path.home() / ".cache"))
    try:
        res = modal.Function.from_name(APP, FN).remote(items, f"/working/{vol_dir}", None, a.set, "retry")
        bad = [r for r in res if not r.get("success")]
        if bad:
            sys.exit(f"{len(bad)} clips failed: {bad[:3]}")
        print("worker:", (res[0].get("worker") or {}))
        vers = {r["segment_idx"]: r.get("tts_version", "") for r in res}
        subprocess.run(mod + ["volume", "get", VOLUME, vol_dir + "/", str(work) + "/"], check=True,
                       capture_output=True)
        for f in sorted(work.rglob("segment_*.m4a")):
            n = int(f.name.split("_")[1])
            v, cid, text = todo[n]
            dst = out / v / f"{cid}.ogg"
            dst.parent.mkdir(parents=True, exist_ok=True)
            subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", str(f),
                            "-af", "loudnorm=I=-16:TP=-1.5,aresample=48000", "-ac", "1",
                            "-c:a", "libopus", "-b:a", "64k", str(dst)], check=True)
            print(f"{dst}  {vers.get(n)}  {text}")
    finally:
        shutil.rmtree(work, ignore_errors=True)
        subprocess.run(mod + ["volume", "rm", "-r", VOLUME, vol_dir], capture_output=True)


if __name__ == "__main__":
    main()
