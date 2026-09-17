package goblinlabour.block;

import goblinlabour.GoblinLabour;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** A plain chest block entity with its own type (for the renderer), shared by all colours, named after its block. */
public class GoblinChestBlockEntity extends ChestBlockEntity {
    public GoblinChestBlockEntity(BlockPos pos, BlockState state) {
        super(GoblinLabour.GOBLIN_CHEST_BLOCK_ENTITY, pos, state);
    }

    @Override
    protected Component getDefaultName() {
        return getBlockState().getBlock().getName();
    }
}
