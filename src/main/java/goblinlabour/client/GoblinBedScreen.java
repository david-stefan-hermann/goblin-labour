package goblinlabour.client;

import goblinlabour.block.GoblinBedBlockEntity;
import goblinlabour.job.Job;
import goblinlabour.menu.GoblinBedMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * Bed screen: goblin name and status, Rest / Chop / Farm / Collect in two rows, the radius for the working jobs and,
 * for lumberjacks, replanting on or off. Dig orders are given with the Goblin Staff. Buttons go through the vanilla "menu button" packet.
 */
public class GoblinBedScreen extends AbstractContainerScreen<GoblinBedMenu> {
    private static final int JOB_Y = 30;
    private static final int JOB_ROW_2_Y = 52;
    private static final int JOB_BUTTON_WIDTH = 78;
    private static final int RADIUS_Y = 80;

    private static final int REPLANT_Y = RADIUS_Y + 18;

    private Button restButton, chopButton, farmButton, collectButton, radiusDown, radiusUp, replantButton;

    public GoblinBedScreen(GoblinBedMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title, GoblinBedMenu.WIDTH, GoblinBedMenu.HEIGHT);
    }

    @Override
    protected void init() {
        super.init();
        titleLabelX = 8;
        titleLabelY = 6;
        inventoryLabelY = 10000;
        int x = leftPos + 8;
        int x2 = x + JOB_BUTTON_WIDTH + 4;
        restButton = addRenderableWidget(jobButton(Job.REST, GoblinBedMenu.BUTTON_REST, x, JOB_Y));
        chopButton = addRenderableWidget(jobButton(Job.CHOP, GoblinBedMenu.BUTTON_CHOP, x2, JOB_Y));
        farmButton = addRenderableWidget(jobButton(Job.FARM, GoblinBedMenu.BUTTON_FARM, x, JOB_ROW_2_Y));
        collectButton = addRenderableWidget(jobButton(Job.COLLECT, GoblinBedMenu.BUTTON_COLLECT, x2, JOB_ROW_2_Y));
        Component radiusTip = Component.translatable("gui.goblinlabour.radius.tooltip");
        radiusDown = addRenderableWidget(GoblinUi.button(Component.literal("-"), b -> send(GoblinBedMenu.BUTTON_RADIUS_DOWN),
                x + 70, topPos + RADIUS_Y, 14, 14, radiusTip));
        radiusUp = addRenderableWidget(GoblinUi.button(Component.literal("+"), b -> send(GoblinBedMenu.BUTTON_RADIUS_UP),
                x + 86, topPos + RADIUS_Y, 14, 14, radiusTip));
        replantButton = addRenderableWidget(GoblinUi.button(Component.empty(), b -> send(GoblinBedMenu.BUTTON_REPLANT),
                x, topPos + REPLANT_Y, 160, 14, Component.translatable("gui.goblinlabour.replant.tooltip")));
        refresh();
    }

    private Button jobButton(Job job, int id, int x, int y) {
        return GoblinUi.button(Component.translatable(job.translationKey()), b -> send(id), x, topPos + y, JOB_BUTTON_WIDTH, 20,
                Component.translatable(job.translationKey() + ".tooltip"));
    }

    private void send(int id) {
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
        }
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        refresh();
    }

    private void refresh() {
        Job current = menu.job();
        restButton.active = current != Job.REST;
        chopButton.active = current != Job.CHOP;
        farmButton.active = current != Job.FARM;
        collectButton.active = current != Job.COLLECT;
        radiusDown.active = menu.radius() > 4;
        radiusUp.active = menu.radius() < 64;
        replantButton.visible = current == Job.CHOP;
        replantButton.setMessage(Component.translatable(menu.replant() ? "gui.goblinlabour.replant.on" : "gui.goblinlabour.replant.off"));
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        GoblinUi.drawPanel(graphics, leftPos, topPos, GoblinBedMenu.WIDTH, GoblinBedMenu.HEIGHT);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        graphics.text(font, title, titleLabelX, titleLabelY, GoblinUi.LABEL, false);
        GoblinBedBlockEntity.Status status = menu.status();
        Component statusText = menu.hasGoblin()
                ? Component.translatable("gui.goblinlabour.status", Component.translatable("goblinlabour.status." + status.name().toLowerCase()))
                : Component.translatable("goblinlabour.bed.empty");
        graphics.text(font, statusText, 8, 17, GoblinUi.LABEL_SOFT, false);
        graphics.text(font, Component.translatable("gui.goblinlabour.radius", menu.radius()), 8, RADIUS_Y + 3, GoblinUi.LABEL, false);
        if (menu.job().needsAssignment()) {
            graphics.text(font, Component.translatable("gui.goblinlabour.staff_order", Component.translatable(menu.job().translationKey())),
                    8, RADIUS_Y + 20, GoblinUi.LABEL_SOFT, false);
        }
    }
}
