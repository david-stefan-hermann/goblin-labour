package goblinlabour.client;

import goblinlabour.item.GoblinHandbookItem;
import net.minecraft.client.Minecraft;

import goblinlabour.GoblinLabour;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
import net.fabricmc.fabric.api.client.render.fluid.v1.FluidRenderingRegistry;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.block.FluidModel;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.client.model.geom.ModelLayerLocation;

public final class GoblinLabourClient implements ClientModInitializer {
    public static final ModelLayerLocation GOBLIN_LAYER = new ModelLayerLocation(GoblinLabour.id("goblin"), "main");

    @Override
    public void onInitializeClient() {
        ModelLayerRegistry.registerModelLayer(GOBLIN_LAYER, GoblinModel::createBodyLayer);
        EntityRendererRegistry.register(GoblinLabour.GOBLIN, GoblinRenderer::new);
        ModelLayerRegistry.registerModelLayer(GoblinChestLayers.SINGLE, GoblinChestLayers::single);
        ModelLayerRegistry.registerModelLayer(GoblinChestLayers.DOUBLE, GoblinChestLayers::doubleChest);
        BlockEntityRendererRegistry.register(GoblinLabour.GOBLIN_CHEST_BLOCK_ENTITY, GoblinChestRenderer::new);
        MenuScreens.register(GoblinLabour.GOBLIN_MENU, GoblinInventoryScreen::new);
        MenuScreens.register(GoblinLabour.BED_MENU, GoblinBedScreen::new);
        MenuScreens.register(GoblinLabour.STAFF_MENU, StaffScreen::new);
        MenuScreens.register(GoblinLabour.RING_MENU, RingScreen::new);
        MenuScreens.register(GoblinLabour.MILK_CHURN_MENU, MilkChurnScreen::new);
        MenuScreens.register(GoblinLabour.MILK_CAN_EXPANSION_MENU, MilkChurnScreen::new);
        // how milk looks where other mods draw fluids (storage screens, tanks): the milk of the can's model
        Material milk = new Material(GoblinLabour.id("block/milk_can_milk"));
        FluidRenderingRegistry.register(GoblinLabour.MILK_FLUID, new FluidModel.Unbaked(milk, milk, null, null));
        MenuScreens.register(GoblinLabour.GOBLIN_CHEST_MENU, GoblinChestScreen::new);
        ClientTickEvents.END_CLIENT_TICK.register(HomeZoneParticles::tick);
        GoblinHandbookItem.setOpener(player -> Minecraft.getInstance().gui.setScreen(new HandbookScreen(0)));
        goblinlabour.entity.GoblinEntity.setClientStaffCheck(goblin -> Minecraft.getInstance().player != null
                && Minecraft.getInstance().player.getMainHandItem().is(GoblinLabour.GOBLIN_STAFF));
        DevClientHooks.init();
    }
}
