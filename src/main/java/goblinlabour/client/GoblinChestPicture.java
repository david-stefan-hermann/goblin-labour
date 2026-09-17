package goblinlabour.client;

import goblinlabour.menu.GoblinChestLayout;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The maw screen's picture: the Goblin Chest seen from the front with its mouth wide open. The green lid with iron
 * straps, the lock plate with the eye hanging over the upper gum, a row of fangs, the dark red mouth the chest's
 * slots sit in, the lower teeth, the chest's front for the player's inventory between two iron corner posts, the
 * iron plinth, and the horns: one at each side of the lid, bent like the model's, and on the double chest a third
 * one on top of the lid. The colours come from the chest's texture, and like the chest the picture comes in the
 * sixteen dye colours: the green steel is recoloured the way {@code art/chest_colors/make_chest_colors.py} recolours
 * the texture, everything else (iron, mouth, teeth, eye, horns) stays.
 *
 * <p>Painted once per row count and colour into runs of one colour, drawn as rectangles; no textures and no
 * Minecraft classes, so it can be previewed outside the game. The horns reach past the screen's edges.
 */
public final class GoblinChestPicture {
    private static final int W = GoblinChestLayout.WIDTH;
    /** How far the canvas reaches past the screen for the horns. */
    private static final int PAD_X = 32;
    private static final int PAD_TOP = 16;

    private static final int OUTLINE = 0xFF111114;
    private static final int IRON = 0xFF2E2E33;
    private static final int IRON_DARK = 0xFF1B1B1F;
    private static final int IRON_LIGHT = 0xFF55555E;
    private static final int STUD = 0xFF3C3C43;
    private static final int STUD_LIGHT = 0xFF7A7A84;
    private static final int GREEN = 0xFF397E44;
    private static final int GREEN_DARK = 0xFF2F6A39;
    private static final int GREEN_LIGHT = 0xFF4A9656;
    private static final int GUM = 0xFFBC4F62;
    private static final int GUM_DARK = 0xFF913D4C;
    private static final int GUM_LIGHT = 0xFFD06A7C;
    private static final int PALATE = 0xFF913D4C;
    private static final int THROAT = 0xFF601C28;
    private static final int DEEP = 0xFF3A0E17;
    private static final int LIPS = 0xFF24080E;
    private static final int TOOTH = 0xFFF8EDCE;
    private static final int TOOTH_LIGHT = 0xFFFFF7DC;
    private static final int TOOTH_SHADE = 0xFFD2C6A4;
    private static final int IRIS = 0xFF90CA3C;
    private static final int IRIS_LIGHT = 0xFFB8E070;
    private static final int IRIS_DARK = 0xFF5E8A22;
    private static final int PUPIL = 0xFF121212;
    private static final int SHINE = 0xFFFFFFB9;

    /** Horn colours from the texture as {plain, lit, shaded}: a piece's root end, its tan ring, its bone end, the tip. */
    private static final int[] HORN_SHADE = {0xFFDBCEAF, 0xFFE8DBB9, 0xFFC6BB9E};
    private static final int[] HORN_RING = {0xFFBB976C, 0xFFCCA87B, 0xFF9C7A52};
    private static final int[] HORN_BONE = {0xFFF2E4C1, 0xFFF9EFD4, 0xFFDBCEAF};
    private static final int[] HORN_TIP = {0xFFF9EFD4, 0xFFFFF7DC, 0xFFEAE1C8};

    /** Widths of a tooth's pixel rows from the gum to the tip. */
    private static final int[] FANG_UPPER = {7, 7, 5, 5, 5, 3, 3, 1, 1};
    private static final int[] TOOTH_UPPER = {5, 5, 3, 3, 1};
    private static final int[] FANG_LOWER = {7, 7, 5, 5, 3, 1, 1};
    private static final int[] TOOTH_LOWER = {5, 5, 3, 1};
    private static final int[] TUSK = {9, 9, 7, 7, 5, 5, 3, 3, 1};

