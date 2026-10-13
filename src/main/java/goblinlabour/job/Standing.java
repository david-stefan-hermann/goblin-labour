package goblinlabour.job;

import goblinlabour.GoblinLabour;
import goblinlabour.block.GoblinBedBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.state.BlockState;

/**
 * "Can a goblin stand at this block?" in the variants the goals need. They differ on purpose, so every caller names
 * the one it wants:
 * <ul>
 *   <li>{@link #room}: the block and the one above it have no collision, the block below has some, and no bed's
 *   foot end reaches into it.</li>
 *   <li>{@link #dry}: {@link #room} without fluid in the two blocks the goblin fills.</li>
 *   <li>{@link #onGround}: {@link #room} on real ground: not leaves, logs (a tree), scaffold or a fluid.</li>
 *   <li>{@link #dryOffLeaves}: {@link #dry}, not on leaves or scaffold (logs count as ground).</li>
 *   <li>{@link #climbable}: goblin scaffold counts as open to stand in and as ground to stand on, a block with a fluid
 *   and no collision is no ground.</li>
 * </ul>
 */
public final class Standing {
    private Standing() {
    }

    public static boolean room(ServerLevel level, BlockPos pos) {
        BlockPos above = pos.above(), below = pos.below();
        return level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()
                && level.getBlockState(above).getCollisionShape(level, above).isEmpty()
                && !level.getBlockState(below).getCollisionShape(level, below).isEmpty()
                && !GoblinBedBlock.isFootEnd(level, pos);
    }

    public static boolean dry(ServerLevel level, BlockPos pos) {
        BlockPos above = pos.above();
        return room(level, pos) && level.getFluidState(pos).isEmpty() && level.getFluidState(above).isEmpty();
    }

    public static boolean onGround(ServerLevel level, BlockPos pos) {
        BlockState ground = level.getBlockState(pos.below());
        return room(level, pos) && !ground.is(BlockTags.LEAVES) && !ground.is(BlockTags.LOGS) && !Climber.isScaffold(ground)
                && !ground.is(GoblinLabour.GOBLIN_STRAW_BED) && ground.getFluidState().isEmpty();
    }

    public static boolean dryOffLeaves(ServerLevel level, BlockPos pos) {
        BlockState ground = level.getBlockState(pos.below());
        return dry(level, pos) && !ground.is(BlockTags.LEAVES) && !Climber.isScaffold(ground);
    }

    public static boolean climbable(ServerLevel level, BlockPos pos) {
        if (!passable(level, pos) || !passable(level, pos.above())) return false;
        BlockPos below = pos.below();
        BlockState ground = level.getBlockState(below);
        if (!ground.getFluidState().isEmpty() && ground.getCollisionShape(level, below).isEmpty()) return false;
        return Climber.isScaffold(ground) || !ground.getCollisionShape(level, below).isEmpty();
    }

    /** No collision for a walking goblin (goblin scaffold counts as open, it can be climbed through). */
    public static boolean passable(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return Climber.isScaffold(state) || state.getCollisionShape(level, pos).isEmpty();
    }
}
