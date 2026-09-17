#!/usr/bin/env python3
"""Builds the Milk Can: block models, textures and the Blockbench script that shows them.

Run from the project root:  python art/milk_can/build_milk_can.py [v1|v2]

Writes  art/milk_can<suffix>/assets/goblinlabour/models/block/milk_can*.json
        art/milk_can<suffix>/assets/goblinlabour/textures/block/milk_can_*.png
        art/milk_can<suffix>/blockbench.js   (risky_eval script: one java_block project, one group per model)

Two variants share this script and their file names, so either drops into the mod the same way:
    v1  art/milk_can/      10 px wide, rounded discs (two chamfer steps)   - the default
    v2  art/milk_can_v2/   12 px wide, one pixel wider on every side, one chamfer step: more angular;
                           a full block high when open (body 11, rim top at 16) with the lid on top of that
                           (18 px), whose sides take a second texture milk_can_side_top; the closed can
                           (milk_can_lid) still has the milk of stage 3 running down its sides into the puddle

An old German milk can in grey aluminium: foot ring, round body with two pressed ribs, shoulder, narrow
neck with a rolled rim and two ear handles. The can itself is open; the fill stages are overlays in the
style of the Milk Churn's multipart models, so a blockstate draws "milk_can" plus at most one of

    milk_can_milk_1   a single drip running over the rim, from the inside down the outside
    milk_can_milk_2   milk up to the rim, running down two sides
    milk_can_milk_3   milk domed over the rim, running down all four sides into a puddle
    milk_can_lid      full: lid on, clean

The round parts are pixel discs made of overlapping boxes (like vanilla's round blocks), so no cube is
rotated and every face maps its texture pixel for pixel. Side faces take the side texture by height
(texture row = 16 - y), top faces the top texture by x and z, which keeps the shading continuous
across all boxes. Needs Pillow.
"""

import json
import math
import os
import random

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
NS = "goblinlabour:block/"

VARIANTS = {  # name -> (output folder, pixels added on every side, angular discs, tall)
    "v1": ("milk_can", 0, False, False),
    "v2": ("milk_can_v2", 1, True, True),
}
OUT = MODELS = TEXTURES = PROJECT = None
G = 0  # pixels added on every side
ANGULAR = False
TALL = False  # a full block high when open, the lid on top of that, and still spilling when closed

# heights of the profile: top of the body, of the shoulder, of the neck (= bottom of the rim), of the rim, of the
# lid plate and of the knob
SHORT_HEIGHTS = {"shoulder": 10, "neck": 11, "rim": 13, "top": 14, "lid": 15, "knob": 16}
TALL_HEIGHTS = {"shoulder": 11, "neck": 12, "rim": 15, "top": 16, "lid": 17, "knob": 18}
H = SHORT_HEIGHTS


def configure(variant):
    global OUT, MODELS, TEXTURES, PROJECT, G, ANGULAR, TALL, H
    folder, G, ANGULAR, TALL = VARIANTS[variant]
    H = TALL_HEIGHTS if TALL else SHORT_HEIGHTS
    OUT = os.path.join(os.path.dirname(HERE), folder)
    MODELS = os.path.join(OUT, "assets", "goblinlabour", "models", "block")
    TEXTURES = os.path.join(OUT, "assets", "goblinlabour", "textures", "block")
    PROJECT = folder


def lift(y):
    """A height of the short can moved to the same place on this variant's profile (identity for the short can)."""
    short = [0, 10, 11, 13, 14]
    here = [0, H["shoulder"], H["neck"], H["rim"], H["top"]]
    for i in range(1, len(short)):
        if y <= short[i]:
            a, b = short[i - 1], short[i]
            v = here[i - 1] + (y - a) * (here[i] - here[i - 1]) / (b - a)
            return int(v) if v == int(v) else v
    return here[-1] + (y - short[-1])


FILM = 0.2  # thickness of milk running over a surface; keeps it off the metal faces (no z-fighting)


# ---------------------------------------------------------------- geometry

