package goblinlabour.job;

import goblinlabour.entity.GoblinEntity;
import goblinlabour.home.HomeRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Set;

/**
 * Harvests ripe crops, nether wart, pumpkins and melons within {@code length} blocks of the bed and replants with the
 * seed item whose block is the harvested crop, taken from the fresh drops or the goblin's storage. Wart blocks are
 * harvested like Minions Remastered's hoe job. Endless. Homes are excluded like everywhere else, so the field must
 * lie outside the home.
 */
public final class FarmJob implements JobTask {
    public static final FarmJob INSTANCE = new FarmJob();
    private static final int VERTICAL = 3;

    private FarmJob() {
    }

    @Override
    public boolean endless() {
        return true;
    }

    @Override
    public BlockPos entryPoint(JobHost bedEntity, JobConfig config) {
        return bedEntity.getBlockPos().relative(config.direction(), 6);
    }

    static boolean ripe(BlockState state) {
        Block block = state.getBlock();
        if (block instanceof CropBlock crop) return crop.isMaxAge(state);
        if (block instanceof NetherWartBlock) return state.getValue(NetherWartBlock.AGE) >= NetherWartBlock.MAX_AGE;
        return state.is(Blocks.PUMPKIN) || state.is(Blocks.MELON) || state.is(BlockTags.WART_BLOCKS);
    }

    @Override
    public Pick pick(ServerLevel level, GoblinEntity goblin, JobHost bedEntity, JobConfig config, Set<BlockPos> skipped) {
        BlockPos bed = bedEntity.getBlockPos();
        int r = config.length();
        Vec3 me = goblin.position();
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int x = bed.getX() - r; x <= bed.getX() + r; x++) {
            for (int z = bed.getZ() - r; z <= bed.getZ() + r; z++) {
                if (!level.isLoaded(cursor.set(x, bed.getY(), z))) continue;
                for (int y = bed.getY() - VERTICAL; y <= bed.getY() + VERTICAL; y++) {
                    cursor.set(x, y, z);
                    BlockState state = level.getBlockState(cursor);
                    if (!ripe(state)) continue;
                    if (Mining.verdict(level, cursor, state, goblin) != Mining.Verdict.OK || skipped.contains(cursor)) continue;
                    double d = me.distanceToSqr(Vec3.atCenterOf(cursor));
                    if (d < bestDist) {
                        bestDist = d;
                        best = cursor.immutable();
                    }
                }
            }
        }
        return best != null ? Pick.of(best) : Pick.DONE;
    }

    /** Remembered by the runner between pick and afterBreak: which crop stood there. */
    @Override
    public void afterBreak(ServerLevel level, GoblinEntity goblin, JobHost bedEntity, JobConfig config, BlockPos broken) {
        Block crop = goblin.runner().lastBrokenBlock();
        if (crop == null || !(crop instanceof CropBlock || crop instanceof NetherWartBlock)) return;
        if (!level.getBlockState(broken).isAir() || HomeRegistry.isProtected(level, broken)) return;
        BlockState state = crop.defaultBlockState();
        if (!state.canSurvive(level, broken)) return;
        // the seeds just popped out of the crop; a seed from the storage does as well
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, new AABB(broken).inflate(1.0), ItemEntity::isAlive)) {
            ItemStack stack = item.getItem();
            if (Block.byItem(stack.getItem()) != crop) continue;
            level.setBlock(broken, state, 3);
            if (stack.getCount() <= 1) {
                item.discard();
            } else {
                item.setItem(stack.copyWithCount(stack.getCount() - 1));
            }
            return;
        }
        SimpleContainer inv = goblin.getInventory();
        for (int i = GoblinEntity.HOTBAR_SIZE; i < GoblinEntity.INVENTORY_SIZE; i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.isEmpty() || Block.byItem(stack.getItem()) != crop) continue;
            level.setBlock(broken, state, 3);
            stack.shrink(1);
            inv.setChanged();
            return;
        }
    }
}
