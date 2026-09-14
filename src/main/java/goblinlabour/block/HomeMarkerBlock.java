package goblinlabour.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Never placed in the world. Exists only so the home-zone display can use a block-marker particle whose icon is
 * the emerald item (the block model's particle texture is {@code minecraft:item/emerald}).
 */
public class HomeMarkerBlock extends Block {
    public static final MapCodec<HomeMarkerBlock> CODEC = simpleCodec(HomeMarkerBlock::new);

    public HomeMarkerBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<HomeMarkerBlock> codec() {
        return CODEC;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }
}
