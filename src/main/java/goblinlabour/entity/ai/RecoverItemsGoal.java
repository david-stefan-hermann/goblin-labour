package goblinlabour.entity.ai;

import goblinlabour.GoblinSpeech;
import goblinlabour.entity.GoblinEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;

/**
 * After a respawn the goblin walks back to where it died and picks up whatever of its dropped storage is still
 * lying around (two minutes, within 100 blocks). Gives up when nothing is left, nothing fits, or it cannot get there.
 */
public class RecoverItemsGoal extends Goal {
    private static final double SEARCH_RADIUS = 6.0;
    private static final double MAX_DISTANCE_SQ = 100.0 * 100.0;
    private static final double PICKUP_SQ = 1.5 * 1.5;
    private static final int STUCK_LIMIT = 200;

    private final GoblinEntity goblin;
    private ItemEntity item;
    private int stuckTicks;
    private Vec3 lastPos = Vec3.ZERO;

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
        stuckTicks = 0;
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
        if (item == null || !item.isAlive()) {
            List<ItemEntity> items = level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(SEARCH_RADIUS), ItemEntity::isAlive);
            item = items.stream().min(Comparator.comparingDouble(goblin::distanceToSqr)).orElse(null);
            if (item == null) {
                goblin.clearRecover();
                return;
            }
        }
        Vec3 target = item.position();
        goblin.getLookControl().setLookAt(target);
        if (goblin.distanceToSqr(target) <= PICKUP_SQ) {
            ItemStack rest = goblin.storeInStorage(item.getItem());
            if (rest.isEmpty()) {
                item.discard();
            } else {
                item.setItem(rest);
                goblin.clearRecover(); // full, the rest stays on the ground
            }
            item = null;
            return;
        }
        if (goblin.tickCount % 10 == 0) goblin.getNavigation().moveTo(target.x, target.y, target.z, 1.0);
        if (goblin.position().distanceToSqr(lastPos) < 0.01) {
            if (++stuckTicks > STUCK_LIMIT) goblin.clearRecover();
        } else {
            stuckTicks = 0;
        }
        lastPos = goblin.position();
    }
}
