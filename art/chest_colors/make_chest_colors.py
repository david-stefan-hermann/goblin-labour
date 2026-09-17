#!/usr/bin/env python3
"""Recolours the Goblin Chest's green steel into all sixteen Minecraft dye colours.

Run from the project root:  python art/chest_colors/make_chest_colors.py

Reads   art/goblin_chest_double.bbmodel, art/goblin_chest_single.bbmodel (the texture every face uses)
Writes  art/chest_colors/goblin_chest_double_<colour>.png   128x256
        art/chest_colors/goblin_chest_single_<colour>.png   128x128

Only the steel changes. Its pixels are told apart by colour, which the texture keeps cleanly apart: the steel
is the only strongly coloured green-blue (hue 105-150); the iris is yellow-green (70-100), horns and teeth
are beige (30-50), fittings and the lock are grey and the mouth is red. Every steel pixel keeps its offset
from the steel's average brightness, saturation and hue, laid over the dye's colour, so the texture's shading
and noise stay the same in every colour. Green is the untouched original.

The files are plain chest textures like the bbmodel's; tools/make_chest_model.py can stack any of them into an
animated, glowing strip because the sparkle and the glow only touch the eye.
"""

import base64
import colorsys
import io
import json
import os

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
OUT = os.path.join(ROOT, "art", "chest_colors")

STEEL_HUE = (105, 150)  # degrees
STEEL_MIN_SATURATION = 0.3

# base colour of the painted steel per dye: Minecraft's dye colours, with the extremes pulled in so the
# shading still reads (pure white would clip its highlights, the dye's black would swallow the iron fittings)
DYES = {
    "white": "#d9dddd", "orange": "#e0741a", "magenta": "#c74ebd", "light_blue": "#3ab3da",
    "yellow": "#e3bf30", "lime": "#80c71f", "pink": "#f38baa", "gray": "#4c5457",
    "light_gray": "#9d9d97", "cyan": "#169c9c", "purple": "#8932b8", "blue": "#3c44aa",
    "brown": "#835432", "green": None, "red": "#b02e26", "black": "#2c2c33",
}


def chest_texture(model):
    data = json.load(open(os.path.join(ROOT, "art", model + ".bbmodel"), encoding="utf-8"))
    ids = {str(f.get("texture")) for e in data["elements"] for f in e["faces"].values()}
    assert len(ids) == 1, "%s: faces use %d textures" % (model, len(ids))
    texture = next(t for t in data["textures"] if str(t["id"]) == ids.pop())
    return Image.open(io.BytesIO(base64.b64decode(texture["source"].split(",", 1)[1]))).convert("RGBA")


def steel_pixels(img):
    px = img.load()
    found = {}
    for y in range(img.height):
        for x in range(img.width):
            r, g, b, a = px[x, y]
            if not a:
                continue
            h, s, v = colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)
            if STEEL_HUE[0] <= h * 360 < STEEL_HUE[1] and s >= STEEL_MIN_SATURATION:
                found[(x, y)] = (h, s, v)
    return found


def recolour(img, steel, colour):
    h0 = sum(h for h, s, v in steel.values()) / len(steel)
    s0 = sum(s for h, s, v in steel.values()) / len(steel)
    v0 = sum(v for h, s, v in steel.values()) / len(steel)
    r, g, b = (int(colour[i:i + 2], 16) / 255 for i in (1, 3, 5))
    ht, st, vt = colorsys.rgb_to_hsv(r, g, b)
    out = img.copy()
    px = out.load()
    for (x, y), (h, s, v) in steel.items():
        nh = (ht + (h - h0)) % 1.0
        ns = min(1.0, st * s / s0)
        nv = min(1.0, vt * v / v0)
        rgb = colorsys.hsv_to_rgb(nh, ns, nv)
        px[x, y] = tuple(int(round(c * 255)) for c in rgb) + (px[x, y][3],)
    return out


def main():
    for model in ("goblin_chest_double", "goblin_chest_single"):
        img = chest_texture(model)
        steel = steel_pixels(img)
        print("%s: %d steel pixels of %d" % (model, len(steel), img.width * img.height))
        for dye, colour in DYES.items():
            result = img if colour is None else recolour(img, steel, colour)
            result.save(os.path.join(OUT, "%s_%s.png" % (model, dye)))
    print("wrote %d textures to %s" % (2 * len(DYES), os.path.relpath(OUT, ROOT)))


if __name__ == "__main__":
    main()
