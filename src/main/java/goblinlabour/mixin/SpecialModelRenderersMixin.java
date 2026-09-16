package goblinlabour.mixin;

import com.mojang.serialization.MapCodec;
import goblinlabour.client.GoblinChestSpecialRenderer;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.client.renderer.special.SpecialModelRenderers;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ExtraCodecs;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Adds {@code goblinlabour:goblin_chest} to the special item model renderers, next to vanilla's
 * {@code minecraft:chest}. It has to happen in {@code bootstrap} because that is what fills the id mapper the
 * item model files are parsed against, long before any mod entrypoint runs.
 */
@Mixin(SpecialModelRenderers.class)
public class SpecialModelRenderersMixin {
    @Shadow
    @Final
    private static ExtraCodecs.LateBoundIdMapper<Identifier, MapCodec<? extends SpecialModelRenderer.Unbaked<?>>> ID_MAPPER;

    @Inject(method = "bootstrap", at = @At("TAIL"))
    private static void goblinlabour$addGoblinChest(CallbackInfo ci) {
        ID_MAPPER.put(GoblinChestSpecialRenderer.ID, GoblinChestSpecialRenderer.Unbaked.MAP_CODEC);
    }
}
