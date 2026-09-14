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
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Per-goblin work loop (Minions Remastered's goalTick, one goblin at a time): pick a block, walk there, swing until
 * it breaks (or until the stair step is placed), collect, repeat. Extras: scaffold climbing for high targets (see
 * {@link Climber}), unloading into the flat's copper chests when the storage is full (waiting in bed when nothing
 * fits), picking up loose items for the collect job, and a "no exit" check. Tool choice and the rules live in
 * {@link Mining}; what comes next is the job's business.
 */
public final class JobRunner {
    /** Distance (squared) from the eyes at which the goblin swings instead of walking. */
    private static final double REACH_SQ = 4.5 * 4.5;
    private static final double CHEST_REACH_SQ = 2.5 * 2.5;
    private static final double PICKUP_SQ = 1.5 * 1.5;
    /** Targets more than this far up (and at most this far sideways) are reached by stacking scaffold. */
    private static final double CLIMB_MIN_DY = 1.5;
    private static final double CLIMB_MAX_HORIZONTAL = 1.6;
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
    @Nullable private ItemEntity itemTarget;
    private float progress;
    private int retryIn;
    private int stuckTicks;
    private int stuckStrikes;
    private long waitUntil;
    private boolean exitChecked;
    private int exitFailures;
    private Vec3 lastPos = Vec3.ZERO;
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

    /** Debug summary for the status command. */
    public String debug() {
        return "phase=" + phase + " target=" + (target == null ? "-" : target.toShortString()) + (placeState != null ? "(place)" : "")
                + (itemTarget != null ? " item=" + itemTarget.getItem().getItem() : "")
                + " progress=" + String.format(java.util.Locale.ROOT, "%.2f", progress) + " stuck=" + stuckTicks + "/" + stuckStrikes
                + " up=" + Climber.isUp(goblin.level(), goblin) + " skipped=" + skipped.size() + " retryIn=" + retryIn;
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
    }

    public void stop(ServerLevel level) {
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
    }

    /** One tick. {@code task} may be null when only a deposit run is pending. */
    public void tick(ServerLevel level, GoblinBedBlockEntity bed, JobConfig config, @Nullable JobTask task) {
        switch (phase) {
            case DEPOSIT -> tickDeposit(level, bed);
            case WAIT_ROOM -> { /* the rest goal has the goblin in bed */ }
            case WORK -> {
                if (task == null) return;
                if (config.job().needsAssignment() && bed.getAssignment() == null) {
                    bed.setStatus(GoblinBedBlockEntity.Status.IDLE);
                    bed.setJob(config.withJob(Job.REST));
                    return;
                }
                if (goblin.isStorageFull()) {
                    startDeposit(level);
                    return;
                }
                if (task instanceof CollectJob collect) tickCollect(level, bed, config, collect);
                else tickWork(level, bed, config, task);
            }
        }
    }

    // ---- work ----------------------------------------------------------------------------------------------------

