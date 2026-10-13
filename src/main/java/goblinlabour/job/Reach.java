package goblinlabour.job;

import goblinlabour.GoblinLabour;
import goblinlabour.entity.GoblinEntity;
import goblinlabour.home.HomeRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * How a working goblin gets to where it can reach a block: a stand spot in reach and in sight, a scaffold column for a
 * block high above, a way cleared through leaves, or a plain walk with a blink when it makes no headway. One per
 * {@link JobRunner}; it remembers the spot or column chosen for the current target.
 */
public final class Reach {
    /** Distance (squared) from the eyes at which the goblin swings instead of walking. */
    static final double REACH_SQ = 3.5 * 3.5;
    private static final double COMFORT_REACH_SQ = 2.75 * 2.75;
    /** Targets more than this far above the eyes are reached by stacking scaffold. */
    private static final double CLIMB_MIN_DY = 1.5;
    /** How far beside a high target a scaffold column may stand, and how many of those spots get a path check. */
    private static final int COLUMN_RANGE = 2;
    private static final int MAX_STAND_PATH_CHECKS = 4;
    private static final int MAX_COLUMN_PATH_CHECKS = 6;
    /** Drops up to this many blocks are stepped off; higher up the goblin sneaks down its column. */
    private static final int SAFE_DROP = 2;
    private static final int STUCK_TICKS = 100;
    static final int SKIP_TICKS = 1200;

    private final GoblinEntity goblin;
    /** The runner's skipped blocks (block to game time it is tried again). */
    private final Map<BlockPos, Long> skipped;
    private final Headway headway = new Headway();
    /** The scaffold column chosen for {@link #climbFor}, the high target the goblin is climbing towards. */
    @Nullable private BlockPos climbFor;
    @Nullable private BlockPos climbColumn;
    /** The spot the goblin walks to for {@link #standFor}, a block out of reach (see {@link #standSpot}). */
    @Nullable private BlockPos standFor;
    @Nullable private BlockPos standSpot;
    /** The current job's {@link JobTask#standBounds}, set by {@link #moveTowards}. */
    @Nullable private BoundingBox bounds;
    /** A lumberjack breaks the leaves in its way instead of giving up on a log it cannot walk to (see {@link LeafPath}). */
    private boolean clearsLeaves;
    /** The leaf block to break first, set when the way to the target leads through leaves. */
    @Nullable private BlockPos leafInTheWay;
    private int stuckStrikes;
    /** Debug: the last movement branch taken. */
    private String branch = "-";

    Reach(GoblinEntity goblin, Map<BlockPos, Long> skipped) {
        this.goblin = goblin;
        this.skipped = skipped;
    }

    String branch() {
        return branch;
    }

    void setBranch(String branch) {
        this.branch = branch;
    }

    void setClearsLeaves(boolean clearsLeaves) {
        this.clearsLeaves = clearsLeaves;
    }

    @Nullable
    BlockPos climbColumn() {
        return climbColumn;
    }

    /** The leaf the last {@link #moveTowards} chose to break first, once; null when there is none. */
    @Nullable
    BlockPos takeLeafInTheWay() {
        BlockPos leaf = leafInTheWay;
        leafInTheWay = null;
        return leaf;
    }

    /** A new target: no spot or column chosen for it yet. */
    void forgetTarget() {
        climbFor = null;
        climbColumn = null;
        standFor = null;
        standSpot = null;
    }

    /** A new thing to walk to: the stuck count and the blinks start over. */
    void resetStuck() {
        headway.clearTicks();
        stuckStrikes = 0;
    }

    /** A new item to walk to: the headway starts over, the blinks so far still count. */
    void resetHeadway() {
        headway.reset();
    }

    String debug() {
        return " stuck=" + headway.stuckTicks() + "/" + stuckStrikes;
    }

    String debugSpots() {
        return " stand=" + (standSpot == null ? "-" : standSpot.toShortString())
                + " column=" + (climbColumn == null ? "-" : climbColumn.toShortString());
    }

