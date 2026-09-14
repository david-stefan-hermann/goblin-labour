package goblinlabour.job;

import goblinlabour.block.GoblinBedBlockEntity;
import goblinlabour.entity.GoblinEntity;
import goblinlabour.home.HomeRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Set;

/**
 * Fells every log within {@code length} blocks of the bed (outside homes), lowest logs first so trunks come down
 * from the bottom and leaves decay by themselves. If the goblin carries a matching sapling it replants the stump.
 * Endless: the goblin stays on duty and checks for new trees.
 */
public final class ChopJob implements JobTask {
    public static final ChopJob INSTANCE = new ChopJob();
    private static final int BELOW = 3;
    private static final int ABOVE = 24;

    private ChopJob() {
    }

    @Override
    public boolean endless() {
        return true;
    }

    @Override
    public BlockPos entryPoint(GoblinBedBlockEntity bedEntity, JobConfig config) {
        return bedEntity.getBlockPos().relative(config.direction(), 6);
    }

    @Override
    public Pick pick(ServerLevel level, GoblinEntity goblin, GoblinBedBlockEntity bedEntity, JobConfig config, Set<BlockPos> skipped) {
        BlockPos bed = bedEntity.getBlockPos();
        int r = config.length();
        BlockPos best = null;
        long bestKey = Long.MAX_VALUE;
        boolean sawToolProblem = false;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int x = bed.getX() - r; x <= bed.getX() + r; x++) {
            for (int z = bed.getZ() - r; z <= bed.getZ() + r; z++) {
                if (!level.isLoaded(cursor.set(x, bed.getY(), z))) continue;
                for (int y = bed.getY() - BELOW; y <= bed.getY() + ABOVE; y++) {
                    cursor.set(x, y, z);
                    BlockState state = level.getBlockState(cursor);
                    if (!state.is(BlockTags.LOGS)) continue;
                    Mining.Verdict verdict = Mining.verdict(level, cursor, state, goblin);
                    if (verdict == Mining.Verdict.NEEDS_TOOL) {
                        sawToolProblem = true;
                        continue;
                    }
                    if (verdict != Mining.Verdict.OK || skipped.contains(cursor)) continue;
                    long dx = x - bed.getX(), dz = z - bed.getZ();
                    long key = (long) (y - (bed.getY() - BELOW)) * 100_000L + dx * dx + dz * dz;
                    if (key < bestKey) {
                        bestKey = key;
                        best = cursor.immutable();
                    }
                }
            }
        }
        if (best != null) return Pick.of(best);
        return sawToolProblem ? Pick.NEEDS_TOOL : Pick.DONE;
    }

    @Override
    public void afterBreak(ServerLevel level, GoblinEntity goblin, GoblinBedBlockEntity bedEntity, JobConfig config, BlockPos broken) {
        // only the stump: something solid below, no log (26.2's #minecraft:dirt no longer lists grass, so the sapling's
        // own canSurvive decides whether the ground is right)
        BlockPos below = broken.below();
        BlockState ground = level.getBlockState(below);
        if (ground.isAir() || ground.is(BlockTags.LOGS)) return;
        if (!level.getBlockState(broken).isAir() || HomeRegistry.isProtected(level, broken)) return;
        SimpleContainer inv = goblin.getInventory();
        for (int i = GoblinEntity.HOTBAR_SIZE; i < GoblinEntity.INVENTORY_SIZE; i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.isEmpty() || !stack.is(ItemTags.SAPLINGS)) continue;
            Block sapling = Block.byItem(stack.getItem());
            BlockState state = sapling.defaultBlockState();
            if (!state.canSurvive(level, broken)) continue;
            level.setBlock(broken, state, 3);
            stack.shrink(1);
            inv.setChanged();
            return;
        }
    }
}