def disc(d):
    """The boxes of a pixel disc of diameter d around the block centre, as (x0, z0, x1, z1)."""
    h = d / 2
    if d >= 8 and not ANGULAR:
        return [(8 - h, 8 - h + 2, 8 + h, 8 + h - 2),
                (8 - h + 1, 8 - h + 1, 8 + h - 1, 8 + h - 1),
                (8 - h + 2, 8 - h, 8 + h - 2, 8 + h)]
    return [(8 - h, 8 - h + 1, 8 + h, 8 + h - 1),
            (8 - h + 1, 8 - h, 8 + h - 1, 8 + h)]


def ring(outer, inner):
    """The rolled rim: a disc with a smaller disc cut out, as boxes."""
    if not ANGULAR:
        assert (outer, inner) == (8, 6)
        return ring8()
    def pixels(d):
        return {(x, z) for (x0, z0, x1, z1) in disc(d) for x in range(int(x0), int(x1)) for z in range(int(z0), int(z1))}
    left = pixels(outer) - pixels(inner)
    boxes = []
    while left:  # greedy: longest run along x, then grow it along z
        x, z = min(left, key=lambda p: (p[1], p[0]))
        x1 = x
        while (x1 + 1, z) in left:
            x1 += 1
        z1 = z
        while all((i, z1 + 1) in left for i in range(x, x1 + 1)):
            z1 += 1
        for i in range(x, x1 + 1):
            for k in range(z, z1 + 1):
                left.discard((i, k))
        boxes.append((x, z, x1 + 1, z1 + 1))
    return boxes


def ring8():
    """The rounded rim of v1: a disc of 8 with a disc of 6 cut out."""
    return [(6, 4, 10, 5), (6, 11, 10, 12), (4, 6, 5, 10), (11, 6, 12, 10),
            (5, 5, 6, 6), (10, 5, 11, 6), (5, 10, 6, 11), (10, 10, 11, 11)]


def face_uvs(frm, to, textures):
    """Faces for a box: sides by height from the side texture, top and bottom by x/z.

    A tall can reaches past the 16 rows of one texture: metal above y 16 takes #side_top, whose rows count on
    from there (row = 32 - y). Nothing made of metal crosses y 16. Milk is plain enough to take any rows.
    """
    x0, y0, z0 = frm
    x1, y1, z1 = to
    side, top, bottom = textures
    v0, v1 = 16 - y1, 16 - y0
    if TALL and y0 >= 16 and side == "#side":
        side, v0, v1 = "#side_top", 32 - y1, 32 - y0
    elif TALL and side == "#milk" and y1 > 16:
        v0, v1 = 0, min(16, y1 - y0)
    faces = {
        "north": {"uv": [16 - x1, v0, 16 - x0, v1], "texture": side},
        "south": {"uv": [x0, v0, x1, v1], "texture": side},
        "east": {"uv": [16 - z1, v0, 16 - z0, v1], "texture": side},
        "west": {"uv": [z0, v0, z1, v1], "texture": side},
        "up": {"uv": [x0, z0, x1, z1], "texture": top},
        "down": {"uv": [x0, 16 - z1, x1, 16 - z0], "texture": bottom},
    }
    return faces


def r(v):
    return round(v, 3)


def box(name, frm, to, textures):
    frm = [r(c) for c in frm]
    to = [r(c) for c in to]
    assert not (TALL and textures[0] == "#side" and frm[1] < 16 < to[1]), "%s crosses y 16" % name
    faces = face_uvs(frm, to, textures)
    for f in faces.values():
        f["uv"] = [r(max(0.0, min(16.0, c))) for c in f["uv"]]
    return {"name": name, "from": frm, "to": to, "faces": faces}


METAL = ("#side", "#top", "#bottom")
INSIDE = ("#side", "#inside", "#bottom")
MILK = ("#milk", "#milk", "#milk")


# the can's profile from the ground up: (y0, y1, diameter)
def profile():
    return [(0, H["shoulder"], 10 + 2 * G), (H["shoulder"], H["neck"], 8 + 2 * G), (H["neck"], H["rim"], 6 + 2 * G)]


def radius_at(y):
    """Half-width of the flat face a drip runs down at height y (the rim above the neck counts)."""
    if y >= H["rim"]:
        return 4 + G
    for y0, y1, d in profile():
        if y0 <= y < y1:
            return d / 2
    return 5 + G


