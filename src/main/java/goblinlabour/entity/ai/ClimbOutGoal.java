package goblinlabour.entity.ai;

import goblinlabour.GoblinLabour;
import goblinlabour.GoblinSpeech;
import goblinlabour.block.GoblinBedBlockEntity;
import goblinlabour.entity.GoblinEntity;
import goblinlabour.home.HomeRegistry;
import goblinlabour.job.Climber;
import goblinlabour.job.Job;
import goblinlabour.job.Standing;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.TorchBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Gets a goblin out of a hole it cannot walk out of, e.g. a shaft dug without stairs: when the goblin wants to go
 * home (resting, unloading) but gets no closer to it outside its flat, in a small closed area, it picks a column at
 * the side of the hole, stacks goblin scaffold there and climbs until it can step onto the ground above. A column
 * somebody else already built is used first (it costs no new blocks), but only one goblin climbs a column at a time:
 * the others wait a little aside and follow one after the other. A climbing goblin cannot be pushed off its column.
 * The scaffold stays behind and vanishes by itself. When there is no way up at all the goblin blinks home.
 */
public class ClimbOutGoal extends Goal {
    private static final int CHECK_INTERVAL = 40;
    private static final int FAILED_CHECK_INTERVAL = 400;
    /**
     * How far the walkable area around the goblin is followed: a shaft with a 32 block tunnel off its floor (and
     * room for a second one) is still one closed hole.
     */
    private static final int SEARCH_RADIUS = 48;
    private static final int MAX_AREA = 2500;
    /** The nearest spots of the area are all tried as a column foot; further away only those with room above. */
    private static final int MAX_CANDIDATES = 64;
    private static final int MAX_COLUMN_CHECKS = 256;
    private static final int GIVE_UP_TICKS = 2400;
    private static final int STUCK_TICKS = 100;
    private static final int SPEECH_COOLDOWN = 2400;
    /** Without getting this much closer to the bed for this long a goblin counts as stuck, even while it moves. */
    private static final int NO_HEADWAY_TICKS = 60;
    private static final double HEADWAY = 0.5;
    /** A waiting goblin asks this often whether the column is free, and stands at least this far from its foot. */
    private static final int WAIT_CHECK = 40;
    private static final int WAIT_GAP_SQ = 2 * 2;
    /**
     * A goblin on its way into its column that gets no closer for this long has somebody standing on the foot: it
     * lets that one climb first instead of shoving until it gives up.
     */
    private static final int WALK_PATIENCE = 60;
    /** A reservation not refreshed for this long belongs to a goblin that is gone. */
    private static final int RESERVATION_TTL = 60;
    private static final boolean DEV = net.fabricmc.loader.api.FabricLoader.getInstance().isDevelopmentEnvironment();
    /** Dev: why the last {@link #closedArea} call found no closed area. */
    private static String areaFailure = "";
    /** Column foot -> the goblin climbing it, per dimension. */
    private static final Map<ResourceKey<Level>, Map<BlockPos, Reservation>> COLUMNS = new HashMap<>();

    /** Forgets it all when the server stops: in single player the next world runs in the same JVM. */
    public static void reset() {
        COLUMNS.clear();
    }

    private record Reservation(UUID goblin, long refreshed) {
    }
    private static final String[] LINES = {
            "No stairs? Goblin makes own way up!", "Up, up, up we go!", "Who dug this hole? Oh. Me.", "Climbing out, boss!",
    };

    private enum Stage { WALK, WAIT, CLIMB, STEP_OUT }

    /** Climb at {@code column} until the feet are at {@code exit}'s height, then step onto {@code exit}. */
    private record Plan(BlockPos column, BlockPos exit, int newBlocks) {
    }

    private final GoblinEntity goblin;
    private int nextCheck;
    private double bestHomeDist = Double.MAX_VALUE;
    private int lastHeadwayTick;
    @Nullable private Plan plan;
    @Nullable private Set<BlockPos> area;
    @Nullable private BlockPos reserved;
    @Nullable private BlockPos waitSpot;
    private Stage stage = Stage.WALK;
    private int ticks;
    private int stageTicks;
    private int stuckTicks;
    private double walkBest = Double.MAX_VALUE;
    private int walkProgressAt;
    private boolean done;
    private Vec3 lastPos = Vec3.ZERO;
    private long lastSpoke = -SPEECH_COOLDOWN;
    private int lastDevLog = -1000;
    /** Dev: the highest feet height of this climb, to see a goblin fall back down. */
    private double climbedTo = -Double.MAX_VALUE;

    public ClimbOutGoal(GoblinEntity goblin) {
        this.goblin = goblin;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
    }

