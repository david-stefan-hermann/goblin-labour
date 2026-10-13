#!/usr/bin/env python
"""ElevenLabs alternative to generate.py: design a goblin voice once, then render the raw clips for lines.json.

Needs only the standard library plus ffmpeg. The API key comes from art/voice/.env (ELEVENLABS_API_KEY=...) or the
environment; it is never printed. Every call costs credits from the account's monthly pool.

    python elevenlabs.py design gollum "raspy, hissing, Gollum-like ..."   # 3 previews -> variants/el_gollum_*.mp3
    python elevenlabs.py create gollum 2 --name "Goblin Labour"            # save preview 2 as a voice, pin its id
    python elevenlabs.py render                                            # raw/<id>.wav for every missing line
    python elevenlabs.py render --only yes_master --force
    python elevenlabs.py say "Hello boss"                                  # one-off preview.wav
    python elevenlabs.py credits                                           # what is left this month
"""
import argparse
import base64
import json
import os
import subprocess
import sys
import urllib.error
import urllib.request
from pathlib import Path

HERE = Path(__file__).resolve().parent
RAW = HERE / "raw"
VARIANTS = HERE / "variants"
API = "https://api.elevenlabs.io/v1"

PREVIEW_TEXT = ("Yes, master, yes! Me dig the hole, me dig it deep. Shiny rocks, so many shiny rocks for the boss! "
                "Nobody digs like me. Nobody! Work, work, work, all day, all night. Heh heh heh. "
                "What? A creeper? Me hate creepers. Me hide now!")


def api_key():
    key = os.environ.get("ELEVENLABS_API_KEY")
    env = HERE / ".env"
    if not key and env.exists():
        for line in env.read_text(encoding="utf-8").splitlines():
            if line.startswith("ELEVENLABS_API_KEY="):
                key = line.split("=", 1)[1].strip().strip('"').strip("'")
    if not key:
        sys.exit("no ELEVENLABS_API_KEY in the environment or art/voice/.env")
    return key


def call(method, path, body=None, query=None, raw=False):
    url = API + path + (("?" + "&".join(f"{k}={v}" for k, v in query.items())) if query else "")
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(url, data=data, method=method)
    req.add_header("xi-api-key", api_key())
    if data is not None:
        req.add_header("Content-Type", "application/json")
    try:
        with urllib.request.urlopen(req, timeout=120) as r:
            payload = r.read()
    except urllib.error.HTTPError as e:
        sys.exit(f"HTTP {e.code} on {method} {path}: {e.read().decode(errors='replace')[:500]}")
    return payload if raw else json.loads(payload)


def load_json(path):
    with open(path, encoding="utf-8") as f:
        return json.load(f)


def dump_json(path, data):
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(data, f, indent=2, ensure_ascii=False)
        f.write("\n")


def cmd_credits(_):
    sub = call("GET", "/user/subscription")
    used, limit = sub.get("character_count", 0), sub.get("character_limit", 0)
    print(f"tier {sub.get('tier')}: {limit - used} of {limit} credits left this period")


def cmd_design(args):
    VARIANTS.mkdir(exist_ok=True)
    body = {
        "voice_description": args.description,
        "text": args.text or PREVIEW_TEXT,
        "model_id": args.model,
        "guidance_scale": args.guidance,
    }
    if args.seed is not None:
        body["seed"] = args.seed
    res = call("POST", "/text-to-voice/design", body)
    previews = res["previews"]
    meta = {"description": args.description, "text": res.get("text"), "model_id": args.model, "previews": []}
    for i, p in enumerate(previews, 1):
        ext = "mp3" if "mpeg" in p.get("media_type", "mpeg") else "bin"
        out = VARIANTS / f"el_{args.slug}_{i}.{ext}"
        out.write_bytes(base64.b64decode(p["audio_base_64"]))
        meta["previews"].append({"index": i, "file": out.name, "generated_voice_id": p["generated_voice_id"],
                                 "duration_secs": p.get("duration_secs")})
        print(f"  {out.name}  {p.get('duration_secs', 0):.1f}s")
    dump_json(VARIANTS / f"el_{args.slug}.json", meta)
    print(f"{len(previews)} previews for '{args.slug}' written; pick one with: elevenlabs.py create {args.slug} <index>")