def can():
    elements = []
    for (y0, y1, d), part in zip(profile(), ["body", "shoulder", "neck"]):
        for i, (x0, z0, x1, z1) in enumerate(disc(d)):
            textures = INSIDE if part == "neck" else METAL
            elements.append(box("%s_%d" % (part, i), (x0, y0, z0), (x1, y1, z1), textures))
    for i, (x0, z0, x1, z1) in enumerate(ring(8 + 2 * G, 6 + 2 * G)):
        elements.append(box("rim_%d" % i, (x0, H["rim"], z0), (x1, H["top"], z1), METAL))
    # ear handles on east and west: a post on the body's top ledge and a bar back to the neck
    for side, (post, bar) in {"east": ((12 + G, 13 + G), (11 + G, 13 + G)),
                                 "west": ((3 - G, 4 - G), (3 - G, 5 - G))}.items():
        elements.append(box("handle_%s_post" % side, (post[0], H["shoulder"], 7.5), (post[1], H["rim"] - 1, 8.5), METAL))
        elements.append(box("handle_%s_bar" % side, (bar[0], H["rim"] - 1, 7.5), (bar[1], H["rim"], 8.5), METAL))
    return elements


def lid():
    elements = []
    for i, (x0, z0, x1, z1) in enumerate(disc(8 + 2 * G)):
        elements.append(box("lid_%d" % i, (x0, H["top"], z0), (x1, H["lid"], z1), METAL))
    for i, (x0, z0, x1, z1) in enumerate(disc(4 + 2 * G)):
        elements.append(box("lid_knob_%d" % i, (x0, H["lid"], z0), (x1, H["knob"], z1), METAL))
    if TALL:
        # closed, but it was filled to the brim: the milk that ran over before the lid went on is still there
        elements += spill_outside()
    return elements


def drip(name, side, offset, width, y_end, y_start=None):
    """Milk running from the rim top down one side, following the steps of the profile, ending at y_end.

    offset/width place it across the face (block units from the centre line); it stays on the flat part.
    """
    y_start = H["top"] if y_start is None else y_start
    out = []
    segments = [(H["rim"], H["top"]), (H["neck"], H["rim"]), (H["shoulder"], H["neck"]), (0, H["shoulder"])]
    for lo, hi in segments:
        hi = min(hi, y_start)
        lo_cut = max(lo, y_end)
        if lo_cut >= hi:
            continue
        rad = radius_at(lo)
        top_film = hi + FILM if hi == y_start else hi
        for part_lo, part_hi in split16(lo_cut, top_film):
            out.append(film_on_face(name + "_%g" % part_lo if (part_lo, part_hi) != (lo_cut, top_film)
                                    else name + "_%g" % lo, side, rad, offset, width, part_lo, part_hi))
    # films over the ledges where the profile steps out (the drip turns outward there)
    ledges = [(H["rim"], 3 + G, 4 + G, "under"), (H["neck"], 3 + G, 4 + G, "top"),
              (H["shoulder"], 4 + G, 5 + G, "top")]
    for y, r_in, r_out, kind in ledges:
        if y_end <= y < y_start or (kind == "under" and y_end < y < y_start + 1):
            out.append(ledge_film(name + "_ledge%g" % y, side, r_in, r_out, offset, width, y, kind))
    return out


def split16(y0, y1):
    """A run of milk cut at y 16 on a tall can, so no box needs more than one texture's rows."""
    if TALL and y0 < 16 < y1:
        return [(y0, 16), (16, y1)]
    return [(y0, y1)]


def along(side, rad, a0, a1, d0, d1):
    """Box corners for something lying against the given side at radius rad (a = across, d = depth out)."""
    if side == "north":
        return (8 + a0, 8 - rad - d1), (8 + a1, 8 - rad - d0), "xz"
    if side == "south":
        return (8 + a0, 8 + rad + d0), (8 + a1, 8 + rad + d1), "xz"
    if side == "west":
        return (8 - rad - d1, 8 + a0), (8 - rad - d0, 8 + a1), "xz"
    return (8 + rad + d0, 8 + a0), (8 + rad + d1, 8 + a1), "xz"


