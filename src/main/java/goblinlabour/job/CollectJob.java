package goblinlabour.job;

import goblinlabour.entity.GoblinEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * Picks up loose items within {@code length} blocks of the bed (the home included) and brings them to the goblin
 * chests like any other loot. Works on item entities instead of blocks, so the runner drives it through
 * {@link #items} rather than {@link #pick}. Endless.
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
    public BlockPos entryPoint(JobHost bed, JobConfig config) {
        return bed.getBlockPos().relative(config.direction(), 6);
    }

    /** Collecting has no blocks to break. */
    @Override
    public Pick pick(ServerLevel level, GoblinEntity goblin, JobHost bed, JobConfig config, Set<BlockPos> skipped) {
        return Pick.DONE;
    }

    /**
     * The items in the radius that can be picked up now and fit into the storage, nearest first. Only items that have
     * landed on ground a goblin walks on: an item still falling, or lying on leaves, logs or a scaffold column (a
     * tree crown drops them there), waits until it comes down; walking under it only gets the goblin stuck.
     */
    public List<ItemEntity> items(ServerLevel level, GoblinEntity goblin, JobHost bed, JobConfig config, Set<Integer> skipped) {
        int r = config.length();
        AABB area = new AABB(bed.getBlockPos()).inflate(r, VERTICAL, r);
        return level.getEntitiesOfClass(ItemEntity.class, area, item -> item.isAlive() && !item.hasPickUpDelay()
                        && item.onGround() && !skipped.contains(item.getId()) && goblin.canStore(item.getItem())
                        && onWalkableGround(level, item))
                .stream().sorted(Comparator.comparingDouble(goblin::distanceToSqr)).toList();
    }

    private static boolean onWalkableGround(ServerLevel level, ItemEntity item) {
        BlockPos pos = item.blockPosition();
        BlockState at = level.getBlockState(pos), below = level.getBlockState(pos.below());
        return !isCanopy(at) && !isCanopy(below);
    }

    private static boolean isCanopy(BlockState state) {
        return state.is(BlockTags.LEAVES) || state.is(BlockTags.LOGS) || Climber.isScaffold(state);
    }
}
