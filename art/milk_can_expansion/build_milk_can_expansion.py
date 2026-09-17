#!/usr/bin/env python3
"""Builds the Milk Can Expansion: a plain 1x1 tank block in the Milk Can's metal, with an opening drawn on top.

Run from the project root:  python art/milk_can_expansion/build_milk_can_expansion.py

Writes  art/milk_can_expansion/assets/goblinlabour/models/block/milk_can_expansion.json
        art/milk_can_expansion/assets/goblinlabour/textures/block/milk_can_expansion_{side,top,bottom}.png
        art/milk_can_expansion/blockbench.js   (risky_eval script that shows the block in a java_block project)

A full cube (minecraft:block/cube_bottom_top): the opening is only painted, the block has no hole. The metal
comes from the Milk Can's v2 palette (art/milk_can/build_milk_can.py), so both read as the same material:
the side is the can body's rows - foot band, two pressed ribs - with a darker frame at the block edges instead
of the can's round shading; the top is a riveted plate with a round opening and a rolled rim around it; the
bottom is the can's bottom. Needs Pillow.
"""

import importlib.util
import json
import math
import os

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))
MODELS = os.path.join(HERE, "assets", "goblinlabour", "models", "block")
TEXTURES = os.path.join(HERE, "assets", "goblinlabour", "textures", "block")
NAME = "milk_can_expansion"


def milk_can():
    """The Milk Can's builder, set up for v2, for its palette and painting helpers."""
    spec = importlib.util.spec_from_file_location("build_milk_can", os.path.join(ROOT, "art", "milk_can", "build_milk_can.py"))
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    module.configure("v2")
    return module


def paint_side(can):
    """The can body's rows (the v2 texture's lower 16), flat instead of round, framed at the block edges."""
    img = Image.new("RGBA", (16, 16))
    noise = can.noise(61)
    rows = dict(can.TALL_ROWS)
    # the can's top rows are shoulder, neck and rim; a tank side continues the body instead: a rib and a top band
    rows.update({11: "#82888f", 12: "#80868d", 13: "#6a7077", 14: "#a3a9b0", 15: "#5f656c"})
    for y, colour in rows.items():
        base = can.hexrgb(colour)
        for u in range(16):
            edge = 0.84 if u in (0, 15) else 0.94 if u in (1, 14) else 1.0
            img.putpixel((u, 15 - y), can.shade(base, edge + 0.015 * noise()))
    for (u, y, f) in [(4, 6, 0.88), (11, 9, 1.07), (9, 3, 0.9)]:
        img.putpixel((u, 15 - y), can.shade(img.getpixel((u, 15 - y))[:3], f))
    return img


def paint_top(can):
    """A metal plate with a round opening: dark inside, a light rolled rim, rivets in the corners."""
    img = Image.new("RGBA", (16, 16))
    noise = can.noise(71)
    for z in range(16):
        for x in range(16):
            d = math.hypot(x + 0.5 - 8, z + 0.5 - 8)
            if d < 4.2:
                depth = 0.55 + 0.1 * (d / 4.2)  # the far side of the opening catches a little light
                colour, f = "#4a4f55", depth - 0.08 * (z - 8) / 8
            elif d < 5.3:
                colour, f = "#a9afb6", 1.0 + 0.06 * ((8 - x) + (8 - z)) / 8
            elif d < 5.9:
                colour, f = "#6a7077", 1.0
            elif x in (0, 15) or z in (0, 15):
                colour, f = "#6a7077", 1.0
            else:
                colour, f = "#858b92", 1.0 + 0.05 * ((8 - x) + (8 - z)) / 8
            img.putpixel((x, z), can.shade(can.hexrgb(colour), f + 0.02 * noise()))
    for (x, z) in [(2, 2), (13, 2), (2, 13), (13, 13)]:
        img.putpixel((x, z), can.shade(can.hexrgb("#b3b9bf"), 1.0))
        img.putpixel((x + 1, z + 1), can.shade(can.hexrgb("#5f656c"), 1.0))
    return img


def main():
    can = milk_can()
    os.makedirs(MODELS, exist_ok=True)
    os.makedirs(TEXTURES, exist_ok=True)
    images = {NAME + "_side": paint_side(can), NAME + "_top": paint_top(can), NAME + "_bottom": can.paint_bottom()}
    for name, img in images.items():
        img.save(os.path.join(TEXTURES, name + ".png"))

    ns = "goblinlabour:block/"
    model = {
        "parent": "minecraft:block/cube_bottom_top",
        "textures": {"side": ns + NAME + "_side", "top": ns + NAME + "_top", "bottom": ns + NAME + "_bottom",
                     "particle": ns + NAME + "_side"},
    }
    with open(os.path.join(MODELS, NAME + ".json"), "w", encoding="utf-8", newline="\n") as f:
        json.dump(model, f, indent=2)
        f.write("\n")

    paths = {k: os.path.join(TEXTURES, k + ".png").replace("\\", "/") for k in images}
    with open(os.path.join(HERE, "blockbench.js"), "w", encoding="utf-8", newline="\n") as f:
        f.write(BLOCKBENCH_JS.replace("__TEXTURES__", json.dumps(paths)).replace("__NAME__", NAME))
    print("wrote %s.json and %d textures" % (NAME, len(images)))


BLOCKBENCH_JS = r"""
const TEXTURES = __TEXTURES__;
for (const p of ModelProject.all.filter(p => p.name === '__NAME__')) p.close(true);
newProject(Formats.java_block);
Project.name = '__NAME__';
const tex = {};
for (const [name, path] of Object.entries(TEXTURES)) {
  tex[name] = new Texture({name: name + '.png'}).fromPath(path).add(false);
}
const side = {uv: [0, 0, 16, 16], texture: tex['__NAME___side'].uuid};
const faces = {north: side, south: {...side}, east: {...side}, west: {...side},
  up: {uv: [0, 0, 16, 16], texture: tex['__NAME___top'].uuid},
  down: {uv: [0, 0, 16, 16], texture: tex['__NAME___bottom'].uuid}};
new Cube({name: '__NAME__', from: [0, 0, 0], to: [16, 16, 16], autouv: 0, faces}).init();
Canvas.updateAll();
({cubes: Cube.all.length, textures: Texture.all.map(t => t.name)});
"""

if __name__ == "__main__":
    main()
