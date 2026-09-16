package goblinlabour.client;

import goblinlabour.GoblinLabour;
import goblinlabour.entity.GoblinEntity;
import goblinlabour.entity.GoblinStyle;
import goblinlabour.ring.RingMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

/**
 * The Goblin Ring in the goblin look: the loot as a double chest, the player's inventory, and a side panel on the
 * left with a crew goblin's portrait and the two slots for enchanted books whose enchantments go onto the crew's tools.
 */
public class RingScreen extends AbstractContainerScreen<RingMenu> {
    /** An entity id no real entity has: ids of entities in the world count up from 1. */
    private static final int PORTRAIT_ID = -29_871_001;
    /** A crew goblin for the portrait, never added to the world. */
    @Nullable private GoblinEntity portrait;

    public RingScreen(RingMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title, RingMenu.WIDTH, RingMenu.HEIGHT);
    }

    @Override
    protected void init() {
        super.init();
        titleLabelX = 8;
        titleLabelY = 6;
        inventoryLabelX = 8;
        inventoryLabelY = RingMenu.PLAYER_Y - 11;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        GoblinUi.drawPanel(graphics, leftPos, topPos, RingMenu.WIDTH, RingMenu.HEIGHT);
        int sideX = leftPos + RingMenu.SIDE_X;
        GoblinUi.drawPanel(graphics, sideX, topPos, RingMenu.SIDE_WIDTH, RingMenu.SIDE_HEIGHT);

        int px0 = sideX + 7, py0 = topPos + 7;
        int px1 = sideX + RingMenu.SIDE_WIDTH - 7, py1 = topPos + 63;
        graphics.fill(px0 - 1, py0 - 1, px1 + 1, py1 + 1, GoblinUi.SLOT_DARK);
        graphics.fill(px0, py0, px1, py1, GoblinUi.PORTRAIT_BG);
        GoblinEntity goblin = portrait();
        if (goblin != null) GoblinUi.extractPortrait(graphics, px0, py0, px1, py1, 26, 0.0625f, mouseX, mouseY, goblin);

        List<Component> tooltip = null;
        for (Slot slot : menu.slots) {
            boolean book = menu.isBookSlot(slot);
            GoblinUi.drawSlot(graphics, leftPos + slot.x - 1, topPos + slot.y - 1, book ? GoblinUi.TOOL_SLOT : GoblinUi.SLOT);
            if (book && !slot.hasItem() && menu.getCarried().isEmpty() && isHovering(slot.x, slot.y, 16, 16, mouseX, mouseY)) {
                tooltip = List.of(Component.translatable("gui.goblinlabour.ring_book_slot"),
                        Component.translatable("gui.goblinlabour.ring_book_slot.hint"));
            }
        }
        if (tooltip != null) graphics.setTooltipForNextFrame(font, tooltip, Optional.empty(), mouseX, mouseY);
    }

    @Nullable
    private GoblinEntity portrait() {
        if (portrait == null && minecraft != null && minecraft.level != null) {
            portrait = new GoblinEntity(GoblinLabour.GOBLIN, minecraft.level);
            // never added to the world, so it gets no id there; the renderer asks for one (and throws without)
            portrait.setId(PORTRAIT_ID);
            portrait.setStyle(GoblinStyle.CREW);
        }
        return portrait;
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        graphics.text(font, title, titleLabelX, titleLabelY, GoblinUi.LABEL, false);
        graphics.text(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, GoblinUi.LABEL, false);
        graphics.text(font, Component.translatable("gui.goblinlabour.ring_books"), RingMenu.SIDE_X + 7, RingMenu.BOOKS_Y - 11,
                GoblinUi.LABEL, false);
    }

    /** The side panel is part of the screen: a click there is not a click outside that throws the carried stack. */
    @Override
    protected boolean hasClickedOutside(double mouseX, double mouseY, int left, int top) {
        boolean inSide = mouseX >= left + RingMenu.SIDE_X && mouseX < left + RingMenu.SIDE_X + RingMenu.SIDE_WIDTH
                && mouseY >= top && mouseY < top + RingMenu.SIDE_HEIGHT;
        return !inSide && super.hasClickedOutside(mouseX, mouseY, left, top);
    }
}
