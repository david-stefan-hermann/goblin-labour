#!/usr/bin/env python3
"""Turns the Blockbench Goblin Chest models into the Java model layers and the chest atlas textures.

Run from the project root:  python tools/make_chest_model.py

Reads   art/goblin_chest_single.bbmodel   (single chest, 128x128 texture)
        art/goblin_chest_double.bbmodel   (whole double chest, 128x256 texture)
Writes  src/main/java/goblinlabour/client/GoblinChestLayers.java
        src/main/resources/assets/goblinlabour/textures/entity/chest/goblin.png
        src/main/resources/assets/goblinlabour/textures/entity/chest/goblin_double.png

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
import json
import math
import os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
TEXTURES = os.path.join(ROOT, "src/main/resources/assets/goblinlabour/textures/entity/chest")
JAVA = os.path.join(ROOT, "src/main/java/goblinlabour/client/GoblinChestLayers.java")

# model name -> (bbmodel, texture png, java method, layer field, offset of Blockbench (0,0,0) in model space)
MODELS = [
    ("single", "goblin_chest_single.bbmodel", "goblin.png", "single", "SINGLE", (8.0, 0.0, 8.0)),
    ("double", "goblin_chest_double.bbmodel", "goblin_double.png", "doubleChest", "DOUBLE", (0.0, 0.0, 8.0)),
]
GROUPS = ["bottom", "lid", "lock"]  # the part names vanilla's ChestModel drives


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
        with open(os.path.join(TEXTURES, texture), "wb") as f:
            f.write(base64.b64decode(used["source"].split(",", 1)[1]))
        print("wrote %s (%s, %dx%d)" % (texture, used["name"],
                                        data["resolution"]["width"], data["resolution"]["height"]))
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
