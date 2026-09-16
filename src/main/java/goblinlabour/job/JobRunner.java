package goblinlabour.job;

import goblinlabour.GoblinLabour;
import goblinlabour.GoblinSounds;
import goblinlabour.GoblinSpeech;
import goblinlabour.block.GoblinBedBlockEntity;
import goblinlabour.entity.GoblinEntity;
import goblinlabour.home.HomeRegistry;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageUtil;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.Map;

/**
 * Per-goblin work loop (Minions Remastered's goalTick, one goblin at a time): pick a block, walk to where it is in
 * reach and in sight, swing until it breaks (or until the stair step is placed), pick up the drops, repeat. Extras:
 * scaffold climbing for high targets (see
 * {@link Climber}), unloading into the flat's goblin chests when the storage is full (waiting in bed when nothing
 * fits), picking up loose items for the collect job, and a "no exit" check. Tool choice and the rules live in
 * {@link Mining}; what comes next is the job's business.
 */
public final class JobRunner {
    /** Distance (squared) from the eyes at which the goblin swings instead of walking. */
    private static final double REACH_SQ = 3.5 * 3.5;
    private static final double COMFORT_REACH_SQ = 2.75 * 2.75;
    private static final double CHEST_REACH_SQ = 2.5 * 2.5;
    private static final int CHEST_OPEN_TICKS = 16;
    private static final double PICKUP_SQ = 1.5 * 1.5;
    /** Own drops further away than this are left for later (e.g. while unloading at home). */
    private static final double LOOT_RANGE_SQ = 32.0 * 32.0;
    private static final int BREAK_HISTORY = 16;
    /** A job is not done while own drops younger than this are still on their way down. */
    private static final int FRESH_DROP_TICKS = 100;
    /** Targets more than this far above the eyes are reached by stacking scaffold. */
    private static final double CLIMB_MIN_DY = 1.5;
    /** How far beside a high target a scaffold column may stand, and how many of those spots get a path check. */
    private static final int COLUMN_RANGE = 2;
    /** Horizontal distance (squared) from a target within which a goblin in scaffold is on a column meant for it. */
    private static final double CLIMB_BESIDE_SQ = 3.0 * 3.0;
    private static final int MAX_STAND_PATH_CHECKS = 4;
    private static final int MAX_COLUMN_PATH_CHECKS = 6;
    /** Drops up to this many blocks are stepped off; higher up the goblin sneaks down its column. */
    private static final int SAFE_DROP = 2;
    /** The exit check must fail this often in a row (100 ticks apart) before the goblin complains. */
    private static final int EXIT_FAILURES_TO_REPORT = 2;
    private static final float PLACE_PROGRESS_PER_TICK = 0.1f;
    private static final int PICK_RETRY_TICKS = 20;
    private static final int ENDLESS_RETRY_TICKS = 100;
    private static final int COLLECT_RETRY_TICKS = 40;
    private static final int STUCK_TICKS = 100;
    private static final int SKIP_TICKS = 1200;
    private static final int WAIT_ROOM_TICKS = 1200;
    private static final int NO_EXIT_RETRY_TICKS = 100;
    private static final int SPEECH_COOLDOWN = 1200;
    /** First working line 30-90 s after the first block, then one every 2-5 minutes. */
    private static final int CHATTER_FIRST_MIN = 600, CHATTER_FIRST_SPAN = 1200;
    private static final int CHATTER_MIN = 2400, CHATTER_SPAN = 3600;

    private enum Phase { WORK, DEPOSIT, WAIT_ROOM }

    private final GoblinEntity goblin;
    private Phase phase = Phase.WORK;
    @Nullable private BlockPos target;
    @Nullable private BlockState placeState;
    @Nullable private BlockPos chestTarget;
    /** The chest the goblin holds open while unloading, and when it lets go. */
    @Nullable private BlockPos openChest;
    private long chestCloseAt;
    @Nullable private ItemEntity itemTarget;
    /** What the goblin's own breaking dropped; it picks these up between two blocks. */
    private final List<ItemEntity> drops = new ArrayList<>();
    /** Debug: the last broken blocks and where the goblin stood ("block@feet"). */
    private final ArrayDeque<String> breaks = new ArrayDeque<>();
    /** The scaffold column chosen for {@link #climbFor}, the high target the goblin is climbing towards. */
    @Nullable private BlockPos climbFor;
    @Nullable private BlockPos climbColumn;
    /** The spot the goblin walks to for {@link #standFor}, a block out of reach (see {@link #standSpot}). */
    @Nullable private BlockPos standFor;
    @Nullable private BlockPos standSpot;
    private float progress;
    private int retryIn;
    private int stuckTicks;
    /** A lumberjack breaks the leaves in its way instead of giving up on a log it cannot walk to (see {@link LeafPath}). */
    private boolean clearsLeaves;
    /** Debug: runner ticks so far and the last movement branch taken. */
    private int ticks;
    private String branch = "-";
    private int stuckStrikes;
    private long waitUntil;
    private boolean exitChecked;
    private int exitFailures;
    /** The point {@link #headway} measures progress towards, and the closest the goblin got to it. */
    @Nullable private Vec3 headwayPoint;
    private double headwayBest;
    @Nullable private Block lastBrokenBlock;
    private final Map<BlockPos, Long> skipped = new HashMap<>();
    private final Map<Integer, Long> skippedItems = new HashMap<>();
    private final Map<String, Long> lastSaid = new HashMap<>();
    private long nextChatter = -1;

