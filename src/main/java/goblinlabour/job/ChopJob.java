package goblinlabour.job;

import goblinlabour.entity.GoblinEntity;
import goblinlabour.home.HomeRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fells the trees within {@code length} blocks of the bed (outside homes), one tree at a time: the goblin claims the
 * tree nearest to it (all logs connected to each other, diagonals included) and takes that tree down from the lowest
 * log up before it moves on. A tree claimed by one goblin is left alone by the others. The goblin picks up the logs
 * and whatever the crowns drop (saplings, sticks, apples). With {@code replant} on (the default) the stumps of a
 * felled tree are remembered and get a sapling as soon as the goblin carries one, the tree's own kind first. Endless:
 * the goblin stays on duty and checks for new trees.
 */
public final class ChopJob implements JobTask {
    public static final ChopJob INSTANCE = new ChopJob();
    private static final int BELOW = 3;
    private static final int ABOVE = 24;
    /** Largest tree the flood fill follows (a big jungle tree has a few hundred logs). */
    private static final int MAX_TREE_LOGS = 512;
    /** A claim nobody worked on for this long (goblin gone, job changed) is dropped. */
    private static final int CLAIM_TIMEOUT = 2400;
    /** A stump nobody found a sapling for within ten minutes is forgotten. */
    private static final int STUMP_TIMEOUT = 12000;
    /** Saplings of each kind a lumberjack keeps when it unloads, for the next stumps. */
    private static final int KEEP_SAPLINGS = 16;

    /** The tree a goblin is working on: its logs, the stumps (logs standing on something else) and its wood. */
    private static final class Tree {
        final Set<BlockPos> logs;
        final List<BlockPos> stumps;
        final Block wood;
        long lastUsed;

        Tree(Set<BlockPos> logs, List<BlockPos> stumps, Block wood, long now) {
            this.logs = logs;
            this.stumps = stumps;
            this.wood = wood;
            this.lastUsed = now;
        }
    }

    /** Where a felled tree stood and waits for a sapling. */
    private record Stump(BlockPos pos, Block wood, long since) {
    }

    /** Claimed trees per goblin, per dimension. */
    private static final Map<ResourceKey<Level>, Map<UUID, Tree>> CLAIMS = new ConcurrentHashMap<>();
    /** Stumps to replant per goblin, per dimension. */
    private static final Map<ResourceKey<Level>, Map<UUID, List<Stump>>> STUMPS = new ConcurrentHashMap<>();

    private ChopJob() {
    }

    @Override
    public boolean endless() {
        return true;
    }

    @Override
    public BlockPos entryPoint(JobHost bedEntity, JobConfig config) {
        return bedEntity.getBlockPos().relative(config.direction(), 6);
    }

    /** Frees the goblin's tree and forgets its stumps, e.g. when its job changes. */
    public static void release(ServerLevel level, GoblinEntity goblin) {
        claims(level).remove(goblin.getUUID());
        stumps(level).remove(goblin.getUUID());
    }

    /** Debug summary for the status command. */
    public static String debug(ServerLevel level, GoblinEntity goblin) {
        List<Stump> stumps = stumps(level).get(goblin.getUUID());
        String replant = " stumps=" + (stumps == null ? 0 : stumps.size());
        Tree tree = claims(level).get(goblin.getUUID());
        if (tree == null) return "tree=-" + replant;
        long left = tree.logs.stream().filter(p -> level.getBlockState(p).is(BlockTags.LOGS)).count();
        return "tree=" + tree.stumps.getFirst().toShortString() + "(" + left + "/" + tree.logs.size() + " logs)" + replant;
    }

