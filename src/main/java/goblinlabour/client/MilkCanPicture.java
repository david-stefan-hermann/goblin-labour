package goblinlabour.client;

import goblinlabour.menu.MilkCanLayout;

import java.util.ArrayList;
import java.util.List;

/**
 * The picture of the Milk Can screen and of the Milk Can Expansion screen, from the front in dark grey metal after
 * the block models. The can has a narrower rolled rim, the recessed neck, a handle on each side (a post standing on
 * the shoulder and a bar reaching over to the neck, with a hole between) and the shoulder band; the expansion is only
 * the body, without opening and handles. Both bodies have two pressed ribs and a foot band and hold the bucket slot
 * above the output slot with an arrow between them, the gauge window to their right, and the player's inventory. The
 * corners are cut off by a pixel or two, like the model's chamfers.
 *
 * <p>Painted once into runs of one colour: the outline is every block pixel next to a pixel outside it, the pixel
 * inside the outline is lit on the left and top and shaded on the right and bottom. No Minecraft classes, so it can be
 * previewed outside the game; {@link #gauge} gives the gauge's contents for a milk amount the same way.
 */
public final class MilkCanPicture {
    private static final int W = MilkCanLayout.WIDTH;
    /** Rim, neck (the handles' bars, then their holes), shoulder; only the can has them. */
    private static final int NECK_Y = 10;
    private static final int HOLE_Y = 20;
    private static final int SHOULDER_Y = 40;
    private static final int RIM_INSET = 15;
    /** The handle's post; its bar reaches over the hole to the neck. */
    private static final int POST = 11;
    private static final int NECK_INSET = 29;

    private static final int BORDER = 0xFF141517;
    private static final int LIGHT = 0xFF5D6166;
    private static final int SHADOW = 0xFF2A2C30;
    private static final int PANEL = 0xFF3C3F43;
    private static final int RIM = 0xFF45494E;
    private static final int NECK = 0xFF313337;
    private static final int SHOULDER = 0xFF464A4F;
    private static final int RIB_DARK = 0xFF222427;
    private static final int RIB_LIGHT = 0xFF52565B;
    private static final int FOOT = 0xFF2F3135;
    private static final int ARROW = 0xFF1B1C1F;

    private static final int GAUGE_TINT = 0x40000000;
    private static final int TICK = 0xFFE8E8E8;
    private static final int MILK = 0xFFF4F1E6;
    private static final int MILK_TOP = 0xFFFFFFFF;
    private static final int MILK_SHADE = 0xFFE3DECB;
    private static final int MILK_TICK = 0xFFC5BFAA;

    /** Steps of brightness across the can, like its texture: a sheen left of the middle, darkest at the right edge. */
    private static final double[] ROUND_EDGES = {0.07, 0.15, 0.33, 0.45, 0.64, 0.82};
    private static final int[] ROUND_STEPS = {0, 1, 2, 1, 0, -1, -2};

    /** Both pictures, painted when the class loads; they come after the arrays above because painting uses them. */
    public static final MilkCanPicture CAN = new MilkCanPicture(MilkCanLayout.CAN_BODY_Y);
    public static final MilkCanPicture EXPANSION = new MilkCanPicture(MilkCanLayout.EXPANSION_BODY_Y);

    /** Where the body starts; above it, if anything, the can's rim, neck and shoulder. */
    private final int bodyY;
    private final int height;
    private final int tankY;
    private final int rib1;
    private final int rib2;
    private final int footY;
    /** Four ints per run, top to bottom: y, x0, x1 (exclusive), colour. */
    public final int[] runs;

    private MilkCanPicture(int bodyY) {
        this.bodyY = bodyY;
        height = MilkCanLayout.height(bodyY);
        tankY = MilkCanLayout.tankY(bodyY);
        rib1 = bodyY + 3;
        rib2 = tankY + MilkCanLayout.TANK_HEIGHT + 5;
        footY = height - 5;
        runs = paint();
    }

    public int height() {
        return height;
    }

    private boolean hasNeck() {
        return bodyY > 0;
    }

    /** Whether the pixel belongs to the block, the gauge window included; clicks anywhere else are outside. */
    public boolean covers(int x, int y) {
        if (x < 0 || x >= W || y < 0 || y >= height) return false;
        int hx = Math.min(x, W - 1 - x);                                       // distance from the nearer side
        if (hasNeck()) {
            if (y < NECK_Y) return hx >= RIM_INSET + (y == 0 ? 2 : y == 1 ? 1 : 0);
            if (y == NECK_Y && hx < 2 || y == NECK_Y + 1 && hx < 1) return false;   // the bars' outer corners
            if (y >= HOLE_Y && y < SHOULDER_Y && hx >= POST && hx < NECK_INSET) {  // the hole, its corners filled
                return (y == HOLE_Y || y == SHOULDER_Y - 1) && (hx == POST || hx == NECK_INSET - 1);
            }
        } else if (y == 0 && hx < 2 || y == 1 && hx < 1) {
            return false;                                                      // the body's upper corners
        }
        return !(y == height - 1 && hx < 2 || y == height - 2 && hx < 1);      // the body's lower corners
    }

