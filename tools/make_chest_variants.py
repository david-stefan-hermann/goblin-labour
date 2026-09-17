#!/usr/bin/env python3
"""Writes the data and asset files of the Goblin Chest's sixteen dye colours.

Run from the project root:  python tools/make_chest_variants.py

Every colour is its own block and item (goblin_chest for green, <colour>_goblin_chest for the others, see
GoblinChestBlock.name). Per colour this writes
    assets/goblinlabour/blockstates/<name>.json         the block model, only there for the breaking particles
    assets/goblinlabour/models/block/<name>.json        particles: moss for green, the colour's concrete otherwise
    assets/goblinlabour/items/<name>.json               the chest renderer with the colour's texture
    data/goblinlabour/loot_table/blocks/<name>.json     drops itself, keeps a custom name
    data/goblinlabour/recipe/dye_<colour>_goblin_chest.json   any other goblin chest + the dye
and once data/goblinlabour/tags/block/goblin_chests.json (mined with an axe). The item model with the
display transforms (models/item/goblin_chest.json) and the textures (tools/make_chest_model.py) are shared or
made elsewhere; the recipe that makes the green chest (recipe/goblin_chest.json) is not touched.
"""

import json
import os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, "src/main/resources/assets/goblinlabour")
DATA = os.path.join(ROOT, "src/main/resources/data/goblinlabour")

# Minecraft's dye colours in DyeColor order
DYES = ["white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray", "light_gray", "cyan",
        "purple", "blue", "brown", "green", "red", "black"]


def name(dye):
    return "goblin_chest" if dye == "green" else dye + "_goblin_chest"


def texture(dye):
    return "goblin" if dye == "green" else "goblin_" + dye


def write(path, content):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(content, f, indent=2)
        f.write("\n")


def main():
    for dye in DYES:
        block = name(dye)
        write(os.path.join(ASSETS, "blockstates", block + ".json"),
              {"variants": {"": {"model": "goblinlabour:block/" + block}}})
        particle = "minecraft:block/moss_block" if dye == "green" else "minecraft:block/%s_concrete" % dye
        write(os.path.join(ASSETS, "models/block", block + ".json"), {"textures": {"particle": particle}})
        write(os.path.join(ASSETS, "items", block + ".json"), {"model": {
            "type": "minecraft:special",
            "base": "goblinlabour:item/goblin_chest",
            "model": {"type": "goblinlabour:goblin_chest", "texture": "goblinlabour:" + texture(dye)}}})
        write(os.path.join(DATA, "loot_table/blocks", block + ".json"), {
            "type": "minecraft:block",
            "pools": [{
                "rolls": 1,
                "entries": [{
                    "type": "minecraft:item",
                    "name": "goblinlabour:" + block,
                    "functions": [{"function": "minecraft:copy_components", "source": "block_entity",
                                   "include": ["minecraft:custom_name"]}]}],
                "conditions": [{"condition": "minecraft:survives_explosion"}]}]})
        write(os.path.join(DATA, "recipe", "dye_%s_goblin_chest.json" % dye), {
            "type": "minecraft:crafting_shapeless",
            "category": "misc",
            "group": "goblin_chest_dye",
            "ingredients": ["minecraft:%s_dye" % dye, ["goblinlabour:" + name(other) for other in DYES if other != dye]],
            "result": {"id": "goblinlabour:" + block}})
    write(os.path.join(DATA, "tags/block/goblin_chests.json"), {"values": ["goblinlabour:" + name(d) for d in DYES]})
    print("wrote blockstates, block models, items, loot tables and dye recipes for %d colours" % len(DYES))


if __name__ == "__main__":
    main()
