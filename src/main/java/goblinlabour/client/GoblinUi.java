package goblinlabour.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * The goblin look shared by all screens: a moss-green panel in vanilla's panel pixel layout, darker slots, and a
 * flat green button. Everything is drawn with rectangles, no textures. The milk can is the one screen in dark grey
 * metal ({@link Palette#METAL}).
 */
public final class GoblinUi {
    public static final int PANEL = 0xFF6FA35F;
    public static final int BORDER = 0xFF000000;
    public static final int LIGHT = 0xFFB9E2A5;
    public static final int SHADOW = 0xFF3B5E30;
    public static final int SLOT = 0xFF4E7A44;
    public static final int SLOT_DARK = 0xFF23391F;
    public static final int TOOL_SLOT = 0xFF8C9A4B;
    public static final int PORTRAIT_BG = 0xFF101810;
    public static final int LABEL = 0xFF14260F;
    public static final int LABEL_SOFT = 0xFF2E4A27;

    public static final int BUTTON = 0xFF2F8A44;
    public static final int BUTTON_HOVER = 0xFF43B25A;
    public static final int BUTTON_OFF = 0xFF55705A;
    public static final int BUTTON_EDGE = 0xFF163C1E;
    public static final int BUTTON_LIGHT = 0xFF7FD68F;
    public static final int BUTTON_TEXT = 0xFFFFFFFF;
    public static final int BUTTON_TEXT_OFF = 0xFFC9D6C6;

    /** The colours of a panel and its slots. */
    public record Palette(int panel, int border, int light, int shadow, int slot, int slotDark, int label) {
        public static final Palette GREEN = new Palette(PANEL, BORDER, LIGHT, SHADOW, SLOT, SLOT_DARK, LABEL);
        /** The milk can's dark grey metal, with light labels. */
        public static final Palette METAL = new Palette(0xFF3C3F43, 0xFF141517, 0xFF5D6166, 0xFF2A2C30, 0xFF2B2E32, 0xFF141517, 0xFFD8DBDE);
    }

    private GoblinUi() {
    }

    /** Vanilla container panel: transparent corner pixels, 1 px black border, 2 px highlight, 2 px shadow. */
    public static void drawPanel(GuiGraphicsExtractor graphics, int x0, int y0, int w, int h) {
        drawPanel(graphics, x0, y0, w, h, Palette.GREEN, null);
    }

    /**
     * A panel in the given colours with an optional {@code hole} (absolute x0, y0, x1, y1) left unpainted, through
     * which the dimmed world shows (a tank gauge).
     */
    public static void drawPanel(GuiGraphicsExtractor graphics, int x0, int y0, int w, int h, Palette colours, int[] hole) {
        int x1 = x0 + w;
        int y1 = y0 + h;
        if (hole == null) {
            graphics.fill(x0 + 1, y0 + 1, x1 - 1, y1 - 1, colours.panel());
        } else {
            graphics.fill(x0 + 1, y0 + 1, x1 - 1, hole[1], colours.panel());
            graphics.fill(x0 + 1, hole[3], x1 - 1, y1 - 1, colours.panel());
            graphics.fill(x0 + 1, hole[1], hole[0], hole[3], colours.panel());
            graphics.fill(hole[2], hole[1], x1 - 1, hole[3], colours.panel());
        }
        int border = colours.border(), light = colours.light(), shadow = colours.shadow();
        graphics.fill(x0 + 2, y0, x1 - 2, y0 + 1, border);
        graphics.fill(x0 + 2, y1 - 1, x1 - 2, y1, border);
        graphics.fill(x0, y0 + 2, x0 + 1, y1 - 2, border);
        graphics.fill(x1 - 1, y0 + 2, x1, y1 - 2, border);
        graphics.fill(x0 + 1, y0 + 1, x0 + 2, y0 + 2, border);
        graphics.fill(x1 - 2, y0 + 1, x1 - 1, y0 + 2, border);
        graphics.fill(x0 + 1, y1 - 2, x0 + 2, y1 - 1, border);
        graphics.fill(x1 - 2, y1 - 2, x1 - 1, y1 - 1, border);
        graphics.fill(x0 + 2, y0 + 1, x1 - 3, y0 + 2, light);
        graphics.fill(x0 + 1, y0 + 2, x1 - 3, y0 + 3, light);
        graphics.fill(x0 + 1, y0 + 3, x0 + 3, y1 - 3, light);
        graphics.fill(x0 + 3, y0 + 3, x0 + 4, y0 + 4, light);
        graphics.fill(x0 + 3, y1 - 2, x1 - 2, y1 - 1, shadow);
        graphics.fill(x0 + 3, y1 - 3, x1 - 1, y1 - 2, shadow);
        graphics.fill(x1 - 3, y0 + 3, x1 - 1, y1 - 3, shadow);
        graphics.fill(x1 - 4, y1 - 4, x1 - 3, y1 - 3, shadow);
    }

    public static void drawSlot(GuiGraphicsExtractor graphics, int x, int y, int fill) {
        drawSlot(graphics, x, y, fill, Palette.GREEN);
    }

    public static void drawSlot(GuiGraphicsExtractor graphics, int x, int y, int fill, Palette colours) {
        graphics.fill(x, y, x + 18, y + 18, fill);
        graphics.fill(x, y, x + 17, y + 1, colours.slotDark());
        graphics.fill(x, y, x + 1, y + 17, colours.slotDark());
        graphics.fill(x + 1, y + 17, x + 18, y + 18, colours.light());
        graphics.fill(x + 17, y + 1, x + 18, y + 18, colours.light());
    }

    /** A flat green button with an optional tooltip. */
    public static GreenButton button(Component label, Button.OnPress onPress, int x, int y, int w, int h, Component tooltip) {
        GreenButton button = new GreenButton(x, y, w, h, label, onPress);
        if (tooltip != null) button.setTooltip(Tooltip.create(tooltip));
        return button;
    }

    public static GreenButton button(Component label, Button.OnPress onPress, int x, int y, int w, int h) {
        return button(label, onPress, x, y, w, h, null);
    }

    /** Vanilla button behaviour (click, focus, narration, tooltip) with goblin colours. */
    public static class GreenButton extends Button {
        public GreenButton(int x, int y, int w, int h, Component label, OnPress onPress) {
            super(x, y, w, h, label, onPress, DEFAULT_NARRATION);
        }

        @Override
        protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
            int x0 = getX();
            int y0 = getY();
            int x1 = x0 + getWidth();
            int y1 = y0 + getHeight();
            int fill = !active ? BUTTON_OFF : isHoveredOrFocused() ? BUTTON_HOVER : BUTTON;
            graphics.fill(x0, y0, x1, y1, BUTTON_EDGE);
            graphics.fill(x0 + 1, y0 + 1, x1 - 1, y1 - 1, fill);
            if (active) graphics.fill(x0 + 1, y0 + 1, x1 - 1, y0 + 2, BUTTON_LIGHT);
            graphics.fill(x0 + 1, y1 - 2, x1 - 1, y1 - 1, SHADOW);
            int textY = y0 + (getHeight() - 8) / 2;
            graphics.centeredText(Minecraft.getInstance().font, getMessage(), (x0 + x1) / 2, textY, active ? BUTTON_TEXT : BUTTON_TEXT_OFF);
        }
    }

    /**
     * {@code InventoryScreen.extractEntityInInventoryFollowsMouse} without the name tag: the name is already the
     * screen's title.
     */
    public static void extractPortrait(GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1, int scale, float yOffset,
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
}
