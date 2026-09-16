package goblinlabour.entity.ai;

import goblinlabour.block.GoblinChestBlockEntity;
import goblinlabour.entity.GoblinEntity;
import goblinlabour.home.HomeRegistry;
import goblinlabour.job.Climber;
import goblinlabour.job.JobConfig;
import goblinlabour.job.JobRunner;
import goblinlabour.job.JobTask;
import goblinlabour.job.Mining;
import goblinlabour.ring.CrewOrder;
import goblinlabour.ring.RingContainer;
import goblinlabour.ring.RingCrew;
import goblinlabour.ring.RingInventory;
import net.fabricmc.fabric.api.tag.convention.v2.ConventionalBlockTags;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Everything a ring crew goblin does (see {@link RingCrew}), in this order:
 * <ol>
 * <li>stay within a chunk of its player: walk back when further away, blink back when far off or stuck;</li>
 * <li>keep out of the player's way: a goblin standing in the player's personal space or in the lane the player looks
 * or walks along steps aside;</li>
 * <li>carry the ring's loot to a goblin chest within 16 blocks, one stack at a time (one goblin at a time);</li>
 * <li>pick up loose items near the player (into the ring);</li>
 * <li>mine ores it can see: from a spot in reach with a clear line of sight, never inside a home, never the block
 * the player is looking at;</li>
 * <li>otherwise stroll about near the player and look around.</li>
 * </ol>
 */
public class CrewGoal extends Goal {
    private static final double REACH_SQ = 3.5 * 3.5;
    private static final double COMFORT_REACH_SQ = 2.75 * 2.75;
    private static final double PICKUP_SQ = 1.5 * 1.5;
    /**
     * From the eyes: an item in a niche of the wall (the drop of an ore mined out of it) is picked out by hand. Ores
     * three blocks up leave their drop about 2.6 blocks from the eyes of a goblin standing below.
     */
    private static final double ARM_REACH_SQ = 3.5 * 3.5;
    private static final double CHEST_REACH_SQ = 2.5 * 2.5;
    /** Further than this from the player (or this far above or below) the goblin blinks back instead of walking. */
    private static final double BLINK_DISTANCE = 14.0, VERTICAL_BLINK = 12.0;
    private static final double VERTICAL_LEASH = 6.0;
    /** A goblin walking back is back once it is this close again. */
    private static final double RETURN_DONE = RingCrew.LEASH - 2.0;
    private static final int WORK_DY = 6;
    /** The player's personal space, and the lane ahead of the player (where it looks or walks). */
    private static final double PERSONAL_SQ = 2.5 * 2.5;
    private static final double LANE_LENGTH = 6.0, LANE_HALF_WIDTH = 1.5;
    /**
     * A spot a goblin walks to has to be this much clear of the personal space and the lanes. Without the margin a
     * goblin picks a spot right at the edge, a nudge puts it back in the way, and it flaps in and out.
     */
    private static final double SPOT_MARGIN = 0.75;
    /** In the way this many ticks in a row before stepping aside: a glance sweeping past does not count. */
    private static final int IN_WAY_TICKS = 8;
    private static final int STUCK_TICKS = 80;
    /** Crew goblins closer than this (horizontally) are nudged apart, as they do not push each other otherwise. */
    private static final double SEPARATION = 0.7, SEPARATION_PUSH = 0.03;
    private static final int RETURN_SPOT_CHECK = 40;
    private static final int SKIP_TICKS = 600;
    /** A hidden vein block usually just waits for its neighbour to be mined, so it is tried again soon. */
    private static final int VEIN_SKIP_TICKS = 100;
    /** Helping the player dig: natural blocks this close to the block the player broke last. */
    private static final int ASSIST_RADIUS = 4;
    private static final int CHOOSE_INTERVAL = 10;
    private static final int CHEST_CHECK_INTERVAL = 40;
    private static final int STORE_INTERVAL = 8;
    private static final int MAX_PATH_CHECKS = 3;
    private static final double WALK_SPEED = 1.0, STROLL_SPEED = 0.7, HURRY_SPEED = 1.25;
    private static final int PAUSE_MIN = 40, PAUSE_MAX = 160, WALK_TIMEOUT = 200;


    private enum Mode { IDLE, RETURN, AVOID, MINE, LOOT, DEPOSIT, ORDER, ASSIST }

    private record SpotSearch(@Nullable BlockPos spot, int budget, boolean exhausted) {
    }

    private final GoblinEntity goblin;
    private Mode mode = Mode.IDLE;
    @Nullable private BlockPos mineTarget;
    @Nullable private BlockPos stand;
    private float progress;
    @Nullable private ItemEntity item;
    /** Where to stand for an item the goblin cannot walk up to, or null to walk to the item itself. */
    @Nullable private BlockPos itemStand;
    @Nullable private BlockPos chosenItemStand;
    @Nullable private BlockPos chest;
    @Nullable private BlockPos chestStand;
    private boolean lidOpen;
    private int storeTimer;
    @Nullable private BlockPos walkTarget;
    /** Where a goblin walking back heads: a free spot near the player, not the player's own position. */
    @Nullable private BlockPos returnSpot;
    private int nextReturnSpotCheck;
    private boolean walking;
    private int walkTicks;
    private int pauseTicks;
    private int lookTicks;
    @Nullable private Vec3 lookPoint;
    private int inWayTicks;
    private int nextChoose;
    private int nextChestCheck;
    private int nextBlink;
    @Nullable private JobConfig lastOrderConfig;
    @Nullable private Vec3 headwayPoint;
    private double headwayBest;
    private int stuckTicks;
    private final Map<BlockPos, Long> skippedBlocks = new HashMap<>();
    private final Map<Integer, Long> skippedItems = new HashMap<>();
    /** What this goblin's own mining dropped: fetched before other loose items. */
    private final List<ItemEntity> drops = new ArrayList<>();

