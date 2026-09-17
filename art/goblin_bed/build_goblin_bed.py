#!/usr/bin/env python3
"""Builds the Goblin Straw Bed (design 2): block model, textures and the Blockbench script that shows them.

Run from the project root:  python art/goblin_bed/build_goblin_bed.py

Writes  art/goblin_bed/assets/goblinlabour/models/block/goblin_straw_bed.json
        art/goblin_bed/assets/goblinlabour/textures/block/goblin_bed_*.png
        art/goblin_bed/blockbench.js   (risky_eval script: one java_block project, one group per part)

A goblin-made cot: a frame of bark poles, a heap of loose straw with tufts sticking out, a patchwork
blanket sewn from scraps of leather, cloth and hide, lying on the frame over the lower part of the straw
with its sheepskin side turned down at the top, a flat sack for a pillow, and a rough plank and a
crossbar for a headboard between the head posts.

One block wide, one and a half long: the head is north and stays inside the block (z 0, like the old
model, so the blockstate keeps working and the head can stand against a wall); the foot reaches 8 px into
the block to the south (z 24). The straw is 9 px high where the goblin sleeps (it lies at y 9, its head at
the block's centre); the frame stays at y 4 and above with only the corner posts reaching the ground, so the
job props still have room under it.

Every face maps its texture one texel per model pixel. Straw and pillow use Minecraft's default UVs
(wrapped every 16 px, so boxes longer than that are split there), so their texture runs on across
neighbouring boxes; wood picks the grain by the face's long side (poles lying down take the turned
texture); the plank takes hand-placed UVs. The blanket is one sheet of cloth laid over the straw with its
edges rounded by a pixel: every blanket face gets its own spot in a 32 x 32 texture, painted texel by texel
from where that texel lies on the unfolded sheet, so the patches run on from the top over the rounded edge
down the sides and the foot. Needs Pillow.
"""

import base64
import json
import math
import os
import random

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "assets", "goblinlabour")
MODELS = os.path.join(OUT, "models", "block")
TEXTURES = os.path.join(OUT, "textures", "block")
NS = "goblinlabour:block/"
PROJECT = "goblin_straw_bed"
LENGTH = 24  # the foot end


# ---------------------------------------------------------------- colours

def hexrgb(h):
    return tuple(int(h[i:i + 2], 16) for i in (1, 3, 5))


def shade(rgb, f, add=0.0):
    return tuple(max(0, min(255, int(round(c * f + add)))) for c in rgb[:3]) + (255,)


BARK = ["#2f2014", "#43301e", "#5a4028", "#6f5033", "#87633f"]
PLANK = ["#4e3822", "#654a2e", "#7a5b3a", "#8e6c47", "#a07c53"]
STRAW = ["#6d5418", "#9a7a26", "#bf9a36", "#d8b448", "#ead06a"]
FUR = ["#9d9079", "#b9ad94", "#cfc4ab", "#e0d7c1", "#ece5d3"]  # sheepskin
SACK = ["#6f5f45", "#8a785a", "#a08d6c", "#b3a07e"]
LEATHER = ["#4a311d", "#5f3f26", "#76502f", "#8c623b"]
MOSS = ["#36421d", "#46552a", "#586a34", "#6b7f40"]
RED = ["#512a1d", "#693626", "#7d4330", "#8f503a"]
HIDE = ["#6b5436", "#86694a", "#9c7f5a", "#b1946b"]
STITCH = "#c9b68a"


# ---------------------------------------------------------------- textures

def new():
    return Image.new("RGBA", (16, 16))


def put(img, x, y, colour, f=1.0):
    img.putpixel((x % 16, y % 16), shade(hexrgb(colour) if isinstance(colour, str) else colour, f))


def streaks(rnd, length, lo, hi):
    """A column of values that stays put for a few pixels, then steps: grain."""
    values, v = [], rnd.randint(lo, hi)
    while len(values) < length:
        values += [v] * rnd.randint(2, 5)
        v = max(lo, min(hi, v + rnd.choice([-1, 1])))
    return values[:length]