    /**
     * The gauge's contents as rectangles, five ints each: x0, y0, x1, y1 (exclusive), colour. The milk rises from the
     * bottom, {@code height * milk / capacity} pixels, with a lit surface; a line marks every bucket, longer every
     * five and longest at half, on the pixel row where the milk's surface stands when the tank holds exactly that
     * many buckets.
     */
    public int[] gauge(int milk, int capacity, int bucket) {
        int x = MilkCanLayout.TANK_X, y = tankY;
        int w = MilkCanLayout.TANK_WIDTH, h = MilkCanLayout.TANK_HEIGHT;
        List<Integer> out = new ArrayList<>();
        rect(out, x, y, x + w, y + h, GAUGE_TINT);
        int level = milk <= 0 ? 0 : Math.clamp((long) h * milk / capacity, 1, h);
        if (level > 0) {
            rect(out, x, y + h - level, x + w, y + h, MILK);
            rect(out, x + w - 4, y + h - level, x + w, y + h, MILK_SHADE);
            rect(out, x, y + h - level, x + w, y + h - level + 1, MILK_TOP);
        }
        int buckets = capacity / bucket;
        for (int k = 1; k < buckets; k++) {
            int row = h - h * k * bucket / capacity;
            int length = 2 * k == buckets ? 12 : k % 5 == 0 ? 8 : 5;
            rect(out, x, y + row, x + length, y + row + 1, row >= h - level ? MILK_TICK : TICK);
        }
        return out.stream().mapToInt(Integer::intValue).toArray();
    }

    private static void rect(List<Integer> out, int x0, int y0, int x1, int y1, int colour) {
        out.add(x0);
        out.add(y0);
        out.add(x1);
        out.add(y1);
        out.add(colour);
    }

    /** Whether the pixel is painted: the block without the gauge window. */
    private boolean metal(int x, int y) {
        return covers(x, y) && !(x >= MilkCanLayout.TANK_X && x < MilkCanLayout.TANK_X + MilkCanLayout.TANK_WIDTH
                && y >= tankY && y < tankY + MilkCanLayout.TANK_HEIGHT);
    }

    /** The colour of a pixel inside the outline, by the part of the block it belongs to, shaded round across. */
    private int zone(int x, int y) {
        int hx = Math.min(x, W - 1 - x);
        if (y < bodyY) {
            if (y < NECK_Y) {                                                  // the rolled rim, its seam to the neck
                return y == NECK_Y - 1 ? BORDER : y == NECK_Y - 2 ? SHADOW : y <= 2 ? LIGHT : round(RIM, x);
            }
            if (y < SHOULDER_Y) {
                boolean handle = hx < POST || y < HOLE_Y && hx < NECK_INSET;
                if (handle) return round(PANEL, x);
                if (hx == NECK_INSET) return BORDER;                           // where the bar meets the neck
                return round(NECK, x);
            }
            if (y == SHOULDER_Y && hx >= NECK_INSET) return BORDER;            // the shoulder band
            return y == SHOULDER_Y + 1 ? LIGHT : y == bodyY - 1 ? BORDER : round(SHOULDER, x);
        }
        if (y == rib1 || y == rib2) return round(RIB_LIGHT, x);
        if (y == rib1 + 1 || y == rib2 + 1 || y == footY - 1) return RIB_DARK;
        if (y >= footY) return round(FOOT, x);
        return round(PANEL, x);
    }

    private static int round(int colour, int x) {
        double across = (x + 0.5) / W;
        int band = 0;
        while (band < ROUND_EDGES.length && across >= ROUND_EDGES[band]) band++;
        int delta = ROUND_STEPS[band] * 5;
        int r = Math.clamp(((colour >> 16) & 255) + delta, 0, 255);
        int g = Math.clamp(((colour >> 8) & 255) + delta, 0, 255);
        int b = Math.clamp((colour & 255) + delta, 0, 255);
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    private int[] paint() {
        int h = height;
        boolean[] edge = new boolean[W * h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < W; x++) {
                edge[y * W + x] = metal(x, y) && !(metal(x - 1, y) && metal(x + 1, y) && metal(x, y - 1) && metal(x, y + 1));
            }
        }
        int[] colour = new int[W * h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < W; x++) {
                int i = y * W + x;
                if (!metal(x, y)) continue;
                if (edge[i]) colour[i] = BORDER;
                else if (x > 0 && edge[i - 1] || y > 0 && edge[i - W]) colour[i] = LIGHT;
                else if (x < W - 1 && edge[i + 1] || y < h - 1 && edge[i + W]) colour[i] = SHADOW;
                else colour[i] = zone(x, y);
            }
        }

        // the arrow from the bucket slot down to the output slot, 14 px in the 18 px between them like a furnace's flame
        int arrowX = MilkCanLayout.SLOT_X + 8;
        int arrowTop = MilkCanLayout.inputY(bodyY) + 19, arrowTip = MilkCanLayout.outputY(bodyY) - 3;
        for (int y = arrowTop; y < arrowTip - 4; y++) {
            for (int x = arrowX - 1; x < arrowX + 1; x++) colour[y * W + x] = ARROW;
        }
        for (int i = 0; i < 4; i++) {
            for (int x = arrowX - 4 + i; x < arrowX + 4 - i; x++) colour[(arrowTip - 4 + i) * W + x] = ARROW;
        }

        List<Integer> out = new ArrayList<>();
        for (int y = 0; y < h; y++) {
            int x = 0;
            while (x < W) {
                int c = colour[y * W + x];
                int end = x + 1;
                while (end < W && colour[y * W + end] == c) end++;
                if (c != 0) {
                    out.add(y);
                    out.add(x);
                    out.add(end);
                    out.add(c);
                }
                x = end;
            }
        }
        return out.stream().mapToInt(Integer::intValue).toArray();
    }
}