    public JobRunner(GoblinEntity goblin) {
        this.goblin = goblin;
    }

    @Nullable
    public Block lastBrokenBlock() {
        return lastBrokenBlock;
    }

    /** The block the goblin is working on (breaking or placing), or null. */
    @Nullable
    public BlockPos target() {
        return target;
    }

    /** Debug summary for the status command. */
    public String debug() {
        return "phase=" + phase + " target=" + (target == null ? "-" : target.toShortString()) + (placeState != null ? "(place)" : "")
                + (itemTarget != null ? " item=" + itemTarget.getItem().getItem() : "")
                + " progress=" + String.format(java.util.Locale.ROOT, "%.2f", progress) + " stuck=" + stuckTicks + "/" + stuckStrikes
                + " up=" + Climber.isUp(goblin.level(), goblin) + " skipped=" + skipped.size() + " retryIn=" + retryIn
                + " ticks=" + ticks + " branch=" + branch
                + " sneak=" + goblin.isShiftKeyDown() + String.format(java.util.Locale.ROOT, " y=%.2f vy=%.2f", goblin.getY(), goblin.getDeltaMovement().y)
                + " stand=" + (standSpot == null ? "-" : standSpot.toShortString())
                + " column=" + (climbColumn == null ? "-" : climbColumn.toShortString())
                + " drops=" + drops.stream().filter(ItemEntity::isAlive).count()
                + (goblin.level() instanceof ServerLevel level ? " " + ChopJob.debug(level, goblin) : "")
                + " breaks=" + String.join(";", breaks);
    }

    public boolean hasPendingDeposit() {
        return phase == Phase.DEPOSIT;
    }

    public boolean isWaitingForRoom() {
        return phase == Phase.WAIT_ROOM;
    }

    /** True when the goblin should be ticked by the work goal right now. */
    public boolean wantsToWork(ServerLevel level, boolean hasJob) {
        if (phase == Phase.DEPOSIT) return true;
        if (phase == Phase.WAIT_ROOM) {
            if (level.getGameTime() < waitUntil) return false;
            phase = Phase.DEPOSIT;
            chestTarget = null;
            return true;
        }
        return hasJob;
    }

    /** Called when the bed's job or order changes. */
    public void onJobChanged() {
        exitChecked = false;
        skipped.clear();
        skippedItems.clear();
        if (phase == Phase.WAIT_ROOM) phase = Phase.DEPOSIT;
        if (goblin.level() instanceof ServerLevel level) ChopJob.release(level, goblin);
    }

    public void stop(ServerLevel level) {
        closeChest(level);
        clearTarget(level);
        itemTarget = null;
        goblin.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        goblin.getNavigation().stop();
        goblin.climber().settle(level);
    }

    private void clearTarget(ServerLevel level) {
        if (target != null && placeState == null) level.destroyBlockProgress(goblin.getId(), target, -1);
        target = null;
        placeState = null;
        progress = 0.0f;
        climbFor = null;
        climbColumn = null;
        standFor = null;
        standSpot = null;
    }

    /** One tick. {@code task} may be null when only a deposit run is pending. */
    public void tick(ServerLevel level, JobHost bed, JobConfig config, @Nullable JobTask task) {
        ticks++;
        switch (phase) {
            case DEPOSIT -> tickDeposit(level, bed);
            case WAIT_ROOM -> { /* the rest goal has the goblin in bed */ }
            case WORK -> {
                if (task == null) return;
                if (config.job().needsAssignment() && bed.getAssignment() == null) {
                    bed.setStatus(GoblinBedBlockEntity.Status.IDLE);
                    bed.setJob(bed.afterOrder(config));
                    return;
                }
                if (goblin.isStorageFull()) {
                    if (bed.hasHome()) startDeposit(level); // a ring crew goblin waits for room in its ring instead
                    return;
                }
                if (task instanceof CollectJob collect) tickCollect(level, bed, config, collect);
                else tickWork(level, bed, config, task);
            }
        }
    }

