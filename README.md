# Goblin Labour

<img src="src/main/resources/assets/goblinlabour/icon.png" alt="logo" width="96" align="right">

Fabric mod for Minecraft 26.2. Craft goblins out of rotten flesh, give each one a straw bed and some tools, and let them mine, dig and chop for you while you do something more interesting.

Inspired by [Minions Remastered](https://github.com/BrassAmber-Mods/MinecarftMinionsRemastered) (LGPL-2.1): the job loop, the spiral stair and the "blink" fallback follow its design. Code, goblin model and textures are this mod's own.

**Status: early development, playable.** Items, beds, goblins, inventory, jobs (mine down with stairs, mine ahead, chop, farm, collect), goblin chests and the home zone are in. Furniture bonuses and polish are next.

![Three goblins, two of them holding tools](docs/goblins.png)

## How it works

1. **Goblin Meat Pack**: nine rotten flesh, shapeless.
2. **Goblin Blank**: smelt (or smoke) the meat pack.
3. **Goblin Straw Bed**: six hay bales, the lower two rows of the crafting grid.
4. Right-click the bed with a blank: a goblin with a random name appears and is bound to that bed.
5. **Goblin Chest**: a chest and a meat pack, shapeless. Mossy green, and when it opens the lid turns out to be a jaw full of teeth with a tongue inside. Two of them next to each other make a double chest.

Every bed has a **home**: 5 blocks in every horizontal direction and 2 up and down. Goblins never mine inside a home, and goblin chests placed in a home are where they unload. Homes of several beds that touch each other merge into one flat. Hold a **Goblin Head** (blank + torch) to see the home borders as floating emeralds.

Breaking a bed sends its goblin back into a blank that keeps the goblin's name and tools. Goblins have 20 health, cannot be targeted by monsters and do not fight. Lava, drowning and falls still kill them; a dead goblin respawns on its bed after five seconds with its tools, the rest of its inventory is dropped where it died.

A goblin's skin shows its trade: lumberjacks (Chop) are green, farmers yellowish, miners (staff orders) greyish, collectors (Collect) bluish and carry a small leather backpack. Resting keeps the last colour. On top of that every goblin has one of four looks, picked by its name so it survives death and blanks: plain leather vest and tusks; patched vest, gold earrings, a scar and a notched ear; sackcloth tunic, bandage, nose ring and a taller skull; fur collar, bone necklace, a single fang and a topknot with a braid. Textures come from `tools/MakeGoblinTexture.java`.

The bed shows the trade too: while its goblin works, something lies under the frame (an axe, logs or sticks for lumberjacks; wheat, vegetables, a hay bale or a hoe for farmers; diamonds, emeralds, redstone or a chunk of ore for miners; bundles or a small barrel for collectors). The items are half a pixel thick, extruded like held items, and peek out at the foot end so you can see them while standing. Each bed picks one of four arrangements; a resting goblin's bed is empty underneath. Bed models come from `tools/MakeBedModels.java`.

Goblins talk in chat, in English, server-wide: when they start, get picked or ordered with the staff ("Yes, master!"), finish a job, come back from the dead, run out of storage, or cannot find a way out of their home. While working they chatter now and then ("Working, working, working!", "I love my job!"). They also have voices: pitched-up villager and witch sounds defined in `sounds.json`, so a resource pack can replace them.

The **Goblin Handbook** (book + rotten flesh) explains all of this in game, in English and German.

## Working

- **Right-click a goblin**: a player-style inventory titled with its name and trade ("Grubnak the Lumberjack"). Tools go into the bottom row; goblins only use tools they carry. Without a pickaxe they cannot break stone, without diamond no obsidian (vanilla rules). Tools do not wear out.
- **Right-click the bed**: Rest, Chop, Farm or Collect and the radius.
  - *Rest*: potter about the home (about nine tenths of the time) and take a short nap in bed now and then (heals fast in bed, slowly while awake). Pottering works like a vanilla mob's stroll: a short walk, then a pause of a few seconds looking around or at a player or goblin nearby. A goblin outside its home walks straight back.
  - *Chop*: fell the trees within the radius one at a time. A goblin claims the tree nearest to it (every log connected to it, branches included) and takes it down from the lowest log up before it moves on; other lumberjacks leave a claimed tree alone. Once a tree is gone its stumps are replanted with saplings from the inventory. High logs are reached by climbing: the goblin puts a **Goblin Scaffold** block into the block it stands in and climbs it like a player holding jump on scaffolding, one block at a time, breaking leaves in the way, and sneaks back down through the column.
  - *Farm*: harvest ripe crops, nether wart, pumpkins and melons within the radius and replant from the inventory.
  - *Collect*: pick up loose items within the radius (the home included) and bring them to the goblin chests. Collectors carry a backpack: a second storage row, twice as much loot per trip.
- **Goblin Scaffold** is vanilla scaffolding with its own block (stand on it, climb inside it, sneak to drop through; every block needs support within seven blocks, so breaking the foot of a column brings the whole column down), has a brown texture, is not craftable and drops nothing. Goblins never break it; a column vanishes two minutes after a goblin last touched it, so any goblin can use a column another one built.
- A goblin that wants to go home (resting or unloading) but is stuck in a hole it cannot walk out of, like a shaft dug without stairs, climbs out on scaffold. It picks the cheapest column at the side of the hole; an existing column costs nothing, so the next goblin in the same hole uses the first one's scaffold. With no way up at all it blinks home.
- **Goblin Staff** (blank + two sticks): left-click goblins to pick them, they glow and follow you. Right-click a block to send all picked goblins to work at once: the floor starts a 3x3 shaft with stairs down to bedrock, a ceiling a 3x3 shaft up to the surface, a wall a 3x3 tunnel two chunks deep. Sneak + right-click opens the settings first: 1x1, 3x3 or 5x5, stairs on or off, target height, tunnel width, height and length. Several goblins share one shaft or tunnel; a shaft that overlaps another goblin's is refused. Stair steps are placed one by one after each layer is dug.
- Goblins place free torches where it is dark and seal water and lava with free cobblestone.
- The inventory is small on purpose (nine stacks, eighteen for collectors): when it is full (or the job is done) the goblin unloads into goblin chests in its home. Copper and other chests are ignored. Homes of beds that touch each other count as one flat. If nothing fits it lies down and complains in chat, then checks again every minute.
- A goblin that cannot get out of its home says so.
- A goblin that died walks back after respawning and picks up whatever it dropped, if it is still there.
- Goblins never break cobblestone stairs, so one goblin cannot wreck another one's staircase.
- All screens use a goblin-green look; every setting (radius, shaft size, stairs, target height, tunnel width, height, length) has a tooltip.

## Planned

- Furniture in the home that makes goblins better at their job.

## Building

```powershell
./gradlew build
```

Java 25, Fabric Loader 0.19.3+, Fabric API. The jar lands in `build/libs/`. `./gradlew runServer` starts a dev server; the scripts in `tools/` drive it over RCON for tests and screenshots.

## License

LGPL-2.1. Parts of the job logic are modelled on Minions Remastered by jodlodi and BrassAmber (LGPL-2.1). The goblin model (`GoblinModel`) and its texture (generated by `tools/MakeGoblinTexture.java`) are original.
