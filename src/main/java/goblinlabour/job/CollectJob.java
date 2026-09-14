package goblinlabour.job;

import goblinlabour.block.GoblinBedBlockEntity;
import goblinlabour.entity.GoblinEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * Picks up loose items within {@code length} blocks of the bed (the home included) and brings them to the goblin
 * chests like any other loot. Works on item entities instead of blocks, so the runner drives it through
 * {@link #findItem} rather than {@link #pick}. Endless.
 */
public final class CollectJob implements JobTask {
    public static final CollectJob INSTANCE = new CollectJob();
    private static final int VERTICAL = 6;

    private CollectJob() {
    }

    @Override
    public boolean endless() {
        return true;
    }

    @Override
    public BlockPos entryPoint(GoblinBedBlockEntity bed, JobConfig config) {
        return bed.getBlockPos().relative(config.direction(), 6);
    }

    /** Collecting has no blocks to break. */
    @Override
    public Pick pick(ServerLevel level, GoblinEntity goblin, GoblinBedBlockEntity bed, JobConfig config, Set<BlockPos> skipped) {
        return Pick.DONE;
    }

    /** The nearest item in the radius that can be picked up now and fits into the storage, or null. */
    @Nullable
    public ItemEntity findItem(ServerLevel level, GoblinEntity goblin, GoblinBedBlockEntity bed, JobConfig config, Set<Integer> skipped) {
        int r = config.length();
        AABB area = new AABB(bed.getBlockPos()).inflate(r, VERTICAL, r);
        ItemEntity best = null;
        double bestDist = Double.MAX_VALUE;
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, area, ItemEntity::isAlive)) {
            if (item.hasPickUpDelay() || skipped.contains(item.getId()) || !goblin.canStore(item.getItem())) continue;
            double d = goblin.distanceToSqr(item);
            if (d < bestDist) {
                bestDist = d;
                best = item;
            }
        }
        return best;
    }
}
