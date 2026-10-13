package goblinlabour.entity.ai;

import goblinlabour.GoblinSpeech;
import goblinlabour.block.GoblinBedBlockEntity;
import goblinlabour.entity.GoblinEntity;
import goblinlabour.job.Headway;
import goblinlabour.job.Mining;
import goblinlabour.job.Reach;
import goblinlabour.job.Standing;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;

/**
 * After a respawn the goblin walks back to where it died and picks up what its own death dropped there (two minutes,
 * within 100 blocks). Items it cannot walk to are taken from the rim of the hole if they are within arm's reach, or
 * from a safe spot beside them it blinks to; with no safe spot at all (lava at the bottom) it says so and gives up
 * rather than standing at the edge until the time is up. Other people's items are left alone.
 */
public class RecoverItemsGoal extends Goal {
    private static final double SEARCH_RADIUS = 6.0;
    private static final double MAX_DISTANCE_SQ = 100.0 * 100.0;
    private static final double PICKUP_SQ = 1.5 * 1.5;
    /** Reaching into a hole from its rim: the same arm's length a crew goblin uses. */
    private static final double ARM_REACH_SQ = 3.5 * 3.5;
    private static final int STUCK_TICKS = 100;
    /**
     * Items older than the goblin's death belong to somebody else. The death time is only known through the respawn
     * (five seconds later, {@link GoblinBedBlockEntity#RECOVER_TICKS} counts from there), so the slack covers that.
     */
    private static final int OWN_AGE_SLACK = 200;

    private static final boolean DEV = net.fabricmc.loader.api.FabricLoader.getInstance().isDevelopmentEnvironment();

    private final GoblinEntity goblin;
    private final Headway headway = new Headway();
    @Nullable private ItemEntity item;
    private boolean blinked;

    public RecoverItemsGoal(GoblinEntity goblin) {
        this.goblin = goblin;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        BlockPos pos = goblin.recoverPos();
        if (pos == null || goblin.isNoAi() || !(goblin.level() instanceof ServerLevel level)) return false;
        if (level.getGameTime() > goblin.recoverUntil() || goblin.distanceToSqr(Vec3.atCenterOf(pos)) > MAX_DISTANCE_SQ) {
            goblin.clearRecover();
            return false;
        }
        return true;
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
        GoblinSpeech.say((ServerLevel) goblin.level(), goblin.goblinName(), GoblinSpeech.FETCH_ITEMS);
        headway.reset();
        blinked = false;
    }

    @Override
    public void stop() {
        item = null;
        goblin.getNavigation().stop();
    }

    @Override
    public void tick() {
        ServerLevel level = (ServerLevel) goblin.level();
        BlockPos pos = goblin.recoverPos();
        if (pos == null) return;
        if (goblin.climber().comeDownFirst(level)) return;
        if (item == null || !item.isAlive() || !goblin.canStore(item.getItem())) {
            item = nextOwnItem(level, pos);
            if (DEV) {
                goblinlabour.GoblinLabour.LOGGER.info("Dev: recover {} at {}: item={} reachable={} safe={}", goblin.goblinName(),
                        goblin.blockPosition().toShortString(), item == null ? "-" : item.getItem().getItem() + "@" + item.blockPosition().toShortString(),
                        item != null && reachable(level), item == null ? "-" : String.valueOf(safeSpot(level, item.blockPosition())));
            }
            if (item == null) {
                goblin.clearRecover(); // nothing of its own left down there
                return;
            }
            headway.reset();
        }

        Vec3 target = item.position();
        goblin.getLookControl().setLookAt(target);
        double distance = goblin.distanceToSqr(target);
        if (distance <= PICKUP_SQ || distance <= ARM_REACH_SQ && !reachable(level) && Mining.canSee(level, goblin.getEyePosition(), item.blockPosition())) {
            // right there, or leaning over the rim into a hole it cannot climb into
            goblin.getNavigation().stop();
            if (item.hasPickUpDelay()) return;
            goblin.pickUp(item);
            item = null;
            return;
        }
        if (reachable(level)) {
            if (goblin.tickCount % 10 == 0) goblin.getNavigation().moveTo(target.x, target.y, target.z, 1.0);
            if (headway.tick(goblin.position(), target) > STUCK_TICKS) giveUp(level);
            return;
        }
        // no way down: blink onto a safe spot beside the items, once
        BlockPos safe = safeSpot(level, item.blockPosition());
        if (safe == null || blinked) {
            giveUp(level);
            return;
        }
        blinked = true;
        goblin.blinkOnto(level, safe, "recover");
        headway.reset();
    }

    private boolean reachable(ServerLevel level) {
        return item != null && Reach.reachable(goblin.getNavigation(), item.blockPosition());
    }

    private void giveUp(ServerLevel level) {
        GoblinSpeech.say(level, goblin.goblinName(), GoblinSpeech.ITEMS_LOST);
        goblin.clearRecover();
        item = null;
    }

    /** The nearest item around the death spot that dropped when this goblin died and still fits into its storage. */
    @Nullable
    private ItemEntity nextOwnItem(ServerLevel level, BlockPos pos) {
        long age = level.getGameTime() - (goblin.recoverUntil() - GoblinBedBlockEntity.RECOVER_TICKS) + OWN_AGE_SLACK;
        List<ItemEntity> items = level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(SEARCH_RADIUS),
                candidate -> candidate.isAlive() && candidate.getAge() <= age && goblin.canStore(candidate.getItem()));
        return items.stream().min(Comparator.comparingDouble(goblin::distanceToSqr)).orElse(null);
    }

    /** A spot next to the items with room for a goblin, no fluid and no lava next to it. */
    @Nullable
    private BlockPos safeSpot(ServerLevel level, BlockPos itemPos) {
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        for (BlockPos spot : BlockPos.betweenClosed(itemPos.offset(-2, -1, -2), itemPos.offset(2, 1, 2))) {
            if (!Standing.dry(level, spot) || Mining.lavaAdjacent(level, spot)) continue;
            double d = Vec3.atBottomCenterOf(spot).distanceToSqr(Vec3.atCenterOf(itemPos));
            if (d < bestDist) {
                bestDist = d;
                best = spot.immutable();
            }
        }
        return best;
    }
}
