package goblinlabour.block;

import com.mojang.serialization.MapCodec;
import goblinlabour.GoblinLabour;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A grey metal tank for milk. Farmer goblins pour the milk they bring home into it; a player fills or empties buckets
 * with a right click, or opens its screen with an empty hand. The milk shows behind the glass ({@link #LEVEL}) and
 * stays in the churn when it is broken.
 */
public class MilkChurnBlock extends BaseEntityBlock {
    public static final MapCodec<MilkChurnBlock> CODEC = simpleCodec(MilkChurnBlock::new);
    /** Buckets of milk (rounded up) the model shows behind the glass; the block entity keeps it in step. */
    public static final IntegerProperty LEVEL = IntegerProperty.create("level", 0,
            MilkChurnBlockEntity.CAPACITY / MilkChurnBlockEntity.BUCKET);
    private static final VoxelShape SHAPE = Block.box(1, 0, 1, 15, 16, 15);

    public MilkChurnBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(LEVEL, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LEVEL);
    }

    @Override
    protected MapCodec<MilkChurnBlock> codec() {
        return CODEC;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    /**
     * Not a way through: vanilla counts every block that is not a full cube as open for paths, so goblins walked into
     * the churn, jumped onto it and got stuck on top (like chests, it is solid for path finding).
     */
    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return false;
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                          InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof MilkChurnBlockEntity churn)) return InteractionResult.TRY_WITH_EMPTY_HAND;
        if (stack.is(Items.MILK_BUCKET)) {
            if (level.isClientSide()) return InteractionResult.SUCCESS;
            if (churn.pour(1) == 0) return InteractionResult.FAIL;
            player.setItemInHand(hand, ItemUtils.createFilledResult(stack, player, new ItemStack(Items.BUCKET)));
            level.playSound(null, pos, SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 1.0f, 1.0f);
            return InteractionResult.SUCCESS;
        }
        if (stack.is(Items.BUCKET)) {
            if (level.isClientSide()) return InteractionResult.SUCCESS;
            if (!churn.drawBucket()) return InteractionResult.FAIL;
            player.setItemInHand(hand, ItemUtils.createFilledResult(stack, player, new ItemStack(Items.MILK_BUCKET)));
            level.playSound(null, pos, SoundEvents.BUCKET_FILL, SoundSource.BLOCKS, 1.0f, 1.0f);
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.TRY_WITH_EMPTY_HAND;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof MilkChurnBlockEntity churn) player.openMenu(churn);
        return InteractionResult.SUCCESS;
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos, Direction direction) {
        return level.getBlockEntity(pos) instanceof MilkChurnBlockEntity churn ? churn.signal() : 0;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MilkChurnBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide() ? null : createTickerHelper(type, GoblinLabour.MILK_CHURN_BLOCK_ENTITY, MilkChurnBlockEntity::serverTick);
    }
}
