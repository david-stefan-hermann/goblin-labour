package goblinlabour.client;

import goblinlabour.menu.GoblinChestLayout;
import goblinlabour.menu.GoblinChestMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/**
 * The Goblin Chest holding the inventory in its open mouth: the chest's slots between the teeth, the player's
 * inventory on the chest's front. The picture is {@link GoblinChestPicture}, in the chest's colour; this draws it and
 * the slots. There is no title: the picture shows which chest it is.
 */
public class GoblinChestScreen extends AbstractContainerScreen<GoblinChestMenu> {
    /** Slots in the mouth are dark pockets with a gum-coloured lower edge; slots on the chest's front are its steel. */
    private static final GoblinUi.Palette MOUTH_SLOTS = new GoblinUi.Palette(0, 0, 0xFF7A2433, 0, 0xFF2A0A10, 0xFF14040A, 0);

    private final int[] runs;
    private final GoblinUi.Palette frontSlots;

    public GoblinChestScreen(GoblinChestMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title, GoblinChestLayout.WIDTH, GoblinChestLayout.height(menu.getRowCount()));
        String dye = menu.color().getSerializedName();
        runs = GoblinChestPicture.runs(menu.getRowCount(), dye);
        frontSlots = new GoblinUi.Palette(0, 0, GoblinChestPicture.tint(0xFF58A365, dye), 0,
                GoblinChestPicture.tint(0xFF1F4A27, dye), GoblinChestPicture.tint(0xFF14301A, dye), 0);
    }

    /**
     * Centres the picture with its horns, not just the screen below them. When the window is too low for both, the
     * screen keeps its bottom on the window and the horn tips go first.
     */
    @Override
    protected void init() {
        super.init();
        int overhang = GoblinChestPicture.overhang(runs);
        topPos = Math.max(0, Math.min((height - imageHeight - overhang) / 2 + overhang, height - imageHeight));
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        for (int i = 0; i < runs.length; i += 4) {
            graphics.fill(leftPos + runs[i + 1], topPos + runs[i], leftPos + runs[i + 2], topPos + runs[i] + 1, runs[i + 3]);
        }
        for (Slot slot : menu.slots) {
            GoblinUi.Palette colours = slot.container == menu.getContainer() ? MOUTH_SLOTS : frontSlots;
            GoblinUi.drawSlot(graphics, leftPos + slot.x - 1, topPos + slot.y - 1, colours.slot(), colours);
        }
    }

    /** A click on a horn is a click on the chest, so it does not throw the carried stack out. */
    @Override
    protected boolean hasClickedOutside(double mouseX, double mouseY, int left, int top) {
        return super.hasClickedOutside(mouseX, mouseY, left, top)
                && !GoblinChestPicture.covers(runs, (int) Math.floor(mouseX) - left, (int) Math.floor(mouseY) - top);
    }

    /** No title and no inventory label: the chest speaks for itself. */
    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
    }
}
