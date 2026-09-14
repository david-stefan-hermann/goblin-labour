package goblinlabour.client;

import goblinlabour.entity.GoblinEntity;
import goblinlabour.menu.GoblinInventoryMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.List;
import java.util.Optional;

/**
 * Player-inventory look for a goblin: main panel with the storage row(s), the tool row and the player's inventory;
 * a side panel on the left with the goblin's portrait. Drawn with rectangles in the goblin colours of
 * {@link GoblinUi}.
 */
public class GoblinInventoryScreen extends AbstractContainerScreen<GoblinInventoryMenu> {
    public GoblinInventoryScreen(GoblinInventoryMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title, GoblinInventoryMenu.WIDTH, menu.height());
    }

    @Override
    protected void init() {
        super.init();
        titleLabelX = 8;
        titleLabelY = 6;
        inventoryLabelX = 8;
        inventoryLabelY = menu.playerY() - 11;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        GoblinUi.drawPanel(graphics, leftPos, topPos, GoblinInventoryMenu.WIDTH, menu.height());
        GoblinUi.drawPanel(graphics, leftPos + GoblinInventoryMenu.SIDE_X, topPos, GoblinInventoryMenu.SIDE_WIDTH, GoblinInventoryMenu.SIDE_HEIGHT);

        // portrait box in the side panel
        int px0 = leftPos + GoblinInventoryMenu.SIDE_X + 7;
        int py0 = topPos + 7;
        int px1 = px0 + GoblinInventoryMenu.SIDE_WIDTH - 14;
        int py1 = py0 + GoblinInventoryMenu.SIDE_HEIGHT - 14;
        graphics.fill(px0 - 1, py0 - 1, px1 + 1, py1 + 1, GoblinUi.SLOT_DARK);
        graphics.fill(px0, py0, px1, py1, GoblinUi.PORTRAIT_BG);
        GoblinEntity goblin = menu.goblin();
        if (goblin == null && minecraft != null && minecraft.level != null
                && minecraft.level.getEntity(menu.data.entityId()) instanceof GoblinEntity g) {
            goblin = g;
        }
        if (goblin != null) {
            extractPortrait(graphics, px0, py0, px1, py1, 26, 0.0625f, mouseX, mouseY, goblin);
        }

        List<Component> tooltip = null;
        for (Slot slot : menu.slots) {
            boolean tool = menu.isToolSlot(slot);
            GoblinUi.drawSlot(graphics, leftPos + slot.x - 1, topPos + slot.y - 1, tool ? GoblinUi.TOOL_SLOT : GoblinUi.SLOT);
            if (tool && !slot.hasItem() && menu.getCarried().isEmpty() && isHovering(slot.x, slot.y, 16, 16, mouseX, mouseY)) {
                tooltip = List.of(Component.translatable("gui.goblinlabour.tool_slot"),
                        Component.translatable("gui.goblinlabour.tool_slot.hint"));
            }
            if (menu.isBackpackSlot(slot) && !slot.hasItem() && menu.getCarried().isEmpty() && isHovering(slot.x, slot.y, 16, 16, mouseX, mouseY)) {
                tooltip = List.of(Component.translatable("gui.goblinlabour.backpack_slot"),
                        Component.translatable(menu.data.backpack() ? "gui.goblinlabour.backpack_slot.hint" : "gui.goblinlabour.backpack_slot.closed"));
            }
        }
        if (tooltip != null) {
            graphics.setTooltipForNextFrame(font, tooltip, Optional.empty(), mouseX, mouseY);
        }
    }

    /**
     * {@code InventoryScreen.extractEntityInInventoryFollowsMouse} without the name tag: the name is already the
     * screen's title.
     */
    private static void extractPortrait(GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1, int scale, float yOffset,
                                        float mouseX, float mouseY, LivingEntity entity) {
        float centerX = (x0 + x1) / 2.0f;
        float centerY = (y0 + y1) / 2.0f;
        float xAngle = (float) Math.atan((centerX - mouseX) / 40.0f);
        float yAngle = (float) Math.atan((centerY - mouseY) / 40.0f);
        Quaternionf rotation = new Quaternionf().rotateZ(Mth.PI);
        Quaternionf xRotation = new Quaternionf().rotateX(yAngle * 20.0f * Mth.DEG_TO_RAD);
        rotation.mul(xRotation);
        EntityRenderState state = Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(entity).createRenderState(entity, 1.0f);
        state.shadowPieces.clear();
        state.outlineColor = 0;
        state.nameTag = null;
        state.scoreText = null;
        if (state instanceof LivingEntityRenderState living) {
            living.bodyRot = 180.0f + xAngle * 20.0f;
            living.yRot = xAngle * 20.0f;
            living.xRot = living.pose != Pose.FALL_FLYING ? -yAngle * 20.0f : 0.0f;
            living.boundingBoxWidth /= living.scale;
            living.boundingBoxHeight /= living.scale;
            living.scale = 1.0f;
        }
        Vector3f translation = new Vector3f(0.0f, state.boundingBoxHeight / 2.0f + yOffset, 0.0f);
        graphics.entity(state, scale, translation, rotation, xRotation, x0, y0, x1, y1);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        graphics.text(font, title, titleLabelX, titleLabelY, GoblinUi.LABEL, false);
        graphics.text(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, GoblinUi.LABEL, false);
        graphics.text(font, Component.translatable("gui.goblinlabour.tools"), 8, menu.toolsY() - 11, GoblinUi.LABEL, false);
    }
}