    /**
     * Climb, come down or walk, whatever brings the goblin closer. A target well above the goblin is reached from a
     * scaffold column: one column is chosen per target (see {@link #chooseColumn}), the goblin walks to its foot and
     * climbs until the target is in reach. Scaffold is never stacked inside a home or under a blocked ceiling.
     * Returns false when the goblin gave up on the target; true with {@link #takeLeafInTheWay} set when it should
     * break a leaf first.
     */
    boolean moveTowards(ServerLevel level, BlockPos targetPos, Vec3 center, long now, @Nullable BoundingBox bounds) {
        this.bounds = bounds;
        // only up on a scaffold column: standing on the rim of a hole (block position over the hole) is not "high"
        boolean high = Climber.isUp(level, goblin) && fallHeight(level) > SAFE_DROP;
        BlockPos spot = standSpot(level, targetPos, center);
        branch = "move" + (spot != null ? "-spot" : "") + (high ? "-high" : "");
        if (spot == null && center.y - goblin.getEyeY() > CLIMB_MIN_DY) {
            if (!targetPos.equals(climbFor)) {
                climbFor = targetPos;
                climbColumn = chooseColumn(level, targetPos);
            }
            if (climbColumn == null) return clearWayOrGiveUp(level, targetPos, center, now);
            BlockPos feet = goblin.blockPosition();
            if (feet.getX() == climbColumn.getX() && feet.getZ() == climbColumn.getZ()) {
                if (goblin.climber().climb(level)) return true;
                return giveUp(targetPos, now); // blocked above, or as high as a column goes, and still out of reach
            }
            if (high) {
                goblin.climber().descend(level); // on another column: come down, then walk over
                return true;
            }
            return approachSpot(level, climbColumn) || clearWayOrGiveUp(level, targetPos, center, now);
        }
        if (high) {
            goblin.climber().descend(level);
            return true;
        }
        if (spot != null) return approachSpot(level, spot) || clearWayOrGiveUp(level, targetPos, center, now);
        // kept inside its work area, the goblin does not walk at the block (and blink next to it) from anywhere else
        if (bounds != null) return giveUp(targetPos, now);
        return approach(level, center) || clearWayOrGiveUp(level, targetPos, center, now);
    }

    /**
     * A stand spot inside the job's {@link JobTask#standBounds} (x and z), or on the bounds' floor or below (the floor
     * the shaft starts from reaches past it: under a low ceiling the first layer is dug from beside the shaft).
     */
    private boolean allowedStand(BlockPos spot) {
        return bounds == null || spot.getY() <= bounds.minY() || allowed(spot);
    }

    /** Inside the job's {@link JobTask#standBounds} (x and z), or no bounds. */
    private boolean allowed(BlockPos spot) {
        return bounds == null || spot.getX() >= bounds.minX() && spot.getX() <= bounds.maxX()
                && spot.getZ() >= bounds.minZ() && spot.getZ() <= bounds.maxZ();
    }

    /**
     * Like {@link #approach}, but the last steps go straight to the middle of the block: path following stops about
     * a block short of its goal, which can leave the goblin just out of reach of what it came for.
     */
    boolean approachSpot(ServerLevel level, BlockPos spot) {
        Vec3 point = Vec3.atBottomCenterOf(spot);
        double dx = point.x - goblin.getX(), dz = point.z - goblin.getZ();
        if (dx * dx + dz * dz > 1.5 * 1.5 || Math.abs(point.y - goblin.getY()) > 1.2) return approach(level, point);
        branch = "steer";
        goblin.setShiftKeyDown(false);
        goblin.getNavigation().stop();
        goblin.getMoveControl().setWantedPosition(point.x, point.y, point.z, 0.8);
        return headway(level, point);
    }

