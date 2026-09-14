package goblinlabour.block;

import com.mojang.serialization.MapCodec;
import goblinlabour.entity.GoblinEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The block goblins stack under themselves to reach high logs. Behaves like vanilla scaffolding: you can stand on
 * top of it, climb inside it (it is in the {@code minecraft:climbable} tag) and sneak to drop through it. Not
 * craftable, drops nothing. Every block removes itself one minute after it was placed; only while a goblin is
 * still in that column does it wait a little longer, so nothing is left behind when a goblin dies or teleports
 * away mid-climb.
 */
public class GoblinScaffoldBlock extends Block {
    public static final MapCodec<GoblinScaffoldBlock> CODEC = simpleCodec(GoblinScaffoldBlock::new);
    /** One minute. */
    public static final int LIFETIME_TICKS = 1200;
    private static final int EXTEND_TICKS = 200;
    private static final double COLUMN_RANGE_XZ = 2.0;
    private static final double COLUMN_RANGE_Y = 48.0;

    private static final VoxelShape SHAPE_STABLE = Shapes.or(
            Block.box(0, 14, 0, 16, 16, 16),
            Block.box(0, 0, 0, 2, 16, 2),
            Block.box(14, 0, 0, 16, 16, 2),
            Block.box(0, 0, 14, 2, 16, 16),
            Block.box(14, 0, 14, 16, 16, 16));

    public GoblinScaffoldBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<GoblinScaffoldBlock> codec() {
        return CODEC;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE_STABLE;
    }

    @Override
    protected VoxelShape getInteractionShape(BlockState state, BlockGetter level, BlockPos pos) {
        return Shapes.block();
    }

    /** Solid only for whoever stands on top and is not sneaking; inside it there is no collision, like scaffolding. */
    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        if (context.isAbove(Shapes.block(), pos, true) && !context.isDescending()) return SHAPE_STABLE;
        return Shapes.empty();
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        level.scheduleTick(pos, this, LIFETIME_TICKS);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        AABB column = new AABB(pos).inflate(COLUMN_RANGE_XZ, COLUMN_RANGE_Y, COLUMN_RANGE_XZ);
        if (level.getEntitiesOfClass(GoblinEntity.class, column).isEmpty()) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        } else {
            level.scheduleTick(pos, this, EXTEND_TICKS);
        }
    }
}
