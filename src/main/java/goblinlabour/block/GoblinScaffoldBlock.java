package goblinlabour.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The block goblins stack under themselves to reach high logs and to climb out of pits. A copy of vanilla
 * scaffolding ({@code ScaffoldingBlock}), working with its own block instead of {@code minecraft:scaffolding}: you
 * can stand on top of it, climb inside it (it is in the {@code minecraft:climbable} tag) and sneak to drop through
 * it; every block keeps its distance to a supporting block, so breaking the bottom of a column brings down everything
 * above it. Not craftable, drops nothing. Goblins never break it; a column removes itself two minutes after a goblin
 * last touched it, so it stays up while goblins are using it.
 */
public class GoblinScaffoldBlock extends Block implements SimpleWaterloggedBlock {
    public static final MapCodec<GoblinScaffoldBlock> CODEC = simpleCodec(GoblinScaffoldBlock::new);
    /** Two minutes. */
    public static final int LIFETIME_TICKS = 2400;
    public static final int STABILITY_MAX_DISTANCE = 7;
    public static final IntegerProperty DISTANCE = BlockStateProperties.STABILITY_DISTANCE;
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
    public static final BooleanProperty BOTTOM = BlockStateProperties.BOTTOM;
    private static final int COLUMN_SCAN = 256;
    private static final int PRUNE_ABOVE = 8192;
    private static final int SWEEP_INTERVAL = 20;

    /**
     * Game time of the last goblin touch per block position, per dimension. Lost on restart; a random tick puts an
     * unknown block back in (see {@link #randomTick}).
     */
    private static final Map<ResourceKey<Level>, Map<Long, Long>> TOUCHED = new ConcurrentHashMap<>();

    private static final VoxelShape SHAPE_STABLE = Shapes.or(
            Block.box(0, 14, 0, 16, 16, 16),
            Block.box(0, 0, 0, 2, 16, 2),
            Block.box(14, 0, 0, 16, 16, 2),
            Block.box(0, 0, 14, 2, 16, 16),
            Block.box(14, 0, 14, 16, 16, 16));
    private static final VoxelShape SHAPE_UNSTABLE_BOTTOM = Block.box(0, 0, 0, 16, 2, 16);
    private static final VoxelShape SHAPE_UNSTABLE = Shapes.or(SHAPE_STABLE, SHAPE_UNSTABLE_BOTTOM,
            Block.box(0, 0, 0, 16, 2, 2),
            Block.box(0, 0, 14, 16, 2, 16),
            Block.box(0, 0, 0, 2, 2, 16),
            Block.box(14, 0, 0, 16, 2, 16));
    private static final VoxelShape SHAPE_BELOW_BLOCK = Shapes.block().move(0.0, -1.0, 0.0).optimize();

