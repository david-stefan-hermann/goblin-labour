package goblinlabour.client;

import com.mojang.blaze3d.vertex.PoseStack;
import goblinlabour.GoblinLabour;
import goblinlabour.block.GoblinChestBlockEntity;
import net.minecraft.client.model.object.chest.ChestModel;
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
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Vanilla's {@link ChestRenderer} with the goblin chest's own models ({@link GoblinChestLayers}) and textures
 * ({@code textures/entity/chest/goblin*.png} in the chest atlas).
 *
 * <p>Unlike vanilla's, the double chest is one model that spans both blocks instead of two halves: its boxes and
 * their box UV run across the seam, so it cannot be cut in two. The left half draws all of it and the right half
 * draws nothing. Both halves still share the openness and the brightness, so it opens and lights as one chest.
 */
public class GoblinChestRenderer implements BlockEntityRenderer<GoblinChestBlockEntity, ChestRenderState> {
    private static final SpriteId SINGLE = Sheets.CHEST_MAPPER.apply(GoblinLabour.id("goblin"));
    private static final SpriteId DOUBLE = Sheets.CHEST_MAPPER.apply(GoblinLabour.id("goblin_double"));

    /**
     * How far the model can reach out of its block, in blocks: the double chest is 1.41 either side of the seam,
     * and an open lid throws its horns about as far up and back. Used for this chest's own frustum test.
     */
    private static final double REACH = 2.0;

    private final SpriteGetter sprites;
    private final ChestModel single;
    private final ChestModel twin;

    public GoblinChestRenderer(BlockEntityRendererProvider.Context context) {
        sprites = context.sprites();
        single = new ChestModel(context.bakeLayer(GoblinChestLayers.SINGLE));
        twin = new ChestModel(context.bakeLayer(GoblinChestLayers.DOUBLE));
    }

    @Override
    public ChestRenderState createRenderState() {
        return new ChestRenderState();
    }

    /**
     * Block entities are normally culled by the 16x16x16 section they sit in. This chest reaches well past its own
     * block - the double chest is almost three blocks wide and the horns stand above it - and the left half draws
     * the right half's share too, so section culling drops it while it is still on screen. Taking it out of the
     * section pass and testing it against the frustum in {@link #submit} instead fixes that; the extra cost is one
     * cheap state extraction per chest within {@link #getViewDistance()}.
     */
    @Override
    public boolean shouldRenderOffScreen() {
        return true;
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
        if (state.type == ChestType.RIGHT) {
            return; // the left half draws the whole double chest
        }
        if (camera.cullFrustum != null && !camera.cullFrustum.isVisible(new AABB(state.blockPos).inflate(REACH))) {
            return;
        }
        boolean isDouble = state.type != ChestType.SINGLE;
        poseStack.pushPose();
        poseStack.mulPose(ChestRenderer.modelTransformation(state.facing));
        float open = 1.0f - state.open;
        open = 1.0f - open * open * open;
        collector.submitModel(isDouble ? twin : single, open, poseStack, state.lightCoords, OverlayTexture.NO_OVERLAY, -1,
                isDouble ? DOUBLE : SINGLE, sprites, 0, state.breakProgress);
        poseStack.popPose();
    }
}