def paint_log():
    """Bark with vertical grain in 2 px strips (lit left column, shaded right one), for posts and poles."""
    rnd = random.Random(7)
    img = new()
    for x in range(16):
        col = streaks(rnd, 16, 2, 4) if x % 2 == 0 else streaks(rnd, 16, 1, 3)
        for y in range(16):
            put(img, x, y, BARK[col[y]])
    for i in range(14):  # crevices, a few of them deep
        x, y, n = rnd.randrange(16), rnd.randrange(16), rnd.randint(2, 4)
        for k in range(n):
            put(img, x, y + k, BARK[0 if i % 3 == 0 else 1])
    for _ in range(6):  # pale scuffs where the bark is worn
        put(img, rnd.randrange(0, 16, 2), rnd.randrange(16), BARK[4], 1.05)
    return img


def paint_pole():
    """The same bark turned on its side, for poles lying down: grain along the pole, lit top row."""
    return paint_log().transpose(Image.Transpose.TRANSPOSE)


def paint_log_end():
    """Cut ends, 2 px across: pale wood lit from the top left, bark at the far corner."""
    img = new()
    tones = [[PLANK[4], PLANK[3]], [PLANK[3], BARK[2]]]
    for y in range(16):
        for x in range(16):
            put(img, x, y, tones[y % 2][x % 2])
    return img


