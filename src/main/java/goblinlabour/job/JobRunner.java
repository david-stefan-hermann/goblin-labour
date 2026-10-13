package goblinlabour.job;

import goblinlabour.GoblinLabour;
import goblinlabour.GoblinSounds;
import goblinlabour.GoblinSpeech;
import goblinlabour.block.GoblinBedBlockEntity;
import goblinlabour.entity.GoblinEntity;
import goblinlabour.home.HomeRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Per-goblin work loop (Minions Remastered's goalTick, one goblin at a time): pick a block, walk to where it is in
 * reach and in sight, swing until it breaks (or until the stair step is placed), pick up the drops, repeat. Getting
 * there (stand spots, scaffold columns, leaves in the way) is {@link Reach}'s business, unloading into the flat's goblin
 * chests when the storage is full {@link Unloader}'s (waiting in bed when nothing fits); the runner also picks up loose
 * items for the collect job and checks that there is a way out of the home. Tool choice and the rules live in
 * {@link Mining}; what comes next is the job's business.
 */
public final class JobRunner {
    private static final double PICKUP_SQ = 1.5 * 1.5;
    /** Own drops further away than this are left for later (e.g. while unloading at home). */
    private static final double LOOT_RANGE_SQ = 32.0 * 32.0;
    private static final int BREAK_HISTORY = 16;
    /** A job is not done while own drops younger than this are still on their way down. */
    private static final int FRESH_DROP_TICKS = 100;
    /** Horizontal distance (squared) from a target within which a goblin in scaffold is on a column meant for it. */
    private static final double CLIMB_BESIDE_SQ = 3.0 * 3.0;
    /** The exit check must fail this often in a row (100 ticks apart) before the goblin complains. */
    private static final int EXIT_FAILURES_TO_REPORT = 2;
    private static final float PLACE_PROGRESS_PER_TICK = 0.1f;
    /** A use (milking, shearing, picking) takes a second of swinging, from this close (eyes to the point). */
    private static final float USE_PROGRESS_PER_TICK = 0.05f;
    private static final double USE_REACH_SQ = 2.75 * 2.75;
    private static final int PICK_RETRY_TICKS = 20;
    private static final int ENDLESS_RETRY_TICKS = 100;
    private static final int COLLECT_RETRY_TICKS = 40;
    private static final int MAX_COLLECT_PATH_CHECKS = 3;
    /** A target held this long without a single tick of breaking, placing or using is skipped (see tickWork). */
    private static final int TARGET_PATIENCE = 1200;
    /** Own drops further below than this wait until the job has nothing else to do (a shaft dug upwards). */
    private static final double LOOT_BELOW = 3.0;
    private static final int WAIT_ROOM_TICKS = 1200;
    /** A goblin heading home because it was full turns back to work once this share of its storage is free again. */
    private static final double DEPOSIT_UNNEEDED_FREE = 1.0 / 3.0;
    private static final int NO_EXIT_RETRY_TICKS = 100;
    private static final int SPEECH_COOLDOWN = 1200;
    /** First working line 30-90 s after the first block, then one every 2-5 minutes. */
    private static final int CHATTER_FIRST_MIN = 600, CHATTER_FIRST_SPAN = 1200;
    private static final int CHATTER_MIN = 2400, CHATTER_SPAN = 3600;

    private enum Phase { WORK, DEPOSIT, WAIT_ROOM }

    private final GoblinEntity goblin;
    private final Map<BlockPos, Long> skipped = new HashMap<>();
    private final Reach reach;
    private final Unloader unloader;
    private Phase phase = Phase.WORK;
    private boolean depositBecauseFull;
    @Nullable private BlockPos target;
    @Nullable private BlockState placeState;
    /** Set instead of a block to break: something to do in reach (see {@link JobTask.Use}). */
    @Nullable private JobTask.Use use;
    @Nullable private ItemEntity itemTarget;
    /** What the goblin's own breaking dropped; it picks these up between two blocks. */
    private final List<ItemEntity> drops = new ArrayList<>();
    /** Debug: the last broken blocks and where the goblin stood ("block@feet"). */
    private final ArrayDeque<String> breaks = new ArrayDeque<>();
    private float progress;
    /** Game time the current target was picked or last made progress (break, place or use). */
    private long targetProgressAt;
    private int retryIn;
    /** Debug: runner ticks so far. */
    private int ticks;
    private long waitUntil;
    /** The job reported done while own drops still lay far below: fetching those before calling it a day. */
    private boolean collectingLeftovers;
    private boolean exitChecked;
    private int exitFailures;
    @Nullable private Block lastBrokenBlock;
    @Nullable private BlockState lastBrokenState;
    private final Map<Integer, Long> skippedItems = new HashMap<>();
    private final Map<String, Long> lastSaid = new HashMap<>();
    private long nextChatter = -1;

    public JobRunner(GoblinEntity goblin) {
        this.goblin = goblin;
        this.reach = new Reach(goblin, skipped);
        this.unloader = new Unloader(goblin, reach, skipped);
    }

    @Nullable
    public Block lastBrokenBlock() {
        return lastBrokenBlock;
    }

    /** The state of the block broken last (a cocoa pod's facing, for replanting it). */
    @Nullable
    public BlockState lastBrokenState() {
        return lastBrokenState;
    }

    /** The block the goblin is working on (breaking or placing), or null. */
    @Nullable
    public BlockPos target() {
        return target;
    }

    /** Debug summary for the status command. */
    public String debug() {
        return "phase=" + phase + " target=" + (target == null ? "-" : target.toShortString()) + (placeState != null ? "(place)" : "") + (use != null ? "(use)" : "")
                + (itemTarget != null ? " item=" + itemTarget.getItem().getItem() : "")
                + " progress=" + String.format(Locale.ROOT, "%.2f", progress) + reach.debug()
                + " up=" + Climber.isUp(goblin.level(), goblin) + " skipped=" + skipped.size() + " retryIn=" + retryIn
                + " ticks=" + ticks + " branch=" + reach.branch()
                + " sneak=" + goblin.isShiftKeyDown() + String.format(Locale.ROOT, " y=%.2f vy=%.2f", goblin.getY(), goblin.getDeltaMovement().y)
                + reach.debugSpots()
                + " drops=" + drops.stream().filter(ItemEntity::isAlive).count()
                + (goblin.level() instanceof ServerLevel level ? " " + ChopJob.debug(level, goblin) : "")
                + " breaks=" + String.join(";", breaks);
    }

    /** Dev (the "columns" command): the scaffold columns considered for a high target. */
    public String columnReport(ServerLevel level, BlockPos target) {
        return reach.columnReport(level, target);
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
            unloader.restart();
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
        unloader.closeChest(level);
        clearTarget(level);
        itemTarget = null;
        goblin.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        goblin.getNavigation().stop();
        // no snap to the foot of the column: whoever takes over next sneaks down first (RestGoal, the runner itself,
        // Climber.comeDownFirst), a snap looked like a teleport
        goblin.setShiftKeyDown(false);
    }

    private void clearTarget(ServerLevel level) {
        if (target != null && placeState == null && use == null) level.destroyBlockProgress(goblin.getId(), target, -1);
        target = null;
        placeState = null;
        use = null;
        progress = 0.0f;
        reach.forgetTarget();
    }

    /** One tick. {@code task} may be null when only a deposit run is pending. */
    public void tick(ServerLevel level, JobHost bed, JobConfig config, @Nullable JobTask task) {
        ticks++;
        tickPhase(level, bed, config, task);
        if (TRACE != null && TRACE.equals(goblin.goblinName())) trace(level);
    }

    /** Dev only: GOBLINLABOUR_TRACE=<name> logs every tick that goblin spends up, in the air or sneaking. */
    @Nullable private static final String TRACE = FabricLoader.getInstance().isDevelopmentEnvironment()
            ? System.getenv("GOBLINLABOUR_TRACE") : null;

    private void trace(ServerLevel level) {
        BlockPos column = Climber.columnUnder(level, goblin);
        if (column == null && goblin.onGround() && !goblin.isShiftKeyDown()) return;
        Vec3 v = goblin.getDeltaMovement();
        BlockPos feet = goblin.blockPosition();
        BlockPos climbColumn = reach.climbColumn();
        GoblinLabour.LOGGER.info(String.format(Locale.ROOT,
                "Trace %d " + phase + " retryIn=" + retryIn + " item=" + (itemTarget != null) + " exit=" + exitChecked
                + " pos=%.3f,%.3f,%.3f v=%.3f,%.3f,%.3f ground=%s sneak=%s yaw=%.0f zza=%.3f xxa=%.3f want=%s,%.3f,%.3f col=%s branch=%s target=%s climb=%s feet=%s below=%s fall=%.1f",
                ticks, goblin.getX(), goblin.getY(), goblin.getZ(), v.x, v.y, v.z, goblin.onGround(), goblin.isShiftKeyDown(),
                goblin.getYRot(), goblin.zza, goblin.xxa, goblin.getMoveControl().hasWanted(), goblin.getMoveControl().getWantedX(),
                goblin.getMoveControl().getWantedZ(),
                column == null ? "-" : column.toShortString(), reach.branch(), target == null ? "-" : target.toShortString(),
                climbColumn == null ? "-" : climbColumn.toShortString(),
                BuiltInRegistries.BLOCK.getKey(level.getBlockState(feet).getBlock()).getPath(),
                BuiltInRegistries.BLOCK.getKey(level.getBlockState(feet.below()).getBlock()).getPath(), goblin.fallDistance));
    }

    private void tickPhase(ServerLevel level, JobHost bed, JobConfig config, @Nullable JobTask task) {
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
                    if (bed.hasHome()) startDeposit(level, true); // a ring crew goblin waits for room in its ring instead
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
        reach.setClearsLeaves(config.job() == Job.CHOP);
        skipped.values().removeIf(until -> until < now);

        if (target == null) {
            if (tickLoot(level, bed, config, task, now)) return;
            if (retryIn-- > 0) {
                if (goblin.isOnBed(level)) Reach.stepOffBed(level, goblin); // waiting for work is no reason to stand on a bed
                return;
            }
            if (!exitChecked && bed.hasHome() && !checkExit(level, bed, config, task)) return;
            JobTask.Pick pick = task.pick(level, goblin, bed, config, skipped.keySet());
            if (pick.target() == null) {
                boolean up = Climber.isUp(level, goblin);
                reach.setBranch("none-" + switch (pick.kind()) {
                    case RETRY -> "retry";
                    case DONE -> "done";
                    default -> pick.verdict();
                } + (up ? "-up" : ""));
                if (pick.verdict() == Mining.Verdict.NEEDS_TOOL) {
                    retryIn = PICK_RETRY_TICKS;
                    bed.setStatus(GoblinBedBlockEntity.Status.BLOCKED);
                    say(level, pick.kind() == JobTask.Pick.Kind.NEEDS_HOE ? GoblinSpeech.NO_HOE : GoblinSpeech.NO_TOOL);
                } else if (pick.kind() == JobTask.Pick.Kind.RETRY) {
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
                    if (goblin.hasStorageItems()) startDeposit(level, false);
                } else if (up) {
                    goblin.climber().descend(level); // come down before calling it a day
                } else if (!goblin.onGround() || dropsStillFalling(now)) {
                    // mid-jump or falling: decide once it has landed (it may land on its column); the last block's
                    // drops are picked up once they have landed, before calling it a day
                    return;
                } else if (!collectingLeftovers && ownDropsLeft()) {
                    collectingLeftovers = true; // the drops that fell far down the shaft: fetch them now (tickLoot)
                    return;
                } else {
                    bed.setStatus(GoblinBedBlockEntity.Status.IDLE);
                    GoblinSpeech.say(level, goblin, GoblinSpeech.random(goblin.getRandom(), GoblinSpeech.DONE), GoblinSounds.YES);
                    task.onDone(level, bed);
                    bed.setJob(bed.afterOrder(config));
                    if (goblin.hasStorageItems()) startDeposit(level, false);
                }
                return;
            }
            target = pick.target();
            targetProgressAt = now;
            collectingLeftovers = false;
            placeState = pick.place();
            use = pick.use();
            progress = 0.0f;
            reach.resetStuck();
            bed.setStatus(GoblinBedBlockEntity.Status.WORKING);
        }

        if (now - targetProgressAt > TARGET_PATIENCE) {
            // a minute on one target without a single swing: caught between climbing and coming down, or a column
            // that never shows the block; skipped like a block it cannot get to, instead of trying forever
            reach.giveUp(target, now);
            clearTarget(level);
            reach.setBranch("watchdog");
            return;
        }
        if (placeState != null) {
            tickPlace(level, bed, config, task, now);
            return;
        }
        if (use != null) {
            tickUse(level, config, now);
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
        BlockPos climbColumn = reach.climbColumn();
        // still rising through the block: the target may already look in reach, but the climb is not finished
        boolean rising = !goblin.onGround() && goblin.getDeltaMovement().y > 0.0 && center.y > goblin.getY();
        if ((center.y - goblin.getEyeY() > 0.5 || rising) && besideX * besideX + besideZ * besideZ <= CLIMB_BESIDE_SQ
                && climbColumn != null && feetPos.getX() == climbColumn.getX() && feetPos.getZ() == climbColumn.getZ()
                && level.getBlockState(feetPos).is(GoblinLabour.GOBLIN_SCAFFOLD)) {
            // half-way up a scaffold block of its column beside the target: finish the climb onto it before swinging,
            // or the goblin bobs at the edge of its reach (rise, in reach, stop jumping, fall, out of reach, ...). On
            // any other column it has to come down instead (moveTowards), or it climbs one it is being sent down.
            // Climbing also keeps it in the middle of the column: off the middle its head catches on the target's block.
            progress = 0.0f;
            level.destroyBlockProgress(goblin.getId(), target, -1);
            goblin.climber().climb(level);
            return;
        }
        // in reach is not enough: the goblin has to see the block (no reaching through stairs or round corners)
        if (goblin.getEyePosition().distanceToSqr(center) > Reach.REACH_SQ || !Mining.canSee(level, goblin.getEyePosition(), target)) {
            progress = 0.0f;
            level.destroyBlockProgress(goblin.getId(), target, -1);
            moveTowards(level, center, now, task.standBounds(bed, config));
            return;
        }

        goblin.getNavigation().stop();
        goblin.setShiftKeyDown(false);
        goblin.swing(InteractionHand.MAIN_HAND);
        progress += Mining.progressPerTick(level, target, state, goblin, tool);
        targetProgressAt = now;
        if (progress < 1.0f) {
            level.destroyBlockProgress(goblin.getId(), target, (int) (progress * 10.0f));
            return;
        }
        level.destroyBlockProgress(goblin.getId(), target, -1);
        BlockPos broken = target;
        lastBrokenBlock = state.getBlock();
        lastBrokenState = state;
        drops.addAll(Mining.harvest(level, broken, state, goblin, tool));
        if (breaks.size() >= BREAK_HISTORY) breaks.removeFirst();
        breaks.addLast(broken.toShortString().replace(" ", "") + "@" + goblin.blockPosition().toShortString().replace(" ", ""));
        chatter(level, config.job());
        task.afterBreak(level, goblin, bed, config, broken);
        if (!Climber.isUp(level, goblin)) Mining.placeTorchIfDark(level, goblin);
        target = null;
        progress = 0.0f;
    }

    /**
     * {@link Reach#moveTowards} for the current target: gives the target up when the goblin cannot get there, or
     * switches to a leaf in the way.
     */
    private void moveTowards(ServerLevel level, Vec3 center, long now, @Nullable BoundingBox bounds) {
        if (!reach.moveTowards(level, target, center, now, bounds)) {
            clearTarget(level);
            return;
        }
        BlockPos leaf = reach.takeLeafInTheWay();
        if (leaf != null) {
            targetProgressAt = level.getGameTime();
            target = leaf;
            placeState = null;
            progress = 0.0f;
        }
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
            BlockPos aside = reach.asideSpot(level, target);
            if (aside == null || !reach.approachSpot(level, aside)) {
                reach.giveUp(target, now);
                clearTarget(level);
            }
            return;
        }
        if (goblin.getEyePosition().distanceToSqr(center) > Reach.REACH_SQ) {
            progress = 0.0f;
            moveTowards(level, center, now, task.standBounds(bed, config));
            return;
        }
        goblin.getNavigation().stop();
        goblin.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(placeState.getBlock()));
        goblin.swing(InteractionHand.MAIN_HAND);
        progress += PLACE_PROGRESS_PER_TICK;
        targetProgressAt = now;
        if (progress < 1.0f) return;
        level.setBlock(target, placeState, 3);
        level.playSound(null, target, placeState.getSoundType().getPlaceSound(), SoundSource.BLOCKS, 1.0f, 0.8f);
        task.afterPlace(level, goblin, bed, config, target, placeState);
        target = null;
        placeState = null;
        progress = 0.0f;
    }

    /**
     * Walks up to an animal or a block, swings its tool for a second and does the use (milk, shear, pick berries).
     * An animal that walks off is followed; one the goblin cannot get to is left alone for a while.
     */
    private void tickUse(ServerLevel level, JobConfig config, long now) {
        Vec3 point = use.point(level);
        if (point == null) {
            clearTarget(level);
            return;
        }
        goblin.getLookControl().setLookAt(point);
        goblin.setItemSlot(EquipmentSlot.MAINHAND, use.tool().copy());
        if (use.climbs()) {
            if (goblin.getEyePosition().distanceToSqr(point) > Reach.REACH_SQ) {
                progress = 0.0f;
                moveTowards(level, point, now, null); // stand spot or scaffold column, like for a high log
                return;
            }
        } else if (goblin.getEyePosition().distanceToSqr(point) > USE_REACH_SQ) {
            progress = 0.0f;
            reach.setBranch("use");
            if (Climber.isUp(level, goblin)) {
                goblin.climber().descend(level);
                return;
            }
            if (!reach.approach(level, point)) {
                use.giveUp(level, now);
                reach.giveUp(target, now);
                clearTarget(level);
            }
            return;
        }
        goblin.getNavigation().stop();
        goblin.setShiftKeyDown(false);
        if (progress == 0.0f || progress >= 0.5f && progress - USE_PROGRESS_PER_TICK < 0.5f) goblin.swing(InteractionHand.MAIN_HAND);
        progress += USE_PROGRESS_PER_TICK;
        targetProgressAt = now;
        if (progress < 1.0f) return;
        use.apply(level, goblin);
        chatter(level, config.job());
        target = null;
        use = null;
        progress = 0.0f;
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
            reach.resetHeadway();
        }
        reach.setBranch("loot");
        Vec3 point = itemTarget.position();
        goblin.getLookControl().setLookAt(point);
        if (goblin.distanceToSqr(point) <= PICKUP_SQ) {
            goblin.getNavigation().stop();
            if (itemTarget.hasPickUpDelay()) return true; // still popping out of the block
            goblin.pickUp(itemTarget);
            itemTarget = null;
            return true;
        }
        if (!reach.approach(level, point, false)) {
            skippedItems.put(itemTarget.getId(), now + Reach.SKIP_TICKS);
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

    /** Own landed drops the goblin could still fetch (those waiting far below, see {@link #LOOT_BELOW}). */
    private boolean ownDropsLeft() {
        drops.removeIf(item -> !item.isAlive());
        for (ItemEntity item : drops) {
            if (!skippedItems.containsKey(item.getId()) && goblin.canStore(item.getItem()) && goblin.distanceToSqr(item) < LOOT_RANGE_SQ) return true;
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
            // far down (a shaft dug upwards): walking down for every drop would take ages, they wait for the end
            if (!collectingLeftovers && item.getY() < goblin.getY() - LOOT_BELOW) continue;
            if (HomeRegistry.isProtected(level, item.blockPosition())) continue; // loot inside a home belongs to the collectors
            double d = goblin.distanceToSqr(item);
            if (d < bestDist) {
                bestDist = d;
                best = item;
            }
        }
        // drops on top of a scaffold column or on leaves cannot be walked to: leave them (and ask again next tick)
        if (best != null && goblin.distanceToSqr(best) > PICKUP_SQ && !Reach.reachable(goblin.getNavigation(), best.blockPosition())) {
            skippedItems.put(best.getId(), now + Reach.SKIP_TICKS);
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
            if (retryIn-- > 0) {
                if (goblin.isOnBed(level)) Reach.stepOffBed(level, goblin);
                return;
            }
            if (!exitChecked && !checkExit(level, bed, config, job)) return;
            itemTarget = nextCollectable(level, job.items(level, goblin, bed, config, skippedItems.keySet()), now);
            if (itemTarget == null) {
                bed.setStatus(GoblinBedBlockEntity.Status.IDLE);
                if (Climber.isUp(level, goblin)) {
                    goblin.climber().descend(level);
                    return;
                }
                retryIn = COLLECT_RETRY_TICKS;
                if (goblin.hasStorageItems()) startDeposit(level, false);
                return;
            }
            reach.resetStuck();
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
        // no blink: next to an item that cannot be walked to is a tree crown or the top of a column
        if (!reach.approach(level, point, false)) {
            skippedItems.put(itemTarget.getId(), now + Reach.SKIP_TICKS);
            itemTarget = null;
        }
    }

    /**
     * The first of the collectable items (nearest first) the goblin can walk to; a few are path-checked per search,
     * those out of reach are left alone for a while (they come back once they have fallen somewhere else).
     */
    @Nullable
    private ItemEntity nextCollectable(ServerLevel level, List<ItemEntity> items, long now) {
        int pathChecks = 0;
        for (ItemEntity item : items) {
            if (goblin.distanceToSqr(item) <= PICKUP_SQ) return item;
            if (pathChecks++ >= MAX_COLLECT_PATH_CHECKS) return null;
            if (Reach.reachable(goblin.getNavigation(), item.blockPosition())) return item;
            skippedItems.put(item.getId(), now + Reach.SKIP_TICKS);
        }
        return null;
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
        if (entry != null && Reach.reachable(navigation, entry)) {
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
                if (!Standing.room(level, spot)) continue;
                if (Reach.reachable(navigation, spot)) {
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

    // ---- deposit -------------------------------------------------------------------------------------------------

    /** {@code full}: the storage filled up at work (not: the work is done and it takes home what it has). */
    private void startDeposit(ServerLevel level, boolean full) {
        stop(level);
        phase = Phase.DEPOSIT;
        depositBecauseFull = full;
        unloader.restart();
    }

    private void tickDeposit(ServerLevel level, JobHost bed) {
        // emptied by hand on the way home: nothing left to take there, or room enough to go on working (only on the
        // way: at the chests a part that fits is no reason to stop, the rest goes into the next chest or waits for room)
        if (!unloader.hasUnloaded()
                && (!goblin.hasStorageItems() || depositBecauseFull && freeStorageShare() >= DEPOSIT_UNNEEDED_FREE)) {
            unloader.closeChest(level);
            goblin.getNavigation().stop(); // not a step further on the way to the chest
            phase = Phase.WORK;
            return;
        }
        if (goblin.climber().comeDownFirst(level)) return; // the storage filled up while up on a column
        switch (unloader.tick(level, bed)) {
            case BUSY -> { }
            case NOTHING_FITS -> {
                phase = Phase.WAIT_ROOM;
                waitUntil = level.getGameTime() + WAIT_ROOM_TICKS;
                bed.setStatus(GoblinBedBlockEntity.Status.WAITING_FULL);
                say(level, GoblinSpeech.INVENTORY_FULL);
            }
            case NOTHING_TO_DO -> phase = Phase.WORK;
        }
    }

    /** Share of the storage slots (not the tool row) that are empty. */
    private double freeStorageShare() {
        int free = 0, all = 0;
        for (int i = GoblinEntity.HOTBAR_SIZE, end = goblin.storageEnd(); i < end; i++, all++) {
            if (goblin.getInventory().getItem(i).isEmpty()) free++;
        }
        return all == 0 ? 0.0 : (double) free / all;
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