    public CrewGoal(GoblinEntity goblin) {
        this.goblin = goblin;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    /** Debug summary for the ring status command. */
    public String debug(Player owner) {
        CrewOrder order = goblin.crewOrder();
        BlockPos orderTarget = goblin.runner().target();
        return "mode=" + mode + " order=" + (order != null && order.active() ? order.getJob().job() + "/" + order.status()
                + (mode == Mode.ORDER && orderTarget != null ? "@" + orderTarget.toShortString().replace(" ", "") : "") : "-")
                + (mineTarget != null ? " mine=" + mineTarget.toShortString().replace(" ", "") : "")
                + (stand != null ? " stand=" + stand.toShortString().replace(" ", "") : "")
                + (item != null ? " item=" + item.getItem().getItem() : "")
                + (chest != null ? " chest=" + chest.toShortString().replace(" ", "") + (lidOpen ? "(open)" : "") : "")
                + String.format(Locale.ROOT, " progress=%.2f away=%.1f dy=%.1f", progress,
                horizontalDistance(goblin.position(), owner.position()), goblin.getY() - owner.getY())
                + " way=" + inWay(owner, goblin.position()) + " stuck=" + stuckTicks + " skipped=" + skippedBlocks.size();
    }

    @Override
    public boolean canUse() {
        // a goblin picked with the staff follows the player (FollowStaffGoal) and its order waits in CrewOrder
        return goblin.crew() != null && !goblin.isNoAi() && goblin.following() == null;
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void start() {
        mode = Mode.IDLE;
        walking = false;
        // three goblins called in the same tick would otherwise pause, choose and walk in step
        int offset = goblin.getId() % 7;
        pauseTicks = goblin.getRandom().nextInt(10, 40) + offset;
        nextChoose = goblin.tickCount + offset;
    }

    @Override
    public void stop() {
        if (goblin.level() instanceof ServerLevel level) release(level);
        mode = Mode.IDLE; // after release, which stops the runner of an ORDER; start() begins idle again anyway
    }

    @Override
    public void tick() {
        RingCrew.Session crew = goblin.crew();
        if (crew == null) return;
        ServerLevel level = (ServerLevel) goblin.level();
        ServerPlayer owner = crew.owner();
        long now = level.getGameTime();
        if (goblin.tickCount % 20 == 0) {
            skippedBlocks.values().removeIf(until -> until < now);
            skippedItems.values().removeIf(until -> until < now);
            drops.removeIf(drop -> !drop.isAlive());
        }

        keepApart(crew);

        // a staff order comes before the goblin's own work (carrying a full ring to a chest aside), and it takes
        // the goblin off the leash until the order is done: it digs the whole shaft or tunnel without waiting
        CrewOrder order = goblin.crewOrder();
        boolean onOrder = mode != Mode.DEPOSIT && order != null && order.active();

        // 1. never further than the leash from the player, unless an order says otherwise
        double away = horizontalDistance(goblin.position(), owner.position());
        double vertical = Math.abs(goblin.getY() - owner.getY());
        // a goblin carrying the ring's loot to a chest may go as far as the chest, else half a leash makes
        // unloading useless; everyone else stays within the leash
        double limit = mode == Mode.RETURN ? RETURN_DONE : mode == Mode.DEPOSIT ? RingCrew.CHEST_RANGE : RingCrew.LEASH;
        if (!onOrder && (away > limit || vertical > VERTICAL_LEASH)) {
            if (mode != Mode.RETURN) switchTo(level, Mode.RETURN);
            tickReturn(level, owner, away, vertical);
            return;
        }
        if (mode == Mode.RETURN) switchTo(level, Mode.IDLE);

        // 2. out of the player's way
        inWayTicks = inWay(owner, goblin.position(), goblin.crewAvoidsLook()) ? inWayTicks + 1 : Math.min(inWayTicks + 1, 0);
        // a short trip into the player's personal space is allowed while fetching an item lying right there
        boolean fetchingClose = mode == Mode.LOOT && item != null && item.isAlive() && goblin.distanceToSqr(item) < 4.0;
        if (inWayTicks >= IN_WAY_TICKS && mode != Mode.AVOID && !fetchingClose) switchTo(level, Mode.AVOID);
        if (mode == Mode.AVOID) {
            if (tickAvoid(level, owner)) return;
            switchTo(level, Mode.IDLE);
        }

        if (onOrder && tickOrder(level, owner, crew, order)) return;

        // 3.-5. the task at hand, or a new one
        boolean busy = switch (mode) {
            case DEPOSIT -> tickDeposit(level, owner, crew);
            case LOOT -> tickLoot(level, owner);
            case MINE, ASSIST -> tickMine(level, owner);
            default -> false;
        };
        if (busy) return;
        if (mode != Mode.IDLE) switchTo(level, Mode.IDLE);
        if (goblin.tickCount >= nextChoose && choose(level, owner, crew, now)) return;
        // 6. potter about
        tickIdle(level, owner);
    }

    private void switchTo(ServerLevel level, Mode next) {
        release(level);
        mode = next;
        walking = false;
        walkTarget = null;
        returnSpot = null;
        headwayPoint = null;
        stuckTicks = 0;
        if (next == Mode.IDLE) pauseTicks = goblin.getRandom().nextInt(10, 40);
    }

    private void release(ServerLevel level) {
        if (mode == Mode.ORDER) goblin.runner().stop(level);
        if (mineTarget != null && progress > 0.0f) level.destroyBlockProgress(goblin.getId(), mineTarget, -1);
        mineTarget = null;
        stand = null;
        progress = 0.0f;
        item = null;
        itemStand = null;
        closeLid(level);
        chest = null;
        chestStand = null;
        RingCrew.Session crew = goblin.crew();
        if (crew != null) crew.releaseAll(goblin);
        goblin.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        goblin.getNavigation().stop();
    }

    /** Picks the next task: unload (if this goblin may carry), the nearest loose item, the nearest visible ore. */
    private boolean choose(ServerLevel level, ServerPlayer owner, RingCrew.Session crew, long now) {
        nextChoose = goblin.tickCount + CHOOSE_INTERVAL;
        RingContainer ring = RingInventory.open(owner, crew.ringId());
        if (ring == null) return false;
        if (!ring.isEmpty() && goblin.tickCount >= nextChestCheck && crew.mayCarry(goblin)) {
            nextChestCheck = goblin.tickCount + CHEST_CHECK_INTERVAL;
            BlockPos found = findChest(level, owner);
            if (found != null) {
                switchTo(level, Mode.DEPOSIT);
                chest = found;
                crew.setPorter(goblin);
                return true;
            }
        }
        ItemEntity next = nextItem(level, owner, crew, ring, now);
        if (next != null) {
            switchTo(level, Mode.LOOT);
            item = next;
            itemStand = chosenItemStand;
            crew.claim(next, goblin);
            return true;
        }
        if (!hasEmptySlot(ring)) {
            crew.notifyFull(); // nothing mined would fit
            return false;
        }
        if (nextOre(level, owner, crew, now)) return true;
        return nextAssist(level, owner, crew, now);
    }

    /**
     * Helping the player dig: natural blocks within ASSIST_RADIUS of the block the player broke last, and within the
     * work radius. Never below the player's feet (nobody's floor is dug away), never the block a crew goblin stands on
     * or the one the player is looking at, never inside a home, and only blocks that can be seen from a stand spot.
     */
    private boolean nextAssist(ServerLevel level, ServerPlayer owner, RingCrew.Session crew, long now) {
        BlockPos focus = crew.assistFocus();
        if (focus == null) return false;
        int feetY = owner.getBlockY();
        List<BlockPos> candidates = new ArrayList<>();
        for (int dx = -ASSIST_RADIUS; dx <= ASSIST_RADIUS; dx++) {
            for (int dy = -ASSIST_RADIUS; dy <= ASSIST_RADIUS; dy++) {
                for (int dz = -ASSIST_RADIUS; dz <= ASSIST_RADIUS; dz++) {
                    if (dx * dx + dy * dy + dz * dz > ASSIST_RADIUS * ASSIST_RADIUS) continue;
                    BlockPos pos = focus.offset(dx, dy, dz);
                    if (pos.getY() < feetY) continue; // the floor the player stands on, and all below, stays
                    if (horizontalDistance(Vec3.atCenterOf(pos), owner.position()) > RingCrew.WORK_RADIUS) continue;
                    if (skippedBlocks.containsKey(pos) || crew.isClaimed(pos, goblin)) continue;
                    if (!level.getBlockState(pos).is(RingCrew.NATURAL) || !RingCrew.exposed(level, pos)) continue;
                    if (underCrewGoblin(crew, pos) || HomeRegistry.isProtected(level, pos)) continue;
                    candidates.add(pos);
                }
            }
        }
        Vec3 here = goblin.position();
        candidates.sort(Comparator.comparingDouble(pos -> Vec3.atCenterOf(pos).distanceToSqr(here)));
        int budget = MAX_PATH_CHECKS;
        for (BlockPos pos : candidates) {
            if (Mining.verdict(level, pos, level.getBlockState(pos), goblin) != Mining.Verdict.OK || targetedBy(owner, pos)) continue;
            SpotSearch search = standSpot(level, owner, crew, pos, budget);
            budget = search.budget();
            if (search.spot() != null && !search.spot().below().equals(pos)) { // not the block it would stand on
                switchTo(level, Mode.ASSIST);
                mineTarget = pos;
                stand = search.spot();
                crew.claim(pos, goblin);
                crew.claim(stand, goblin);
                return true;
            }
            if (search.exhausted()) return false;
            skippedBlocks.put(pos, now + VEIN_SKIP_TICKS);
        }
        return false;
    }

    private static boolean underCrewGoblin(RingCrew.Session crew, BlockPos pos) {
        for (GoblinEntity mate : crew.goblins()) {
            if (mate.blockPosition().below().equals(pos)) return true;
        }
        return false;
    }

    // ---- staff orders --------------------------------------------------------------------------------------------

    /**
     * One tick of the staff order through the same work loop bed goblins use (the order stands in for the bed).
     * The order is off the leash: the goblin digs the whole shaft or tunnel wherever it leads, and only waits
     * while the ring is full.
     */
    private boolean tickOrder(ServerLevel level, ServerPlayer owner, RingCrew.Session crew, CrewOrder order) {
        if (RingCrew.isFull(goblin)) {
            crew.notifyFull(); // the drops would not fit: unload into a chest nearby first, or the player makes room
            return false;
        }
        JobConfig config = order.getJob();
        JobTask task = JobTask.of(config.job());
        if (task == null) return false;
        if (mode != Mode.ORDER) switchTo(level, Mode.ORDER);
        JobRunner runner = goblin.runner();
        if (!config.equals(lastOrderConfig)) {
            runner.stop(level); // a new order: drop the old target
            lastOrderConfig = config;
        }
        runner.tick(level, order, config, task);
        return true;
    }

    // ---- staying near --------------------------------------------------------------------------------------------

    private void tickReturn(ServerLevel level, ServerPlayer owner, double away, double vertical) {
        if (away > BLINK_DISTANCE || vertical > VERTICAL_BLINK) {
            blinkBack(level, owner);
            return;
        }
        // Not the player's own position: three goblins heading for one point walk into each other. The spot is
        // only swapped when it has gone bad, since a new target also resets the stuck check in headway().
        if (goblin.tickCount >= nextReturnSpotCheck) {
            nextReturnSpotCheck = goblin.tickCount + RETURN_SPOT_CHECK;
            if (returnSpot == null || horizontalDistance(Vec3.atBottomCenterOf(returnSpot), owner.position()) > RETURN_DONE
                    || inWay(owner, Vec3.atBottomCenterOf(returnSpot))) {
                returnSpot = RingCrew.spotNearOwner(level, owner, goblin.getRandom(), takenSpots());
                holdSpot(returnSpot);
            }
        }
        Vec3 target = returnSpot != null ? Vec3.atBottomCenterOf(returnSpot) : owner.position();
        PathNavigation navigation = goblin.getNavigation();
        if (goblin.tickCount % 10 == 0 || (navigation.isDone() && goblin.tickCount % 5 == 0)) {
            navigation.moveTo(target.x, target.y, target.z, HURRY_SPEED);
        }
        if (!headway(target)) blinkBack(level, owner);
    }

    /** Where the other goblins of the crew stand and where they are heading. */
    private Set<BlockPos> takenSpots() {
        RingCrew.Session crew = goblin.crew();
        return crew == null ? new HashSet<>() : crew.spotsOfOthers(goblin);
    }

    private void holdSpot(@Nullable BlockPos spot) {
        RingCrew.Session crew = goblin.crew();
        if (crew != null) crew.holdSpot(goblin, spot);
    }

    /**
     * Nudges this goblin away from crew mates standing in (almost) the same place. Crew goblins are not pushable,
     * so the player is never shoved about by them, which also means they would stack up in one block.
     */
    private void keepApart(RingCrew.Session crew) {
        if (!goblin.onGround()) return;
        for (GoblinEntity other : crew.goblins()) {
            if (other == goblin || !other.isAlive() || !other.onGround() || Math.abs(other.getY() - goblin.getY()) > 1.0) continue;
            double dx = goblin.getX() - other.getX(), dz = goblin.getZ() - other.getZ();
            double distSq = dx * dx + dz * dz;
            if (distSq >= SEPARATION * SEPARATION) continue;
            double dist = Math.sqrt(distSq);
            if (dist < 1.0e-3) { // exactly on top of each other: a direction of its own per goblin
                double angle = goblin.getId() * 2.399963;
                dx = Math.cos(angle);
                dz = Math.sin(angle);
                dist = 1.0;
            }
            goblin.push(dx / dist * SEPARATION_PUSH, 0.0, dz / dist * SEPARATION_PUSH);
        }
    }

    private void blinkBack(ServerLevel level, ServerPlayer owner) {
        if (goblin.tickCount < nextBlink) return;
        nextBlink = goblin.tickCount + 40;
        BlockPos spot = RingCrew.spotNearOwner(level, owner, goblin.getRandom(), takenSpots());
        if (spot == null) return;
        holdSpot(spot);
        GoblinEntity.poof(level, goblin.getX(), goblin.getY(), goblin.getZ());
        goblin.snapTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, goblin.getYRot(), goblin.getXRot());
        goblin.getNavigation().stop();
        headwayPoint = null;
        stuckTicks = 0;
        GoblinEntity.poof(level, goblin.getX(), goblin.getY(), goblin.getZ());
    }

    /**
     * True if a goblin at {@code point} would be in the player's way: within 2.5 blocks of the player, or in the lane
     * six blocks ahead of where the player looks (unless it looks steeply up or down) or eight ahead of where it walks.
     */
    public static boolean inWay(Player owner, Vec3 point) {
        return inWay(owner, point, true);
    }

    /** {@link #inWay(Player, Vec3)}, optionally without the lane ahead of the player's look (a staff order is there). */
    public static boolean inWay(Player owner, Vec3 point, boolean lookLane) {
        return inWay(owner, point, lookLane, 0.0);
    }

    /** Whether a spot is fit to walk to: out of the player's way with {@link #SPOT_MARGIN} to spare. */
    public static boolean clearOfWay(Player owner, Vec3 point) {
        return !inWay(owner, point, true, SPOT_MARGIN);
    }

    private static boolean inWay(Player owner, Vec3 point, boolean lookLane, double margin) {
        double dx = point.x - owner.getX(), dz = point.z - owner.getZ();
        if (Math.abs(point.y - owner.getY()) > 3.0) return false;
        double personal = Math.sqrt(PERSONAL_SQ) + margin;
        if (dx * dx + dz * dz < personal * personal) return true;
        return inLanes(owner, dx, dz, lookLane, margin);
    }

    /**
     * Only the lanes ahead of the player, without the personal space around it. Used for loose items: a drop almost
     * always lands at the player's feet, so a crew that respected the personal space would never fetch one.
     */
    public static boolean inLane(Player owner, Vec3 point) {
        if (Math.abs(point.y - owner.getY()) > 3.0) return false;
        return inLanes(owner, point.x - owner.getX(), point.z - owner.getZ(), true, 0.0);
    }

    private static boolean inLanes(Player owner, double dx, double dz, boolean lookLane, double margin) {
        Vec3 look = owner.getLookAngle();
        if (lookLane && inLane(dx, dz, look.x, look.z, 0.3, LANE_LENGTH + margin, margin)) return true;
        Vec3 move = owner.getKnownMovement();
        return inLane(dx, dz, move.x, move.z, 0.05, LANE_LENGTH + 2.0 + margin, margin);
    }

    private static boolean inLane(double dx, double dz, double dirX, double dirZ, double minLength, double length, double margin) {
        double len = Math.sqrt(dirX * dirX + dirZ * dirZ);
        if (len < minLength) return false;
        dirX /= len;
        dirZ /= len;
        double along = dx * dirX + dz * dirZ;
        double lateral = Math.abs(dx * dirZ - dz * dirX);
        return along > -margin && along < length && lateral < LANE_HALF_WIDTH + margin;
    }

    private boolean tickAvoid(ServerLevel level, ServerPlayer owner) {
        if (walkTarget == null || inWay(owner, Vec3.atBottomCenterOf(walkTarget))) {
            walkTarget = asideSpot(level, owner);
            holdSpot(walkTarget);
            headwayPoint = null;
            if (walkTarget == null) {
                inWayTicks = -40; // nowhere better to stand right now; look again in a moment
                return false;
            }
        }
        Vec3 point = Vec3.atBottomCenterOf(walkTarget);
        if (!inWay(owner, goblin.position()) && goblin.position().distanceToSqr(point) < 0.5) return false;
        if (!walkTo(point, WALK_SPEED)) {
            inWayTicks = -40;
            return false;
        }
        return true;
    }

    /** The nearest free spot around the goblin that is out of the player's way and still close to the player. */
    @Nullable
    private BlockPos asideSpot(ServerLevel level, ServerPlayer owner) {
        Set<BlockPos> taken = takenSpots();
        BlockPos here = goblin.blockPosition();
        BlockPos best = null;
        double bestCost = Double.MAX_VALUE;
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                if (dx == 0 && dz == 0) continue;
                for (int dy = 2; dy >= -2; dy--) {
                    BlockPos spot = here.offset(dx, dy, dz);
                    if (!standable(level, spot)) continue;
                    if (RingCrew.nearTaken(spot, taken)) break; // a crew mate stands or heads there, or right next to it
                    Vec3 bottom = Vec3.atBottomCenterOf(spot);
                    double fromOwner = horizontalDistance(bottom, owner.position());
                    if (clearOfWay(owner, bottom) && fromOwner >= 2.5 && fromOwner <= RingCrew.WORK_RADIUS) {
                        double cost = goblin.position().distanceToSqr(bottom) + Math.abs(dy) * 2.0;
                        if (cost < bestCost) {
                            bestCost = cost;
                            best = spot.immutable();
                        }
                    }
                    break; // the highest spot of each column only
                }
            }
        }
        return best;
    }

    // ---- mining --------------------------------------------------------------------------------------------------

    private boolean nextOre(ServerLevel level, ServerPlayer owner, RingCrew.Session crew, long now) {
        // a vein is taken out completely, hidden blocks included, before the crew turns to any other ore
        boolean inVein = !crew.vein().isEmpty();
        List<BlockPos> ores = new ArrayList<>(inVein ? crew.vein() : crew.ores());
        if (ores.isEmpty()) return false;
        Vec3 here = goblin.position();
        ores.sort(Comparator.comparingDouble(pos -> Vec3.atCenterOf(pos).distanceToSqr(here)));
        int budget = MAX_PATH_CHECKS;
        for (BlockPos pos : ores) {
            if (skippedBlocks.containsKey(pos) || crew.isClaimed(pos, goblin)) continue;
            BlockState state = level.getBlockState(pos);
            if (!state.is(ConventionalBlockTags.ORES) || Mining.verdict(level, pos, state, goblin) != Mining.Verdict.OK) continue;
            if (targetedBy(owner, pos)) continue;
            SpotSearch search = standSpot(level, owner, crew, pos, budget);
            budget = search.budget();
            if (search.spot() != null) {
                switchTo(level, Mode.MINE);
                mineTarget = pos;
                stand = search.spot();
                crew.claim(pos, goblin);
                crew.claim(stand, goblin);
                if (!inVein) crew.startVein(pos);
                return true;
            }
            if (search.exhausted()) return false; // out of path checks for now, try again next time
            // not in sight from anywhere near, or no way there
            skippedBlocks.put(pos, now + (inVein ? VEIN_SKIP_TICKS : SKIP_TICKS));
        }
        return false;
    }

    /**
     * Where to stand to mine {@code target}: the goblin's own spot if the ore is in reach and in sight from there,
     * otherwise the nearest spot around it with solid ground, the ore in reach and in plain sight, close to the player
     * and out of its way, that the goblin can walk to.
     */
    private SpotSearch standSpot(ServerLevel level, ServerPlayer owner, RingCrew.Session crew, BlockPos target, int budget) {
        Vec3 center = Vec3.atCenterOf(target);
        Vec3 eyes = goblin.getEyePosition();
        if (goblin.onGround() && eyes.distanceToSqr(center) <= REACH_SQ && Mining.canSee(level, eyes, target)
                && !crew.isClaimed(goblin.blockPosition(), goblin)) {
            return new SpotSearch(goblin.blockPosition(), budget, false);
        }
        record Candidate(BlockPos spot, double cost) {
        }
        List<Candidate> candidates = new ArrayList<>();
        for (int dy = -3; dy <= 1; dy++) {
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    BlockPos spot = target.offset(dx, dy, dz);
                    if (!standableOnGround(level, spot) || crew.isClaimed(spot, goblin)) continue;
                    Vec3 bottom = Vec3.atBottomCenterOf(spot);
                    if (horizontalDistance(bottom, owner.position()) > RingCrew.WORK_RADIUS || Math.abs(spot.getY() - owner.getY()) > WORK_DY) continue;
                    if (inWay(owner, bottom)) continue;
                    Vec3 spotEyes = bottom.add(0.0, goblin.getEyeHeight(), 0.0);
                    double reach = spotEyes.distanceToSqr(center);
                    if (reach > REACH_SQ || !Mining.canSee(level, spotEyes, target)) continue;
                    double cost = goblin.position().distanceToSqr(bottom) + (reach > COMFORT_REACH_SQ ? 1000.0 : 0.0);
                    candidates.add(new Candidate(spot.immutable(), cost));
                }
            }
        }
        candidates.sort(Comparator.comparingDouble(Candidate::cost));
        for (Candidate candidate : candidates) {
            if (goblin.position().distanceToSqr(Vec3.atBottomCenterOf(candidate.spot())) < 2.0) {
                return new SpotSearch(candidate.spot(), budget, false);
            }
            if (budget <= 0) return new SpotSearch(null, 0, true);
            budget--;
            if (reachable(candidate.spot())) return new SpotSearch(candidate.spot(), budget, false);
        }
        return new SpotSearch(null, budget, false);
    }

    private boolean tickMine(ServerLevel level, ServerPlayer owner) {
        if (mineTarget == null) return false;
        BlockState state = level.getBlockState(mineTarget);
        RingCrew.Session crew = goblin.crew();
        boolean fits = mode == Mode.ASSIST ? state.is(RingCrew.NATURAL) && crew != null && crew.assistFocus() != null
                : state.is(ConventionalBlockTags.ORES);
        if (!fits || Mining.verdict(level, mineTarget, state, goblin) != Mining.Verdict.OK) return false;
        long now = level.getGameTime();
        if (targetedBy(owner, mineTarget)) {
            skippedBlocks.put(mineTarget, now + SKIP_TICKS); // the player is mining it
            return false;
        }
        int slot = Mining.bestToolSlot(goblin, state);
        ItemStack tool = slot < 0 ? ItemStack.EMPTY : goblin.getInventory().getItem(slot);
        if (!ItemStack.isSameItem(goblin.getMainHandItem(), tool)) goblin.setItemSlot(EquipmentSlot.MAINHAND, tool.copy());
        Vec3 center = Vec3.atCenterOf(mineTarget);
        goblin.getLookControl().setLookAt(center);
        Vec3 eyes = goblin.getEyePosition();
        if (goblin.onGround() && eyes.distanceToSqr(center) <= REACH_SQ && Mining.canSee(level, eyes, mineTarget)) {
            goblin.getNavigation().stop();
            goblin.swing(InteractionHand.MAIN_HAND);
            progress += Mining.progressPerTick(level, mineTarget, state, goblin, tool);
            if (progress < 1.0f) {
                level.destroyBlockProgress(goblin.getId(), mineTarget, (int) (progress * 10.0f));
                return true;
            }
            level.destroyBlockProgress(goblin.getId(), mineTarget, -1);
            progress = 0.0f;
            drops.addAll(Mining.harvest(level, mineTarget, state, goblin, tool));
            return false;
        }
        if (progress > 0.0f) {
            progress = 0.0f;
            level.destroyBlockProgress(goblin.getId(), mineTarget, -1);
        }
        if (stand == null || !standableOnGround(level, stand)) return false;
        if (!walkTo(Vec3.atBottomCenterOf(stand), WALK_SPEED)) {
            skippedBlocks.put(mineTarget, now + SKIP_TICKS);
            return false;
        }
        return true;
    }

    /** True if the player's crosshair is on that block: the player is about to mine it, the goblin leaves it alone. */
    private static boolean targetedBy(Player owner, BlockPos pos) {
        HitResult hit = owner.pick(5.0, 1.0f, false);
        return hit.getType() == HitResult.Type.BLOCK && hit instanceof BlockHitResult block && block.getBlockPos().equals(pos);
    }

    // ---- loot ----------------------------------------------------------------------------------------------------

    @Nullable
    private ItemEntity nextItem(ServerLevel level, ServerPlayer owner, RingCrew.Session crew, RingContainer ring, long now) {
        AABB area = new AABB(owner.blockPosition()).inflate(RingCrew.WORK_RADIUS, WORK_DY, RingCrew.WORK_RADIUS);
        ItemEntity best = null;
        double bestCost = Double.MAX_VALUE;
        for (ItemEntity candidate : level.getEntitiesOfClass(ItemEntity.class, area, ItemEntity::isAlive)) {
            if (skippedItems.containsKey(candidate.getId()) || crew.isClaimed(candidate, goblin)) continue;
            if (!candidate.onGround() || !RingCrew.collectable(candidate, owner) || !ring.canAddItem(candidate.getItem())) continue;
            if (HomeRegistry.isProtected(level, candidate.blockPosition()) || inLane(owner, candidate.position())) continue;
            double cost = goblin.distanceToSqr(candidate) - (drops.contains(candidate) ? 64.0 : 0.0);
            if (cost < bestCost) {
                bestCost = cost;
                best = candidate;
            }
        }
        chosenItemStand = null;
        if (best != null && goblin.distanceToSqr(best) > PICKUP_SQ && !inArmReach(level, best) && !reachable(best.blockPosition())) {
            // in a niche of the wall or up on a ledge: a spot to reach in from, or leave it
            chosenItemStand = reachInSpot(level, owner, best);
            if (chosenItemStand == null) {
                skippedItems.put(best.getId(), now + SKIP_TICKS);
                return null;
            }
        }
        return best;
    }

    private boolean inArmReach(ServerLevel level, ItemEntity target) {
        Vec3 eyes = goblin.getEyePosition();
        return eyes.distanceToSqr(target.position()) <= ARM_REACH_SQ && Mining.canSee(level, eyes, target.blockPosition());
    }

    /** A spot from which the goblin can reach the item by hand (in reach, in sight) and which it can walk to. */
    @Nullable
    private BlockPos reachInSpot(ServerLevel level, ServerPlayer owner, ItemEntity target) {
        BlockPos at = target.blockPosition();
        Vec3 point = target.position();
        List<BlockPos> spots = new ArrayList<>();
        for (int dy = -3; dy <= 0; dy++) {
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    BlockPos spot = at.offset(dx, dy, dz);
                    if (!standable(level, spot)) continue;
                    Vec3 bottom = Vec3.atBottomCenterOf(spot);
                    Vec3 eyes = bottom.add(0.0, goblin.getEyeHeight(), 0.0);
                    if (eyes.distanceToSqr(point) > ARM_REACH_SQ || inLane(owner, bottom) || !Mining.canSee(level, eyes, at)) continue;
                    spots.add(spot.immutable());
                }
            }
        }
        Vec3 here = goblin.position();
        spots.sort(Comparator.comparingDouble(spot -> Vec3.atBottomCenterOf(spot).distanceToSqr(here)));
        int checks = 0;
        for (BlockPos spot : spots) {
            if (Vec3.atBottomCenterOf(spot).distanceToSqr(here) < 2.0) return spot;
            if (checks++ >= 2) break;
            if (reachable(spot)) return spot;
        }
        return null;
    }

    private boolean tickLoot(ServerLevel level, ServerPlayer owner) {
        if (item == null || !item.isAlive()) return false;
        Vec3 point = item.position();
        if (horizontalDistance(point, owner.position()) > RingCrew.LEASH) return false;
        goblin.getLookControl().setLookAt(point);
        if (goblin.distanceToSqr(point) <= PICKUP_SQ || (goblin.onGround() && inArmReach(level, item))) {
            goblin.getNavigation().stop();
            if (item.hasPickUpDelay()) return true; // still popping out of the block
            goblin.swing(InteractionHand.MAIN_HAND);
            if (!RingCrew.store(goblin, item)) skippedItems.put(item.getId(), level.getGameTime() + SKIP_TICKS);
            return false;
        }
        if (!walkTo(itemStand != null ? Vec3.atBottomCenterOf(itemStand) : point, WALK_SPEED)) {
            skippedItems.put(item.getId(), level.getGameTime() + SKIP_TICKS);
            return false;
        }
        return true;
    }

    private static boolean hasEmptySlot(RingContainer ring) {
        for (int i = 0; i < ring.getContainerSize(); i++) {
            if (ring.getItem(i).isEmpty()) return true;
        }
        return false;
    }

    // ---- unloading -----------------------------------------------------------------------------------------------

    /** The nearest goblin chest within 16 blocks of the goblin (loaded chunks only) that is also near the player. */
    @Nullable
    private BlockPos findChest(ServerLevel level, ServerPlayer owner) {
        BlockPos here = goblin.blockPosition();
        int range = RingCrew.CHEST_RANGE;
        BlockPos best = null;
        double bestDist = range * range;
        for (int cx = (here.getX() - range) >> 4; cx <= (here.getX() + range) >> 4; cx++) {
            for (int cz = (here.getZ() - range) >> 4; cz <= (here.getZ() + range) >> 4; cz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) continue;
                for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
                    if (!(blockEntity instanceof GoblinChestBlockEntity)) continue;
                    BlockPos pos = blockEntity.getBlockPos();
                    if (skippedBlocks.containsKey(pos)) continue;
                    double d = pos.distSqr(here);
                    if (d > bestDist || horizontalDistance(Vec3.atCenterOf(pos), owner.position()) > RingCrew.CHEST_RANGE) continue;
                    bestDist = d;
                    best = pos.immutable();
                }
            }
        }
        return best;
    }

    /** Walks to the chest, lifts the lid and moves one stack from the ring into it every few ticks. */
    private boolean tickDeposit(ServerLevel level, ServerPlayer owner, RingCrew.Session crew) {
        if (chest == null || !(level.getBlockEntity(chest) instanceof GoblinChestBlockEntity)) return false;
        long now = level.getGameTime();
        Vec3 center = Vec3.atCenterOf(chest);
        if (horizontalDistance(center, owner.position()) > RingCrew.CHEST_RANGE) return false;
        RingContainer ring = RingInventory.open(owner, crew.ringId());
        if (ring == null || ring.isEmpty()) return false;
        goblin.getLookControl().setLookAt(center);
        if (goblin.getEyePosition().distanceToSqr(center) > CHEST_REACH_SQ) {
            closeLid(level);
            if (chestStand == null) {
                chestStand = besideChest(level, owner, chest);
                if (chestStand == null) {
                    skippedBlocks.put(chest, now + SKIP_TICKS);
                    return false;
                }
            }
            if (!walkTo(Vec3.atBottomCenterOf(chestStand), WALK_SPEED)) {
                skippedBlocks.put(chest, now + SKIP_TICKS);
                return false;
            }
            return true;
        }
        goblin.getNavigation().stop();
        if (!lidOpen) {
            openLid(level);
            storeTimer = STORE_INTERVAL;
            goblin.swing(InteractionHand.MAIN_HAND);
            return true;
        }
        if (--storeTimer > 0) return true;
        storeTimer = STORE_INTERVAL;
        Storage<ItemVariant> storage = ItemStorage.SIDED.find(level, chest, Direction.UP);
        if (storage != null) {
            for (int i = 0; i < ring.getContainerSize(); i++) {
                ItemStack stack = ring.getItem(i);
                if (stack.isEmpty()) continue;
                long inserted;
                try (Transaction tx = Transaction.openOuter()) {
                    inserted = storage.insert(ItemVariant.of(stack), stack.getCount(), tx);
                    tx.commit();
                }
                if (inserted <= 0) continue;
                stack.shrink((int) inserted);
                ring.setItem(i, stack.isEmpty() ? ItemStack.EMPTY : stack); // writes the ring
                goblin.swing(InteractionHand.MAIN_HAND);
                return true;
            }
        }
        skippedBlocks.put(chest, now + SKIP_TICKS); // the chest is full
        return false;
    }

    @Nullable
    private BlockPos besideChest(ServerLevel level, ServerPlayer owner, BlockPos chestPos) {
        List<BlockPos> spots = new ArrayList<>();
        Vec3 center = Vec3.atCenterOf(chestPos);
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dz == 0) continue;
                    BlockPos spot = chestPos.offset(dx, dy, dz);
                    if (!standable(level, spot)) continue;
                    Vec3 bottom = Vec3.atBottomCenterOf(spot);
                    if (bottom.add(0.0, goblin.getEyeHeight(), 0.0).distanceToSqr(center) > CHEST_REACH_SQ || inWay(owner, bottom)) continue;
                    spots.add(spot.immutable());
                }
            }
        }
        Vec3 here = goblin.position();
        spots.sort(Comparator.comparingDouble(spot -> Vec3.atBottomCenterOf(spot).distanceToSqr(here)));
        int checks = 0;
        for (BlockPos spot : spots) {
            if (Vec3.atBottomCenterOf(spot).distanceToSqr(here) < 2.0) return spot;
            if (checks++ >= MAX_PATH_CHECKS) break;
            if (reachable(spot)) return spot;
        }
        return null;
    }

    private void openLid(ServerLevel level) {
        if (chest != null && level.getBlockEntity(chest) instanceof ChestBlockEntity blockEntity) {
            goblin.setOpenChest(chest);
            blockEntity.startOpen(goblin);
            lidOpen = true;
        }
    }

    private void closeLid(ServerLevel level) {
        if (!lidOpen) return;
        lidOpen = false;
        if (chest != null && level.getBlockEntity(chest) instanceof ChestBlockEntity blockEntity) blockEntity.stopOpen(goblin);
        goblin.setOpenChest(null);
    }

    // ---- idling --------------------------------------------------------------------------------------------------

    private void tickIdle(ServerLevel level, ServerPlayer owner) {
        PathNavigation navigation = goblin.getNavigation();
        RandomSource random = goblin.getRandom();
        if (walking) {
            if (navigation.isDone() || ++walkTicks > WALK_TIMEOUT || (walkTarget != null && inWay(owner, Vec3.atBottomCenterOf(walkTarget)))) {
                navigation.stop();
                walking = false;
                pauseTicks = random.nextInt(PAUSE_MIN, PAUSE_MAX);
            }
            return;
        }
        if (--pauseTicks > 0) {
            idleLook(owner);
            return;
        }
        BlockPos spot = strollSpot(level, owner);
        if (spot != null && navigation.moveTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, STROLL_SPEED)) {
            walking = true;
            walkTicks = 0;
            walkTarget = spot;
            holdSpot(spot);
            lookTicks = 0;
        } else {
            pauseTicks = random.nextInt(20, 40);
        }
    }

    /** A short walk: a free spot 2.5 to 5.5 blocks from the player, in the player's sight and out of its way. */
    @Nullable
    private BlockPos strollSpot(ServerLevel level, ServerPlayer owner) {
        RandomSource random = goblin.getRandom();
        Set<BlockPos> taken = takenSpots();
        int feetY = owner.blockPosition().getY();
        for (int attempt = 0; attempt < 8; attempt++) {
            double angle = random.nextDouble() * Mth.TWO_PI;
            double r = 2.5 + random.nextDouble() * 3.0;
            int x = Mth.floor(owner.getX() + Math.cos(angle) * r);
            int z = Mth.floor(owner.getZ() + Math.sin(angle) * r);
            for (int y = feetY + 2; y >= feetY - 3; y--) {
                BlockPos spot = new BlockPos(x, y, z);
                if (!standable(level, spot)) continue;
                if (RingCrew.nearTaken(spot, taken)) break; // a crew mate stands or heads there, or right next to it
                Vec3 bottom = Vec3.atBottomCenterOf(spot);
                if (clearOfWay(owner, bottom) && goblin.position().distanceToSqr(bottom) >= 4.0
                        && Mining.canSee(level, owner.getEyePosition(), spot.below())) {
                    Path path = goblin.getNavigation().createPath(spot, 0);
                    if (path != null && path.canReach()) return spot;
                }
                break;
            }
        }
        return null;
    }

    private void idleLook(ServerPlayer owner) {
        RandomSource random = goblin.getRandom();
        if (lookTicks > 0) {
            lookTicks--;
            if (lookPoint == null) goblin.getLookControl().setLookAt(owner, 10.0f, goblin.getMaxHeadXRot());
            else goblin.getLookControl().setLookAt(lookPoint);
            return;
        }
        if (random.nextFloat() < 0.02f) {
            lookPoint = null; // a look at the boss
            lookTicks = 30 + random.nextInt(50);
        } else if (random.nextFloat() < 0.02f) {
            double angle = random.nextDouble() * Mth.TWO_PI;
            lookPoint = new Vec3(goblin.getX() + Math.cos(angle) * 3.0, goblin.getEyeY() + random.nextDouble() - 0.5, goblin.getZ() + Math.sin(angle) * 3.0);
            lookTicks = 20 + random.nextInt(30);
        }
    }

    // ---- movement helpers ----------------------------------------------------------------------------------------

    /** Walks towards the point, the last steps straight to it. False once the goblin made no headway for a while. */
    private boolean walkTo(Vec3 point, double speed) {
        PathNavigation navigation = goblin.getNavigation();
        double dx = point.x - goblin.getX(), dz = point.z - goblin.getZ();
        if (dx * dx + dz * dz <= 1.5 * 1.5 && Math.abs(point.y - goblin.getY()) <= 1.2) {
            navigation.stop();
            goblin.getMoveControl().setWantedPosition(point.x, point.y, point.z, speed * 0.8);
        } else if (goblin.tickCount % 10 == 0 || (navigation.isDone() && goblin.tickCount % 5 == 0)) {
            navigation.moveTo(point.x, point.y, point.z, speed);
        }
        return headway(point);
    }

    private boolean headway(Vec3 point) {
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

    private boolean reachable(BlockPos pos) {
        Path path = goblin.getNavigation().createPath(pos, 1);
        return path != null && path.canReach();
    }

    private static double horizontalDistance(Vec3 a, Vec3 b) {
        double dx = a.x - b.x, dz = a.z - b.z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    /** Room for a goblin at {@code pos}: two open blocks without fluid on top of something solid. */
    public static boolean standable(ServerLevel level, BlockPos pos) {
        BlockPos above = pos.above(), below = pos.below();
        return level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()
                && level.getBlockState(above).getCollisionShape(level, above).isEmpty()
                && level.getFluidState(pos).isEmpty() && level.getFluidState(above).isEmpty()
                && !level.getBlockState(below).getCollisionShape(level, below).isEmpty();
    }

    private static boolean standableOnGround(ServerLevel level, BlockPos pos) {
        BlockState ground = level.getBlockState(pos.below());
        return standable(level, pos) && !ground.is(BlockTags.LEAVES) && !Climber.isScaffold(ground);
    }
}
