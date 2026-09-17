package goblinlabour.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import goblinlabour.GoblinLabour;
import net.minecraft.client.model.object.chest.ChestModel;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.special.NoDataSpecialModelRenderer;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.client.resources.model.sprite.SpriteGetter;
import net.minecraft.client.resources.model.sprite.SpriteId;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import org.joml.Vector3fc;

import java.util.function.Consumer;

/**
 * Draws the Goblin Chest as an item (in hand, in the inventory, on the ground): vanilla's
 * {@code minecraft:chest} special renderer is hard-wired to vanilla's chest model, which is not this chest's
 * shape, so the goblin chest brings its own. Same job, but with {@link GoblinChestLayers#SINGLE} and the goblin
 * chest's sprite in the chest atlas ({@code texture}, one per colour; the glowing eye is the same for all).
 *
 * <p>Referenced from {@code assets/goblinlabour/items/goblin_chest.json} as
 * {@code {"type": "goblinlabour:goblin_chest"}}; the id is registered in
 * {@code goblinlabour.mixin.client.SpecialModelRenderersMixin}.
 */
public class GoblinChestSpecialRenderer implements NoDataSpecialModelRenderer {
    public static final Identifier ID = GoblinLabour.id("goblin_chest");

    private final SpriteGetter sprites;
    private final ChestModel model;
    private final SpriteId sprite;
    private final SpriteId glow;
    private final float openness;

    public GoblinChestSpecialRenderer(SpriteGetter sprites, ChestModel model, SpriteId sprite, SpriteId glow, float openness) {
        this.sprites = sprites;
        this.model = model;
        this.sprite = sprite;
        this.glow = glow;
        this.openness = openness;
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, int overlayCoords,
                       boolean hasFoil, int outlineColor) {
        collector.submitModel(model, openness, poseStack, lightCoords, overlayCoords, -1, sprite, sprites, outlineColor, null);
        // the eye glows in the item too, the same way as in the world (see GoblinChestRenderer)
        collector.order(1).submitModel(model, openness, poseStack, LightCoordsUtil.FULL_BRIGHT, overlayCoords, -1,
                glow, sprites, outlineColor, null);
    }

    @Override
    public void getExtents(Consumer<Vector3fc> consumer) {
        model.setupAnim(openness);
        model.root().getExtentsForGui(new PoseStack(), consumer);
    }

    /** The unbaked form the item model file is parsed into; {@code openness} is 0 (closed) unless given. */
    public record Unbaked(Identifier texture, float openness) implements NoDataSpecialModelRenderer.Unbaked {
        public static final MapCodec<Unbaked> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                Identifier.CODEC.fieldOf("texture").forGetter(Unbaked::texture),
                Codec.FLOAT.optionalFieldOf("openness", 0.0f).forGetter(Unbaked::openness)
        ).apply(instance, Unbaked::new));

        @Override
        public MapCodec<Unbaked> type() {
            return MAP_CODEC;
        }

        @Override
        public SpecialModelRenderer<Void> bake(SpecialModelRenderer.BakingContext context) {
            ChestModel model = new ChestModel(context.entityModelSet().bakeLayer(GoblinChestLayers.SINGLE));
            return new GoblinChestSpecialRenderer(context.sprites(), model, Sheets.CHEST_MAPPER.apply(texture),
                    Sheets.CHEST_MAPPER.apply(GoblinLabour.id("goblin_glow")), openness);
        }
    }
}
