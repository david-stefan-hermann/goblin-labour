package goblinlabour.client;

import goblinlabour.menu.MilkChurnMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

import java.util.List;
import java.util.Optional;

/**
 * The Milk Churn in vanilla grey, the only grey screen of the mod: input slot, an arrow down to the output slot, and a
 * BuildCraft-style tank gauge. The gauge is a hole in the panel through which the dimmed world shows, with a thin
 * frame, the milk level and BuildCraft's tick scale on top.
 */
public class MilkChurnScreen extends AbstractContainerScreen<MilkChurnMenu> {
    private static final GoblinUi.Palette GREY = GoblinUi.Palette.GREY;
    private static final int GAUGE_TINT = 0x40000000;
    private static final int GAUGE_FRAME = 0xFF373737;
    private static final int TICK = 0xFFE8E8E8;
    private static final int MILK = 0xFFF4F1E6;
    private static final int MILK_TOP = 0xFFFFFFFF;
    private static final int MILK_SHADE = 0xFFE3DECB;

    public MilkChurnScreen(MilkChurnMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title, MilkChurnMenu.WIDTH, MilkChurnMenu.HEIGHT);
    }

    @Override
    protected void init() {
        super.init();
        titleLabelX = 8;
        titleLabelY = 6;
        inventoryLabelX = 8;
        inventoryLabelY = MilkChurnMenu.PLAYER_Y - 11;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        int x = leftPos + MilkChurnMenu.TANK_X, y = topPos + MilkChurnMenu.TANK_Y;
        int w = MilkChurnMenu.TANK_WIDTH, h = MilkChurnMenu.TANK_HEIGHT;
        GoblinUi.drawPanel(graphics, leftPos, topPos, MilkChurnMenu.WIDTH, MilkChurnMenu.HEIGHT, GREY, new int[]{x, y, x + w, y + h});

        List<Component> tooltip = null;
        for (Slot slot : menu.slots) {
            GoblinUi.drawSlot(graphics, leftPos + slot.x - 1, topPos + slot.y - 1, GREY.slot(), GREY);
            if (menu.isInputSlot(slot) && !slot.hasItem() && menu.getCarried().isEmpty() && isHovering(slot.x, slot.y, 16, 16, mouseX, mouseY)) {
                tooltip = List.of(Component.translatable("gui.goblinlabour.churn_input"), Component.translatable("gui.goblinlabour.churn_input.hint"));
            }
        }
        drawArrow(graphics, leftPos + MilkChurnMenu.SLOT_X + 8, topPos + MilkChurnMenu.INPUT_Y + 19, topPos + MilkChurnMenu.OUTPUT_Y - 3);

        // the gauge: tint over the world, a thin frame, the milk, the scale on top
        graphics.fill(x, y, x + w, y + h, GAUGE_TINT);
        graphics.fill(x - 1, y - 1, x + w + 1, y, GAUGE_FRAME);
        graphics.fill(x - 1, y + h, x + w + 1, y + h + 1, GAUGE_FRAME);
        graphics.fill(x - 1, y, x, y + h, GAUGE_FRAME);
        graphics.fill(x + w, y, x + w + 1, y + h, GAUGE_FRAME);
        int milk = menu.milk(), capacity = menu.capacity();
        if (milk > 0) {
            int level = Math.max(1, Math.min(h, h * milk / capacity));
            int top = y + h - level;
            graphics.fill(x, top, x + w, y + h, MILK);
            graphics.fill(x + w - 3, top, x + w, y + h, MILK_SHADE);
            graphics.fill(x, top, x + w, top + 1, MILK_TOP);
        }
        drawScale(graphics, x, y, h, w);
        if (mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h) {
            tooltip = List.of(Component.translatable("gui.goblinlabour.milk"), Component.literal(milk + " / " + capacity + " mB"));
        }
        if (tooltip != null) graphics.setTooltipForNextFrame(font, tooltip, Optional.empty(), mouseX, mouseY);
    }

    /** A small arrow pointing down from the input to the output slot. */
    private static void drawArrow(GuiGraphicsExtractor graphics, int centerX, int top, int bottom) {
        graphics.fill(centerX - 1, top, centerX + 1, bottom - 3, GREY.shadow());
        for (int i = 0; i < 4; i++) {
            graphics.fill(centerX - 4 + i, bottom - 4 + i, centerX + 4 - i, bottom - 3 + i, GREY.shadow());
        }
    }

    /** BuildCraft's tank scale: a tick every 4 px, longer at the quarters, the full width at the half. */
    private static void drawScale(GuiGraphicsExtractor graphics, int x, int y, int height, int width) {
        for (int row = 0; row < height; row += 4) {
            int length = row == height / 2 ? width : row == height / 4 || row == height * 3 / 4 ? 8 : row == 0 ? 7 : 5;
            graphics.fill(x, y + row, x + Math.min(length, width), y + row + 1, TICK);
        }
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        graphics.text(font, title, titleLabelX, titleLabelY, GREY.label(), false);
        graphics.text(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, GREY.label(), false);
    }
}
