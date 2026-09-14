package goblinlabour.client;

import goblinlabour.GoblinLabour;
import goblinlabour.entity.GoblinEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.resources.Identifier;

public class GoblinRenderer extends HumanoidMobRenderer<GoblinEntity, HumanoidRenderState, GoblinModel> {
    private static final Identifier TEXTURE = GoblinLabour.id("textures/entity/goblin.png");

    public GoblinRenderer(EntityRendererProvider.Context context) {
        super(context, new GoblinModel(context.bakeLayer(GoblinLabourClient.GOBLIN_LAYER)), 0.35f);
    }

    @Override
    public HumanoidRenderState createRenderState() {
        return new HumanoidRenderState();
    }

    @Override
    public Identifier getTextureLocation(HumanoidRenderState state) {
        return TEXTURE;
    }
}