    /** The free block around {@code pos} (its own block included, one up or down) nearest to the goblin, or null. */
    @Nullable
    BlockPos asideSpot(ServerLevel level, BlockPos pos) {
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dz == 0) continue;
                    BlockPos spot = pos.offset(dx, dy, dz);
                    if (!Standing.room(level, spot)) continue;
                    double d = goblin.position().distanceToSqr(Vec3.atBottomCenterOf(spot));
                    if (d < bestDist) {
                        bestDist = d;
                        best = spot;
                    }
                }
            }
        }
        return best;
    }

    /**
     * Before giving up on a block it cannot walk to, a lumberjack looks for a way through leaves to a spot it could
     * work from (a stand spot in reach, or the foot of a scaffold column for a high log). The first leaf block on that
     * way becomes the target; once it is broken the goblin tries the log again, and clears the next leaf if need be.
     */
    private boolean clearWayOrGiveUp(ServerLevel level, BlockPos targetPos, Vec3 center, long now) {
        if (!clearsLeaves || level.getBlockState(targetPos).is(BlockTags.LEAVES)) return giveUp(targetPos, now);
        // only from the ground: up in a crown a broken leaf or a step onto the next one is a fall
        if (Climber.isUp(level, goblin) || fallHeight(level) > 0 || !goblin.onGround()) return giveUp(targetPos, now);
        Set<BlockPos> goals = new HashSet<>(standCandidates(level, targetPos, center));
        if (center.y - goblin.getEyeY() > CLIMB_MIN_DY) goals.addAll(columnFeet(level, targetPos));
        BlockPos leaf = LeafPath.firstLeaf(level, goblin.blockPosition(), goals, skipped.keySet());
        if (leaf == null || Mining.verdict(level, leaf, level.getBlockState(leaf), goblin) != Mining.Verdict.OK) {
            return giveUp(targetPos, now);
        }
        leafInTheWay = leaf;
        forgetTarget();
        headway.reset();
        branch = "clear-leaves";
        return true;
    }

    /** Skips {@code targetPos} for a while; always false, for {@code return giveUp(...)}. */
    boolean giveUp(BlockPos targetPos, long now) {
        skipped.put(targetPos, now + SKIP_TICKS);
        forgetTarget();
        return false;
    }

    /**
     * Where to stand to work on a block that is out of reach or out of sight: the nearest spot around it with solid
     * ground (not leaves or scaffold) from which the block is in reach and in plain sight, or null (then the goblin
     * climbs, or walks at the block).
     * Walking at a solid block itself does not work: GroundPathNavigation lifts a solid target up to the first open
     * block above it, which sent tunnel diggers up the stairs and across the surface above their tunnel.
     */
    @Nullable
    private BlockPos standSpot(ServerLevel level, BlockPos targetPos, Vec3 center) {
        if (targetPos.equals(standFor) && (standSpot == null || Standing.onGround(level, standSpot))) return standSpot;
        standFor = targetPos;
        standSpot = null;
        // only spots the goblin can walk to: the bottom of a shaft without stairs or the ground above a shaft dug
        // upwards are next to the target but not a way to it (then it climbs, or walks at the block and blinks)
        int pathChecks = 0;
        for (BlockPos candidate : standCandidates(level, targetPos, center)) {
            if (goblin.position().distanceToSqr(Vec3.atBottomCenterOf(candidate)) < 2.0) {
                standSpot = candidate;
                break;
            }
            if (pathChecks++ >= MAX_STAND_PATH_CHECKS) break;
            if (reachable(goblin.getNavigation(), candidate)) {
                standSpot = candidate;
                break;
            }
        }
        return standSpot;
    }

    /** Spots around a block on solid ground with the block in reach and in sight, best first (whether reachable or not). */
    private List<BlockPos> standCandidates(ServerLevel level, BlockPos targetPos, Vec3 center) {
        record Candidate(BlockPos spot, double cost) {
        }
        List<Candidate> candidates = new ArrayList<>();
        for (int dy = -3; dy <= 1; dy++) {
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    BlockPos spot = targetPos.offset(dx, dy, dz);
                    if (!allowedStand(spot) || !Standing.onGround(level, spot)) continue;
                    Vec3 eyes = new Vec3(spot.getX() + 0.5, spot.getY() + goblin.getEyeHeight(), spot.getZ() + 0.5);
                    double reach = eyes.distanceToSqr(center);
                    if (reach > REACH_SQ || !Mining.canSee(level, eyes, targetPos)) continue;
                    // spots well inside the reach first: at the very edge a goblin half a block off is out of reach
                    double cost = goblin.position().distanceToSqr(Vec3.atBottomCenterOf(spot)) + (reach > COMFORT_REACH_SQ ? 1000.0 : 0.0);
                    candidates.add(new Candidate(spot.immutable(), cost));
                }
            }
        }
        candidates.sort((a, b) -> Double.compare(a.cost(), b.cost()));
        return candidates.stream().map(Candidate::spot).toList();
    }

    /** The feet of every scaffold column {@link #chooseColumn} would consider for a high block, reachable or not. */
    private static List<BlockPos> columnFeet(ServerLevel level, BlockPos target) {
        List<BlockPos> feet = new ArrayList<>();
        for (int dx = -COLUMN_RANGE; dx <= COLUMN_RANGE; dx++) {
            for (int dz = -COLUMN_RANGE; dz <= COLUMN_RANGE; dz++) {
                if (dx == 0 && dz == 0) continue;
                BlockPos foot = columnFoot(level, target.getX() + dx, target.getY() - 1, target.getZ() + dz);
                if (foot != null) feet.add(foot);
            }
        }
        return feet;
    }

    /** Blocks of air (or scaffold) below the goblin's feet: more than {@link #SAFE_DROP} and it sneaks down instead of stepping off. */
    private int fallHeight(ServerLevel level) {
        BlockPos.MutableBlockPos cursor = goblin.blockPosition().mutable().move(0, -1, 0);
        int height = 0;
        while (height <= SAFE_DROP) {
            BlockState state = level.getBlockState(cursor);
            if (!Climber.isScaffold(state) && !state.getCollisionShape(level, cursor).isEmpty()) break;
            height++;
            cursor.move(0, -1, 0);
        }
        return height;
    }

    /**
     * Where to stack scaffold for a target high above: a spot up to two blocks beside the target's column (not right
     * under it, where the goblin's head would hit the target) whose ground holds scaffold and whose column is free up
     * to just below the target: air, plants, leaves (broken on the way) or existing scaffold. Existing scaffold is
     * preferred, then spots near the target, then spots near the goblin; the first one the goblin can walk to wins.
     */
    @Nullable
    private BlockPos chooseColumn(ServerLevel level, BlockPos target) {
        record Candidate(BlockPos foot, double cost) {
        }
        List<Candidate> candidates = new ArrayList<>();
        BlockPos feet = goblin.blockPosition();
        boolean up = Climber.isUp(level, goblin);
        // a shaft one block wide has no column beside the target: climb right under it and dig overhead
        boolean narrow = bounds != null && bounds.getXSpan() == 1 && bounds.getZSpan() == 1;
        for (int dx = -COLUMN_RANGE; dx <= COLUMN_RANGE; dx++) {
            for (int dz = -COLUMN_RANGE; dz <= COLUMN_RANGE; dz++) {
                if (dx == 0 && dz == 0 && !narrow) continue;
                if (!allowed(target.offset(dx, 0, dz))) continue;
                BlockPos foot = columnFoot(level, target.getX() + dx, target.getY() - 1, target.getZ() + dz);
                if (foot == null) continue;
                boolean existing = Climber.isScaffold(level.getBlockState(foot));
                double cost = (existing ? -10.0 : 0.0) + dx * dx + dz * dz + 0.05 * Math.sqrt(foot.distSqr(feet));
                // at the top of the column the target has to be in sight (another log of a thick trunk may hide it)
                Vec3 topEyes = new Vec3(foot.getX() + 0.5, target.getY() + 0.3, foot.getZ() + 0.5);
                if (!Mining.canSee(level, topEyes, target)) cost += 20.0;
                if (foot.getX() == feet.getX() && foot.getZ() == feet.getZ()) {
                    cost -= up ? 30.0 : 1.0; // already standing there, or even climbing that column
                }
                candidates.add(new Candidate(foot, cost));
            }
        }
        candidates.sort((a, b) -> Double.compare(a.cost(), b.cost()));
        int pathChecks = 0;
        for (Candidate candidate : candidates) {
            BlockPos foot = candidate.foot();
            if (foot.getX() == feet.getX() && foot.getZ() == feet.getZ()) return foot;
            if (pathChecks++ >= MAX_COLUMN_PATH_CHECKS) break;
            if (reachable(goblin.getNavigation(), foot)) return foot;
        }
        return null;
    }

    /**
     * The foot of a scaffold column at x/z that reaches up to {@code topY}: scanning down from {@code topY}, the block
     * above the first thing a column cannot go through. Null when the column is blocked, protected, too tall, stands
     * in liquid or on ground that does not hold scaffold.
     */
    @Nullable
    private static BlockPos columnFoot(ServerLevel level, int x, int topY, int z) {
        return findFoot(level, x, topY, z).pos();
    }

    /** A column foot, or null and why there is none (the reason is for {@link #columnReport}). */
    private record Foot(@Nullable BlockPos pos, String why) {
    }

    private static Foot findFoot(ServerLevel level, int x, int topY, int z) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos(x, topY, z);
        if (!level.isLoaded(cursor)) return new Foot(null, "not loaded");
        for (int i = 0; i < Climber.MAX_HEIGHT; i++, cursor.move(0, -1, 0)) {
            BlockState state = level.getBlockState(cursor);
            if (HomeRegistry.isProtected(level, cursor)) return new Foot(null, "home at y" + cursor.getY());
            if (Climber.isScaffold(state) || state.is(BlockTags.LEAVES) || (state.canBeReplaced() && state.getFluidState().isEmpty())) continue;
            if (!state.getFluidState().isEmpty()) return new Foot(null, "liquid at y" + cursor.getY());
            BlockPos foot = cursor.above();
            String on = " on " + BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath() + " y" + cursor.getY();
            if (foot.getY() > topY) return new Foot(null, "no room" + on); // no room at all below the target
            BlockState footState = level.getBlockState(foot);
            if (Climber.isScaffold(footState)) return new Foot(foot.immutable(), "scaffold" + on);
            if (!footState.canBeReplaced()) {
                // leaves at the foot: the goblin could not stand there
                return new Foot(null, BuiltInRegistries.BLOCK.getKey(footState.getBlock()).getPath() + " at the foot" + on);
            }
            return GoblinLabour.GOBLIN_SCAFFOLD.placementState(level, foot) != null
                    ? new Foot(foot.immutable(), "ground" + on) : new Foot(null, "scaffold would not hold" + on);
        }
        return new Foot(null, "too tall");
    }

    /**
     * Dev (the "columns" command): every column {@link #chooseColumn} looks at for a target, with its foot or why it
     * is out, whether the goblin can walk to the foot and whether the target is in sight from the top.
     */
    String columnReport(ServerLevel level, BlockPos target) {
        StringBuilder sb = new StringBuilder("columns for " + target.toShortString().replace(" ", "") + ":");
        for (int dx = -COLUMN_RANGE; dx <= COLUMN_RANGE; dx++) {
            for (int dz = -COLUMN_RANGE; dz <= COLUMN_RANGE; dz++) {
                if (dx == 0 && dz == 0) continue;
                Foot foot = findFoot(level, target.getX() + dx, target.getY() - 1, target.getZ() + dz);
                sb.append("\n  ").append(dx).append(',').append(dz).append(": ");
                if (foot.pos() == null) {
                    sb.append("OUT ").append(foot.why());
                    continue;
                }
                Vec3 topEyes = new Vec3(foot.pos().getX() + 0.5, target.getY() + 0.3, foot.pos().getZ() + 0.5);
                sb.append("foot y").append(foot.pos().getY()).append(' ').append(foot.why())
                        .append(reachable(goblin.getNavigation(), foot.pos()) ? ", reachable" : ", NOT reachable")
                        .append(Mining.canSee(level, topEyes, target) ? "" : ", target hidden from the top");
            }
        }
        return sb.toString();
    }

    /**
     * Walks towards {@code point}; teleports next to it after making no headway too long (outside homes). Only
     * horizontal movement counts: bobbing up and down in a scaffold block is no progress. Returns false when the
     * goblin gave up on this point.
     */
    boolean approach(ServerLevel level, Vec3 point) {
        return approach(level, point, true);
    }

    boolean approach(ServerLevel level, Vec3 point, boolean blink) {
        PathNavigation navigation = goblin.getNavigation();
        goblin.setShiftKeyDown(false);
        if (goblin.tickCount % 10 == 0 || (navigation.isDone() && goblin.tickCount % 5 == 0)) navigation.moveTo(point.x, point.y, point.z, 1.0);
        return blink ? headway(level, point) : headway.tick(goblin.position(), point) <= STUCK_TICKS;
    }

    /**
     * Counts ticks in which the goblin got no closer to {@code point} (jittering at the end of a path or bobbing in a
     * scaffold block is no progress); blinks next to the point now and then, false after three tries.
     */
    private boolean headway(ServerLevel level, Vec3 point) {
        if (!branch.endsWith("-headway")) branch += "-headway"; // once: it runs every tick, the string must not grow
        if (headway.tick(goblin.position(), point) > STUCK_TICKS) {
            headway.clearTicks();
            if (++stuckStrikes > 2) return false;
            goblin.blinkTo(level, BlockPos.containing(point), "approach");
            headway.restartFrom(goblin.position(), point);
        }
        return true;
    }

    /**
     * A goblin standing (not sleeping) on a straw bed steps off it: it steers to the nearest spot on real ground
     * within three blocks. True while it is on its way off; false when there is nowhere to go.
     */
    public static boolean stepOffBed(ServerLevel level, GoblinEntity goblin) {
        BlockPos feet = goblin.blockPosition();
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        for (BlockPos spot : BlockPos.betweenClosed(feet.offset(-3, -2, -3), feet.offset(3, 1, 3))) {
            if (!Standing.onGround(level, spot)) continue;
            double d = goblin.position().distanceToSqr(Vec3.atBottomCenterOf(spot));
            if (d < bestDist) {
                bestDist = d;
                best = spot.immutable();
            }
        }
        if (best == null) return false;
        Vec3 point = Vec3.atBottomCenterOf(best);
        goblin.setShiftKeyDown(false);
        goblin.getNavigation().stop();
        goblin.getMoveControl().setWantedPosition(point.x, point.y, point.z, 0.8);
        return true;
    }

    /** A path to {@code pos} that gets there (within one block). */
    public static boolean reachable(PathNavigation navigation, BlockPos pos) {
        Path path = navigation.createPath(pos, 1);
        return path != null && path.canReach();
    }
}
