#!/usr/bin/env python3
"""Turns the Blockbench Goblin Chest models into the Java model layers and the chest atlas textures.

Run from the project root:  python tools/make_chest_model.py

Reads   art/goblin_chest_single.bbmodel   (single chest, 128x128 texture)
        art/goblin_chest_double.bbmodel   (whole double chest, 128x256 texture)
        art/anim/goblin_chest_*.png(.mcmeta)  the sparkling iris, one frame per strip
Writes  src/main/java/goblinlabour/client/GoblinChestLayers.java
        src/main/resources/assets/goblinlabour/textures/entity/chest/goblin.png(.mcmeta)
        src/main/resources/assets/goblinlabour/textures/entity/chest/goblin_double.png(.mcmeta)
        src/main/resources/assets/goblinlabour/textures/entity/chest/goblin_glow.png(.mcmeta)
        src/main/resources/assets/goblinlabour/textures/entity/chest/goblin_double_glow.png(.mcmeta)

The chest texture in the atlas is animated: the eye sparkles. The frames are not copied from
art/anim straight across - the script stacks the chest texture from the bbmodel once per frame and
paints only those pixels from the template into each one that the template itself moves from frame
to frame (the iris). That way repainting the chest in Blockbench keeps the sparkle, and only a new
iris needs a new template. Needs Pillow (pip install pillow).

The *_glow texture is that same strip with everything cut away but the lit pixels of the eye cubes,
so the renderer can draw the chest a second time at full brightness and only the iris survives the
cutout - the eye then shines in the dark while the rest of the chest takes the room's light
(GoblinChestRenderer, GoblinChestSpecialRenderer). The pupil is dark and drops out with the rest.

Coordinates: Blockbench's "Modded Entity" space is the Java model space turned 180 degrees around z
(x and y are negated, z is kept), and its box UV follows that turn - Blockbench's east face carries the
UV strip that Minecraft puts on the west face, its up face the one Minecraft puts on the down face.
Chests are block entities and are rendered without the entity flip, so the turn has to happen in the
model itself: every cube goes in as (-x, -y, z) and the part that holds it carries a 180 degree z
rotation, which stands the model upright again while the UV strips stay where Blockbench shows them.
Cube rotations are conjugated by that turn, so x and y angles flip sign and z angles do not.

Blockbench origin (0, 0, 0) is the middle of the floor of the chest, so the offsets below put the
single chest in the middle of its block and the double chest on the seam of its two blocks: the left
half draws the whole double chest, the right half draws nothing (see GoblinChestRenderer).
"""

import base64
import io
import json
import math
import os

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ANIM = os.path.join(ROOT, "art", "anim")
TEXTURES = os.path.join(ROOT, "src/main/resources/assets/goblinlabour/textures/entity/chest")
JAVA = os.path.join(ROOT, "src/main/java/goblinlabour/client/GoblinChestLayers.java")

# model name -> (bbmodel, texture png, java method, layer field, offset of Blockbench (0,0,0) in model space)
MODELS = [
    ("single", "goblin_chest_single.bbmodel", "goblin.png", "single", "SINGLE", (8.0, 0.0, 8.0)),
    ("double", "goblin_chest_double.bbmodel", "goblin_double.png", "doubleChest", "DOUBLE", (0.0, 0.0, 8.0)),
]
GROUPS = ["bottom", "lid", "lock"]  # the part names vanilla's ChestModel drives
EYE = "eye_row"  # the cubes of the iris; their lit pixels are what glows in the dark
DARK = 60  # anything below this brightness is the pupil (or a shadowed edge) and does not glow


def fmt(value):
    """A Java float literal."""
    rounded = round(value + 0.0, 4)
    text = ("%.4f" % rounded).rstrip("0")
    return (text + "0" if text.endswith(".") else text) + "F"


def angle(degrees):
    return "0.0F" if degrees == 0 else "deg(%s)" % ("%.4f" % round(degrees, 4)).rstrip("0").rstrip(".")


def read(name):
    with open(os.path.join(ROOT, "art", name), encoding="utf-8") as f:
        return json.load(f)


