package goblinlabour.menu;

/**
 * Where things sit on the maw screen, shared by the menu (slot positions) and the client's picture. Kept free of
 * Minecraft classes so the picture can be previewed outside the game.
 *
 * <p>The double chest's screen plus the horns sticking out above it has to fit into 240 GUI pixels, the height
 * Minecraft guarantees at automatic GUI scale; that is why the gaps are one pixel and the teeth this short.
 */
public final class GoblinChestLayout {
    public static final int WIDTH = 176;
    /** The lid, the iron rim under it, the upper gum and the room the upper teeth hang into. */
    public static final int LID = 15;
    public static final int RIM = 2;
    public static final int GUM = 3;
    public static final int UPPER_TEETH = 9;
    public static final int LOWER_TEETH = 7;
    /** Top of the first row of chest slot frames; the tips of the upper teeth touch it. */
    public static final int CHEST_FRAME_Y = LID + RIM + GUM + UPPER_TEETH;

    private GoblinChestLayout() {
    }

    /** Where the chest's body starts: below the last chest row, the lower teeth, the lower gum and the iron rim. */
    public static int bodyY(int rows) {
        return CHEST_FRAME_Y + rows * 18 + LOWER_TEETH + GUM + RIM;
    }

    public static int playerFrameY(int rows) {
        return bodyY(rows) + 1;
    }

    public static int hotbarFrameY(int rows) {
        return playerFrameY(rows) + 58;
    }

    /** The iron plinth under the body. */
    public static int plinthY(int rows) {
        return hotbarFrameY(rows) + 18 + 1;
    }

    public static int height(int rows) {
        return plinthY(rows) + 3;
    }
}