def film_on_face(name, side, rad, offset, width, y0, y1):
    (x0, z0), (x1, z1), _ = along(side, rad, offset, offset + width, 0, FILM)
    return box(name, (x0, y0, z0), (x1, y1, z1), MILK)


def ledge_film(name, side, r_in, r_out, offset, width, y, kind):
    y0, y1 = (y, y + FILM) if kind == "top" else (y - FILM, y)
    (x0, z0), (x1, z1), _ = along(side, r_in, offset, offset + width, -FILM if kind == "top" else 0,
                                  (r_out - r_in) + FILM)
    return box(name, (x0, y0, z0), (x1, y1, z1), MILK)


def spill_outside():
    """The milk of a can that ran over: streams down all four sides and the puddle it stands in."""
    elements = []
    elements += drip("drip_n", "north", -2, 2, lift(0))
    elements += drip("drip_n2", "north", 1, 1, lift(6))
    elements += drip("drip_s", "south", 0, 2, lift(0))
    elements += drip("drip_e", "east", -1, 1, lift(2))
    elements += drip("drip_w", "west", 0, 1, lift(4))
    # the puddle the can stands in
    for i, (x0, z0, x1, z1) in enumerate(disc(12 + 2 * G)):
        elements.append(box("puddle_%d" % i, (x0, 0, z0), (x1, 0.1, z1), MILK))
    return elements


def milk(stage):
    elements = []
    rim, top = H["rim"], H["top"]
    if stage == 1:
        # no milk to see yet, only one drip over the north rim: up the inner wall, over the top, down outside
        elements.append(box("drip_n_inside", (7, rim, 5 - G), (8, top, 5 - G + FILM), MILK))
        elements.append(box("spill_n", (7, top, 3.8 - G), (8, top + FILM, 5 - G + FILM), MILK))
        elements += drip("drip_n", "north", -1, 1, lift(11.4))
    elif stage == 2:
        for i, (x0, z0, x1, z1) in enumerate(disc(6 + 2 * G)):
            elements.append(box("surface_%d" % i, (x0, rim, z0), (x1, top + FILM / 2, z1), MILK))
        elements.append(box("spill_n", (6, top, 3.8 - G), (9, top + FILM, 5 - G), MILK))
        elements.append(box("spill_s", (7, top, 11 + G), (9, top + FILM, 12.2 + G), MILK))
        elements += drip("drip_n", "north", -2, 1, lift(5))
        elements += drip("drip_n2", "north", 0, 1, lift(10))
        elements += drip("drip_s", "south", -1, 1, lift(7))
    elif stage == 3:
        for i, (x0, z0, x1, z1) in enumerate(disc(6 + 2 * G)):
            elements.append(box("surface_%d" % i, (x0, rim, z0), (x1, top + 0.6, z1), MILK))
        for i, (x0, z0, x1, z1) in enumerate(disc(8 + 2 * G)):
            elements.append(box("overflow_%d" % i, (x0 - FILM, top, z0 - FILM), (x1 + FILM, top + 0.3, z1 + FILM), MILK))
        elements += spill_outside()
    return elements


# ---------------------------------------------------------------- textures

def noise(seed):
    rnd = random.Random(seed)
    return lambda: rnd.uniform(-1, 1)


def hexrgb(h):
    return tuple(int(h[i:i + 2], 16) for i in (1, 3, 5))


def shade(rgb, f, add=0.0):
    return tuple(max(0, min(255, int(round(c * f + add)))) for c in rgb) + (255,)