    private void tickWork(ServerLevel level, GoblinBedBlockEntity bed, JobConfig config, JobTask task) {
        long now = level.getGameTime();
        skipped.values().removeIf(until -> until < now);

        if (target == null) {
            if (retryIn-- > 0) return;
            if (!exitChecked && !checkExit(level, bed, config, task)) return;
            JobTask.Pick pick = task.pick(level, goblin, bed, config, skipped.keySet());
            if (pick.target() == null) {
                boolean up = Climber.isUp(level, goblin);
                if (pick.verdict() == Mining.Verdict.NEEDS_TOOL) {
                    retryIn = PICK_RETRY_TICKS;
                    bed.setStatus(GoblinBedBlockEntity.Status.BLOCKED);
                    say(level, GoblinSpeech.NO_TOOL);
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
                } else {
                    bed.setStatus(GoblinBedBlockEntity.Status.IDLE);
                    GoblinSpeech.say(level, goblin, GoblinSpeech.random(goblin.getRandom(), GoblinSpeech.DONE), GoblinSounds.YES);
                    task.onDone(level, bed);
                    bed.setJob(config.withJob(Job.REST));
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
            tickPlace(level, now);
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
        if (center.y - goblin.getEyeY() > 0.5 && level.getBlockState(goblin.blockPosition()).is(GoblinLabour.GOBLIN_SCAFFOLD)) {
            // half-way up a scaffold block: finish the climb onto it before swinging, or the goblin bobs at the
            // edge of its reach (rise, in reach, stop jumping, fall, out of reach, ...)
            progress = 0.0f;
            level.destroyBlockProgress(goblin.getId(), target, -1);
            goblin.getNavigation().stop();
            goblin.setShiftKeyDown(false);
            goblin.getJumpControl().jump();
            return;
        }
        if (goblin.getEyePosition().distanceToSqr(center) > REACH_SQ) {
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
        Mining.harvest(level, broken, state, goblin, tool);
        chatter(level, config.job());
        task.afterBreak(level, goblin, bed, config, broken);
        if (!Climber.isUp(level, goblin)) Mining.placeTorchIfDark(level, goblin);
        target = null;
        progress = 0.0f;
    }

    /** Walks to a stair position, swings a few times, then sets the block (unless something got in the way). */
    private void tickPlace(ServerLevel level, long now) {
        BlockState current = level.getBlockState(target);
        if (!current.canBeReplaced() || placeState == null) {
            clearTarget(level);
            return;
        }
        Vec3 center = Vec3.atCenterOf(target);
        goblin.getLookControl().setLookAt(center);
        AABB blockBox = new AABB(target);
        if (goblin.getBoundingBox().intersects(blockBox)) {
            // standing in the spot: step aside first
            Vec3 away = goblin.position().subtract(center).multiply(1, 0, 1);
            if (away.lengthSqr() < 0.01) away = new Vec3(1, 0, 0);
            Vec3 aside = center.add(away.normalize().scale(1.6));
            if (goblin.tickCount % 10 == 0) goblin.getNavigation().moveTo(aside.x, aside.y, aside.z, 0.8);
            progress = 0.0f;
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
        target = null;
        placeState = null;
        progress = 0.0f;
    }

    /**
     * Climb, come down or walk, whatever brings the goblin closer. A target well above the goblin is reached by
     * first walking (almost) under it and then climbing scaffold; scaffold is never stacked inside a home or under
     * a blocked ceiling. Returns false when the goblin gave up on the target.
     */
    private boolean moveTowards(ServerLevel level, BlockPos targetPos, Vec3 center, long now) {
        double dy = center.y - goblin.getEyeY();
        double horizontal = Math.hypot(center.x - goblin.getX(), center.z - goblin.getZ());
        boolean up = Climber.isUp(level, goblin);
        if (dy > CLIMB_MIN_DY) {
            if (horizontal <= CLIMB_MAX_HORIZONTAL && goblin.climber().climb(level)) return true;
            if (up) {
                goblin.climber().descend(level); // wrong column: come down and walk over
                return true;
            }
            Vec3 under = new Vec3(center.x, goblin.getY(), center.z);
            if (!approach(level, under)) {
                skipped.put(targetPos, now + SKIP_TICKS);
                return false;
            }
            return true;
        }
        if (up) {
            goblin.climber().descend(level);
            return true;
        }
        if (!approach(level, center)) {
            skipped.put(targetPos, now + SKIP_TICKS);
            return false;
        }
        return true;
    }

    /**
     * Walks towards {@code point}; teleports next to it after standing still too long (outside homes). Returns false
     * when the goblin gave up on this point.
     */
    private boolean approach(ServerLevel level, Vec3 point) {
        PathNavigation navigation = goblin.getNavigation();
        goblin.setShiftKeyDown(false);
        if (goblin.tickCount % 10 == 0) navigation.moveTo(point.x, point.y, point.z, 1.0);
        if (goblin.position().distanceToSqr(lastPos) < 0.01) {
            if (++stuckTicks > STUCK_TICKS) {
                stuckTicks = 0;
                if (++stuckStrikes > 2) return false;
                goblin.blinkTo(level, BlockPos.containing(point));
            }
        } else {
            stuckTicks = 0;
        }
        lastPos = goblin.position();
        return true;
    }

    // ---- collect -------------------------------------------------------------------------------------------------

    /** Walks to the nearest loose item in the radius and picks it up; unloads when there is nothing left to fetch. */
    private void tickCollect(ServerLevel level, GoblinBedBlockEntity bed, JobConfig config, CollectJob job) {
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
    private boolean checkExit(ServerLevel level, GoblinBedBlockEntity bed, JobConfig config, JobTask task) {
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

    private void tickDeposit(ServerLevel level, GoblinBedBlockEntity bed) {
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
        goblin.swing(InteractionHand.MAIN_HAND);
        unloadInto(level, chestTarget);
        chestTarget = null;
    }

    /** Nearest copper chest of the flat that accepts at least one of the carried stacks. */
    @Nullable
    private BlockPos findChest(ServerLevel level, GoblinBedBlockEntity bed) {
        List<BlockPos> chests = HomeRegistry.copperChests(level, bed.getBlockPos());
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        for (BlockPos chest : chests) {
            if (skipped.containsKey(chest)) continue;
            Storage<ItemVariant> storage = ItemStorage.SIDED.find(level, chest, Direction.UP);
            if (storage == null || !acceptsAnything(storage)) continue;
            double d = goblin.distanceToSqr(Vec3.atCenterOf(chest));
            if (d < bestDist) {
                bestDist = d;
                best = chest;
            }
        }
        return best;
    }

    private boolean acceptsAnything(Storage<ItemVariant> storage) {
        SimpleContainer inv = goblin.getInventory();
        for (int i = GoblinEntity.HOTBAR_SIZE; i < GoblinEntity.INVENTORY_SIZE; i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.isEmpty()) continue;
            if (StorageUtil.simulateInsert(storage, ItemVariant.of(stack), stack.getCount(), null) > 0) return true;
        }
        return false;
    }

    private void unloadInto(ServerLevel level, BlockPos chest) {
        Storage<ItemVariant> storage = ItemStorage.SIDED.find(level, chest, Direction.UP);
        if (storage == null) return;
        SimpleContainer inv = goblin.getInventory();
        try (Transaction tx = Transaction.openOuter()) {
            for (int i = GoblinEntity.HOTBAR_SIZE; i < GoblinEntity.INVENTORY_SIZE; i++) {
                ItemStack stack = inv.getItem(i);
                if (stack.isEmpty()) continue;
                long inserted = storage.insert(ItemVariant.of(stack), stack.getCount(), tx);
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