    @Override
    public Pick pick(ServerLevel level, GoblinEntity goblin, JobHost bedEntity, JobConfig config, Set<BlockPos> skipped) {
        Map<UUID, Tree> claims = claims(level);
        long now = level.getGameTime();
        claims.values().removeIf(tree -> tree.lastUsed + CLAIM_TIMEOUT < now);

        Tree mine = claims.get(goblin.getUUID());
        if (mine != null) {
            Pick pick = lowestLog(level, goblin, mine, skipped);
            if (pick.target() != null || pick.verdict() == Mining.Verdict.NEEDS_TOOL) {
                mine.lastUsed = now;
                return pick;
            }
            claims.remove(goblin.getUUID()); // felled (afterBreak already took it), or only unreachable logs left: next tree
        }

        if (config.replant()) {
            Pick plant = plant(level, goblin, skipped, now);
            if (plant != null) return plant;
        }

        Set<BlockPos> taken = new HashSet<>();
        for (Map.Entry<UUID, Tree> entry : claims.entrySet()) {
            if (!entry.getKey().equals(goblin.getUUID())) taken.addAll(entry.getValue().logs);
        }
        boolean sawToolProblem = false;
        BlockPos bed = bedEntity.getBlockPos();
        int r = config.length();
        BlockPos nearest = null;
        long bestKey = Long.MAX_VALUE;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int x = bed.getX() - r; x <= bed.getX() + r; x++) {
            for (int z = bed.getZ() - r; z <= bed.getZ() + r; z++) {
                if (!level.isLoaded(cursor.set(x, bed.getY(), z))) continue;
                for (int y = bed.getY() - BELOW; y <= bed.getY() + ABOVE; y++) {
                    cursor.set(x, y, z);
                    BlockState state = level.getBlockState(cursor);
                    if (!state.is(BlockTags.LOGS) || taken.contains(cursor)) continue;
                    Mining.Verdict verdict = Mining.verdict(level, cursor, state, goblin);
                    if (verdict == Mining.Verdict.NEEDS_TOOL) {
                        sawToolProblem = true;
                        continue;
                    }
                    if (verdict != Mining.Verdict.OK || skipped.contains(cursor)) continue;
                    // the nearest tree to the goblin; within a column the lowest log
                    long dx = x - goblin.getBlockX(), dz = z - goblin.getBlockZ();
                    long key = (dx * dx + dz * dz) * 1000L + (y - (bed.getY() - BELOW));
                    if (key < bestKey) {
                        bestKey = key;
                        nearest = cursor.immutable();
                    }
                    break; // higher logs of this column belong to the same trunk
                }
            }
        }
        if (nearest == null) return sawToolProblem ? Pick.NEEDS_TOOL : Pick.DONE;

