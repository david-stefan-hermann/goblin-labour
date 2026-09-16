package goblinlabour.block;

import com.mojang.serialization.MapCodec;
import goblinlabour.GoblinLabour;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.Map;

/**
 * A chest for goblin loot: vanilla chest behaviour (27 slots, double chests, comparators, hoppers), its own block
 * entity type so it gets its own renderer (green, with teeth). Goblins unload only into these.
 */
public class GoblinChestBlock extends ChestBlock {
    public static final MapCodec<GoblinChestBlock> CODEC = simpleCodec(GoblinChestBlock::new);

    /**
     * The chest's own hull instead of vanilla's 14x14x14 box: the goblin chest fills its block from side to side
     * (plinth, rim and corner posts all reach the block edge) and its lid ends at the top of the block. Only the
     * studs and the horns stick out, and those stay out of the shape because a block shape cannot leave its block.
     * Both halves of a double chest fill their own block, so all types share this shape.
     */
    private static final Map<Direction, VoxelShape> SHAPES = Shapes.rotateHorizontal(Block.box(0, 0, 1, 16, 16, 15));

    public GoblinChestBlock(Properties properties) {
        super(() -> GoblinLabour.GOBLIN_CHEST_BLOCK_ENTITY, SoundEvents.CHEST_OPEN, SoundEvents.CHEST_CLOSE, properties);
    }

    @Override
    public MapCodec<? extends ChestBlock> codec() {
        return CODEC;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(FACING));
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new GoblinChestBlockEntity(pos, state);
    }
}
