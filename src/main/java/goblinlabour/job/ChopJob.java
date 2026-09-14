package goblinlabour.job;

import goblinlabour.block.GoblinBedBlockEntity;
import goblinlabour.entity.GoblinEntity;
import goblinlabour.home.HomeRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fells the trees within {@code length} blocks of the bed (outside homes), one tree at a time: the goblin claims the
 * tree nearest to it (all logs connected to each other, diagonals included) and takes that tree down from the lowest
 * log up before it moves on. A tree claimed by one goblin is left alone by the others. When the last log of a tree is
 * gone the goblin replants its stumps with saplings it carries. Endless: the goblin stays on duty and checks for new
 * trees.
 */
public final class ChopJob implements JobTask {
    public static final ChopJob INSTANCE = new ChopJob();
    private static final int BELOW = 3;
    private static final int ABOVE = 24;
    /** Largest tree the flood fill follows (a big jungle tree has a few hundred logs). */
    private static final int MAX_TREE_LOGS = 512;
    /** A claim nobody worked on for this long (goblin gone, job changed) is dropped. */
    private static final int CLAIM_TIMEOUT = 2400;

    /** The tree a goblin is working on: its logs and the stumps (logs standing on something else) to replant. */
    private static final class Tree {
        final Set<BlockPos> logs;
        final List<BlockPos> stumps;
        long lastUsed;

        Tree(Set<BlockPos> logs, List<BlockPos> stumps, long now) {
            this.logs = logs;
            this.stumps = stumps;
            this.lastUsed = now;
        }
    }

    /** Claimed trees per goblin, per dimension. */
    private static final Map<ResourceKey<Level>, Map<UUID, Tree>> CLAIMS = new ConcurrentHashMap<>();

    private ChopJob() {
    }

    @Override
    public boolean endless() {
        return true;
    }

    @Override
    public BlockPos entryPoint(GoblinBedBlockEntity bedEntity, JobConfig config) {
        return bedEntity.getBlockPos().relative(config.direction(), 6);
    }

    /** Frees the goblin's tree, e.g. when its job changes. */
    public static void release(ServerLevel level, GoblinEntity goblin) {
        claims(level).remove(goblin.getUUID());
    }

    /** Debug summary for the status command. */
    public static String debug(ServerLevel level, GoblinEntity goblin) {
        Tree tree = claims(level).get(goblin.getUUID());
        if (tree == null) return "tree=-";
        long left = tree.logs.stream().filter(p -> level.getBlockState(p).is(BlockTags.LOGS)).count();
        return "tree=" + tree.stumps.getFirst().toShortString() + "(" + left + "/" + tree.logs.size() + " logs)";
    }

    @Override
    public Pick pick(ServerLevel level, GoblinEntity goblin, GoblinBedBlockEntity bedEntity, JobConfig config, Set<BlockPos> skipped) {
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
            claims.remove(goblin.getUUID()); // felled, or only unreachable logs left: next tree
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
        return new Tree(logs, stumps, now);
    }

    @Override
    public void afterBreak(ServerLevel level, GoblinEntity goblin, GoblinBedBlockEntity bedEntity, JobConfig config, BlockPos broken) {
        Tree tree = claims(level).get(goblin.getUUID());
        if (tree == null || !tree.logs.contains(broken)) return;
        for (BlockPos log : tree.logs) {
            if (level.getBlockState(log).is(BlockTags.LOGS)) return; // replant once the whole tree is down
        }
        int lowest = tree.stumps.getFirst().getY();
        for (BlockPos stump : tree.stumps) {
            if (stump.getY() == lowest) replant(level, goblin, stump);
        }
    }

    /** A sapling from the storage onto the stump, if one fits the ground there (the sapling's own canSurvive decides). */
    private static void replant(ServerLevel level, GoblinEntity goblin, BlockPos stump) {
        if (!level.getBlockState(stump).isAir() || HomeRegistry.isProtected(level, stump)) return;
        BlockState ground = level.getBlockState(stump.below());
        if (ground.isAir() || ground.is(BlockTags.LOGS)) return;
        SimpleContainer inv = goblin.getInventory();
        for (int i = GoblinEntity.HOTBAR_SIZE; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.isEmpty() || !stack.is(ItemTags.SAPLINGS)) continue;
            BlockState state = Block.byItem(stack.getItem()).defaultBlockState();
            if (!state.canSurvive(level, stump)) continue;
            level.setBlock(stump, state, 3);
            stack.shrink(1);
            inv.setChanged();
            return;
        }
    }

    private static Map<UUID, Tree> claims(ServerLevel level) {
        return CLAIMS.computeIfAbsent(level.dimension(), k -> new ConcurrentHashMap<>());
    }
}
