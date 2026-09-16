package goblinlabour.job;

import goblinlabour.home.HomeRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * A way for a lumberjack through undergrowth: a cheapest-path search over the blocks a goblin can stand in, where a
 * block of leaves in the way is passable but expensive. When the only way to a spot leads through leaves, the first
 * leaf block on that way is what the goblin breaks next, like a player hacking a path through a jungle.
 */
public final class LeafPath {
    /** Breaking a leaf block costs this many steps: a walk round a bush is preferred when it is not much longer. */
    private static final int LEAF_COST = 6;
    private static final int MAX_NODES = 4000;
    private static final int MAX_RANGE = 24;
    private static final int MAX_DROP = 3;

    private LeafPath() {
    }

    /**
     * The first leaf block on the cheapest way from {@code start} (the goblin's feet) to any of {@code goals}, or null
     * when no goal is in range or the cheapest way needs no leaf broken (the goblin is stuck for some other reason).
     * Leaves in {@code skipped} or inside a home count as walls.
     */
    @Nullable
    public static BlockPos firstLeaf(ServerLevel level, BlockPos start, Set<BlockPos> goals, Set<BlockPos> skipped) {
        if (goals.isEmpty()) return null;
        record Node(BlockPos pos, int cost) {
        }
        Map<BlockPos, Integer> best = new HashMap<>();
        Map<BlockPos, BlockPos> parent = new HashMap<>();
        PriorityQueue<Node> open = new PriorityQueue<>((a, b) -> Integer.compare(a.cost(), b.cost()));
        best.put(start, 0);
        open.add(new Node(start, 0));
        int expanded = 0;
        while (!open.isEmpty() && expanded++ < MAX_NODES) {
            Node node = open.poll();
            if (node.cost() > best.getOrDefault(node.pos(), Integer.MAX_VALUE)) continue;
            if (goals.contains(node.pos())) return firstLeafOnWay(level, start, node.pos(), parent);
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                BlockPos side = node.pos().relative(direction);
                if (Math.abs(side.getX() - start.getX()) > MAX_RANGE || Math.abs(side.getZ() - start.getZ()) > MAX_RANGE) continue;
                for (int dy = 1; dy >= -MAX_DROP; dy--) {
                    BlockPos next = side.above(dy);
                    if (dy == 1 && !open(level, node.pos().above(2), skipped)) continue; // no head room to step up
                    if (dy < 0 && !open(level, side.above(dy + 1), skipped)) break; // something in the way down
                    if (!standable(level, next, skipped)) continue;
                    int cost = node.cost() + 1 + LEAF_COST * (leaves(level, next) + leaves(level, next.above())
                            + (dy == 1 ? leaves(level, node.pos().above(2)) : 0));
                    if (cost < best.getOrDefault(next, Integer.MAX_VALUE)) {
                        best.put(next, cost);
                        parent.put(next, node.pos());
                        open.add(new Node(next, cost));
                    }
                    break;
                }
            }
        }
        return null;
    }

    /** Walks the found way from the start and returns the first leaf block the goblin has to break on it. */
    @Nullable
    private static BlockPos firstLeafOnWay(ServerLevel level, BlockPos start, BlockPos goal, Map<BlockPos, BlockPos> parent) {
        java.util.ArrayDeque<BlockPos> way = new java.util.ArrayDeque<>();
        for (BlockPos pos = goal; pos != null && !pos.equals(start); pos = parent.get(pos)) way.addFirst(pos);
        BlockPos previous = start;
        for (BlockPos pos : way) {
            // stepping up needs the block over the head first, then the head and the feet of the next block
            if (pos.getY() > previous.getY() && isLeaves(level, previous.above(2))) return previous.above(2);
            if (isLeaves(level, pos.above())) return pos.above();
            if (isLeaves(level, pos)) return pos;
            previous = pos;
        }
        return null;
    }

    private static int leaves(ServerLevel level, BlockPos pos) {
        return isLeaves(level, pos) ? 1 : 0;
    }

    private static boolean isLeaves(ServerLevel level, BlockPos pos) {
        return level.getBlockState(pos).is(BlockTags.LEAVES);
    }

    /** Nothing to bump into, or leaves the goblin may break (not skipped, not in a home). */
    private static boolean open(ServerLevel level, BlockPos pos, Set<BlockPos> skipped) {
        BlockState state = level.getBlockState(pos);
        if (state.is(BlockTags.LEAVES)) return !skipped.contains(pos) && !HomeRegistry.isProtected(level, pos);
        return Climber.isScaffold(state) || state.getCollisionShape(level, pos).isEmpty();
    }

    /**
     * Feet and head open (leaves allowed) on real ground: not leaves and not logs, so the way stays on the forest floor
     * (or goblin scaffold) and never leads across a crown, where breaking the next leaf would drop the goblin.
     */
    private static boolean standable(ServerLevel level, BlockPos pos, Set<BlockPos> skipped) {
        if (!open(level, pos, skipped) || !open(level, pos.above(), skipped)) return false;
        BlockPos below = pos.below();
        BlockState ground = level.getBlockState(below);
        if (ground.is(BlockTags.LEAVES) || ground.is(BlockTags.LOGS)) return false;
        return ground.getFluidState().isEmpty() && (Climber.isScaffold(ground) || !ground.getCollisionShape(level, below).isEmpty());
    }
}