def cubes(name, pivot, elements):
    """A CubeListBuilder holding the given cubes, turned into the pivot's (turned) frame."""
    lines = ["        CubeListBuilder %s = CubeListBuilder.create()" % name]
    for e in elements:
        frm, to = e["from"], e["to"]
        u, v = e.get("uv_offset", [0, 0])
        lines.append("                .texOffs(%d, %d).addBox(%s, %s, %s, %s, %s, %s) // %s" % (
            u, v,
            fmt(pivot[0] - to[0]), fmt(pivot[1] - to[1]), fmt(frm[2] - pivot[2]),
            fmt(to[0] - frm[0]), fmt(to[1] - frm[1]), fmt(to[2] - frm[2]),
            e["name"]))
    lines[-1] = lines[-1].replace(") //", "); //", 1) if len(lines) > 1 else lines[-1]
    if len(lines) == 1:
        lines[0] += ";"
    return lines


def sparkle(model, base_png, template_path):
    """The chest texture stacked once per animation frame, with the template's moving pixels painted in.

    Returns the strip and how many frames and moving pixels went into it. Everything outside those pixels
    comes from the Blockbench model, so the template only has to carry the iris.
    """
    base = Image.open(io.BytesIO(base_png)).convert("RGBA")
    assert os.path.exists(template_path), \
        "%s: no animation template at %s" % (model, os.path.relpath(template_path, ROOT).replace("\\", "/"))
    template = Image.open(template_path).convert("RGBA")
    assert template.width == base.width and template.height % base.height == 0, \
        "%s: template is %dx%d, not a stack of %dx%d frames - regenerate it for the new texture size" \
        % (model, template.width, template.height, base.width, base.height)
    count = template.height // base.height
    frames = [template.crop((0, i * base.height, base.width, (i + 1) * base.height)).load() for i in range(count)]
    moving = [(x, y) for y in range(base.height) for x in range(base.width)
              if any(frames[i][x, y] != frames[0][x, y] for i in range(1, count))]
    assert moving, "%s: the template's %d frames are all the same, nothing would move" % (model, count)

    strip = Image.new("RGBA", (base.width, base.height * count))
    for i in range(count):
        frame = base.copy()
        pixels = frame.load()
        for x, y in moving:
            pixels[x, y] = frames[i][x, y]
        strip.paste(frame, (0, i * base.height))
    return strip, count, len(moving)


def eye_pixels(model, data):
    """Every texture pixel the eye cubes sit on, from all six of their faces.

    Taken from the model rather than from a hand-drawn mask, so moving or resizing the eye in
    Blockbench moves the glow with it.
    """
    pixels = set()
    for e in data["elements"]:
        if not e["name"].startswith(EYE):
            continue
        for face in e["faces"].values():
            u = face["uv"]
            x0, x1 = sorted((int(round(u[0])), int(round(u[2]))))
            y0, y1 = sorted((int(round(u[1])), int(round(u[3]))))
            pixels |= {(x, y) for y in range(y0, y1) for x in range(x0, x1)}
    assert pixels, "%s: no %s* cubes, so there is nothing to make glow" % (model, EYE)
    return pixels


def glow(strip, pixels, frame_height):
    """The strip with everything cut away but the lit pixels among the given ones.

    Drawn over the chest at full brightness on the same cutout layer: transparent pixels are
    discarded, so only the iris is lit and the chest keeps the light of the room it stands in.
    """
    out = Image.new("RGBA", strip.size)
    source, target = strip.load(), out.load()
    frames = strip.height // frame_height
    lit = 0
    for frame in range(frames):
        for x, y in pixels:
            r, g, b, a = source[x, y + frame * frame_height]
            if a == 255 and 0.3 * r + 0.59 * g + 0.11 * b >= DARK:
                target[x, y + frame * frame_height] = (r, g, b, 255)
                lit += 1
    assert lit, "the eye has no pixel brighter than %d, nothing would glow" % DARK
    return out, lit // frames