    public GoblinScaffoldBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(DISTANCE, STABILITY_MAX_DISTANCE)
                .setValue(WATERLOGGED, false).setValue(BOTTOM, false));
    }

    @Override
    protected MapCodec<GoblinScaffoldBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(DISTANCE, WATERLOGGED, BOTTOM);
    }

    // ---- vanilla scaffolding -------------------------------------------------------------------------------------

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        if (context.isHoldingItem(state.getBlock().asItem())) return Shapes.block();
        return state.getValue(BOTTOM) ? SHAPE_UNSTABLE : SHAPE_STABLE;
    }

    @Override
    protected VoxelShape getInteractionShape(BlockState state, BlockGetter level, BlockPos pos) {
        return Shapes.block();
    }

    @Override
    protected boolean canBeReplaced(BlockState state, BlockPlaceContext context) {
        return context.getItemInHand().is(asItem());
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return stateAt(context.getLevel(), context.getClickedPos());
    }

    /** The state a scaffold block gets at {@code pos} (distance, bottom, water), whether it can stand there or not. */
    public BlockState stateAt(BlockGetter level, BlockPos pos) {
        int distance = getDistance(level, pos);
        return defaultBlockState()
                .setValue(WATERLOGGED, level.getFluidState(pos).is(Fluids.WATER))
                .setValue(DISTANCE, distance)
                .setValue(BOTTOM, isBottom(level, pos, distance));
    }

    /** The state for a goblin placing a block at {@code pos}, or null when nothing would hold it up there. */
    @Nullable
    public BlockState placementState(BlockGetter level, BlockPos pos) {
        BlockState state = stateAt(level, pos);
        return state.getValue(DISTANCE) < STABILITY_MAX_DISTANCE ? state : null;
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        if (!(level instanceof ServerLevel serverLevel)) return;
        level.scheduleTick(pos, this, 1);
        if (!oldState.is(this)) touched(serverLevel).put(pos.asLong(), serverLevel.getGameTime());
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction direction,
                                     BlockPos neighborPos, BlockState neighborState, RandomSource random) {
        if (state.getValue(WATERLOGGED)) ticks.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        if (!level.isClientSide()) ticks.scheduleTick(pos, this, 1);
        return state;
    }

    /** Vanilla stability: without support within seven blocks the block breaks (and so does everything it held). */
    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        int distance = getDistance(level, pos);
        BlockState updated = state.setValue(DISTANCE, distance).setValue(BOTTOM, isBottom(level, pos, distance));
        if (updated.getValue(DISTANCE) == STABILITY_MAX_DISTANCE) {
            // vanilla lets a block that never had support fall as an entity; a goblin scaffold just breaks
            level.destroyBlock(pos, false);
        } else if (state != updated) {
            level.setBlock(pos, updated, Block.UPDATE_ALL);
        }
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return getDistance(level, pos) < STABILITY_MAX_DISTANCE;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        if (context.isPlacement()) return Shapes.empty();
        if (context.isAbove(Shapes.block(), pos, true) && !context.isDescending()) return SHAPE_STABLE;
        return state.getValue(DISTANCE) != 0 && state.getValue(BOTTOM) && context.isAbove(SHAPE_BELOW_BLOCK, pos, true)
                ? SHAPE_UNSTABLE_BOTTOM : Shapes.empty();
    }

    @Override
    protected FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    private boolean isBottom(BlockGetter level, BlockPos pos, int distance) {
        return distance > 0 && !level.getBlockState(pos.below()).is(this);
    }

    /** 0 on solid ground, the block below's distance on a scaffold, else one more than the nearest side neighbour. */
    public static int getDistance(BlockGetter level, BlockPos pos) {
        BlockPos.MutableBlockPos cursor = pos.mutable().move(Direction.DOWN);
        BlockState below = level.getBlockState(cursor);
        int distance = STABILITY_MAX_DISTANCE;
        if (below.getBlock() instanceof GoblinScaffoldBlock) {
            distance = below.getValue(DISTANCE);
        } else if (below.isFaceSturdy(level, cursor, Direction.UP)) {
            return 0;
        }
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockState side = level.getBlockState(cursor.setWithOffset(pos, direction));
            if (side.getBlock() instanceof GoblinScaffoldBlock) {
                distance = Math.min(distance, side.getValue(DISTANCE) + 1);
                if (distance == 1) break;
            }
        }
        return distance;
    }

    // ---- lifetime ------------------------------------------------------------------------------------------------

    /** After a restart the touch times are gone: a random tick registers the block as touched just now. */
    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        touched(level).putIfAbsent(pos.asLong(), level.getGameTime());
    }

    /** Server level tick: removes scaffold blocks nobody touched for two minutes; the blocks above follow. */
    public static void sweep(ServerLevel level) {
        if (level.getGameTime() % SWEEP_INTERVAL != 0) return;
        Map<Long, Long> touched = TOUCHED.get(level.dimension());
        if (touched == null || touched.isEmpty()) return;
        long now = level.getGameTime();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (Iterator<Map.Entry<Long, Long>> it = touched.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Long, Long> entry = it.next();
            if (entry.getValue() > now) entry.setValue(now); // from another world with the same dimension
            if (entry.getValue() + LIFETIME_TICKS > now) continue;
            cursor.set(entry.getKey());
            if (!level.isLoaded(cursor)) continue; // decided when the chunk is back
            it.remove();
            if (level.getBlockState(cursor).getBlock() instanceof GoblinScaffoldBlock) {
                level.destroyBlock(cursor, false);
            }
        }
    }

    /** A goblin touched the block at {@code pos}: the whole column it belongs to starts its two minutes again. */
    public static void touchColumn(ServerLevel level, BlockPos pos) {
        Map<Long, Long> touched = touched(level);
        long now = level.getGameTime();
        if (touched.size() > PRUNE_ABOVE) touched.values().removeIf(t -> t + LIFETIME_TICKS * 4L < now);
        BlockPos.MutableBlockPos cursor = pos.mutable();
        for (int i = 0; i < COLUMN_SCAN && level.getBlockState(cursor).getBlock() instanceof GoblinScaffoldBlock; i++) {
            touched.put(cursor.asLong(), now);
            cursor.move(0, -1, 0);
        }
        cursor.set(pos).move(0, 1, 0);
        for (int i = 0; i < COLUMN_SCAN && level.getBlockState(cursor).getBlock() instanceof GoblinScaffoldBlock; i++) {
            touched.put(cursor.asLong(), now);
            cursor.move(0, 1, 0);
        }
    }

    private static Map<Long, Long> touched(ServerLevel level) {
        return TOUCHED.computeIfAbsent(level.dimension(), k -> new ConcurrentHashMap<>());
    }
}
