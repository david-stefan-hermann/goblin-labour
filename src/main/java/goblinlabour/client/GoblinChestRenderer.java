package goblinlabour.client;

import com.mojang.blaze3d.vertex.PoseStack;
import goblinlabour.GoblinLabour;
import goblinlabour.block.GoblinChestBlockEntity;
import net.minecraft.client.model.object.chest.ChestModel;
import net.minecraft.client.renderer.MultiblockChestResources;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.BrightnessCombiner;
import net.minecraft.client.renderer.blockentity.ChestRenderer;
import net.minecraft.client.renderer.blockentity.state.ChestRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.sprite.SpriteGetter;
import net.minecraft.client.resources.model.sprite.SpriteId;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.DoubleBlockCombiner;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Vanilla's {@link ChestRenderer} with the goblin chest's own models ({@link GoblinChestLayers}, with teeth) and
 * textures ({@code textures/entity/chest/goblin*.png} in the chest atlas).
 */
public class GoblinChestRenderer implements BlockEntityRenderer<GoblinChestBlockEntity, ChestRenderState> {
    private static final MultiblockChestResources<SpriteId> SPRITES = new MultiblockChestResources<>(
            Sheets.CHEST_MAPPER.apply(GoblinLabour.id("goblin")),
            Sheets.CHEST_MAPPER.apply(GoblinLabour.id("goblin_left")),
            Sheets.CHEST_MAPPER.apply(GoblinLabour.id("goblin_right")));

    private final SpriteGetter sprites;
    private final MultiblockChestResources<ChestModel> models;

    public GoblinChestRenderer(BlockEntityRendererProvider.Context context) {
        sprites = context.sprites();
        models = new MultiblockChestResources<>(GoblinChestLayers.SINGLE, GoblinChestLayers.LEFT, GoblinChestLayers.RIGHT)
                .map(layer -> new ChestModel(context.bakeLayer(layer)));
    }

    @Override
    public ChestRenderState createRenderState() {
        return new ChestRenderState();
    }

    @Override
    public void extractRenderState(GoblinChestBlockEntity chest, ChestRenderState state, float partialTick, Vec3 cameraPosition,
                                   @Nullable ModelFeatureRenderer.CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(chest, state, partialTick, cameraPosition, breakProgress);
        boolean inLevel = chest.getLevel() != null;
        BlockState blockState = inLevel ? chest.getBlockState()
                : GoblinLabour.GOBLIN_CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.SOUTH);
        state.type = blockState.hasProperty(ChestBlock.TYPE) ? blockState.getValue(ChestBlock.TYPE) : ChestType.SINGLE;
        state.facing = blockState.getValue(ChestBlock.FACING);
        DoubleBlockCombiner.NeighborCombineResult<? extends ChestBlockEntity> combined =
                inLevel && blockState.getBlock() instanceof ChestBlock block
                        ? block.combine(blockState, chest.getLevel(), chest.getBlockPos(), true)
                        : DoubleBlockCombiner.Combiner::acceptNone;
        state.open = combined.apply(ChestBlock.opennessCombiner(chest)).get(partialTick);
        if (state.type != ChestType.SINGLE) {
            state.lightCoords = combined.apply(new BrightnessCombiner<>()).applyAsInt(state.lightCoords);
        }
    }

    @Override
    public void submit(ChestRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        poseStack.pushPose();
        poseStack.mulPose(ChestRenderer.modelTransformation(state.facing));
        float open = 1.0f - state.open;
        open = 1.0f - open * open * open;
        collector.submitModel(models.select(state.type), open, poseStack, state.lightCoords, OverlayTexture.NO_OVERLAY, -1,
                SPRITES.select(state.type), sprites, 0, state.breakProgress);
        poseStack.popPose();
    }
}
