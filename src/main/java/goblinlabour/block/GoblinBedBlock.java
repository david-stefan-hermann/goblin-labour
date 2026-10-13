package goblinlabour.block;

import goblinlabour.GoblinLabour;
import goblinlabour.menu.GoblinBedMenuProvider;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * A straw bed a block and a half long: the block holds the head end, the foot end reaches half a block into the block
 * behind it (towards whoever placed it). FACING points from the foot end towards the pillow. The foot's half block
 * belongs to the bed's shapes (the collision is 9 px high there too, so it can be stepped on like the old bed), and it
 * has to be free when the bed is placed.
 * <p>
 * Two beds stacked the same way round make a bunk bed: the lower one ({@link #BUNK}) gets foot posts a block high like
 * its head posts, which carry the upper bed's legs, and the upper one ({@link #UPPER}) leaves out the props under it,
 * which would hang in the air. A bed placed onto a bed turns the way the lower one does.
 */
public class GoblinBedBlock extends HorizontalDirectionalBlock implements EntityBlock {
    public static final MapCodec<GoblinBedBlock> CODEC = simpleCodec(GoblinBedBlock::new);
    public static final BooleanProperty OCCUPIED = BlockStateProperties.OCCUPIED;
    /** Kept in sync with the goblin's job by {@link GoblinBedBlockEntity}; only changes the model. */
    public static final EnumProperty<BedProps> PROPS = EnumProperty.create("props", BedProps.class);
    /** A bed facing the same way stands on this one: the bunk model with foot posts up to it. */
    public static final BooleanProperty BUNK = BooleanProperty.create("bunk");
    /** This bed stands on a bed facing the same way: no props under it. */
    public static final BooleanProperty UPPER = BooleanProperty.create("upper");
    /** Frame and blanket (10 px) over the whole length plus the headboard, for facing north; rotated per facing. */
    private static final Map<Direction, VoxelShape> OUTLINE = Shapes.rotateHorizontal(Shapes.or(
            Block.box(0, 0, 0, 16, 10, 24), Block.box(0, 10, 0, 16, 16, 2)));
    /** The straw top the goblin lies on (9 px, low enough to step onto) plus the headboard. */
    private static final Map<Direction, VoxelShape> COLLISION = Shapes.rotateHorizontal(Shapes.or(
            Block.box(0, 0, 0, 16, 9, 24), Block.box(0, 9, 0, 16, 16, 2)));
    /** A bunk's foot posts, up to the bed on top. */
    private static final VoxelShape FOOT_POSTS = Shapes.or(Block.box(0, 0, 22, 2, 16, 24), Block.box(14, 0, 22, 16, 16, 24));
    private static final Map<Direction, VoxelShape> BUNK_OUTLINE = Shapes.rotateHorizontal(Shapes.or(
            Block.box(0, 0, 0, 16, 10, 24), Block.box(0, 10, 0, 16, 16, 2), FOOT_POSTS));
    private static final Map<Direction, VoxelShape> BUNK_COLLISION = Shapes.rotateHorizontal(Shapes.or(
            Block.box(0, 0, 0, 16, 9, 24), Block.box(0, 9, 0, 16, 16, 2), FOOT_POSTS));

    public GoblinBedBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(OCCUPIED, false)
                .setValue(PROPS, BedProps.NONE).setValue(BUNK, false).setValue(UPPER, false));
    }

    @Override
    protected MapCodec<GoblinBedBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, OCCUPIED, PROPS, BUNK, UPPER);
    }

    /**
     * Like a vanilla bed: nothing solid may stand where the foot end goes, and no other bed's foot end may reach into
     * it (the bed's own block is kept free of foot ends by {@code BlockItemMixin}).
     */
    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockState below = level.getBlockState(pos.below());
        // onto another bed: turned like it, so the two make a bunk bed
        Direction facing = below.is(this) ? below.getValue(FACING) : context.getHorizontalDirection();
        BlockPos foot = pos.relative(facing.getOpposite());
        if (!level.getBlockState(foot).canBeReplaced(context) || !level.getWorldBorder().isWithinBounds(foot)
                || isFootEnd(level, foot)) return null;
        return stacked(defaultBlockState().setValue(FACING, facing), level, pos);
    }

    /** The state with {@link #BUNK} and {@link #UPPER} matching the beds above and below. */
    private BlockState stacked(BlockState state, LevelReader level, BlockPos pos) {
        return state.setValue(BUNK, sameWay(state, level.getBlockState(pos.above())))
                .setValue(UPPER, sameWay(state, level.getBlockState(pos.below())));
    }

    private boolean sameWay(BlockState state, BlockState other) {
        return other.is(this) && other.getValue(FACING) == state.getValue(FACING);
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos,
                                     Direction direction, BlockPos neighborPos, BlockState neighborState, RandomSource random) {
        if (direction == Direction.UP) return state.setValue(BUNK, sameWay(state, neighborState));
        if (direction == Direction.DOWN) return state.setValue(UPPER, sameWay(state, neighborState));
        return state;
    }

    /** Beds set by commands or structures do not go through placement: match the beds above and below here. */
    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        BlockState wanted = stacked(state, level, pos);
        if (wanted != state) level.setBlock(pos, wanted, Block.UPDATE_CLIENTS);
    }

    /** Whether this bed stands on a bunk: its goblin climbs in from the ground. */
    public static boolean isUpperBunk(BlockState state) {
        return state.hasProperty(UPPER) && state.getValue(UPPER);
    }

    /** The block the foot end of a bed at {@code pos} reaches into. */
    public static BlockPos footOf(BlockPos pos, BlockState state) {
        return pos.relative(state.getValue(FACING).getOpposite());
    }

    /** Whether the foot end of a bed next to {@code pos} lies in it. */
    public static boolean isFootEnd(LevelReader level, BlockPos pos) {
        for (Direction side : Direction.Plane.HORIZONTAL) {
            BlockPos next = pos.relative(side);
            BlockState state = level.getBlockState(next);
            if (state.is(GoblinLabour.GOBLIN_STRAW_BED) && footOf(next, state).equals(pos)) return true;
        }
        return false;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return (state.getValue(BUNK) ? BUNK_OUTLINE : OUTLINE).get(state.getValue(FACING));
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return (state.getValue(BUNK) ? BUNK_COLLISION : COLLISION).get(state.getValue(FACING));
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        // grass or snow where the foot end goes would poke through the blanket
        BlockPos foot = footOf(pos, state);
        BlockState there = level.getBlockState(foot);
        if (!level.isClientSide() && !there.isAir() && there.getFluidState().isEmpty()) level.destroyBlock(foot, false);
        if (placer instanceof Player player && level.getBlockEntity(pos) instanceof GoblinBedBlockEntity bed) {
            bed.setOwner(player.getUUID());
        }
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                          InteractionHand hand, BlockHitResult hit) {
        if (stack.is(GoblinLabour.GOBLIN_BLANK)) {
            if (level instanceof ServerLevel serverLevel && level.getBlockEntity(pos) instanceof GoblinBedBlockEntity bed) {
                bed.trySpawnFromBlank(serverLevel, stack, player);
            }
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.TRY_WITH_EMPTY_HAND;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level instanceof ServerLevel && level.getBlockEntity(pos) instanceof GoblinBedBlockEntity bed) {
            player.openMenu(new GoblinBedMenuProvider(bed));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new GoblinBedBlockEntity(pos, state);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide() || type != GoblinLabour.GOBLIN_BED_BLOCK_ENTITY) return null;
        return (BlockEntityTicker<T>) (BlockEntityTicker<GoblinBedBlockEntity>) GoblinBedBlockEntity::serverTick;
    }
}
