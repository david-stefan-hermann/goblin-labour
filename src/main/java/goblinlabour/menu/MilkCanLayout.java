package goblinlabour.menu;

/**
 * Where things sit on the Milk Can and Milk Can Expansion screens, shared by the menu (slot positions) and the
 * client's picture. Both screens have the same slots and gauge; the can has its rim, neck and handles above the body,
 * the expansion only the body, so everything sits {@code bodyY} lower on the can. Kept free of Minecraft classes so
 * the picture can be previewed outside the game.
 */
public final class MilkCanLayout {
    public static final int WIDTH = 176;
    /** Where the body starts: below the Milk Can's rim, neck and shoulder, at the top of the expansion. */
    public static final int CAN_BODY_Y = 50;
    public static final int EXPANSION_BODY_Y = 0;
    public static final int TANK_WIDTH = 20;
    /** Six pixels per bucket in the can, three in the expansion: the milk stands exactly on a bucket's line. */
    public static final int TANK_HEIGHT = 60;
    /** The slot column and the gauge side by side, centred: an 18 px slot frame, a 22 px gap, the 22 px gauge frame. */
    public static final int SLOT_X = (WIDTH - 62) / 2 + 1;
    public static final int TANK_X = SLOT_X + 40;
    /** Between the bucket slot and the output slot, like a furnace's input and fuel slots. */
    private static final int SLOT_SPACING = 36;

    private MilkCanLayout() {
    }

    public static int tankY(int bodyY) {
        return bodyY + 9;
    }

    /**
     * The bucket slot above the output slot, 36 px apart like a furnace's input and fuel slots (18 px between the
     * frames for the arrow), the pair centred on the gauge.
     */
    public static int inputY(int bodyY) {
        return tankY(bodyY) + (TANK_HEIGHT - SLOT_SPACING - 16) / 2;
    }

    public static int outputY(int bodyY) {
        return inputY(bodyY) + SLOT_SPACING;
    }

    public static int playerY(int bodyY) {
        return tankY(bodyY) + TANK_HEIGHT + 22;
    }

    public static int hotbarY(int bodyY) {
        return playerY(bodyY) + 58;
    }

    public static int height(int bodyY) {
        return hotbarY(bodyY) + 26;
    }
}