def paint_straw():
    rnd = random.Random(3)
    img = new()
    for y in range(16):
        for x in range(16):
            put(img, x, y, STRAW[rnd.choice([1, 2, 2, 2])])
    for _ in range(58):
        x, y = rnd.randrange(16), rnd.randrange(16)
        slope = rnd.choice([0, 0, 0, 1, -1])
        tone = STRAW[rnd.choice([3, 3, 4])]
        for k in range(rnd.randint(3, 6)):
            px, py = (x + k) % 16, (y + (k * slope) // 3) % 16
            put(img, px, py, tone)
            if img.getpixel((px, (py + 1) % 16))[:3] == hexrgb(STRAW[2]):
                put(img, px, py + 1, STRAW[1])
    for _ in range(12):
        put(img, rnd.randrange(16), rnd.randrange(16), STRAW[0])
    return img


def paint_plank():
    """Rough boards with horizontal grain, a darker edge every 5 rows (the headboard's front uses rows 1-5,
    its back rows 9-13) and a few cracks."""
    rnd = random.Random(19)
    img = new()
    for y in range(16):
        row = streaks(rnd, 16, 1, 3)
        for x in range(16):
            put(img, x, y, PLANK[row[x]])
    for y in (1, 5, 9, 13):
        for x in range(16):
            put(img, x, y, img.getpixel((x, y)), 0.82)
    for (x, y) in [(3, 2), (4, 2), (5, 3), (10, 4), (11, 4), (12, 3), (6, 11), (7, 11), (11, 12)]:
        put(img, x, y, PLANK[0])
    return img


def weave(rnd, palette, x, y):
    t = 1 if (x + y) % 2 else 2
    if rnd.random() < 0.15:
        t += rnd.choice([-1, 1])
    return palette[max(0, min(len(palette) - 1, t))]


def mottle(rnd, palette, x, y):
    return palette[rnd.choice([1, 2, 2, 2, 2])]


# The blanket unfolded: s runs across the bed from the west hem up the west side (s 0-2), over the rounded
# edge (3), across the top (4-17, x 1-15), over the east edge (18) and down to the east hem (19-21); t runs
# from the head of the blanket along the top (0-10, z 12-23), over the rounded foot edge (11) and down to
# the foot hem (12-14). The sheepskin fold lies at t -2 and -1, its head edge at t -3.
SHEET_S = 22
SHEET_T = (-3, 15)
PATCHES = [  # (s0, t0, s1, t1, palette, pattern)
    (0, 0, 9, 5, LEATHER, mottle),
    (9, 0, 22, 4, MOSS, weave),
    (9, 4, 14, 9, RED, weave),
    (14, 4, 22, 9, HIDE, mottle),
    (0, 5, 9, 9, MOSS, weave),
    (0, 9, 12, 15, HIDE, mottle),
    (12, 9, 22, 15, LEATHER, mottle),
]


def paint_sheet():
    """The unfolded blanket as an image, pixel (s, t - SHEET_T[0]): scraps with a dashed seam along each
    inner left and top edge, a darker hem, and the sheepskin rows of the fold."""
    rnd = random.Random(23)
    t0 = SHEET_T[0]
    img = Image.new("RGBA", (SHEET_S, SHEET_T[1] - t0))
    for t in range(t0, 0):
        for x in range(SHEET_S):
            img.putpixel((x, t - t0), shade(hexrgb(FUR[rnd.choice([2, 2, 3, 3, 1, 4])]), 1.0))
    for (a0, b0, a1, b1, pal, pattern) in PATCHES:
        for t in range(b0, b1):
            for x in range(a0, a1):
                img.putpixel((x, t - t0), shade(hexrgb(pattern(rnd, pal, x, t)), 1.0))
    for (a0, b0, a1, b1, pal, _) in PATCHES:
        if a0 > 0:
            for t in range(b0, b1):
                img.putpixel((a0, t - t0), shade(hexrgb(STITCH if (t - b0) % 2 == 0 else pal[0]), 1.0))
        if b0 > 0:
            for x in range(a0 + 1, a1, 2):
                img.putpixel((x, b0 - t0), shade(hexrgb(STITCH), 1.0))
    for (x, t) in [(16, 6), (19, 7), (15, 5), (5, 12), (8, 11)]:  # spots on the hide
        img.putpixel((x, t - t0), shade(hexrgb(HIDE[0]), 1.0))
    for x in range(SHEET_S):  # hems
        for t in range(0, SHEET_T[1]):
            if x in (0, SHEET_S - 1) or t == SHEET_T[1] - 1:
                img.putpixel((x, t - t0), shade(img.getpixel((x, t - t0)), 0.85))
    return img


class ClothAtlas:
    """Blanket faces packed into one square texture (32 px if they fit, else 64), a texel per model pixel,
    each with a pixel of gutter that repeats its edge (keeps mipmaps from bleeding). The model is built
    twice: the first pass only collects the faces' sizes, the second gets their packed spots."""

    def __init__(self):
        self.sizes, self.spots, self.faces, self.size = [], None, [], 32

    def place(self, texels):
        h, w = len(texels), len(texels[0])
        if self.spots is None:
            self.sizes.append((w, h))
            return 0, 0
        x, y = self.spots[len(self.faces)]
        self.faces.append((x, y, texels))
        return x, y

    def layout(self):
        """Shelves, tallest faces first."""
        order = sorted(range(len(self.sizes)), key=lambda k: (-self.sizes[k][1], -self.sizes[k][0]))
        for size in (32, 64):
            spots, x, y, row = [None] * len(self.sizes), 0, 0, 0
            for k in order:
                w, h = self.sizes[k][0] + 2, self.sizes[k][1] + 2
                if x + w > size:
                    x, y, row = 0, y + row, 0
                spots[k] = (x + 1, y + 1)
                x, row = x + w, max(row, h)
            if y + row <= size:
                self.spots, self.size = spots, size
                return
        raise AssertionError("blanket faces do not fit a 64 px texture")

    def uv(self, x, y, w, h):
        f = 16 / self.size
        return [x * f, y * f, (x + w) * f, (y + h) * f]

    def paint(self):
        sheet = paint_sheet()
        img = Image.new("RGBA", (self.size, self.size))
        for (fx, fy, texels) in self.faces:
            h, w = len(texels), len(texels[0])
            for j in range(-1, h + 1):
                for i in range(-1, w + 1):
                    s_, t_ = texels[max(0, min(h - 1, j))][max(0, min(w - 1, i))]
                    s_ = max(0, min(SHEET_S - 1, s_))
                    t_ = max(SHEET_T[0], min(SHEET_T[1] - 1, t_))
                    img.putpixel((fx + i, fy + j), sheet.getpixel((s_, t_ - SHEET_T[0])))
        return img


ATLAS = ClothAtlas()


def paint_blanket():
    return ATLAS.paint()


def paint_sack():
    rnd = random.Random(31)
    img = new()
    for y in range(16):
        for x in range(16):
            put(img, x, y, weave(rnd, SACK, x, y))
    for x in range(0, 16, 2):  # seam
        put(img, x, 4, SACK[0])
    return img


TEXTURE_PAINTERS = {
    "goblin_bed_log": paint_log,
    "goblin_bed_pole": paint_pole,
    "goblin_bed_log_end": paint_log_end,
    "goblin_bed_straw": paint_straw,
    "goblin_bed_plank": paint_plank,
    "goblin_bed_blanket": paint_blanket,
    "goblin_bed_sack": paint_sack,
}
TEXTURE_KEYS = {name[len("goblin_bed_"):]: name for name in TEXTURE_PAINTERS}


# ---------------------------------------------------------------- geometry

DIRS = ("north", "south", "east", "west", "up", "down")


def default_uv(d, f, t):
    (x1, y1, z1), (x2, y2, z2) = f, t
    return {
        "north": [16 - x2, 16 - y2, 16 - x1, 16 - y1],
        "south": [x1, 16 - y2, x2, 16 - y1],
        "west": [z1, 16 - y2, z2, 16 - y1],
        "east": [16 - z2, 16 - y2, 16 - z1, 16 - y1],
        "up": [x1, z1, x2, z2],
        "down": [x1, 16 - z2, x2, 16 - z1],
    }[d]


def fit(uv):
    """Wraps a UV rectangle into the texture (by 16, so tiling continues), else slides it in."""
    for a, b in ((0, 2), (1, 3)):
        lo, hi = uv[a], uv[b]
        while hi <= 0:
            lo, hi = lo + 16, hi + 16
        while lo >= 16:
            lo, hi = lo - 16, hi - 16
        if lo < 0:
            lo, hi = 0, hi - lo
        if hi > 16:
            lo, hi = lo - (hi - 16), 16
        uv[a], uv[b] = lo, hi
    return uv


def r(v):
    return round(v, 3)


ELEMENTS = []  # (group, element)


def box(group, name, frm, to, faces, rotation=None):
    """A box; one longer than 16 px from head to foot is split at z 16 so that every face fits the texture."""
    frm, to = [r(c) for c in frm], [r(c) for c in to]
    if to[2] - frm[2] > 16 and frm[2] < 16 < to[2] and not rotation:
        box(group, name + "_head", frm, [to[0], to[1], 16], faces)
        box(group, name + "_foot", [frm[0], frm[1], 16], to, faces)
        return
    assert all(to[i] > frm[i] for i in range(3)), name
    out = {}
    for d in DIRS:
        spec = faces(d, frm, to)
        if spec is None:
            continue
        uv = [r(c) for c in fit(list(spec[1]))]
        assert uv[2] - uv[0] <= 16 and uv[3] - uv[1] <= 16, "%s %s: face larger than the texture" % (name, d)
        out[d] = {"uv": uv, "texture": "#" + spec[0]}
    e = {"name": name, "from": frm, "to": to, "faces": out}
    if rotation:
        e["rotation"] = rotation
    ELEMENTS.append((group, e))


def plain(tex, skip=()):
    """Minecraft's default UVs on one texture: neighbouring boxes continue each other."""
    return lambda d, f, t: None if d in skip else (tex, default_uv(d, f, t))


def wood(skip=()):
    """Bark: grain along the face's long side; the 2 x 2 cut ends of posts and poles take the end texture."""
    def faces(d, f, t):
        if d in skip:
            return None
        uv = default_uv(d, f, t)
        w, h = abs(uv[2] - uv[0]), abs(uv[3] - uv[1])
        if w == h and w <= 2:
            return ("log_end", uv)
        return ("pole" if w > h else "log", uv)
    return faces


def patch(tex, uvs, skip=()):
    """Hand-placed UVs: a dict direction (or "*" for the rest) -> [u, v] or (texture, [u, v]); the face's
    size is added."""
    def faces(d, f, t):
        if d in skip:
            return None
        uv = default_uv(d, f, t)
        w, h = abs(uv[2] - uv[0]), abs(uv[3] - uv[1])
        spot, face_tex = uvs.get(d, uvs.get("*")), tex
        if isinstance(spot, tuple):
            face_tex, spot = spot
        if spot is not None:
            uv = [spot[0], spot[1], spot[0] + w, spot[1] + h]
        return (face_tex, uv)
    return faces


def frame():
    g = "frame"
    L = LENGTH
    box(g, "post_nw", [0, 0, 0], [2, 16, 2], wood())
    box(g, "post_ne", [14, 0, 0], [16, 16, 2], wood())
    # the foot posts end at the rail top, under the blanket
    box(g, "post_sw", [0, 0, L - 2], [2, 6, L], wood())
    box(g, "post_se", [14, 0, L - 2], [16, 6, L], wood())
    box(g, "rail_w", [0, 4, 2], [2, 6, L - 2], wood(skip=("north", "south")))
    box(g, "rail_e", [14, 4, 2], [16, 6, L - 2], wood(skip=("north", "south")))
    box(g, "rail_head", [2, 4, 0], [14, 6, 2], wood(skip=("east", "west")))
    box(g, "rail_foot", [2, 4, L - 2], [14, 6, L], wood(skip=("east", "west")))
    # sticks under the straw, seen from below
    for i, z in enumerate((5, 9, 13, 17)):
        box(g, "slat_%d" % i, [2, 4, z], [14, 5, z + 1], wood(skip=("east", "west")))


def straw():
    g = "straw"
    L = LENGTH
    box(g, "heap_core", [2, 5, 2], [14, 9, L - 2], plain("straw"))
    box(g, "heap_bulge", [1, 6, 1], [15, 8, L - 1], plain("straw"))
    # tufts poking out of the heap: 0.8 wide, half a pixel thick, turned 22.5 degrees around y
    tufts = [  # x0, x1, y, z (middle), angle: out of the sides and the top where the blanket leaves it bare
        (-1, 3, 6.5, 3.5, 22.5),
        (-1.5, 2, 6, 9, -22.5),
        (13, 17, 7, 5, -22.5),
        (13.5, 17, 6.5, 9.5, 22.5),
        (3, 7, 9, 6, 22.5),
        (9, 12, 9, 6.5, -22.5),
        (5, 8, 9, 9, -22.5),
    ]
    posts = [(0, 2, 0, 2), (14, 16, 0, 2)]  # x0, x1, z0, z1 of the head posts
    for i, (x0, x1, y, z, angle) in enumerate(tufts):
        mid, half, a = (x0 + x1) / 2, (x1 - x0) / 2, math.radians(angle)
        for k in range(21):  # no straw through a post: walk along the turned tuft
            t = -half + 2 * half * k / 20
            px, pz = mid + t * math.cos(a), z - t * math.sin(a)
            inside = [a0 - 0.4 < px < a1 + 0.4 and b0 - 0.4 < pz < b1 + 0.4 for a0, a1, b0, b1 in posts]
            assert not any(inside), "tuft %d runs through a post" % i
        origin = [r(mid), y, z]
        box(g, "tuft_%d" % i, [x0, y, z - 0.4], [x1, y + 0.5, z + 0.4], patch("straw", {"*": [1 + i, 3]}),
            rotation={"angle": angle, "axis": "y", "origin": origin})


BLANKET_Z = 12  # where the blanket starts; the sheepskin fold lies just above it
FOLD_Z = BLANKET_Z - 2


def face_point(d, frm, to, u, v):
    """The model point under default UV (u, v) of a face: default_uv run backwards."""
    return {
        "north": (16 - u, 16 - v, frm[2]),
        "south": (u, 16 - v, to[2]),
        "west": (frm[0], 16 - v, u),
        "east": (to[0], 16 - v, 16 - u),
        "up": (u, to[1], v),
        "down": (u, frm[1], 16 - v),
    }[d]


def sheet_spot(d, x, y, z):
    """Where a texel of the blanket's surface lies on the unfolded sheet (see SHEET_S)."""
    L = LENGTH
    if y > 9:  # the top layer: its top, or its 1 px sides at the rounded edges
        s = 3 if d == "west" else 18 if d == "east" else 4 + math.floor(x - 1)
    elif x < 1:  # west side; its top is the rounded edge, its foot end the corner next to the foot side
        s = 3 if d in ("up", "south") else math.floor(y - 6)
    elif x > 15:
        s = 18 if d in ("up", "south") else 19 + math.floor(9 - y)
    else:  # the foot side, whose ends at the rounded corners are edge columns
        s = 3 if d == "west" else 18 if d == "east" else 4 + math.floor(x - 1)
    if d == "north" and z <= FOLD_Z:
        t = -3
    elif d == "south" and z >= L - 1 and y > 9:
        t = 11
    elif z > L - 1:  # the foot side; the corners' outer sides continue the long sides
        t = 11 if d in ("up", "west", "east") else 12 + math.floor(9 - y)
    else:
        t = math.floor(z - BLANKET_Z)
    return s, t


def fur_spot(d, x, y, z):
    """A sheepskin texel of the sheet (its rows t -3 to -1), varied by position."""
    s_, _ = sheet_spot(d, x, y, z)
    return s_, -1 - math.floor(x + z) % 3


def cloth(skip=(), spot=None):
    """Blanket faces: each gets its own spot in the blanket texture, painted from the unfolded sheet."""
    spot = spot or sheet_spot

    def faces(d, f, t):
        if d in skip:
            return None
        u1, v1, u2, v2 = default_uv(d, f, t)
        w, h = round(u2 - u1), round(v2 - v1)
        assert (w, h) == (u2 - u1, v2 - v1), "blanket faces must be whole pixels"
        texels = [[spot(d, *face_point(d, f, t, u1 + i + 0.5, v1 + j + 0.5)) for i in range(w)]
                  for j in range(h)]
        x, y = ATLAS.place(texels)
        return ("blanket", ATLAS.uv(x, y, w, h))
    return faces


def blanket():
    """One px of cloth on the straw, 3 px down the sides and the foot onto the rails; the edge pixel between
    top and sides is left out all round, which rounds the edges."""
    g = "blanket"
    L = LENGTH
    z = BLANKET_Z
    box(g, "blanket_top", [1, 9, z], [15, 10, L - 1], cloth(skip=("north", "down")))
    box(g, "blanket_w", [0, 6, z], [1, 9, L], cloth(skip=("north", "east", "down")))
    box(g, "blanket_e", [15, 6, z], [16, 9, L], cloth(skip=("north", "west", "down")))
    box(g, "blanket_foot", [1, 6, L - 1], [15, 9, L], cloth(skip=("north", "east", "west", "down")))
    # the top edge turned down, sheepskin side up, flat and rounded like the rest
    box(g, "fold_top", [1, 9, FOLD_Z], [15, 10, z], cloth(skip=("south", "down")))
    box(g, "fold_w", [0, 6, FOLD_Z], [1, 9, z], cloth(skip=("south", "east", "down")))
    box(g, "fold_e", [15, 6, FOLD_Z], [16, 9, z], cloth(skip=("south", "west", "down")))
    # the pixel of air inside the rounded edges, between cloth and straw, filled in the fold's sheepskin
    box(g, "fill_w", [1, 8, FOLD_Z], [2, 9, L - 1], cloth(spot=fur_spot))
    box(g, "fill_e", [14, 8, FOLD_Z], [15, 9, L - 1], cloth(spot=fur_spot))
    box(g, "fill_foot", [2, 8, L - 2], [14, 9, L - 1], cloth(spot=fur_spot))


def pillow():
    g = "pillow"
    box(g, "pillow", [3, 9, 2.5], [13, 10, 6], plain("sack"))


def headboard():
    g = "headboard"
    box(g, "crossbar", [2, 8, 0.5], [14, 9, 1.5], wood(skip=("east", "west")))
    box(g, "plank", [2, 10, 0.5], [14, 15, 1.5], patch("plank", {"south": [2, 1], "north": [2, 9],
                                                                 "up": [2, 0], "down": [2, 6]}))


# ---------------------------------------------------------------- output

def main():
    os.makedirs(MODELS, exist_ok=True)
    os.makedirs(TEXTURES, exist_ok=True)
    for old in os.listdir(TEXTURES):
        if old.startswith("goblin_bed_") and old[:-4] not in TEXTURE_PAINTERS:
            os.remove(os.path.join(TEXTURES, old))
    parts = (frame, straw, blanket, pillow, headboard)
    for part in parts:
        part()
    ATLAS.layout()
    ELEMENTS.clear()
    for part in parts:
        part()
    for name, painter in TEXTURE_PAINTERS.items():
        painter().save(os.path.join(TEXTURES, name + ".png"))
    for _, e in ELEMENTS:
        for c in e["from"] + e["to"]:
            assert -16 <= c <= 32, e["name"]

    used = sorted({f["texture"][1:] for _, e in ELEMENTS for f in e["faces"].values()})
    textures = {"particle": NS + "goblin_bed_straw"}
    textures.update({k: NS + TEXTURE_KEYS[k] for k in used})
    model = {
        "parent": "minecraft:block/block",
        "textures": textures,
        "elements": [e for _, e in ELEMENTS],
        "display": {
            "gui": {"rotation": [30, 45, 0], "translation": [0, 0, -2], "scale": [0.5, 0.5, 0.5]},
        },
    }
    with open(os.path.join(MODELS, "goblin_straw_bed.json"), "w", encoding="utf-8", newline="\n") as f:
        json.dump(model, f, indent=2)
        f.write("\n")
    print("goblin_straw_bed: %d elements, textures %s" % (len(ELEMENTS), ", ".join(used)))

    groups = {}
    for group, e in ELEMENTS:
        groups.setdefault(group, []).append(e)
    # embedded as data URLs: Blockbench caches images by path and would keep showing an older paint; URL-safe
    # base64, because risky_eval refuses code containing two slashes in a row
    tex_data = {}
    for k in used:
        with open(os.path.join(TEXTURES, TEXTURE_KEYS[k] + ".png"), "rb") as png:
            tex_data[k] = [TEXTURE_KEYS[k] + ".png", base64.urlsafe_b64encode(png.read()).decode()]
    with open(os.path.join(HERE, "blockbench.js"), "w", encoding="utf-8", newline="\n") as f:
        f.write(BLOCKBENCH_JS.replace("__GROUPS__", json.dumps(groups)).replace("__TEXTURES__", json.dumps(tex_data))
                .replace("__PROJECT__", PROJECT))


BLOCKBENCH_JS = r"""
const GROUPS = __GROUPS__;
const TEXTURES = __TEXTURES__;
for (const p of ModelProject.all.filter(p => p.name === '__PROJECT__')) p.close(true);
newProject(Formats.java_block);
Project.name = '__PROJECT__';
const tex = {};
for (const [key, [name, data]] of Object.entries(TEXTURES)) {
  const url = 'data:image/png;base64,' + data.replace(/-/g, '+').replace(/_/g, '/');
  tex[key] = new Texture({name}).fromDataURL(url).add(false);
}
for (const [name, elements] of Object.entries(GROUPS)) {
  const group = new Group({name, origin: [8, 8, 8]}).init();
  for (const e of elements) {
    const faces = {};
    for (const [dir, f] of Object.entries(e.faces)) {
      faces[dir] = {uv: f.uv, texture: tex[f.texture.slice(1)].uuid};
    }
    for (const dir of ['north', 'south', 'east', 'west', 'up', 'down']) {
      if (!faces[dir]) faces[dir] = {texture: null};
    }
    const opts = {name: e.name, from: e.from, to: e.to, autouv: 0, faces};
    if (e.rotation) {
      opts.origin = e.rotation.origin;
      opts.rotation = [0, 0, 0];
      opts.rotation[{x: 0, y: 1, z: 2}[e.rotation.axis]] = e.rotation.angle;
    }
    const cube = new Cube(opts).init();
    cube.addTo(group);
  }
}
Canvas.updateAll();
({cubes: Cube.all.length, groups: Group.all.map(g => g.name + ':' + g.children.length), textures: Texture.all.map(t => t.name)});
"""

if __name__ == "__main__":
    main()
