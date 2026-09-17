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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * A full block tank under the Milk Can: the can standing on it pours its milk down into it, twenty buckets fit, and
 * other mods take the milk out through Fabric's transfer API (see {@link MilkCanExpansionBlockEntity}). Right-click
 * with a milk bucket or an empty bucket like the can, with an empty hand for its screen.
 */
public class MilkCanExpansionBlock extends BaseEntityBlock {
    public static final MapCodec<MilkCanExpansionBlock> CODEC = simpleCodec(MilkCanExpansionBlock::new);

    public MilkCanExpansionBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<MilkCanExpansionBlock> codec() {
        return CODEC;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                          InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof MilkCanExpansionBlockEntity expansion)) return InteractionResult.TRY_WITH_EMPTY_HAND;
        if (stack.is(Items.MILK_BUCKET)) {
            if (level.isClientSide()) return InteractionResult.SUCCESS;
            if (expansion.room() < MilkChurnBlockEntity.BUCKET) return InteractionResult.FAIL;
            expansion.fill(MilkChurnBlockEntity.BUCKET);
            player.setItemInHand(hand, ItemUtils.createFilledResult(stack, player, new ItemStack(Items.BUCKET)));
            level.playSound(null, pos, SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 1.0f, 1.0f);
            return InteractionResult.SUCCESS;
        }
        if (stack.is(Items.BUCKET)) {
            if (level.isClientSide()) return InteractionResult.SUCCESS;
            if (expansion.getMilk() < MilkChurnBlockEntity.BUCKET) return InteractionResult.FAIL;
            expansion.drain(MilkChurnBlockEntity.BUCKET);
            player.setItemInHand(hand, ItemUtils.createFilledResult(stack, player, new ItemStack(Items.MILK_BUCKET)));
            level.playSound(null, pos, SoundEvents.BUCKET_FILL, SoundSource.BLOCKS, 1.0f, 1.0f);
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.TRY_WITH_EMPTY_HAND;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof MilkCanExpansionBlockEntity expansion) player.openMenu(expansion);
        return InteractionResult.SUCCESS;
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos, Direction direction) {
        return level.getBlockEntity(pos) instanceof MilkCanExpansionBlockEntity expansion ? expansion.signal() : 0;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MilkCanExpansionBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide() ? null
                : createTickerHelper(type, GoblinLabour.MILK_CAN_EXPANSION_BLOCK_ENTITY, MilkCanExpansionBlockEntity::serverTick);
    }
}