    // ---- work ----------------------------------------------------------------------------------------------------

    private void tickWork(ServerLevel level, JobHost bed, JobConfig config, JobTask task) {
        long now = level.getGameTime();
        clearsLeaves = config.job() == Job.CHOP;
        skipped.values().removeIf(until -> until < now);

        if (target == null) {
            if (tickLoot(level, bed, config, task, now)) return;
            if (retryIn-- > 0) return;
            if (!exitChecked && bed.hasHome() && !checkExit(level, bed, config, task)) return;
            JobTask.Pick pick = task.pick(level, goblin, bed, config, skipped.keySet());
            if (pick.target() == null) {
                boolean up = Climber.isUp(level, goblin);
                if (pick.verdict() == Mining.Verdict.NEEDS_TOOL) {
                    retryIn = PICK_RETRY_TICKS;
                    bed.setStatus(GoblinBedBlockEntity.Status.BLOCKED);
                    say(level, GoblinSpeech.NO_TOOL);
                } else if (pick == JobTask.Pick.RETRY) {
                    // only blocks the goblin could not get to are left for now; they come back after the skip time
                    if (up) {
                        goblin.climber().descend(level);
                        return;
                    }
                    retryIn = ENDLESS_RETRY_TICKS;
                } else if (task.endless()) {
                    bed.setStatus(GoblinBedBlockEntity.Status.IDLE);
                    if (up) {
                        goblin.climber().descend(level); // every tick until down, then rest between scans
                        return;
                    }
                    retryIn = ENDLESS_RETRY_TICKS;
                    if (goblin.hasStorageItems()) startDeposit(level);
                } else if (up) {
                    goblin.climber().descend(level); // come down before calling it a day
                } else if (!goblin.onGround() || dropsStillFalling(now)) {
                    // mid-jump or falling: decide once it has landed (it may land on its column); the last block's
                    // drops are picked up once they have landed, before calling it a day
                    return;
                } else {
                    bed.setStatus(GoblinBedBlockEntity.Status.IDLE);
                    GoblinSpeech.say(level, goblin, GoblinSpeech.random(goblin.getRandom(), GoblinSpeech.DONE), GoblinSounds.YES);
                    task.onDone(level, bed);
                    bed.setJob(bed.afterOrder(config));
                    if (goblin.hasStorageItems()) startDeposit(level);
                }
                return;
            }
            target = pick.target();
            placeState = pick.place();
            progress = 0.0f;
            stuckTicks = 0;
            stuckStrikes = 0;
            bed.setStatus(GoblinBedBlockEntity.Status.WORKING);
        }

        if (placeState != null) {
            tickPlace(level, bed, config, task, now);
            return;
        }

        BlockState state = level.getBlockState(target);
        if (Mining.verdict(level, target, state, goblin) != Mining.Verdict.OK) {
            clearTarget(level);
            return;
        }
        int toolSlot = Mining.bestToolSlot(goblin, state);
        ItemStack tool = toolSlot < 0 ? ItemStack.EMPTY : goblin.getInventory().getItem(toolSlot);
        goblin.setItemSlot(EquipmentSlot.MAINHAND, tool.copy());

        Vec3 center = Vec3.atCenterOf(target);
        goblin.getLookControl().setLookAt(center);
        double besideX = center.x - goblin.getX(), besideZ = center.z - goblin.getZ();
        BlockPos feetPos = goblin.blockPosition();
        if (center.y - goblin.getEyeY() > 0.5 && besideX * besideX + besideZ * besideZ <= CLIMB_BESIDE_SQ
                && climbColumn != null && feetPos.getX() == climbColumn.getX() && feetPos.getZ() == climbColumn.getZ()
                && level.getBlockState(feetPos).is(GoblinLabour.GOBLIN_SCAFFOLD)) {
            // half-way up a scaffold block of its column beside the target: finish the climb onto it before swinging,
            // or the goblin bobs at the edge of its reach (rise, in reach, stop jumping, fall, out of reach, ...). On
            // any other column it has to come down instead (moveTowards), or it climbs one it is being sent down.
            progress = 0.0f;
            level.destroyBlockProgress(goblin.getId(), target, -1);
            goblin.getNavigation().stop();
            goblin.setShiftKeyDown(false);
            goblin.getJumpControl().jump();
            return;
        }
        // in reach is not enough: the goblin has to see the block (no reaching through stairs or round corners)
        if (goblin.getEyePosition().distanceToSqr(center) > REACH_SQ || !Mining.canSee(level, goblin.getEyePosition(), target)) {
            progress = 0.0f;
            level.destroyBlockProgress(goblin.getId(), target, -1);
            if (!moveTowards(level, target, center, now)) clearTarget(level);
            return;
        }

        goblin.getNavigation().stop();
        goblin.setShiftKeyDown(false);
        goblin.swing(InteractionHand.MAIN_HAND);
        progress += Mining.progressPerTick(level, target, state, goblin, tool);
        if (progress < 1.0f) {
            level.destroyBlockProgress(goblin.getId(), target, (int) (progress * 10.0f));
            return;
        }
        level.destroyBlockProgress(goblin.getId(), target, -1);
        BlockPos broken = target;
        lastBrokenBlock = state.getBlock();
        drops.addAll(Mining.harvest(level, broken, state, goblin, tool));
        if (breaks.size() >= BREAK_HISTORY) breaks.removeFirst();
        breaks.addLast(broken.toShortString().replace(" ", "") + "@" + goblin.blockPosition().toShortString().replace(" ", ""));
        chatter(level, config.job());
        task.afterBreak(level, goblin, bed, config, broken);
        if (!Climber.isUp(level, goblin)) Mining.placeTorchIfDark(level, goblin);
        target = null;
        progress = 0.0f;
    }