    private boolean wantsHome(GoblinBedBlockEntity bed) {
        return bed.getJob().job() == Job.REST || goblin.runner().hasPendingDeposit() || goblin.runner().isWaitingForRoom();
    }

    @Override
    public boolean canUse() {
        if (goblin.isNoAi() || goblin.following() != null || goblin.recoverPos() != null) return false;
        if (goblin.tickCount < nextCheck || !(goblin.level() instanceof ServerLevel level)) return false;
        nextCheck = goblin.tickCount + CHECK_INTERVAL;
        GoblinBedBlockEntity bed = goblin.bed();
        if (bed == null || !bed.owns(goblin.getUUID()) || !wantsHome(bed)) {
            bestHomeDist = Double.MAX_VALUE;
            return false;
        }
        if (!goblin.onGround() || goblin.isInWater() || Climber.isUp(level, goblin)) return false;
        BoundingBox flat = HomeRegistry.flatBox(level, bed.getBlockPos());
        if (flat.isInside(goblin.blockPosition())) {
            bestHomeDist = Double.MAX_VALUE;
            return false;
        }
        // Stuck on the way home: no path, or no headway towards the bed for a while. Standing still is no sign: goblins
        // shoving each other in a pit never stand still.
        double homeDist = goblin.position().distanceTo(Vec3.atBottomCenterOf(bed.getBlockPos()));
        if (bestHomeDist == Double.MAX_VALUE || homeDist < bestHomeDist - HEADWAY) {
            bestHomeDist = homeDist;
            lastHeadwayTick = goblin.tickCount;
        }
        if (!goblin.getNavigation().isDone() && goblin.tickCount - lastHeadwayTick < NO_HEADWAY_TICKS) return false;

        Set<BlockPos> found = closedArea(level, goblin.blockPosition(), flat);
        if (found == null) {
            devLog("stuck on the way home at %s, but no closed area: %s", goblin.blockPosition().toShortString(), areaFailure);
            return false;
        }
        area = found;
        plan = findPlan(level, found);
        devLog("stuck at %s in an area of %d: %s", goblin.blockPosition().toShortString(), found.size(),
                plan == null ? "no column, blinks home" : "column " + plan.column().toShortString() + " to " + plan.exit().toShortString());
        if (plan != null) reserve(level, plan.column()); // at once: a crew mate planning in this tick joins this column
        if (plan == null) {
            nextCheck = goblin.tickCount + FAILED_CHECK_INTERVAL;
            goblin.blinkTo(level, bed.getBlockPos(), "no way up");
            return false;
        }
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        if (plan == null || done || ticks > GIVE_UP_TICKS || goblin.isNoAi()) return false;
        GoblinBedBlockEntity bed = goblin.bed();
        return bed != null && wantsHome(bed);
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void start() {
        climbedTo = -Double.MAX_VALUE;
        ticks = 0;
        stageTicks = 0;
        stuckTicks = 0;
        done = false;
        waitSpot = null;
        ServerLevel level = (ServerLevel) goblin.level();
        // the column is ours since planning, or another goblin climbs it and this one waits its turn
        stage = plan != null && reserve(level, plan.column()) ? Stage.WALK : Stage.WAIT;
        if (level.getGameTime() - lastSpoke > SPEECH_COOLDOWN) {
            lastSpoke = level.getGameTime();
            GoblinSpeech.say(level, goblin.goblinName(), GoblinSpeech.random(goblin.getRandom(), LINES));
        }
    }

    @Override
    public void stop() {
        ServerLevel level = (ServerLevel) goblin.level();
        goblin.getNavigation().stop();
        if (!done && DEV) {
            GoblinBedBlockEntity bed = goblin.bed();
            GoblinLabour.LOGGER.info("Dev: {} climb-out aborted at {} (stage {}, {} ticks, wants home {})", goblin.goblinName(),
                    goblin.blockPosition().toShortString(), stage, ticks, bed != null && wantsHome(bed));
        }
        if (!done) goblin.climber().settle(level); // aborted half-way: back to the foot of the column
        goblin.setShiftKeyDown(false);
        goblin.setClimbing(false);
        releaseColumn(level);
        plan = null;
        area = null;
        waitSpot = null;
        bestHomeDist = Double.MAX_VALUE;
        nextCheck = goblin.tickCount + CHECK_INTERVAL;
    }

    @Override
    public void tick() {
        // replan() clears the plan inside tick(); the selector only checks canContinueToUse every other tick, so the
        // goal can be ticked once more without a plan
        if (plan == null) return;
        ServerLevel level = (ServerLevel) goblin.level();
        ticks++;
        stageTicks++;
        BlockPos column = plan.column();
        BlockPos exit = plan.exit();
        BlockPos feet = goblin.blockPosition();
        boolean inColumn = feet.getX() == column.getX() && feet.getZ() == column.getZ();
        Vec3 columnCenter = new Vec3(column.getX() + 0.5, goblin.getY(), column.getZ() + 0.5);

        if (stage != Stage.WAIT) reserve(level, column); // still ours: keep it fresh
        if (DEV && (stage == Stage.CLIMB || stage == Stage.STEP_OUT)) {
            if (goblin.getY() > climbedTo) climbedTo = goblin.getY();
            else if (goblin.getY() < climbedTo - 1.5) {
                GoblinLabour.LOGGER.info("Dev: {} fell while climbing out from y={} to y={} (stage {}, in column {})", goblin.goblinName(),
                        String.format("%.1f", climbedTo), String.format("%.1f", goblin.getY()), stage, inColumn);
                climbedTo = goblin.getY();
            }
        }

        switch (stage) {
            case WAIT -> {
                goblin.getLookControl().setLookAt(columnCenter.x, goblin.getEyeY(), columnCenter.z);
                // already standing on the foot: this one climbs next, as soon as the column is free
                if ((inColumn || stageTicks % WAIT_CHECK == 0) && reserve(level, column)) {
                    setStage(Stage.WALK);
                    return;
                }
                if (waitSpot == null) waitSpot = waitSpot(level, column);
                if (waitSpot != null && !feet.equals(waitSpot) && goblin.tickCount % 10 == 0) {
                    goblin.getNavigation().moveTo(waitSpot.getX() + 0.5, waitSpot.getY(), waitSpot.getZ() + 0.5, 0.8);
                }
            }
            case WALK -> {
                goblin.getLookControl().setLookAt(columnCenter.x, goblin.getEyeY(), columnCenter.z);
                if (inColumn && horizontalDistSqr(columnCenter) < 0.09) {
                    goblin.setClimbing(true);
                    setStage(Stage.CLIMB);
                    return;
                }
                double toColumn = horizontalDistSqr(columnCenter);
                if (toColumn < walkBest - 0.01) {
                    walkBest = toColumn;
                    walkProgressAt = stageTicks;
                } else if (toColumn < 2.25 && stageTicks - walkProgressAt > WALK_PATIENCE) {
                    if (DEV) GoblinLabour.LOGGER.info("Dev: {} climb-out lets the goblin on the foot of {} go first",
                            goblin.goblinName(), column.toShortString());
                    releaseColumn(level);
                    waitSpot = null;
                    setStage(Stage.WAIT);
                    return;
                }
                if (toColumn < 2.25) {
                    goblin.getNavigation().stop();
                    goblin.getMoveControl().setWantedPosition(columnCenter.x, column.getY(), columnCenter.z, 0.6);
                } else if (goblin.tickCount % 10 == 0) {
                    goblin.getNavigation().moveTo(columnCenter.x, column.getY(), columnCenter.z, 1.0);
                }
                if (goblin.position().distanceToSqr(lastPos) < 0.0001) {
                    if (++stuckTicks > STUCK_TICKS) replan("stuck on the way to the column");
                } else {
                    stuckTicks = 0;
                }
                lastPos = goblin.position();
            }
            case CLIMB -> {
                if (feet.getY() >= exit.getY()) {
                    setStage(Stage.STEP_OUT);
                    return;
                }
                if (!inColumn) {
                    if (feet.getY() <= column.getY()) setStage(Stage.WALK); // pushed off at the bottom
                    else goblin.getMoveControl().setWantedPosition(columnCenter.x, goblin.getY(), columnCenter.z, 0.4);
                    return;
                }
                if (horizontalDistSqr(columnCenter) > 0.0625) {
                    goblin.getMoveControl().setWantedPosition(columnCenter.x, goblin.getY(), columnCenter.z, 0.3);
                }
                if (!goblin.climber().climb(level)) replan("cannot climb on");
            }
            case STEP_OUT -> {
                Vec3 target = Vec3.atBottomCenterOf(exit);
                goblin.getLookControl().setLookAt(target.x, goblin.getEyeY(), target.z);
                goblin.getMoveControl().setWantedPosition(target.x, target.y, target.z, 0.8);
                if (feet.equals(exit) || (!inColumn && goblin.onGround() && feet.getY() >= exit.getY() && !Climber.isUp(level, goblin))) {
                    done = true;
                    return;
                }
                if (feet.getY() < exit.getY() && inColumn) {
                    setStage(Stage.CLIMB); // slipped back down
                } else if (stageTicks > 60) {
                    goblin.getJumpControl().jump();
                    if (stageTicks > 160) replan("cannot step out");
                }
            }
        }
    }

    private void setStage(Stage next) {
        stage = next;
        stageTicks = 0;
        stuckTicks = 0;
        walkBest = Double.MAX_VALUE;
        walkProgressAt = 0;
    }

    /** Takes (or refreshes) the column for this goblin. False while another goblin climbs it. */
    private boolean reserve(ServerLevel level, BlockPos column) {
        Map<BlockPos, Reservation> columns = COLUMNS.computeIfAbsent(level.dimension(), k -> new HashMap<>());
        long now = level.getGameTime();
        Reservation holder = columns.get(column);
        if (holder != null && !holder.goblin().equals(goblin.getUUID()) && now - holder.refreshed() <= RESERVATION_TTL) {
            return false;
        }
        columns.put(column.immutable(), new Reservation(goblin.getUUID(), now));
        reserved = column.immutable();
        return true;
    }

    /** Whether another goblin holds a fresh reservation on this column. */
    private boolean climbedByOther(ServerLevel level, BlockPos column) {
        Map<BlockPos, Reservation> columns = COLUMNS.get(level.dimension());
        Reservation holder = columns == null ? null : columns.get(column);
        return holder != null && !holder.goblin().equals(goblin.getUUID())
                && level.getGameTime() - holder.refreshed() <= RESERVATION_TTL;
    }

    private void releaseColumn(ServerLevel level) {
        Map<BlockPos, Reservation> columns = COLUMNS.get(level.dimension());
        if (columns != null && reserved != null) {
            columns.computeIfPresent(reserved, (pos, holder) -> holder.goblin().equals(goblin.getUUID()) ? null : holder);
        }
        reserved = null;
    }

    /** Where to wait for the column: a spot in the hole at least two blocks from its foot, the nearest one. */
    @Nullable
    private BlockPos waitSpot(ServerLevel level, BlockPos column) {
        if (area == null) return null;
        BlockPos here = goblin.blockPosition();
        BlockPos best = null;
        for (BlockPos spot : area) {
            int dx = spot.getX() - column.getX(), dz = spot.getZ() - column.getZ();
            if (dx * dx + dz * dz < WAIT_GAP_SQ || !standable(level, spot)) continue;
            if (best == null || spot.distSqr(here) < best.distSqr(here)) best = spot;
        }
        return best;
    }

    /** For the status line: the stage while the goal runs. */
    public String stageName() {
        return plan == null ? "-" : stage.name();
    }

    private void replan(String why) {
        if (DEV) GoblinLabour.LOGGER.info("Dev: {} climb-out replans at {} (stage {}): {}", goblin.goblinName(),
                goblin.blockPosition().toShortString(), stage, why);
        plan = null; // canContinueToUse ends the goal; the next check plans again from where the goblin is
    }

    private double horizontalDistSqr(Vec3 point) {
        double dx = point.x - goblin.getX(), dz = point.z - goblin.getZ();
        return dx * dx + dz * dz;
    }

    // ---- planning ------------------------------------------------------------------------------------------------

    /**
     * Everything the goblin can walk to from {@code start} (steps of one up, not onto scaffold, drops of up to three).
     * Returns null when that area is not a closed hole: it is large, reaches far, or touches the goblin's flat.
     */
    @Nullable
    /** Dev only, at most every 20 seconds per goblin. */
    private void devLog(String format, Object... args) {
        if (!DEV || goblin.tickCount - lastDevLog < 400) return;
        lastDevLog = goblin.tickCount;
        GoblinLabour.LOGGER.info("Dev: {} climb-out: {}", goblin.goblinName(), String.format(format, args));
    }

    static Set<BlockPos> closedArea(ServerLevel level, BlockPos start, BoundingBox flat) {
        Set<BlockPos> area = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        area.add(start);
        queue.add(start);
        while (!queue.isEmpty()) {
            BlockPos cur = queue.poll();
            for (Direction dir : Direction.Plane.HORIZONTAL) {
                BlockPos side = cur.relative(dir);
                for (int dy = 1; dy >= -3; dy--) {
                    BlockPos n = side.above(dy);
                    if (dy == 1 && !passable(level, cur.above(2))) continue; // no head room for the step up
                    if (dy == -1 && !passable(level, side.above())) break;
                    if (dy < 0 && !passable(level, side.above(dy + 1))) break; // something blocks the way down
                    // Up onto scaffold is climbing, not walking: two columns side by side would otherwise make a
                    // staircase out of the hole, and the goblins below would never be seen as stuck.
                    if (dy == 1 && Climber.isScaffold(level.getBlockState(n.below()))) continue;
                    if (!walkable(level, n)) continue;
                    if (Math.abs(n.getX() - start.getX()) > SEARCH_RADIUS || Math.abs(n.getZ() - start.getZ()) > SEARCH_RADIUS) {
                        areaFailure = "reaches past " + SEARCH_RADIUS + " blocks at " + n.toShortString();
                        return null;
                    }
                    if (flat.isInside(n)) {
                        areaFailure = "leads home at " + n.toShortString();
                        return null;
                    }
                    if (area.add(n)) {
                        if (area.size() > MAX_AREA) {
                            areaFailure = "more than " + MAX_AREA + " blocks";
                            return null;
                        }
                        queue.add(n);
                    }
                    break;
                }
            }
        }
        return area;
    }

    /**
     * The cheapest column in the hole: for each spot of the area, how high the goblin has to climb until a
     * neighbouring block outside the area is standable. Existing scaffold in the column counts as free.
     */
    @Nullable
    private Plan findPlan(ServerLevel level, Set<BlockPos> area) {
        BlockPos start = goblin.blockPosition();
        Plan best = null;
        double bestCost = Double.MAX_VALUE;
        int candidates = 0;
        for (BlockPos spot : area.stream().sorted((a, b) -> Double.compare(a.distSqr(start), b.distSqr(start))).toList()) {
            // deep in a tunnel the nearest spots all have a ceiling right above: the shaft further back is the way out
            if (candidates >= MAX_CANDIDATES && !passable(level, spot.above(3))) continue;
            if (candidates++ >= MAX_COLUMN_CHECKS) break;
            // a column another goblin is about to climb counts as built: all goblins in a hole share one column,
            // side-by-side columns would form a scaffold tower that goblins walk up into and cannot leave
            boolean shared = climbedByOther(level, spot);
            int existing = Climber.isScaffold(level.getBlockState(spot)) ? 1 : 0;
            for (int h = 1; h <= Climber.MAX_HEIGHT; h++) {
                BlockPos feet = spot.above(h);
                BlockState below = level.getBlockState(feet.below());
                if (h > 1 && !(below.canBeReplaced() || Climber.isScaffold(below) || below.getBlock() instanceof TorchBlock)) break;
                if (h > 1 && Climber.isScaffold(below)) existing++;
                if (!passable(level, feet) || !passable(level, feet.above()) || HomeRegistry.isProtected(level, feet.below())) break;
                BlockPos exit = null;
                for (Direction dir : Direction.Plane.HORIZONTAL) {
                    BlockPos side = feet.relative(dir);
                    if (!area.contains(side) && isExit(level, side)) {
                        exit = side;
                        break;
                    }
                }
                if (exit == null) continue;
                int newBlocks = shared ? 0 : Math.max(0, h - existing);
                double cost = newBlocks * 4.0 + h * 0.5 + Math.sqrt(spot.distSqr(start));
                if (cost < bestCost) {
                    bestCost = cost;
                    best = new Plan(spot, exit, newBlocks);
                }
                break;
            }
        }
        return best;
    }

    private static boolean passable(ServerLevel level, BlockPos pos) {
        return Standing.passable(level, pos);
    }

    /** Scaffold counts as open and as ground here: the goblin climbs through it. */
    private static boolean standable(ServerLevel level, BlockPos pos) {
        return Standing.climbable(level, pos);
    }

    /**
     * A block to step out onto at the top of a column: room to stand on real ground. The top of a neighbouring
     * scaffold column (the one the goblin before built) is no way out: stepping onto it the goblin sinks back into
     * the hole and plans the same one-block climb again, over and over.
     */
    private static boolean isExit(ServerLevel level, BlockPos pos) {
        return standable(level, pos) && !Climber.isScaffold(level.getBlockState(pos))
                && !Climber.isScaffold(level.getBlockState(pos.below()));
    }

    /**
     * Where a goblin walks: it is 0.95 blocks tall, so one free block is enough (a 1x1 tunnel is a way, not a wall).
     * Scaffold counts as open and as ground, like in {@link #standable}.
     */
    private static boolean walkable(ServerLevel level, BlockPos pos) {
        if (!passable(level, pos)) return false;
        BlockPos below = pos.below();
        BlockState ground = level.getBlockState(below);
        if (!ground.getFluidState().isEmpty() && ground.getCollisionShape(level, below).isEmpty()) return false;
        return Climber.isScaffold(ground) || !ground.getCollisionShape(level, below).isEmpty();
    }
}