def cmd_create(args):
    meta = load_json(VARIANTS / f"el_{args.slug}.json")
    preview = next(p for p in meta["previews"] if p["index"] == args.index)
    res = call("POST", "/text-to-voice", {
        "voice_name": args.name,
        "voice_description": meta["description"],
        "generated_voice_id": preview["generated_voice_id"],
        "labels": {"project": "goblin-labour", "slug": args.slug},
    })
    voice = load_json(HERE / "voice.json")
    voice["elevenlabs"] = {
        "voice_id": res["voice_id"],
        "voice_name": args.name,
        "designed_from": {"slug": args.slug, "preview": args.index, "description": meta["description"],
                          "design_model": meta["model_id"]},
        "model_id": "eleven_v3",
        "output_format": "mp3_44100_128",
        "voice_settings": {"stability": 0.5, "similarity_boost": 0.8, "style": 0.4, "speed": 1.0,
                           "use_speaker_boost": True},
        "seed": 1234,
    }
    dump_json(HERE / "voice.json", voice)
    print(f"voice '{args.name}' created and pinned in voice.json")


def tts(cfg, text, out_wav):
    query = {"output_format": cfg["output_format"]}
    body = {"text": text, "model_id": cfg["model_id"], "voice_settings": cfg["voice_settings"]}
    if cfg.get("seed") is not None:
        body["seed"] = cfg["seed"]
    audio = call("POST", f"/text-to-speech/{cfg['voice_id']}", body, query, raw=True)
    tmp = out_wav.with_suffix(".mp3")
    tmp.write_bytes(audio)
    subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", str(tmp), "-ac", "1", str(out_wav)], check=True)
    tmp.unlink()


def cmd_render(args):
    voice = load_json(HERE / "voice.json")
    cfg = voice.get("elevenlabs")
    if not cfg or not cfg.get("voice_id"):
        sys.exit("no elevenlabs.voice_id in voice.json; run `design` and `create` first")
    lines = load_json(HERE / "lines.json")["lines"]
    if args.only:
        unknown = set(args.only) - {l["id"] for l in lines}
        if unknown:
            sys.exit(f"unknown ids: {' '.join(sorted(unknown))}")
        jobs = [l for l in lines if l["id"] in args.only]
    elif args.force:
        jobs = lines
    else:
        jobs = [l for l in lines if not (RAW / f"{l['id']}.wav").exists()]
    if not jobs:
        print("nothing to render")
        return
    RAW.mkdir(exist_ok=True)
    chars = sum(len(l["text"]) for l in jobs)
    print(f"rendering {len(jobs)} line(s), about {chars} credits, voice {cfg['voice_name']} / {cfg['model_id']}")
    for job in jobs:
        out = RAW / f"{job['id']}.wav"
        tts(cfg, job["text"], out)
        print(f"  {out.name:28} {job['text']}")


def cmd_say(args):
    cfg = load_json(HERE / "voice.json").get("elevenlabs")
    if not cfg:
        sys.exit("no elevenlabs voice in voice.json")
    tts(cfg, args.text, HERE / "preview.wav")
    print("preview.wav written")


def main():
    ap = argparse.ArgumentParser()
    sub = ap.add_subparsers(dest="cmd", required=True)
    sub.add_parser("credits").set_defaults(fn=cmd_credits)
    d = sub.add_parser("design")
    d.add_argument("slug")
    d.add_argument("description")
    d.add_argument("--text", help="preview text, 100-1000 characters")
    d.add_argument("--model", default="eleven_ttv_v3", choices=["eleven_ttv_v3", "eleven_multilingual_ttv_v2"])
    d.add_argument("--guidance", type=float, default=5.0)
    d.add_argument("--seed", type=int)
    d.set_defaults(fn=cmd_design)
    c = sub.add_parser("create")
    c.add_argument("slug")
    c.add_argument("index", type=int)
    c.add_argument("--name", default="Goblin Labour")
    c.set_defaults(fn=cmd_create)
    r = sub.add_parser("render")
    r.add_argument("--only", action="append", default=[])
    r.add_argument("--force", action="store_true")
    r.set_defaults(fn=cmd_render)
    s = sub.add_parser("say")
    s.add_argument("text")
    s.set_defaults(fn=cmd_say)
    args = ap.parse_args()
    args.fn(args)


if __name__ == "__main__":
    main()
