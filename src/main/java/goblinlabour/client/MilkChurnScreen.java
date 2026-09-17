package goblinlabour.client;

import goblinlabour.block.MilkChurnBlockEntity;
import goblinlabour.menu.MilkCanLayout;
import goblinlabour.menu.MilkChurnMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

import java.util.List;
import java.util.Optional;

/**
 * The screen of the Milk Can and of the Milk Can Expansion, drawn as the block itself ({@link MilkCanPicture}): the
 * bucket slot above the output slot, the tank gauge to their right, the player's inventory below. The gauge is a
 * window through which the dimmed world shows, with the milk and a line for every bucket. There is no title: the
 * picture shows what it is.
 */
public class MilkChurnScreen extends AbstractContainerScreen<MilkChurnMenu> {
    private static final GoblinUi.Palette METAL = GoblinUi.Palette.METAL;

    private final MilkCanPicture picture;

    public MilkChurnScreen(MilkChurnMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title, MilkCanLayout.WIDTH, MilkCanLayout.height(menu.bodyY()));
        picture = menu.bodyY() == MilkCanLayout.CAN_BODY_Y ? MilkCanPicture.CAN : MilkCanPicture.EXPANSION;
    }

    @Override
    protected void init() {
        super.init();
        inventoryLabelX = 8;
        inventoryLabelY = MilkCanLayout.playerY(menu.bodyY()) - 11;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        int[] runs = picture.runs;
        for (int i = 0; i < runs.length; i += 4) {
            graphics.fill(leftPos + runs[i + 1], topPos + runs[i], leftPos + runs[i + 2], topPos + runs[i] + 1, runs[i + 3]);
        }

        List<Component> tooltip = null;
        for (Slot slot : menu.slots) {
            GoblinUi.drawSlot(graphics, leftPos + slot.x - 1, topPos + slot.y - 1, METAL.slot(), METAL);
            if (menu.isInputSlot(slot) && !slot.hasItem() && menu.getCarried().isEmpty() && isHovering(slot.x, slot.y, 16, 16, mouseX, mouseY)) {
                tooltip = List.of(Component.translatable("gui.goblinlabour.churn_input"), Component.translatable("gui.goblinlabour.churn_input.hint"));
            }
        }

        int milk = menu.milk(), capacity = menu.capacity();
        int[] gauge = picture.gauge(milk, capacity, MilkChurnBlockEntity.BUCKET);
        for (int i = 0; i < gauge.length; i += 5) {
            graphics.fill(leftPos + gauge[i], topPos + gauge[i + 1], leftPos + gauge[i + 2], topPos + gauge[i + 3], gauge[i + 4]);
        }
        if (isHovering(MilkCanLayout.TANK_X, MilkCanLayout.tankY(menu.bodyY()), MilkCanLayout.TANK_WIDTH, MilkCanLayout.TANK_HEIGHT, mouseX, mouseY)) {
            tooltip = List.of(Component.translatable("gui.goblinlabour.milk"), Component.literal(milk + " / " + capacity + " mB"));
        }
        if (tooltip != null) graphics.setTooltipForNextFrame(font, tooltip, Optional.empty(), mouseX, mouseY);
    }

    @Override
    protected boolean hasClickedOutside(double mouseX, double mouseY, int left, int top) {
        return !picture.covers((int) Math.floor(mouseX) - left, (int) Math.floor(mouseY) - top);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        graphics.text(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, METAL.label(), false);
    }
}