    /**
     * A horn as the chest model builds it, read off {@code art/goblin_chest_*.bbmodel}: the middle points of the
     * horn's pieces in model units (x away from the chest, y up from the lid's top), drawn at {@code scaleX} /
     * {@code scaleY} GUI pixels per unit. The model is blocky; here a smooth curve runs through the points and the
     * horn narrows evenly from {@code rootHalf} (half its width, in model units) to a point, faster at the root the
     * higher {@code taper}. Point 1 is where the horn leaves the lid and sits at screen row {@code rootY}; before it the
     * horn is inside the lid. The pieces between the following points keep the texture's colours: the first
     * {@code rings} go from shade through a tan ring (if {@code tan}) to bone, the rest are the light tip. The double
     * chest's side horns are stretched sideways: at their size upwards they would not fit into 240 GUI pixels.
     */
    private record Horn(double scaleX, double scaleY, int rootY, int rings, boolean tan, double rootHalf, double taper,
                        double[] xs, double[] ys) {
    }

    private static final Horn SINGLE_HORN = new Horn(7.0, 6.0, 21, 2, true, 1.0, 0.7,
            new double[]{-1.0, 0.3, 1.95, 2.85, 2.95, 2.5},
            new double[]{-5.3, -5.1, -4.0, -2.7, -1.2, 0.0});
    private static final Horn DOUBLE_HORN = new Horn(5.0, 3.5, 28, 3, true, 2.0, 0.7,
            new double[]{-1.0, -0.3, 2.75, 4.1, 4.55, 4.1, 3.3},
            new double[]{-5.2, -4.6, -2.4, -0.3, 2.2, 4.15, 5.7});
    /** The double chest's third horn, standing straight up on the lid with a wide base. */
    private static final Horn MIDDLE_HORN = new Horn(2.9, 2.9, 2, 1, false, 1.5, 1.2,
            new double[]{0.0, 0.0, 0.0, 0.0},
            new double[]{-1.5, 0.0, 2.0, 3.5});
    /** Half the width of a horn's tip in GUI pixels. */
    private static final double TIP_HALF = 0.55;

    /**
     * The painted steel's base colour per dye, and the green steel's average hue, saturation and brightness in the
     * single chest's texture: the same numbers as {@code art/chest_colors/make_chest_colors.py}, so the screen
     * matches the chest. Green has no entry; it is the original.
     */
    private static final Map<String, Integer> DYES = Map.ofEntries(
            Map.entry("white", 0xd9dddd), Map.entry("orange", 0xe0741a), Map.entry("magenta", 0xc74ebd),
            Map.entry("light_blue", 0x3ab3da), Map.entry("yellow", 0xe3bf30), Map.entry("lime", 0x80c71f),
            Map.entry("pink", 0xf38baa), Map.entry("gray", 0x4c5457), Map.entry("light_gray", 0x9d9d97),
            Map.entry("cyan", 0x169c9c), Map.entry("purple", 0x8932b8), Map.entry("blue", 0x3c44aa),
            Map.entry("brown", 0x835432), Map.entry("red", 0xb02e26), Map.entry("black", 0x2c2c33));
    private static final double STEEL_HUE = 0.359180, STEEL_SATURATION = 0.532111, STEEL_VALUE = 0.501265;

    private static final Map<String, int[]> RUNS = new HashMap<>();

    private GoblinChestPicture() {
    }

    /**
     * Four ints per run, top to bottom, in screen coordinates: y, x0, x1 (exclusive), colour. {@code dye} is the
     * chest's colour as Minecraft names it ({@code red}, {@code light_blue}, ...).
     */
    public static synchronized int[] runs(int rows, String dye) {
        return RUNS.computeIfAbsent(rows + ":" + dye, key -> {
            int[] runs = paint(rows);
            for (int i = 3; i < runs.length; i += 4) runs[i] = tint(runs[i], dye);
            return runs;
        });
    }

    /**
     * The colour in the chest's dye: a green steel colour (hue 105-150 degrees, saturation from 0.3, as the texture
     * script picks them) keeps its offset from the steel's average hue, saturation and brightness, laid over the
     * dye's base colour. Any other colour, and every colour of a green chest, stays as it is.
     */
    public static int tint(int argb, String dye) {
        Integer base = DYES.get(dye);
        if (base == null) return argb;
        double[] hsv = hsv(argb);
        if (hsv[0] * 360 < 105 || hsv[0] * 360 >= 150 || hsv[1] < 0.3) return argb;
        double[] target = hsv(base);
        double hue = ((target[0] + hsv[0] - STEEL_HUE) % 1.0 + 1.0) % 1.0;
        double saturation = Math.min(1.0, target[1] * hsv[1] / STEEL_SATURATION);
        double value = Math.min(1.0, target[2] * hsv[2] / STEEL_VALUE);
        return argb & 0xFF000000 | rgb(hue, saturation, value);
    }

