package goblinlabour.client;

import goblinlabour.item.GoblinHandbookItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.BookViewScreen;

import goblinlabour.GoblinLabour;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
import net.minecraft.client.gui.screens.MenuScreens;
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
        ClientTickEvents.END_CLIENT_TICK.register(HomeZoneParticles::tick);
        GoblinHandbookItem.setOpener(player -> Minecraft.getInstance().gui.setScreen(
                new BookViewScreen(new BookViewScreen.BookAccess(GoblinHandbookItem.pages()))));
        DevClientHooks.init();
    }
}
