package goblinlabour.client;

import goblinlabour.item.GoblinStaffItem;
import goblinlabour.job.Assignment;
import goblinlabour.menu.StaffMenu;
import goblinlabour.menu.StaffMenuData;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;

/**
 * Settings of a staff order (sneak + right-click). Shafts: size 1x1 / 3x3 / 5x5, stairs, target height with a
 * "bedrock" or "surface" shortcut. Tunnels: width, height, length. "Start" packs everything into one button id.
 */
public class StaffScreen extends AbstractContainerScreen<StaffMenu> {
    private static final int ROW1 = 34;
    private static final int ROW2 = 58;
    private static final int ROW3 = 82;
    private static final int START_Y = 104;

    private final StaffMenuData data;
    private int width_ = GoblinStaffItem.DEFAULT_SHAFT_WIDTH;
    private int height_ = GoblinStaffItem.DEFAULT_TUNNEL_HEIGHT;
    private int length_ = GoblinStaffItem.DEFAULT_TUNNEL_LENGTH;
    private boolean stairs = true;
    private int targetY;

    private Button widthDown, widthUp, heightDown, heightUp, lengthDown, lengthUp, stairsButton, presetButton;
    private EditBox targetBox;

    public StaffScreen(StaffMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title, StaffMenu.WIDTH, StaffMenu.HEIGHT);
        this.data = menu.data;
        this.width_ = data.kind().isShaft() ? GoblinStaffItem.DEFAULT_SHAFT_WIDTH : GoblinStaffItem.DEFAULT_TUNNEL_WIDTH;
        this.targetY = data.kind() == Assignment.Kind.DIG_UP ? Math.max(data.surfaceY(), data.pos().getY()) : data.minY();
    }

    @Override
    protected void init() {
        super.init();
        titleLabelX = 8;
        titleLabelY = 6;
        inventoryLabelY = 10000;
        int x = leftPos + 8;
        boolean shaft = data.kind().isShaft();

        Component sizeTip = Component.translatable(shaft ? "gui.goblinlabour.staff.size.tooltip" : "gui.goblinlabour.width.tooltip");
        widthDown = addRenderableWidget(GoblinUi.button(Component.literal("-"), b -> changeWidth(-1), x + 60, topPos + ROW1, 14, 14, sizeTip));
        widthUp = addRenderableWidget(GoblinUi.button(Component.literal("+"), b -> changeWidth(1), x + 76, topPos + ROW1, 14, 14, sizeTip));

        if (shaft) {
            stairsButton = addRenderableWidget(GoblinUi.button(Component.empty(), b -> {
                stairs = !stairs;
                refresh();
            }, x + 96, topPos + ROW1, 64, 14, Component.translatable("gui.goblinlabour.stairs.tooltip")));
            targetBox = new EditBox(font, x + 60, topPos + ROW2, 40, 14, Component.translatable("gui.goblinlabour.staff.target"));
            targetBox.setMaxLength(5);
            targetBox.setValue(Integer.toString(targetY));
            targetBox.setTooltip(Tooltip.create(Component.translatable("gui.goblinlabour.staff.target.tooltip")));
            targetBox.setResponder(text -> {
                try {
                    targetY = Integer.parseInt(text.trim());
                } catch (NumberFormatException ignored) {
                    // keep the last valid value
                }
            });
            addRenderableWidget(targetBox);
            boolean up = data.kind() == Assignment.Kind.DIG_UP;
            Component presetLabel = Component.translatable(up ? "gui.goblinlabour.staff.surface" : "gui.goblinlabour.staff.bedrock");
            presetButton = addRenderableWidget(GoblinUi.button(presetLabel, b -> {
                targetY = up ? Math.max(data.surfaceY(), data.pos().getY()) : data.minY();
                targetBox.setValue(Integer.toString(targetY));
            }, x + 104, topPos + ROW2, 56, 14, Component.translatable(up ? "gui.goblinlabour.staff.surface.tooltip" : "gui.goblinlabour.staff.bedrock.tooltip")));
        } else {
            Component heightTip = Component.translatable("gui.goblinlabour.staff.height.tooltip");
            Component lengthTip = Component.translatable("gui.goblinlabour.length.tooltip");
            heightDown = addRenderableWidget(GoblinUi.button(Component.literal("-"), b -> changeHeight(-1), x + 60, topPos + ROW2, 14, 14, heightTip));
            heightUp = addRenderableWidget(GoblinUi.button(Component.literal("+"), b -> changeHeight(1), x + 76, topPos + ROW2, 14, 14, heightTip));
            lengthDown = addRenderableWidget(GoblinUi.button(Component.literal("-"), b -> changeLength(-4), x + 60, topPos + ROW3, 14, 14, lengthTip));
            lengthUp = addRenderableWidget(GoblinUi.button(Component.literal("+"), b -> changeLength(4), x + 76, topPos + ROW3, 14, 14, lengthTip));
        }

        addRenderableWidget(GoblinUi.button(Component.translatable("gui.goblinlabour.staff.start"), b -> start(),
                x, topPos + START_Y, 160, 20, Component.translatable("gui.goblinlabour.staff.start.tooltip")));
        refresh();
    }

    private void changeWidth(int delta) {
        if (data.kind().isShaft()) width_ = Mth.clamp(width_ + delta * 2, 1, 5);
        else width_ = Mth.clamp(width_ + delta, 1, 5);
        refresh();
    }

    private void changeHeight(int delta) {
        height_ = Mth.clamp(height_ + delta, 2, 5);
        refresh();
    }

    private void changeLength(int delta) {
        length_ = Mth.clamp(length_ + delta, 4, 96);
        refresh();
    }

    private void refresh() {
        widthDown.active = width_ > 1;
        widthUp.active = width_ < 5;
        if (stairsButton != null) {
            stairsButton.setMessage(Component.translatable(stairs ? "gui.goblinlabour.stairs.on" : "gui.goblinlabour.stairs.off"));
        }
        if (heightDown != null) {
            heightDown.active = height_ > 2;
            heightUp.active = height_ < 5;
            lengthDown.active = length_ > 4;
            lengthUp.active = length_ < 96;
        }
    }

    private void start() {
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, StaffMenu.encode(width_, height_, length_, stairs, targetY));
        }
        onClose();
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        GoblinUi.drawPanel(graphics, leftPos, topPos, StaffMenu.WIDTH, StaffMenu.HEIGHT);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        graphics.text(font, title, titleLabelX, titleLabelY, GoblinUi.LABEL, false);
        graphics.text(font, Component.translatable("gui.goblinlabour.staff.goblins", data.selectedGoblins(),
                data.pos().getX(), data.pos().getY(), data.pos().getZ()), 8, 18, GoblinUi.LABEL_SOFT, false);
        boolean shaft = data.kind().isShaft();
        graphics.text(font, Component.translatable(shaft ? "gui.goblinlabour.staff.size" : "gui.goblinlabour.width", shaft ? width_ + "x" + width_ : width_),
                8, ROW1 + 3, GoblinUi.LABEL, false);
        if (shaft) {
            graphics.text(font, Component.translatable("gui.goblinlabour.staff.target"), 8, ROW2 + 3, GoblinUi.LABEL, false);
        } else {
            graphics.text(font, Component.translatable("gui.goblinlabour.staff.height", height_), 8, ROW2 + 3, GoblinUi.LABEL, false);
            graphics.text(font, Component.translatable("gui.goblinlabour.length", length_), 8, ROW3 + 3, GoblinUi.LABEL, false);
        }
    }
}
