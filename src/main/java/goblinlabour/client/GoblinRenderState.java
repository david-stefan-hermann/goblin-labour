package goblinlabour.client;

import goblinlabour.entity.GoblinStyle;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;

public class GoblinRenderState extends HumanoidRenderState {
    public GoblinStyle style = GoblinStyle.LUMBERJACK;
    public int look;
}