SHORT_ROWS = {  # y -> colour: foot band, two pressed ribs, shoulder, neck, rim, lid
    0: "#5f656c", 1: "#8a9097", 2: "#80868d", 3: "#6a7077", 4: "#a3a9b0", 5: "#848a91",
    6: "#82888f", 7: "#80868d", 8: "#6a7077", 9: "#a3a9b0", 10: "#989ea5", 11: "#767c83",
    12: "#7b8188", 13: "#a9afb6", 14: "#8e949b", 15: "#a0a6ad",
}
TALL_ROWS = {  # y 0-15: foot band, two pressed ribs, shoulder, neck, rim
    0: "#5f656c", 1: "#8a9097", 2: "#82888f", 3: "#6a7077", 4: "#a3a9b0", 5: "#848a91",
    6: "#82888f", 7: "#6a7077", 8: "#a3a9b0", 9: "#80868d", 10: "#848a91", 11: "#989ea5",
    12: "#767c83", 13: "#7b8188", 14: "#7b8188", 15: "#a9afb6",
}
TALL_TOP_ROWS = {  # y 16-17 as rows 15-14 of #side_top: lid plate and knob
    16: "#8e949b", 17: "#a0a6ad",
}


def paint_side(rows=None, first=0):
    """Rows of metal by height: texture row 15 is y `first`, row 0 is y `first` + 15."""
    rows = SHORT_ROWS if rows is None else rows
    img = Image.new("RGBA", (16, 16))
    n = noise(11 + first)
    rows = {y - first: colour for y, colour in rows.items()}
    for y in range(16):
        rows.setdefault(y, "#80868d")
    streak = [0.0] * 16
    rnd = random.Random(5)
    for u in range(16):
        streak[u] = rnd.uniform(-0.03, 0.03)
    for y, colour in rows.items():
        base = hexrgb(colour)
        for u in range(16):
            roundness = 0.84 + 0.24 * math.cos(math.pi * (u - 6.0) / 17.0)
            f = roundness + streak[u] + 0.015 * n()
            img.putpixel((u, 16 - 1 - y), shade(base, f))
    # a couple of dents and scratches on the body
    dents = [] if first else [(4, 4, 0.86), (5, 4, 0.9), (11, 5, 1.08), (9, 3, 0.88)]
    if first == 0 and rows is not None and TALL:
        dents += [(12, 6, 0.88), (3, 9, 1.07)]
    for (u, y, f) in dents:
        img.putpixel((u, 15 - y), shade(img.getpixel((u, 15 - y))[:3], f))
    return img


def paint_top():
    img = Image.new("RGBA", (16, 16))
    n = noise(21)
    for z in range(16):
        for x in range(16):
            d = math.hypot(x + 0.5 - 8, z + 0.5 - 8)
            if d < 2.0 + G:
                base = "#a6acb3"
            elif d < 2.9 + G:
                base = "#747a81"
            elif d < 4.0 + G:
                base = "#979da4"
            elif d < 5.0 + G:
                base = "#8c9299"
            else:
                base = "#858b92"
            light = 1.0 + 0.06 * ((8 - x) + (8 - z)) / 8
            img.putpixel((x, z), shade(hexrgb(base), light + 0.02 * n()))
    return img


def paint_inside():
    img = Image.new("RGBA", (16, 16))
    n = noise(31)
    for z in range(16):
        for x in range(16):
            d = math.hypot(x + 0.5 - 8, z + 0.5 - 8)
            f = 0.55 + 0.12 * min(d, 3) / 3
            img.putpixel((x, z), shade(hexrgb("#4a4f55"), f + 0.03 * n()))
    return img


def paint_bottom():
    img = Image.new("RGBA", (16, 16))
    n = noise(41)
    for z in range(16):
        for x in range(16):
            img.putpixel((x, z), shade(hexrgb("#6a7076"), 1 + 0.03 * n()))
    return img


def paint_milk():
    img = Image.new("RGBA", (16, 16))
    n = noise(51)
    for y in range(16):
        for x in range(16):
            f = 1.0 + 0.012 * n() + 0.02 * math.sin((x + 2 * y) * 0.9)
            img.putpixel((x, y), shade(hexrgb("#f1ede0"), f))
    for (x, y) in [(3, 4), (4, 4), (10, 9), (11, 9), (6, 13), (12, 2)]:
        img.putpixel((x, y), shade(hexrgb("#fcfaf3"), 1))
    for (x, y) in [(7, 6), (2, 11), (13, 12)]:
        img.putpixel((x, y), shade(hexrgb("#ddd6c3"), 1))
    return img


# ---------------------------------------------------------------- output

