# Goblin Labour

<img src="src/main/resources/assets/goblinlabour/icon.png" alt="logo" width="96" align="right">

Fabric mod for Minecraft 26.2. Craft goblins out of rotten flesh, give each one a straw bed and some tools, and let them mine, dig and chop for you while you do something more interesting.

Inspired by [Minions Remastered](https://github.com/BrassAmber-Mods/MinecarftMinionsRemastered) (LGPL-2.1): the job loop, the spiral stair and the "blink" fallback follow its design. Code, goblin model and textures are this mod's own.

**Status: early development, playable.** Items, beds, goblins, inventory, jobs (mine down with stairs, mine ahead, chop, farm), copper-chest unloading and the home zone are in. Furniture bonuses and polish are next.

![Three goblins, two of them holding tools](docs/goblins.png)

## How it works

1. **Goblin Meat Pack**: nine rotten flesh, shapeless.
2. **Goblin Blank**: smelt (or smoke) the meat pack.
3. **Goblin Straw Bed**: six hay bales, the lower two rows of the crafting grid.
4. Right-click the bed with a blank: a goblin with a random name appears and is bound to that bed.

Every bed has a **home**: 5 blocks in every horizontal direction and 2 up and down. Goblins never mine inside a home, and copper chests placed in a home are where they unload. Homes of several beds that touch each other merge into one flat. Hold a **Goblin Head** (blank + torch) to see the home borders as floating emeralds.

Breaking a bed sends its goblin back into a blank that keeps the goblin's name and tools. Goblins have 20 health, cannot be targeted by monsters and do not fight. Lava, drowning and falls still kill them; a dead goblin respawns on its bed after five seconds with its tools, the rest of its inventory is dropped where it died.

A goblin's skin shows its trade: lumberjacks (Chop) are green, farmers yellowish, miners (staff orders) greyish, collectors bluish (the collector job is still to come). Resting keeps the last colour. On top of that every goblin has one of four looks, picked by its name so it survives death and blanks: plain leather vest; patched vest, gold earrings and a scar; sackcloth tunic, bandage and nose ring; fur collar, bone necklace, war paint and a topknot. Textures come from `tools/MakeGoblinTexture.java`.

Goblins talk in chat, in English, server-wide: when they start, get picked or ordered with the staff ("Yes, master!"), finish a job, come back from the dead, run out of storage, or cannot find a way out of their home. While working they chatter now and then ("Working, working, working!", "I love my job!"). They also have voices: pitched-up villager and witch sounds defined in `sounds.json`, so a resource pack can replace them.

The **Goblin Handbook** (book + rotten flesh) explains all of this in game, in English and German.

## Working

- **Right-click a goblin**: a player-style inventory. Tools go into the bottom row; goblins only use tools they carry. Without a pickaxe they cannot break stone, without diamond no obsidian (vanilla rules). Tools do not wear out.
- **Right-click the bed**: Rest, Chop or Farm and the radius.
  - *Rest*: potter about the home (about nine tenths of the time) and take a short nap in bed now and then (heals fast in bed, slowly while awake).
  - *Chop*: fell trees within the radius, replant saplings from the inventory. High logs are reached by climbing: the goblin puts a **Goblin Scaffold** block into the block it stands in and climbs it like a player holding jump on scaffolding, one block at a time, breaking leaves in the way. The scaffold behaves like vanilla scaffolding (stand on it, climb inside it, sneak to drop through), has a brown texture, is not craftable, drops nothing, and every block removes itself one minute after it was placed unless a goblin is still in that column.
  - *Farm*: harvest ripe crops, nether wart, pumpkins and melons within the radius and replant from the inventory.
- **Goblin Staff** (blank + two sticks): left-click goblins to pick them, they glow and follow you. Right-click a block to send all picked goblins to work at once: the floor starts a 3x3 shaft with stairs down to bedrock, a ceiling a 3x3 shaft up to the surface, a wall a 3x3 tunnel two chunks deep. Sneak + right-click opens the settings first: 1x1, 3x3 or 5x5, stairs on or off, target height, tunnel width, height and length. Several goblins share one shaft or tunnel; a shaft that overlaps another goblin's is refused. Stair steps are placed one by one after each layer is dug.
- Goblins place free torches where it is dark and seal water and lava with free cobblestone.
- The inventory is small on purpose (nine stacks): when it is full (or the job is done) the goblin unloads into copper chests in its home. Homes of beds that touch each other count as one flat. If nothing fits it lies down and complains in chat, then checks again every minute.
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