        Tree tree = floodTree(level, nearest, bed, r, taken, now);
        claims.put(goblin.getUUID(), tree);
        Pick pick = lowestLog(level, goblin, tree, skipped);
        return pick.target() != null ? pick : Pick.of(nearest);
    }

    /** The lowest log of the tree that can be broken now; NEEDS_TOOL if only tool-locked logs are left, else DONE. */
    private static Pick lowestLog(ServerLevel level, GoblinEntity goblin, Tree tree, Set<BlockPos> skipped) {
        BlockPos best = null;
        double bestKey = Double.MAX_VALUE;
        boolean sawToolProblem = false;
        for (BlockPos pos : tree.logs) {
            BlockState state = level.getBlockState(pos);
            if (!state.is(BlockTags.LOGS)) continue;
            Mining.Verdict verdict = Mining.verdict(level, pos, state, goblin);
            if (verdict == Mining.Verdict.NEEDS_TOOL) {
                sawToolProblem = true;
                continue;
            }
            if (verdict != Mining.Verdict.OK || skipped.contains(pos)) continue;
            double key = pos.getY() * 10_000.0 + goblin.position().distanceToSqr(pos.getX() + 0.5, goblin.getY(), pos.getZ() + 0.5);
            if (key < bestKey) {
                bestKey = key;
                best = pos;
            }
        }
        if (best != null) return Pick.of(best);
        return sawToolProblem ? Pick.NEEDS_TOOL : Pick.DONE;
    }

    /** All logs connected to {@code start} (26 neighbours) inside the work area, minus logs of other goblins' trees. */
    private static Tree floodTree(ServerLevel level, BlockPos start, BlockPos bed, int r, Set<BlockPos> taken, long now) {
        Set<BlockPos> logs = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        logs.add(start);
        queue.add(start);
        while (!queue.isEmpty() && logs.size() < MAX_TREE_LOGS) {
            BlockPos cur = queue.poll();
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) continue;
                        BlockPos n = cur.offset(dx, dy, dz);
                        if (Math.abs(n.getX() - bed.getX()) > r + 4 || Math.abs(n.getZ() - bed.getZ()) > r + 4) continue;
                        if (n.getY() < bed.getY() - BELOW || n.getY() > bed.getY() + ABOVE + 8) continue;
                        if (taken.contains(n) || logs.contains(n) || !level.getBlockState(n).is(BlockTags.LOGS)) continue;
                        logs.add(n);
                        queue.add(n);
                    }
                }
            }
        }
        List<BlockPos> stumps = logs.stream().filter(p -> !logs.contains(p.below()) && !level.getBlockState(p.below()).isAir())
                .sorted((a, b) -> Integer.compare(a.getY(), b.getY())).toList();
        if (stumps.isEmpty()) stumps = List.of(start);
        return new Tree(logs, stumps, level.getBlockState(start).getBlock(), now);
    }

    /** When the last log of the goblin's tree is gone: free the claim and remember the lowest stumps for replanting. */
    @Override
    public void afterBreak(ServerLevel level, GoblinEntity goblin, JobHost bedEntity, JobConfig config, BlockPos broken) {
        Tree tree = claims(level).get(goblin.getUUID());
        if (tree == null || !tree.logs.contains(broken)) return;
        for (BlockPos log : tree.logs) {
            if (level.getBlockState(log).is(BlockTags.LOGS)) return;
        }
        claims(level).remove(goblin.getUUID());
        if (!config.replant()) return;
        List<Stump> list = stumps(level).computeIfAbsent(goblin.getUUID(), k -> new ArrayList<>());
        int lowest = tree.stumps.getFirst().getY();
        long now = level.getGameTime();
        for (BlockPos stump : tree.stumps) {
            if (stump.getY() == lowest) list.add(new Stump(stump, tree.wood, now));
        }
    }

    /** A remembered stump the goblin has a fitting sapling for, as a placement; null when there is none. */
    @Nullable
    private static Pick plant(ServerLevel level, GoblinEntity goblin, Set<BlockPos> skipped, long now) {
        List<Stump> list = stumps(level).get(goblin.getUUID());
        if (list == null) return null;
        list.removeIf(s -> now - s.since() > STUMP_TIMEOUT || !level.isLoaded(s.pos()) || !level.getBlockState(s.pos()).isAir()
                || HomeRegistry.isProtected(level, s.pos()));
        Stump best = null;
        double bestDist = Double.MAX_VALUE;
        for (Stump stump : list) {
            if (skipped.contains(stump.pos())) continue;
            double d = goblin.position().distanceToSqr(stump.pos().getX() + 0.5, stump.pos().getY(), stump.pos().getZ() + 0.5);
            if (d < bestDist && sapling(level, goblin, stump) != null) {
                bestDist = d;
                best = stump;
            }
        }
        return best == null ? null : Pick.place(best.pos(), sapling(level, goblin, best));
    }

    /** The sapling from the storage that grows on the stump: the tree's own kind if there is one, else any. */
    @Nullable
    private static BlockState sapling(ServerLevel level, GoblinEntity goblin, Stump stump) {
        String kind = woodKind(stump.wood());
        BlockState any = null;
        SimpleContainer inv = goblin.getInventory();
        for (int i = GoblinEntity.HOTBAR_SIZE; i < GoblinEntity.INVENTORY_SIZE; i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.isEmpty() || !stack.is(ItemTags.SAPLINGS)) continue;
            BlockState state = Block.byItem(stack.getItem()).defaultBlockState();
            if (state.isAir() || !state.canSurvive(level, stump.pos())) continue;
            if (BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath().startsWith(kind + "_")) return state;
            if (any == null) any = state;
        }
        return any;
    }

    /** "oak" for oak logs, "dark_oak" for stripped dark oak wood, "crimson" for crimson stems. */
    private static String woodKind(Block wood) {
        String path = BuiltInRegistries.BLOCK.getKey(wood).getPath();
        if (path.startsWith("stripped_")) path = path.substring("stripped_".length());
        for (String suffix : new String[]{"_log", "_wood", "_stem", "_hyphae"}) {
            if (path.endsWith(suffix)) return path.substring(0, path.length() - suffix.length());
        }
        return path;
    }

    /** The planted sapling comes out of the storage; without one in there it is taken back (no free saplings). */
    @Override
    public void afterPlace(ServerLevel level, GoblinEntity goblin, JobHost bed, JobConfig config, BlockPos pos, BlockState placed) {
        SimpleContainer inv = goblin.getInventory();
        for (int i = GoblinEntity.HOTBAR_SIZE; i < GoblinEntity.INVENTORY_SIZE; i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.isEmpty() || Block.byItem(stack.getItem()) != placed.getBlock()) continue;
            stack.shrink(1);
            inv.setChanged();
            return;
        }
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
    }

    /** The work area: logs fall here, crowns drop their saplings, sticks and apples here. */
    @Override
    public AABB lootArea(JobHost bedEntity, JobConfig config) {
        BlockPos bed = bedEntity.getBlockPos();
        int r = config.length() + 4;
        return new AABB(bed.getX() - r, bed.getY() - BELOW, bed.getZ() - r, bed.getX() + r + 1, bed.getY() + ABOVE, bed.getZ() + r + 1);
    }

    @Override
    public boolean wantsLoot(ItemStack stack) {
        return stack.is(ItemTags.SAPLINGS) || stack.is(Items.STICK) || stack.is(Items.APPLE);
    }

    @Override
    public int keepOnUnload(JobConfig config, ItemStack stack) {
        return config.replant() && stack.is(ItemTags.SAPLINGS) ? KEEP_SAPLINGS : 0;
    }

    private static Map<UUID, Tree> claims(ServerLevel level) {
        return CLAIMS.computeIfAbsent(level.dimension(), k -> new ConcurrentHashMap<>());
    }

    private static Map<UUID, List<Stump>> stumps(ServerLevel level) {
        return STUMPS.computeIfAbsent(level.dimension(), k -> new ConcurrentHashMap<>());
    }
}