    /** Walks to a stair position, swings a few times, then sets the block (unless something got in the way). */
    private void tickPlace(ServerLevel level, JobHost bed, JobConfig config, JobTask task, long now) {
        BlockState current = level.getBlockState(target);
        if (!current.canBeReplaced() || placeState == null) {
            clearTarget(level);
            return;
        }
        Vec3 center = Vec3.atCenterOf(target);
        goblin.getLookControl().setLookAt(center);
        AABB blockBox = new AABB(target);
        if (!placeState.getCollisionShape(level, target).isEmpty() && goblin.getBoundingBox().intersects(blockBox)) {
            // standing in the spot (e.g. after picking up a drop there): step into the middle of a free block beside it
            progress = 0.0f;
            BlockPos aside = asideSpot(level, target);
            if (aside == null || !approachSpot(level, aside)) {
                giveUp(target, now);
                clearTarget(level);
            }
            return;
        }
        if (goblin.getEyePosition().distanceToSqr(center) > REACH_SQ) {
            progress = 0.0f;
            if (!moveTowards(level, target, center, now)) clearTarget(level);
            return;
        }
        goblin.getNavigation().stop();
        goblin.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(placeState.getBlock()));
        goblin.swing(InteractionHand.MAIN_HAND);
        progress += PLACE_PROGRESS_PER_TICK;
        if (progress < 1.0f) return;
        level.setBlock(target, placeState, 3);
        level.playSound(null, target, placeState.getSoundType().getPlaceSound(), SoundSource.BLOCKS, 1.0f, 0.8f);
        task.afterPlace(level, goblin, bed, config, target, placeState);
        target = null;
        placeState = null;
        progress = 0.0f;
    }

    /**
     * Climb, come down or walk, whatever brings the goblin closer. A target well above the goblin is reached from a
     * scaffold column: one column is chosen per target (see {@link #chooseColumn}), the goblin walks to its foot and
     * climbs until the target is in reach. Scaffold is never stacked inside a home or under a blocked ceiling.
     * Returns false when the goblin gave up on the target.
     */
    private boolean moveTowards(ServerLevel level, BlockPos targetPos, Vec3 center, long now) {
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
        return approach(level, center) || clearWayOrGiveUp(level, targetPos, center, now);
    }

    /**
     * Like {@link #approach}, but the last steps go straight to the middle of the block: path following stops about
     * a block short of its goal, which can leave the goblin just out of reach of what it came for.
     */
    private boolean approachSpot(ServerLevel level, BlockPos spot) {
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
    private BlockPos asideSpot(ServerLevel level, BlockPos pos) {
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dz == 0) continue;
                    BlockPos spot = pos.offset(dx, dy, dz);
                    if (!standable(level, spot)) continue;
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
        Set<BlockPos> goals = new java.util.HashSet<>(standCandidates(level, targetPos, center));
        if (center.y - goblin.getEyeY() > CLIMB_MIN_DY) goals.addAll(columnFeet(level, targetPos));
        BlockPos leaf = LeafPath.firstLeaf(level, goblin.blockPosition(), goals, skipped.keySet());
        if (leaf == null || Mining.verdict(level, leaf, level.getBlockState(leaf), goblin) != Mining.Verdict.OK) {
            return giveUp(targetPos, now);
        }
        target = leaf;
        placeState = null;
        progress = 0.0f;
        climbFor = null;
        climbColumn = null;
        standFor = null;
        standSpot = null;
        headwayPoint = null;
        stuckTicks = 0;
        branch = "clear-leaves";
        return true;
    }

    private boolean giveUp(BlockPos targetPos, long now) {
        skipped.put(targetPos, now + SKIP_TICKS);
        climbFor = null;
        climbColumn = null;
        standFor = null;
        standSpot = null;
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
        if (targetPos.equals(standFor) && (standSpot == null || standableOnGround(level, standSpot))) return standSpot;
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
        List<Candidate> candidates = new java.util.ArrayList<>();
        for (int dy = -3; dy <= 1; dy++) {
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    BlockPos spot = targetPos.offset(dx, dy, dz);
                    if (!standableOnGround(level, spot)) continue;
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
        List<BlockPos> feet = new java.util.ArrayList<>();
        for (int dx = -COLUMN_RANGE; dx <= COLUMN_RANGE; dx++) {
            for (int dz = -COLUMN_RANGE; dz <= COLUMN_RANGE; dz++) {
                if (dx == 0 && dz == 0) continue;
                BlockPos foot = columnFoot(level, target.getX() + dx, target.getY() - 1, target.getZ() + dz);
                if (foot != null) feet.add(foot);
            }
        }
        return feet;
    }

    /** Room for the goblin at {@code pos} on solid ground; leaves, logs (a tree) and scaffold do not count as ground here. */
    private static boolean standableOnGround(ServerLevel level, BlockPos pos) {
        BlockState ground = level.getBlockState(pos.below());
        return standable(level, pos) && !ground.is(BlockTags.LEAVES) && !ground.is(BlockTags.LOGS) && !Climber.isScaffold(ground)
                && ground.getFluidState().isEmpty();
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
        List<Candidate> candidates = new java.util.ArrayList<>();
        BlockPos feet = goblin.blockPosition();
        boolean up = Climber.isUp(level, goblin);
        for (int dx = -COLUMN_RANGE; dx <= COLUMN_RANGE; dx++) {
            for (int dz = -COLUMN_RANGE; dz <= COLUMN_RANGE; dz++) {
                if (dx == 0 && dz == 0) continue;
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
            return ((goblinlabour.block.GoblinScaffoldBlock) GoblinLabour.GOBLIN_SCAFFOLD).placementState(level, foot) != null
                    ? new Foot(foot.immutable(), "ground" + on) : new Foot(null, "scaffold would not hold" + on);
        }
        return new Foot(null, "too tall");
    }

    /**
     * Dev (the "columns" command): every column {@link #chooseColumn} looks at for a target, with its foot or why it
     * is out, whether the goblin can walk to the foot and whether the target is in sight from the top.
     */
    public String columnReport(ServerLevel level, BlockPos target) {
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
    private boolean approach(ServerLevel level, Vec3 point) {
        return approach(level, point, true);
    }

    private boolean approach(ServerLevel level, Vec3 point, boolean blink) {
        PathNavigation navigation = goblin.getNavigation();
        goblin.setShiftKeyDown(false);
        if (goblin.tickCount % 10 == 0 || (navigation.isDone() && goblin.tickCount % 5 == 0)) navigation.moveTo(point.x, point.y, point.z, 1.0);
        return blink ? headway(level, point) : headwayNoBlink(point);
    }

    /** Like {@link #headway} without blinking: false as soon as the goblin made no headway for a while. */
    private boolean headwayNoBlink(Vec3 point) {
        double distance = goblin.position().distanceTo(point);
        if (headwayPoint == null || headwayPoint.distanceToSqr(point) > 0.25) {
            headwayPoint = point;
            headwayBest = distance;
            stuckTicks = 0;
        }
        if (distance < headwayBest - 0.3) {
            headwayBest = distance;
            stuckTicks = 0;
            return true;
        }
        return ++stuckTicks <= STUCK_TICKS;
    }

    /**
     * Counts ticks in which the goblin got no closer to {@code point} (jittering at the end of a path or bobbing in a
     * scaffold block is no progress); blinks next to the point now and then, false after three tries.
     */
    private boolean headway(ServerLevel level, Vec3 point) {
        double distance = goblin.position().distanceTo(point);
        if (!branch.endsWith("-headway")) branch += "-headway"; // once: it runs every tick, the string must not grow
        if (headwayPoint == null || headwayPoint.distanceToSqr(point) > 0.25) {
            headwayPoint = point;
            headwayBest = distance;
            stuckTicks = 0;
        }
        if (distance < headwayBest - 0.3) {
            headwayBest = distance;
            stuckTicks = 0;
        } else if (++stuckTicks > STUCK_TICKS) {
            stuckTicks = 0;
            if (++stuckStrikes > 2) return false;
            goblin.blinkTo(level, BlockPos.containing(point));
            headwayBest = goblin.position().distanceTo(point);
        }
        return true;
    }

    // ---- loot ----------------------------------------------------------------------------------------------------

    /**
     * Between two blocks, while on the ground: walk over to the nearest drop of its own (or loose item the job wants,
     * see {@link JobTask#lootArea}) and pick it up. Returns true while busy with that. Up on a column the goblin goes
     * on working; the drops wait until it comes down.
     */
    private boolean tickLoot(ServerLevel level, JobHost bed, JobConfig config, JobTask task, long now) {
        skippedItems.values().removeIf(until -> until < now);
        if (itemTarget != null && (!itemTarget.isAlive() || !goblin.canStore(itemTarget.getItem()))) itemTarget = null;
        if (itemTarget == null) {
            if (goblin.isStorageFull() || Climber.isUp(level, goblin)) return false;
            itemTarget = nextLoot(level, bed, config, task, now);
            if (itemTarget == null) return false;
            stuckTicks = 0;
            headwayPoint = null;
        }
        branch = "loot";
        Vec3 point = itemTarget.position();
        goblin.getLookControl().setLookAt(point);
        if (goblin.distanceToSqr(point) <= PICKUP_SQ) {
            goblin.getNavigation().stop();
            if (itemTarget.hasPickUpDelay()) return true; // still popping out of the block
            goblin.pickUp(itemTarget);
            itemTarget = null;
            return true;
        }
        if (!approach(level, point, false)) {
            skippedItems.put(itemTarget.getId(), now + SKIP_TICKS);
            itemTarget = null;
        }
        return true;
    }

    /** Own fresh drops (up to five seconds old) that the goblin could still fetch once they land. */
    private boolean dropsStillFalling(long now) {
        drops.removeIf(item -> !item.isAlive());
        for (ItemEntity item : drops) {
            if (item.getAge() < FRESH_DROP_TICKS && !skippedItems.containsKey(item.getId()) && goblin.canStore(item.getItem())
                    && goblin.distanceToSqr(item) < LOOT_RANGE_SQ) return true;
        }
        return false;
    }

    @Nullable
    private ItemEntity nextLoot(ServerLevel level, JobHost bed, JobConfig config, JobTask task, long now) {
        drops.removeIf(item -> !item.isAlive());
        List<ItemEntity> candidates = new ArrayList<>(drops);
        AABB area = task.lootArea(bed, config);
        if (area != null) candidates.addAll(level.getEntitiesOfClass(ItemEntity.class, area, item -> item.isAlive() && task.wantsLoot(item.getItem())));
        ItemEntity best = null;
        double bestDist = LOOT_RANGE_SQ;
        for (ItemEntity item : candidates) {
            if (skippedItems.containsKey(item.getId()) || !goblin.canStore(item.getItem())) continue;
            if (!item.onGround()) continue; // still falling: fetched once it has landed
            if (HomeRegistry.isProtected(level, item.blockPosition())) continue; // loot inside a home belongs to the collectors
            double d = goblin.distanceToSqr(item);
            if (d < bestDist) {
                bestDist = d;
                best = item;
            }
        }
        // drops on top of a scaffold column or on leaves cannot be walked to: leave them (and ask again next tick)
        if (best != null && goblin.distanceToSqr(best) > PICKUP_SQ && !reachable(goblin.getNavigation(), best.blockPosition())) {
            skippedItems.put(best.getId(), now + SKIP_TICKS);
            return null;
        }
        return best;
    }

    // ---- collect -------------------------------------------------------------------------------------------------

    /** Walks to the nearest loose item in the radius and picks it up; unloads when there is nothing left to fetch. */
    private void tickCollect(ServerLevel level, JobHost bed, JobConfig config, CollectJob job) {
        long now = level.getGameTime();
        skippedItems.values().removeIf(until -> until < now);
        if (itemTarget != null && (!itemTarget.isAlive() || !goblin.canStore(itemTarget.getItem()))) itemTarget = null;

        if (itemTarget == null) {
            if (retryIn-- > 0) return;
            if (!exitChecked && !checkExit(level, bed, config, job)) return;
            itemTarget = job.findItem(level, goblin, bed, config, skippedItems.keySet());
            if (itemTarget == null) {
                bed.setStatus(GoblinBedBlockEntity.Status.IDLE);
                if (Climber.isUp(level, goblin)) {
                    goblin.climber().descend(level);
                    return;
                }
                retryIn = COLLECT_RETRY_TICKS;
                if (goblin.hasStorageItems()) startDeposit(level);
                return;
            }
            stuckTicks = 0;
            stuckStrikes = 0;
            bed.setStatus(GoblinBedBlockEntity.Status.WORKING);
        }

        Vec3 point = itemTarget.position();
        goblin.getLookControl().setLookAt(point);
        if (goblin.distanceToSqr(point) <= PICKUP_SQ) {
            goblin.getNavigation().stop();
            goblin.pickUp(itemTarget);
            chatter(level, Job.COLLECT);
            itemTarget = null;
            return;
        }
        if (Climber.isUp(level, goblin)) {
            goblin.climber().descend(level);
            return;
        }
        if (!approach(level, point)) {
            skippedItems.put(itemTarget.getId(), now + SKIP_TICKS);
            itemTarget = null;
        }
    }

    // ---- exit check ----------------------------------------------------------------------------------------------

    /**
     * Before the first block of a job: can the goblin get out of its home at all? Samples standable spots just
     * outside the flat in eight directions and a few heights; one reachable spot is enough.
     */
    private boolean checkExit(ServerLevel level, JobHost bed, JobConfig config, JobTask task) {
        BoundingBox flat = HomeRegistry.flatBox(level, bed.getBlockPos());
        if (!flat.isInside(goblin.blockPosition())) {
            exitChecked = true;
            exitFailures = 0;
            return true;
        }
        PathNavigation navigation = goblin.getNavigation();
        BlockPos entry = task.entryPoint(bed, config);
        if (entry != null && reachable(navigation, entry)) {
            exitChecked = true;
            exitFailures = 0;
            return true;
        }
        int cx = (flat.minX() + flat.maxX()) / 2;
        int cz = (flat.minZ() + flat.maxZ()) / 2;
        int rx = (flat.maxX() - flat.minX()) / 2 + 1;
        int rz = (flat.maxZ() - flat.minZ()) / 2 + 1;
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}};
        int[] dys = {0, 1, -1, 2, -2, 3, -3};
        for (int[] d : dirs) {
            for (int dy : dys) {
                BlockPos spot = new BlockPos(cx + d[0] * rx, bed.getBlockPos().getY() + dy, cz + d[1] * rz);
                if (!standable(level, spot)) continue;
                if (reachable(navigation, spot)) {
                    exitChecked = true;
                    exitFailures = 0;
                    return true;
                }
                break; // one standable spot per direction is enough to test
            }
        }
        retryIn = NO_EXIT_RETRY_TICKS;
        if (++exitFailures >= EXIT_FAILURES_TO_REPORT) {
            bed.setStatus(GoblinBedBlockEntity.Status.NO_EXIT);
            say(level, GoblinSpeech.NO_EXIT);
        }
        return false;
    }

    private static boolean standable(ServerLevel level, BlockPos pos) {
        return level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()
                && level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty()
                && !level.getBlockState(pos.below()).getCollisionShape(level, pos.below()).isEmpty();
    }

    private static boolean reachable(PathNavigation navigation, BlockPos pos) {
        Path path = navigation.createPath(pos, 1);
        return path != null && path.canReach();
    }

    // ---- deposit -------------------------------------------------------------------------------------------------

    private void startDeposit(ServerLevel level) {
        stop(level);
        phase = Phase.DEPOSIT;
        chestTarget = null;
    }

    private void tickDeposit(ServerLevel level, JobHost bed) {
        if (chestTarget == null) {
            chestTarget = findChest(level, bed);
            if (chestTarget == null) {
                if (goblin.isStorageFull()) {
                    phase = Phase.WAIT_ROOM;
                    waitUntil = level.getGameTime() + WAIT_ROOM_TICKS;
                    bed.setStatus(GoblinBedBlockEntity.Status.WAITING_FULL);
                    say(level, GoblinSpeech.INVENTORY_FULL);
                } else {
                    phase = Phase.WORK;
                }
                return;
            }
            stuckTicks = 0;
            stuckStrikes = 0;
        }
        Vec3 center = Vec3.atCenterOf(chestTarget);
        goblin.getLookControl().setLookAt(center);
        if (goblin.getEyePosition().distanceToSqr(center) > CHEST_REACH_SQ) {
            if (!approach(level, center)) {
                skipped.put(chestTarget, level.getGameTime() + SKIP_TICKS);
                chestTarget = null;
            }
            return;
        }
        goblin.getNavigation().stop();
        if (openChest == null) {
            // lift the lid (the jaw opens), rummage for a moment, then unload
            openChest(level, chestTarget);
            chestCloseAt = level.getGameTime() + CHEST_OPEN_TICKS;
            goblin.swing(InteractionHand.MAIN_HAND);
            return;
        }
        if (level.getGameTime() < chestCloseAt) return;
        goblin.swing(InteractionHand.MAIN_HAND);
        unloadInto(level, chestTarget, bed);
        closeChest(level);
        chestTarget = null;
    }

    private void openChest(ServerLevel level, BlockPos pos) {
        openChest = pos;
        if (level.getBlockEntity(pos) instanceof ChestBlockEntity chest) {
            goblin.setOpenChest(pos);
            chest.startOpen(goblin);
        }
    }

    private void closeChest(ServerLevel level) {
        if (openChest == null) return;
        if (level.getBlockEntity(openChest) instanceof ChestBlockEntity chest) chest.stopOpen(goblin);
        goblin.setOpenChest(null);
        openChest = null;
    }

    /** Nearest goblin chest of the flat that accepts at least one of the carried stacks. */
    @Nullable
    private BlockPos findChest(ServerLevel level, JobHost bed) {
        List<BlockPos> chests = HomeRegistry.goblinChests(level, bed.getBlockPos());
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        for (BlockPos chest : chests) {
            if (skipped.containsKey(chest)) continue;
            Storage<ItemVariant> storage = ItemStorage.SIDED.find(level, chest, Direction.UP);
            if (storage == null || !acceptsAnything(storage, bed)) continue;
            double d = goblin.distanceToSqr(Vec3.atCenterOf(chest));
            if (d < bestDist) {
                bestDist = d;
                best = chest;
            }
        }
        return best;
    }

    private boolean acceptsAnything(Storage<ItemVariant> storage, JobHost bed) {
        SimpleContainer inv = goblin.getInventory();
        int[] unload = unloadCounts(bed);
        for (int i = GoblinEntity.HOTBAR_SIZE; i < GoblinEntity.INVENTORY_SIZE; i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.isEmpty() || unload[i] <= 0) continue;
            if (StorageUtil.simulateInsert(storage, ItemVariant.of(stack), unload[i], null) > 0) return true;
        }
        return false;
    }

    /** Per slot, how many items go into the chest: all, minus what the job keeps (a lumberjack's saplings). */
    private int[] unloadCounts(JobHost bed) {
        SimpleContainer inv = goblin.getInventory();
        int[] counts = new int[GoblinEntity.INVENTORY_SIZE];
        JobTask task = JobTask.of(bed.getJob().job());
        Map<net.minecraft.world.item.Item, Integer> kept = new HashMap<>();
        for (int i = GoblinEntity.HOTBAR_SIZE; i < GoblinEntity.INVENTORY_SIZE; i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.isEmpty()) continue;
            int keep = task == null ? 0 : Math.max(0, task.keepOnUnload(bed.getJob(), stack) - kept.getOrDefault(stack.getItem(), 0));
            keep = Math.min(keep, stack.getCount());
            kept.merge(stack.getItem(), keep, Integer::sum);
            counts[i] = stack.getCount() - keep;
        }
        return counts;
    }

    private void unloadInto(ServerLevel level, BlockPos chest, JobHost bed) {
        Storage<ItemVariant> storage = ItemStorage.SIDED.find(level, chest, Direction.UP);
        if (storage == null) return;
        SimpleContainer inv = goblin.getInventory();
        int[] unload = unloadCounts(bed);
        try (Transaction tx = Transaction.openOuter()) {
            for (int i = GoblinEntity.HOTBAR_SIZE; i < GoblinEntity.INVENTORY_SIZE; i++) {
                ItemStack stack = inv.getItem(i);
                if (stack.isEmpty() || unload[i] <= 0) continue;
                long inserted = storage.insert(ItemVariant.of(stack), unload[i], tx);
                if (inserted > 0) {
                    stack.shrink((int) inserted);
                    if (stack.isEmpty()) inv.setItem(i, ItemStack.EMPTY);
                }
            }
            tx.commit();
        }
        inv.setChanged();
    }

    /** A complaint, at most once per cooldown per line. */
    private void say(ServerLevel level, String line) {
        long now = level.getGameTime();
        Long last = lastSaid.get(line);
        if (last != null && now - last < SPEECH_COOLDOWN) return;
        lastSaid.put(line, now);
        GoblinSpeech.say(level, goblin, line, GoblinSounds.NO);
    }

    /** Now and then a line about the work, with a voice sound. */
    private void chatter(ServerLevel level, Job job) {
        long now = level.getGameTime();
        RandomSource random = goblin.getRandom();
        if (nextChatter < 0) nextChatter = now + CHATTER_FIRST_MIN + random.nextInt(CHATTER_FIRST_SPAN);
        if (now < nextChatter) return;
        nextChatter = now + CHATTER_MIN + random.nextInt(CHATTER_SPAN);
        GoblinSpeech.say(level, goblin, GoblinSpeech.workingLine(random, job), GoblinSounds.CHATTER);
    }
}