def write_mcmeta(template_path, out_path, size):
    """The .mcmeta next to the strip: the template's timing, but with the frame size spelled out.

    Without width and height Minecraft falls back to square frames (Math.min of the image), which cuts the
    double chest's 128x256 frames into 128x128 ones and smears the texture over the model.
    """
    source = template_path + ".mcmeta"
    assert os.path.exists(source), "%s is missing" % os.path.relpath(source, ROOT).replace("\\", "/")
    with open(source, encoding="utf-8") as f:
        meta = json.load(f)
    animation = meta.setdefault("animation", {})
    animation["width"], animation["height"] = size
    with open(out_path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(meta, f, indent=2)
        f.write("\n")


def used_texture(model, data):
    """The one texture every face is painted with; a Blockbench project may hold unused variants next to it."""
    ids = {f.get("texture") for e in data["elements"] for f in e["faces"].values()}
    assert len(ids) == 1 and None not in ids, \
        "%s: the cubes use %d different textures, the chest model needs exactly one" % (model, len(ids))
    wanted = str(ids.pop())
    for texture in data["textures"]:
        if texture["id"] == wanted:
            return texture
    raise AssertionError("%s: no texture with id %s" % (model, wanted))


def check(model, elements, texture_id):
    """The converter only handles what these two models actually use."""
    for e in elements:
        assert e.get("type", "cube") == "cube", "%s: %s is not a cube" % (model, e["name"])
        assert e.get("box_uv", True), "%s: %s does not use box UV" % (model, e["name"])
        assert not e.get("inflate"), "%s: %s is inflated" % (model, e["name"])
        assert not e.get("mirror_uv"), "%s: %s is UV mirrored" % (model, e["name"])
        assert e.get("export", True), "%s: %s is not exported" % (model, e["name"])
        assert len(e["faces"]) == 6 and all(str(f.get("texture")) == texture_id for f in e["faces"].values()), \
            "%s: %s does not paint all six faces with texture %s" % (model, e["name"], texture_id)
        assert all(e["to"][i] > e["from"][i] for i in range(3)), "%s: %s is empty" % (model, e["name"])
        assert len([a for a in e.get("rotation") or [0, 0, 0] if a]) < 2, \
            "%s: %s turns around more than one axis" % (model, e["name"])
        assert all(float(x) == int(x) for x in e.get("uv_offset", [0, 0])), \
            "%s: %s has a fractional UV offset" % (model, e["name"])


def part(model, group_name, group, elements, offset):
    """The Java for one root part: the group's own cubes plus one child part per rotation."""
    pivot = group["origin"]
    plain = [e for e in elements if not any(e.get("rotation") or [0, 0, 0])]
    turned = {}
    for e in elements:
        rotation = e.get("rotation") or [0, 0, 0]
        if any(rotation):
            turned.setdefault((tuple(rotation), tuple(e.get("origin", [0, 0, 0]))), []).append(e)

    lines = ["    private static void %s%s(PartDefinition root) {" % (model, group_name.capitalize())]
    lines += cubes("body", pivot, plain)
    lines.append('        PartDefinition part = root.addOrReplaceChild("%s", CubeListBuilder.create(),' % group_name)
    lines.append("                PartPose.offset(%s, %s, %s));"
                 % (fmt(offset[0] + pivot[0]), fmt(offset[1] + pivot[1]), fmt(offset[2] + pivot[2])))
    lines.append('        PartDefinition turned = part.addOrReplaceChild("body", body,')
    lines.append("                PartPose.rotation(0.0F, 0.0F, TURN));")
    for i, ((rotation, origin), group) in enumerate(turned.items()):
        lines += cubes("turn%d" % i, origin, group)
        lines.append('        turned.addOrReplaceChild("turn%d", turn%d, PartPose.offsetAndRotation(' % (i, i))
        lines.append("                %s, %s, %s, %s, %s, %s));" % (
            fmt(pivot[0] - origin[0]), fmt(pivot[1] - origin[1]), fmt(origin[2] - pivot[2]),
            angle(-rotation[0]), angle(-rotation[1]), angle(rotation[2])))
    lines.append("    }")
    return lines


def layer(model, data, method, offset, texture_id):
    elements = {e["uuid"]: e for e in data["elements"]}
    groups = {g["uuid"]: g for g in data["groups"]}
    check(model, data["elements"], texture_id)
    width, height = data["resolution"]["width"], data["resolution"]["height"]

    body = ["    public static LayerDefinition %s() {" % method,
            "        MeshDefinition mesh = new MeshDefinition();",
            "        PartDefinition root = mesh.getRoot();"]
    parts = []
    for node in data["outliner"]:
        group = groups[node["uuid"]]
        assert group["name"] in GROUPS, "%s: unexpected group %s" % (model, group["name"])
        assert not any(group.get("rotation") or [0, 0, 0]), "%s: group %s is rotated" % (model, group["name"])
        assert all(c in elements for c in node["children"]), "%s: group %s has a nested group" % (model, group["name"])
        body.append("        %s%s(root);" % (model, group["name"].capitalize()))
        parts += part(model, group["name"], group, [elements[c] for c in node["children"]], offset) + [""]
    body.append("        return LayerDefinition.create(mesh, %d, %d);" % (width, height))
    body += ["    }", ""]
    return body + parts


def main():
    layers = []
    fields = []
    for model, source, texture, method, field, offset in MODELS:
        data = read(source)
        used = used_texture(model, data)
        template = os.path.join(ANIM, os.path.splitext(source)[0] + ".png")
        strip, count, moving = sparkle(model, base64.b64decode(used["source"].split(",", 1)[1]), template)
        strip.save(os.path.join(TEXTURES, texture))
        size = (data["resolution"]["width"], data["resolution"]["height"])
        write_mcmeta(template, os.path.join(TEXTURES, texture + ".mcmeta"), size)
        print("wrote %s and its .mcmeta (%s, %dx%d frames, %d of them, %d moving pixels)"
              % (texture, used["name"], size[0], size[1], count, moving))

        lights, lit = glow(strip, eye_pixels(model, data), size[1])
        glow_texture = texture.replace(".png", "_glow.png")
        lights.save(os.path.join(TEXTURES, glow_texture))
        write_mcmeta(template, os.path.join(TEXTURES, glow_texture + ".mcmeta"), size)
        print("wrote %s and its .mcmeta (%d glowing pixels per frame)" % (glow_texture, lit))
        fields.append('    public static final ModelLayerLocation %s = '
                      'new ModelLayerLocation(GoblinLabour.id("goblin_chest"), "%s");' % (field, model))
        layers += layer(model, data, method, offset, used["id"])

    head = '''package goblinlabour.client;

import goblinlabour.GoblinLabour;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

/**
 * Model layers of the Goblin Chest, generated from {@code art/goblin_chest_single.bbmodel} and
 * {@code art/goblin_chest_double.bbmodel} by {@code tools/make_chest_model.py} - do not edit by hand, edit the
 * Blockbench models and run the script again.
 *
 * <p>The parts are named {@code bottom}, {@code lid} and {@code lock} like vanilla's, so vanilla's
 * {@code ChestModel} opens the lid. Each of them holds a single {@code body} child that carries a half turn around
 * z: Blockbench's Modded Entity space is the model space turned that way, and its box UV follows the turn, so the
 * cubes go in turned (x and y negated) and the {@code body} stands them upright again while the UV strips stay where
 * Blockbench shows them. Cube rotations are conjugated by the same turn, hence the flipped x and y angles.
 *
 * <p>{@link #DOUBLE} is the whole double chest, drawn by the left half alone; see {@link GoblinChestRenderer}.
 */
public final class GoblinChestLayers {
%s

    /** Half turn around z, see the class comment. */
    private static final float TURN = (float) Math.PI;

    private GoblinChestLayers() {
    }

    private static float deg(double degrees) {
        return (float) Math.toRadians(degrees);
    }

''' % "\n".join(fields)

    with open(JAVA, "w", encoding="utf-8", newline="\n") as f:
        f.write(head)
        f.write("\n".join(layers).rstrip() + "\n}\n")
    print("wrote " + os.path.relpath(JAVA, ROOT).replace("\\", "/"))


if __name__ == "__main__":
    main()
