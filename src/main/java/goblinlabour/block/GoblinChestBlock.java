package goblinlabour.block;

import com.mojang.serialization.MapCodec;
import goblinlabour.GoblinLabour;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A chest for goblin loot: vanilla chest behaviour (27 slots, double chests, comparators, hoppers), its own block
 * entity type so it gets its own renderer (green, with teeth). Goblins unload only into these.
 */
public class GoblinChestBlock extends ChestBlock {
    public static final MapCodec<GoblinChestBlock> CODEC = simpleCodec(GoblinChestBlock::new);

    public GoblinChestBlock(Properties properties) {
        super(() -> GoblinLabour.GOBLIN_CHEST_BLOCK_ENTITY, SoundEvents.CHEST_OPEN, SoundEvents.CHEST_CLOSE, properties);
    }

    @Override
    public MapCodec<? extends ChestBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new GoblinChestBlockEntity(pos, state);
    }
}