    /** Hue, saturation and value in 0..1, like Python's colorsys.rgb_to_hsv. */
    private static double[] hsv(int rgb) {
        double r = (rgb >> 16 & 255) / 255.0, g = (rgb >> 8 & 255) / 255.0, b = (rgb & 255) / 255.0;
        double max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b));
        if (max == min) return new double[]{0, 0, max};
        double range = max - min;
        double rc = (max - r) / range, gc = (max - g) / range, bc = (max - b) / range;
        double h = r == max ? bc - gc : g == max ? 2.0 + rc - bc : 4.0 + gc - rc;
        return new double[]{((h / 6.0) % 1.0 + 1.0) % 1.0, range / max, max};
    }

    /** Back from hue, saturation and value to 0xRRGGBB, like Python's colorsys.hsv_to_rgb. */
    private static int rgb(double h, double s, double v) {
        int i = (int) (h * 6.0);
        double f = h * 6.0 - i, p = v * (1 - s), q = v * (1 - s * f), t = v * (1 - s * (1 - f));
        double[] c = switch (i % 6) {
            case 0 -> new double[]{v, t, p};
            case 1 -> new double[]{q, v, p};
            case 2 -> new double[]{p, v, t};
            case 3 -> new double[]{p, q, v};
            case 4 -> new double[]{t, p, v};
            default -> new double[]{v, p, q};
        };
        return (int) Math.round(c[0] * 255) << 16 | (int) Math.round(c[1] * 255) << 8 | (int) Math.round(c[2] * 255);
    }

    /** How far the picture reaches above the screen's top. */
    public static int overhang(int[] runs) {
        return runs.length == 0 ? 0 : Math.max(0, -runs[0]);
    }

    /** Whether the picture has a pixel at x, y (screen coordinates), the horns included. */
    public static boolean covers(int[] runs, int x, int y) {
        for (int i = 0; i < runs.length && runs[i] <= y; i += 4) {
            if (runs[i] == y && x >= runs[i + 1] && x < runs[i + 2]) return true;
        }
        return false;
    }

    private static int[] paint(int rows) {
        int height = GoblinChestLayout.height(rows);
        Canvas c = new Canvas(height);
        int lid = GoblinChestLayout.LID;
        int rim = GoblinChestLayout.RIM;
        int gum = GoblinChestLayout.GUM;
        int mouthTop = lid + rim + gum;
        int body = GoblinChestLayout.bodyY(rows);
        int mouthBottom = body - rim - gum;
        int plinth = GoblinChestLayout.plinthY(rows);

        // the horns first: the lid covers their roots
        Canvas horns = new Canvas(height);
        Spine side = new Spine(rows > 3 ? DOUBLE_HORN : SINGLE_HORN);
        for (int y = -PAD_TOP; y < side.horn.rootY + 24; y++) {
            for (int x = -PAD_X; x < 12; x++) {                              // the left horn, mirrored to the right
                int colour = side.colour(-(x + 0.5), side.horn.rootY - (y + 0.5), 1);
                horns.put(x, y, colour);
                horns.put(W - 1 - x, y, colour);
            }
        }
        if (rows > 3) {
            Spine middle = new Spine(MIDDLE_HORN);
            for (int y = -PAD_TOP; y < MIDDLE_HORN.rootY + 8; y++) {
                for (int x = W / 2 - 12; x < W / 2 + 12; x++) {
                    int colour = middle.colour(x + 0.5 - W / 2.0, MIDDLE_HORN.rootY - (y + 0.5), -1);
                    if (colour != 0) horns.put(x, y, colour);
                }
            }
        }
        horns.outline(OUTLINE);
        c.paste(horns);

        // the lid: rounded on top, an iron edge, green planks
        int[] insets = {4, 2, 1, 1};
        for (int y = 0; y < lid; y++) {
            int inset = y < insets.length ? insets[y] : 0;
            for (int x = inset; x < W - inset; x++) {
                int colour = y == 0 || x == inset || x == W - inset - 1 ? OUTLINE
                        : y == 1 ? IRON_LIGHT : y == 2 ? IRON : y == 3 ? GREEN_LIGHT : (y - 4) % 4 == 3 ? GREEN_DARK : GREEN;
                c.put(x, y, colour);
            }
        }
        for (int x0 : new int[]{W / 2 - 15, W / 2 + 9}) {                    // straps against the lock plate
            c.fill(x0, 2, x0 + 6, lid, IRON);
            c.fill(x0, 2, x0 + 1, lid, IRON_LIGHT);
            for (int y : new int[]{5, 10}) {
                c.fill(x0 + 2, y, x0 + 4, y + 2, STUD_LIGHT);
                c.put(x0 + 3, y + 1, STUD);
            }
        }
        for (int x0 : new int[]{1, W - 7}) {                                 // corner caps
            c.fill(x0, 3, x0 + 6, lid, IRON);
            c.fill(x0 + 2, 7, x0 + 4, 9, STUD_LIGHT);
        }

        // iron rim, upper gum, the mouth (light at the gums, deep inside, dark at the corners), lower gum, iron rim
        c.fill(0, lid, W, lid + rim, IRON_DARK);
        c.fill(1, lid, W - 1, lid + 1, IRON);
        c.fill(0, lid + rim, W, mouthTop, GUM);
        c.fill(0, lid + rim, W, lid + rim + 1, GUM_LIGHT);
        for (int y = mouthTop; y < mouthBottom; y++) {
            int depth = Math.min(y - mouthTop, mouthBottom - 1 - y);
            c.fill(0, y, W, y + 1, depth < 1 ? GUM_DARK : depth < 3 ? PALATE : depth < 9 ? THROAT : DEEP);
            c.fill(0, y, 3, y + 1, LIPS);
            c.fill(W - 3, y, W, y + 1, LIPS);
        }
        c.fill(0, mouthBottom, W, mouthBottom + gum, GUM);
        c.fill(0, mouthBottom + gum - 1, W, mouthBottom + gum, GUM_DARK);
        c.fill(0, mouthBottom + gum, W, body, IRON);
        c.fill(1, mouthBottom + gum, W - 1, mouthBottom + gum + 1, IRON_LIGHT);

        // the chest's front: planks, iron corner posts with studs, the plinth, the outline
        c.fill(0, body, W, plinth, GREEN);
        for (int y = body; y < plinth; y++) {
            if ((y - body) % 5 == 4) c.fill(0, y, W, y + 1, GREEN_DARK);
        }
        for (int x0 : new int[]{0, W - 7}) {
            c.fill(x0, body, x0 + 7, plinth, IRON);
            c.fill(x0 + 1, body, x0 + 2, plinth, IRON_LIGHT);
            for (int y = body + 5; y < plinth - 3; y += 11) {
                c.fill(x0 + 2, y, x0 + 5, y + 3, STUD);
                c.fill(x0 + 2, y, x0 + 4, y + 1, STUD_LIGHT);
            }
        }
        c.fill(0, plinth, W, height, IRON);
        c.fill(1, plinth, W - 1, plinth + 1, IRON_LIGHT);
        for (int y = 4; y < height; y++) {
            c.put(0, y, OUTLINE);
            c.put(W - 1, y, OUTLINE);
        }
        c.fill(0, height - 1, W, height, OUTLINE);

        // teeth over every slot column and between the columns, big fangs every other one, the lower row offset
        // against the upper so the jaws interlock; tusks in the corners
        List<Integer> places = new ArrayList<>();
        for (int column = 0; column < 9; column++) places.add(16 + column * 18);
        for (int column = 1; column < 9; column++) places.add(7 + column * 18);
        places.sort(null);
        for (int x : places) {
            if (x < 78 || x > 98) tooth(c, x, mouthTop, (x - 16) % 36 == 0 ? FANG_UPPER : TOOTH_UPPER, true);
            tooth(c, x, mouthBottom - 1, (x - 25) % 36 == 0 ? FANG_LOWER : TOOTH_LOWER, false);
        }
        tooth(c, 5, mouthTop, TUSK, true);
        tooth(c, W - 6, mouthTop, TUSK, true);
        tooth(c, 5, mouthBottom - 1, FANG_LOWER, false);
        tooth(c, W - 6, mouthBottom - 1, FANG_LOWER, false);

        // the lock plate with the eye, hanging from the lid over the gum
        int plateBottom = mouthTop + 7;
        c.fill(W / 2 - 9, 3, W / 2 + 9, plateBottom, OUTLINE);
        c.fill(W / 2 - 8, 4, W / 2 + 8, plateBottom - 1, IRON);
        c.fill(W / 2 - 8, 4, W / 2 + 8, 5, IRON_LIGHT);
        c.fill(W / 2 - 8, 4, W / 2 - 7, plateBottom - 1, IRON_LIGHT);
        int[] eye = {6, 10, 12, 12, 12, 12, 12, 12, 12, 10, 6};
        int eyeTop = 5;
        for (int row = 0; row < eye.length; row++) {
            c.fill(W / 2 - eye[row] / 2, eyeTop + row, W / 2 + eye[row] / 2, eyeTop + row + 1, OUTLINE);
            if (row == 0 || row == eye.length - 1) continue;
            int colour = row <= 2 ? IRIS_LIGHT : row >= eye.length - 3 ? IRIS_DARK : IRIS;
            c.fill(W / 2 - eye[row] / 2 + 1, eyeTop + row, W / 2 + eye[row] / 2 - 1, eyeTop + row + 1, colour);
        }
        c.fill(W / 2 - 1, eyeTop + 1, W / 2 + 1, eyeTop + eye.length - 1, PUPIL);
        c.fill(W / 2 - 2, eyeTop + 4, W / 2 + 2, eyeTop + 7, PUPIL);
        c.fill(W / 2 - 4, eyeTop + 2, W / 2 - 2, eyeTop + 4, SHINE);
        c.fill(W / 2 - 2, plateBottom - 4, W / 2 + 2, plateBottom - 2, STUD_LIGHT);
        return c.runs();
    }

    /** A horn's middle line as a smooth curve in GUI pixels (x away from the chest, y up from the root). */
    private static final class Spine {
        private static final int STEPS = 16;

        private final Horn horn;
        private final double[] xs, ys;
        /** How far along the horn each sample lies: below 0 inside the lid, 1 at the tip. */
        private final double[] along;
        /** Where each piece after the root ends, in the same measure. */
        private final double[] pieceEnds;
        private final double rootHalf;

        Spine(Horn horn) {
            this.horn = horn;
            int n = horn.xs.length;
            double[] px = new double[n], py = new double[n];
            for (int i = 0; i < n; i++) {
                px[i] = horn.xs[i] * horn.scaleX;
                py[i] = (horn.ys[i] - horn.ys[1]) * horn.scaleY;
            }
            int samples = (n - 1) * STEPS + 1;
            xs = new double[samples];
            ys = new double[samples];
            double[] arc = new double[samples];
            for (int s = 0; s < samples; s++) {                               // Catmull-Rom through the points
                int i = Math.min(s / STEPS, n - 2);
                double t = (s - i * STEPS) / (double) STEPS;
                xs[s] = catmullRom(px[Math.max(i - 1, 0)], px[i], px[i + 1], px[Math.min(i + 2, n - 1)], t);
                ys[s] = catmullRom(py[Math.max(i - 1, 0)], py[i], py[i + 1], py[Math.min(i + 2, n - 1)], t);
                if (s > 0) arc[s] = arc[s - 1] + Math.hypot(xs[s] - xs[s - 1], ys[s] - ys[s - 1]);
            }
            double root = arc[STEPS], length = arc[samples - 1] - root;
            along = new double[samples];
            for (int s = 0; s < samples; s++) along[s] = (arc[s] - root) / length;
            pieceEnds = new double[n - 2];
            for (int k = 0; k < n - 2; k++) pieceEnds[k] = along[(k + 2) * STEPS];
            rootHalf = horn.rootHalf * (horn.scaleX + horn.scaleY) / 2;
        }

        private static double catmullRom(double p0, double p1, double p2, double p3, double t) {
            return 0.5 * (2 * p1 + (p2 - p0) * t + (2 * p0 - 5 * p1 + 4 * p2 - p3) * t * t
                    + (3 * p1 - p0 - 3 * p2 + p3) * t * t * t);
        }

        private double half(double along) {
            return along <= 0 ? rootHalf : TIP_HALF + (rootHalf - TIP_HALF) * Math.pow(1 - Math.min(along, 1), horn.taper);
        }

        /**
         * The colour at a point in the spine's pixels, or 0 outside the horn. The lit side faces up and away from the
         * chest ({@code lightX} 1) or up and towards negative x ({@code lightX} -1); the other side is shaded.
         */
        int colour(double x, double y, int lightX) {
            double best = Double.MAX_VALUE, where = 0, side = 0;
            for (int s = 0; s + 1 < xs.length; s++) {
                double dx = xs[s + 1] - xs[s], dy = ys[s + 1] - ys[s];
                double t = Math.clamp(((x - xs[s]) * dx + (y - ys[s]) * dy) / (dx * dx + dy * dy), 0.0, 1.0);
                double ox = x - (xs[s] + t * dx), oy = y - (ys[s] + t * dy);
                double at = along[s] + t * (along[s + 1] - along[s]);
                double half = half(at);
                double ratio = Math.hypot(ox, oy) / half;
                if (ratio < best) {
                    best = ratio;
                    where = at;
                    side = (lightX * ox + oy) / (Math.sqrt(2) * half);
                }
            }
            if (best > 1) return 0;
            int[] tone = HORN_TIP;
            if (where < 0) {
                tone = HORN_SHADE;
            } else {
                double start = 0;
                for (int k = 0; k < pieceEnds.length && k < horn.rings; k++) {
                    if (where < pieceEnds[k]) {
                        double u = (where - start) / (pieceEnds[k] - start);
                        tone = horn.tan ? (u < 0.4 ? HORN_SHADE : u < 0.7 ? HORN_RING : HORN_BONE) : u < 0.5 ? HORN_SHADE : HORN_BONE;
                        break;
                    }
                    start = pieceEnds[k];
                }
            }
            return side > 0.45 ? tone[1] : side < -0.45 ? tone[2] : tone[0];
        }
    }

    /** A tooth from the gum at {@code gumY}, hanging down or standing up, lit on its left and shaded on its right. */
    private static void tooth(Canvas c, int centerX, int gumY, int[] widths, boolean down) {
        for (int i = 0; i < widths.length; i++) {
            int w = widths[i];
            int y = down ? gumY + i : gumY - i;
            for (int k = 0; k < w; k++) {
                int colour = w > 2 && k == 0 ? TOOTH_LIGHT : w > 2 && k == w - 1 ? TOOTH_SHADE : TOOTH;
                c.put(centerX - w / 2 + k, y, colour);
            }
        }
    }

    /** A pixel grid around the screen (padded for the horns) that turns into runs of one colour. */
    private static final class Canvas {
        private final int width = W + 2 * PAD_X;
        private final int height;
        private final int[] pixels;

        Canvas(int screenHeight) {
            height = screenHeight + PAD_TOP;
            pixels = new int[width * height];
        }

        void put(int x, int y, int colour) {
            int px = x + PAD_X, py = y + PAD_TOP;
            if (px >= 0 && px < width && py >= 0 && py < height) pixels[py * width + px] = colour;
        }

        void fill(int x0, int y0, int x1, int y1, int colour) {
            for (int y = y0; y < y1; y++) {
                for (int x = x0; x < x1; x++) put(x, y, colour);
            }
        }

        /** Paints the other canvas's pixels over this one's, leaving out its empty ones. */
        void paste(Canvas other) {
            for (int i = 0; i < pixels.length; i++) {
                if (other.pixels[i] != 0) pixels[i] = other.pixels[i];
            }
        }

        /** Gives every painted area a one pixel border in the empty pixels around it. */
        void outline(int colour) {
            int[] copy = pixels.clone();
            for (int py = 0; py < height; py++) {
                for (int px = 0; px < width; px++) {
                    if (copy[py * width + px] != 0) continue;
                    boolean next = px > 0 && copy[py * width + px - 1] != 0 || px + 1 < width && copy[py * width + px + 1] != 0
                            || py > 0 && copy[(py - 1) * width + px] != 0 || py + 1 < height && copy[(py + 1) * width + px] != 0;
                    if (next) pixels[py * width + px] = colour;
                }
            }
        }

        int[] runs() {
            List<Integer> out = new ArrayList<>();
            for (int y = 0; y < height; y++) {
                int x = 0;
                while (x < width) {
                    int colour = pixels[y * width + x];
                    int end = x + 1;
                    while (end < width && pixels[y * width + end] == colour) end++;
                    if (colour != 0) {
                        out.add(y - PAD_TOP);
                        out.add(x - PAD_X);
                        out.add(end - PAD_X);
                        out.add(colour);
                    }
                    x = end;
                }
            }
            return out.stream().mapToInt(Integer::intValue).toArray();
        }
    }
}