def model_set():
    metal = {"particle": "milk_can_side", "side": "milk_can_side", "top": "milk_can_top", "bottom": "milk_can_bottom"}
    if TALL:
        metal["side_top"] = "milk_can_side_top"
    milk_only = {"particle": "milk_can_milk", "milk": "milk_can_milk"}
    lid_textures = dict(metal, milk="milk_can_milk") if TALL else metal
    return [
        ("milk_can", lambda: can(), dict(metal, inside="milk_can_inside"), True),
        ("milk_can_milk_1", lambda: milk(1), milk_only, False),
        ("milk_can_milk_2", lambda: milk(2), milk_only, False),
        ("milk_can_milk_3", lambda: milk(3), milk_only, False),
        ("milk_can_lid", lambda: lid(), lid_textures, False),
    ]


def write_json(name, elements, textures, parent):
    model = {}
    if parent:
        model["parent"] = "minecraft:block/block"
    model["textures"] = {k: NS + v for k, v in textures.items()}
    model["elements"] = [{"name": e["name"], "from": e["from"], "to": e["to"], "faces": e["faces"]}
                         for e in elements]
    with open(os.path.join(MODELS, name + ".json"), "w", encoding="utf-8", newline="\n") as f:
        json.dump(model, f, indent=2)
        f.write("\n")


def main(variant):
    configure(variant)
    os.makedirs(MODELS, exist_ok=True)
    os.makedirs(TEXTURES, exist_ok=True)
    images = {"milk_can_side": paint_side(TALL_ROWS if TALL else None), "milk_can_top": paint_top(),
              "milk_can_inside": paint_inside(), "milk_can_bottom": paint_bottom(), "milk_can_milk": paint_milk()}
    if TALL:
        images["milk_can_side_top"] = paint_side(TALL_TOP_ROWS, 16)
    for name, img in images.items():
        img.save(os.path.join(TEXTURES, name + ".png"))

    groups = []
    for name, build, textures, parent in model_set():
        elements = build()
        for e in elements:
            for c in e["from"] + e["to"]:
                assert -16 <= c <= 32, "%s: %s is outside the model range" % (name, e["name"])
            assert all(e["to"][i] > e["from"][i] for i in range(3)), "%s: %s is empty" % (name, e["name"])
        write_json(name, elements, textures, parent)
        groups.append({"name": name, "textures": textures, "elements": elements})
        print("%-16s %3d elements" % (name, len(elements)))

    tex_paths = {k: os.path.join(TEXTURES, k + ".png").replace("\\", "/") for k in images}
    with open(os.path.join(OUT, "blockbench.js"), "w", encoding="utf-8", newline="\n") as f:
        f.write(BLOCKBENCH_JS.replace("__GROUPS__", json.dumps(groups)).replace("__TEXTURES__", json.dumps(tex_paths))
                .replace("__PROJECT__", PROJECT))


BLOCKBENCH_JS = r"""
const GROUPS = __GROUPS__;
const TEXTURES = __TEXTURES__;
for (const p of ModelProject.all.filter(p => p.name === '__PROJECT__')) p.close(true);
newProject(Formats.java_block);
Project.name = '__PROJECT__';
const tex = {};
for (const [name, path] of Object.entries(TEXTURES)) {
  const t = new Texture({name: name + '.png'}).fromPath(path).add(false);
  tex[name] = t;
}
for (const g of GROUPS) {
  const group = new Group({name: g.name, origin: [8, 8, 8]}).init();
  for (const e of g.elements) {
    const faces = {};
    for (const [dir, f] of Object.entries(e.faces)) {
      const key = f.texture.slice(1);
      faces[dir] = {uv: f.uv, texture: tex[g.textures[key]].uuid};
    }
    const cube = new Cube({name: e.name, from: e.from, to: e.to, autouv: 0, faces}).init();
    cube.addTo(group);
  }
}
Canvas.updateAll();
({cubes: Cube.all.length, groups: Group.all.map(g => g.name + ':' + g.children.length), textures: Texture.all.map(t => t.name)});
"""

if __name__ == "__main__":
    import sys
    main(sys.argv[1] if len(sys.argv) > 1 else "v1")
