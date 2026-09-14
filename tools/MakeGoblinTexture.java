import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Map;
import java.util.Random;

/**
 * Generates the 64x64 goblin entity texture. Box positions and sizes must match GoblinModel.createBodyLayer.
 * Also writes a 8x scaled copy to build/goblin-texture-preview.png for a quick look.
 * Run from the project root: java tools/MakeGoblinTexture.java
 */
public class MakeGoblinTexture {
    static final String OUT = "src/main/resources/assets/goblinlabour/textures/entity/goblin.png";

    static final int SKIN = 0x6BB04E, SKIN_DARK = 0x55913D, SKIN_DEEP = 0x437531, SKIN_LIGHT = 0x7FC262;
    static final int EYE = 0xF2D14B, PUPIL = 0x1C1C1C, MOUTH = 0x2B1B12, FANG = 0xEDE6D2, EAR_INNER = 0xA7715F;
    static final int LEATHER = 0x6B4A2B, LEATHER_DARK = 0x4E3520, STITCH = 0x8C6A45;
    static final int BELT = 0x3A2A1C, BUCKLE = 0xB8A060, CLOTH = 0x8A6F4A, CLOTH_DARK = 0x6E5638, CLAW = 0xD9D2B0;

    static final BufferedImage IMG = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
    static final Random RNG = new Random(1337);

    /** Texture layout of a model box at texOffs (u, v) with size w x h x d (vanilla ModelPart.Cube unwrap). */
    record Box(int u, int v, int w, int h, int d) {
        int[] top() { return new int[]{u + d, v, w, d}; }
        int[] bottom() { return new int[]{u + d + w, v, w, d}; }
        /** -x side: the goblin's right. */
        int[] right() { return new int[]{u, v + d, d, h}; }
        int[] front() { return new int[]{u + d, v + d, w, h}; }
        int[] left() { return new int[]{u + d + w, v + d, d, h}; }
        int[] back() { return new int[]{u + d + w + d, v + d, w, h}; }
        int[][] sides() { return new int[][]{right(), front(), left(), back()}; }
        int[][] all() { return new int[][]{top(), bottom(), right(), front(), left(), back()}; }
    }

    public static void main(String[] args) throws Exception {
        head();
        body();
        arm(new Box(0, 24, 2, 7, 2));
        arm(new Box(8, 24, 2, 7, 2));
        leg(new Box(16, 24, 2, 4, 2), new Box(16, 30, 2, 1, 1));
        leg(new Box(24, 24, 2, 4, 2), new Box(24, 30, 2, 1, 1));
        ears();
        nose();
        loincloth();

        File file = new File(OUT);
        file.getParentFile().mkdirs();
        ImageIO.write(IMG, "png", file);
        File preview = new File("build/goblin-texture-preview.png");
        preview.getParentFile().mkdirs();
        ImageIO.write(scale(IMG, 8), "png", preview);
        System.out.println("Goblin texture written");
    }

    static void head() {
        Box head = new Box(0, 0, 8, 7, 6);
        for (int[] face : head.all()) fill(face, SKIN, 10);
        fill(head.bottom(), SKIN_DARK, 6);
        // a few dark hairs and warts on the scalp
        int[] top = head.top();
        for (int i = 0; i < 5; i++) set(top[0] + RNG.nextInt(top[2]), top[1] + RNG.nextInt(top[3]), 0x2E3024);
        set(top[0] + 2, top[1] + 4, SKIN_DEEP);
        paint(head.front(), new String[]{
                "GGGGhGGG",
                "GhGGGGGG",
                "DDdGGdDD",
                "gYBGGBYg",
                "gGGddGGg",
                "dMFMMFMd",
                "ddGGGGdd",
        });
        // jaw shadow along the bottom row of the sides and back, a wart on one cheek
        for (int[] face : new int[][]{head.right(), head.left(), head.back()}) {
            for (int x = 0; x < face[2]; x++) set(face[0] + x, face[1] + face[3] - 1, vary(SKIN_DARK, 6));
        }
        set(head.right()[0] + 1, head.right()[1] + 4, SKIN_DEEP);
        int[] back = head.back();
        for (int x = 0; x < back[2]; x++) set(back[0] + x, back[1], vary(SKIN_DARK, 6));
    }

    static void body() {
        Box skin = new Box(0, 14, 6, 6, 4);
        for (int[] face : skin.all()) fill(face, SKIN, 10);
        // belly shows through the open vest
        paint(skin.front(), new String[]{
                "gGGGGg",
                "GdGGdG",
                "GGhlGG",
                "GlldlG",
                "gGllGg",
                "gggggg",
        });

        Box vest = new Box(20, 14, 6, 6, 4);
        for (int[] face : vest.all()) fill(face, LEATHER, 12);
        clear(vest.bottom());
        // open front and a neck hole
        paint(vest.front(), new String[]{
                "LS..SL",
                "LS..SL",
                "LS..SL",
                "LLS.SL",
                "LLS.LL",
                "kkk.kk",
        });
        int[] top = vest.top();
        for (int y = 0; y < top[3]; y++) {
            set(top[0] + 2, top[1] + y, 0);
            set(top[0] + 3, top[1] + y, 0);
        }
        int[] back = vest.back();
        for (int y = 0; y < back[3]; y++) set(back[0] + 2 + (y % 2), back[1] + y, STITCH);
        for (int[] face : new int[][]{vest.right(), vest.left(), back}) {
            for (int x = 0; x < face[2]; x++) set(face[0] + x, face[1] + face[3] - 1, vary(LEATHER_DARK, 6));
        }

        Box belt = new Box(40, 18, 6, 1, 4);
        for (int[] face : belt.all()) fill(face, BELT, 8);
        int[] front = belt.front();
        set(front[0] + 2, front[1], BUCKLE);
        set(front[0] + 3, front[1], BUCKLE);
    }

    static void arm(Box arm) {
        for (int[] face : arm.all()) fill(face, SKIN, 10);
        fill(arm.bottom(), SKIN_DARK, 6);
        for (int[] face : arm.sides()) {
            for (int x = 0; x < face[2]; x++) {
                set(face[0] + x, face[1] + 3, vary(LEATHER, 10)); // leather wrap on the forearm
                set(face[0] + x, face[1] + 4, vary(LEATHER_DARK, 8));
                set(face[0] + x, face[1] + 6, vary(SKIN_DARK, 6)); // knuckles
            }
        }
        int[] front = arm.front();
        set(front[0], front[1] + 6, CLAW);
        set(front[0] + 1, front[1] + 6, CLAW);
    }

    static void leg(Box leg, Box foot) {
        for (int[] face : leg.all()) fill(face, SKIN_DARK, 8);
        for (int[] face : leg.sides()) {
            for (int x = 0; x < face[2]; x++) set(face[0] + x, face[1], vary(SKIN_DEEP, 6));
        }
        for (int[] face : foot.all()) fill(face, SKIN_DARK, 8);
        int[] front = foot.front();
        set(front[0], front[1], CLAW);
        set(front[0] + 1, front[1], CLAW);
    }

    static void ears() {
        Box[] parts = {new Box(32, 24, 2, 3, 1), new Box(32, 28, 2, 2, 1), new Box(32, 31, 2, 1, 1)};
        for (Box part : parts) {
            for (int[] face : part.all()) fill(face, SKIN, 10);
        }
        // pinkish inner ear on the forward face, fading towards the tip
        int[] base = parts[0].front();
        set(base[0], base[1] + 1, EAR_INNER);
        set(base[0] + 1, base[1] + 1, EAR_INNER);
        set(base[0] + 1, base[1] + 2, SKIN_DARK);
        int[] mid = parts[1].front();
        set(mid[0], mid[1] + 1, EAR_INNER);
        set(mid[0] + 1, mid[1] + 1, SKIN_DARK);
        int[] tip = parts[2].front();
        set(tip[0] + 1, tip[1], SKIN_DARK);
    }

    static void nose() {
        Box nose = new Box(40, 24, 2, 2, 2);
        Box tip = new Box(40, 28, 2, 1, 1);
        for (int[] face : nose.all()) fill(face, SKIN, 8);
        for (int[] face : tip.all()) fill(face, SKIN_DARK, 6);
        int[] top = nose.top();
        set(top[0], top[1], SKIN_LIGHT);
        set(top[0] + 1, top[1], SKIN_LIGHT);
        int[] under = tip.bottom();
        set(under[0], under[1], SKIN_DEEP);
        set(under[0] + 1, under[1], SKIN_DEEP);
    }

    static void loincloth() {
        for (int[] face : new int[][]{new Box(48, 24, 4, 3, 0).front(), new Box(48, 24, 4, 3, 0).back(),
                new Box(48, 27, 4, 3, 0).front(), new Box(48, 27, 4, 3, 0).back()}) {
            fill(face, CLOTH, 12);
            for (int x = 0; x < face[2]; x++) set(face[0] + x, face[1], vary(CLOTH_DARK, 6));
            // frayed hem
            set(face[0] + 1 + RNG.nextInt(2), face[1] + face[3] - 1, 0);
        }
    }

    // ---- painting helpers ----

    static void paint(int[] face, String[] rows) {
        Map<Character, Integer> colors = Map.ofEntries(
                Map.entry('G', SKIN), Map.entry('g', SKIN_DARK), Map.entry('d', SKIN_DEEP), Map.entry('h', SKIN_LIGHT),
                Map.entry('l', SKIN_LIGHT), Map.entry('D', 0x2F4A22), Map.entry('Y', EYE), Map.entry('B', PUPIL),
                Map.entry('M', MOUTH), Map.entry('F', FANG), Map.entry('L', LEATHER), Map.entry('k', LEATHER_DARK),
                Map.entry('S', STITCH));
        if (rows.length != face[3] || rows[0].length() != face[2]) {
            throw new IllegalArgumentException("Map is " + rows[0].length() + "x" + rows.length
                    + " but face is " + face[2] + "x" + face[3]);
        }
        for (int y = 0; y < face[3]; y++) {
            for (int x = 0; x < face[2]; x++) {
                char c = rows[y].charAt(x);
                if (c == '.') {
                    set(face[0] + x, face[1] + y, 0);
                    continue;
                }
                Integer rgb = colors.get(c);
                if (rgb == null) throw new IllegalArgumentException("No colour for '" + c + "'");
                boolean flat = c == 'Y' || c == 'B' || c == 'F' || c == 'M';
                set(face[0] + x, face[1] + y, flat ? rgb : vary(rgb, 8));
            }
        }
    }

    static void fill(int[] face, int rgb, int noise) {
        for (int y = 0; y < face[3]; y++) {
            for (int x = 0; x < face[2]; x++) set(face[0] + x, face[1] + y, vary(rgb, noise));
        }
    }

    static void clear(int[] face) {
        for (int y = 0; y < face[3]; y++) {
            for (int x = 0; x < face[2]; x++) set(face[0] + x, face[1] + y, 0);
        }
    }

    /** Brightness jitter so flat faces get the usual Minecraft pixel grain. */
    static int vary(int rgb, int amount) {
        int delta = amount == 0 ? 0 : RNG.nextInt(2 * amount + 1) - amount;
        int r = clamp(((rgb >> 16) & 0xFF) + delta);
        int g = clamp(((rgb >> 8) & 0xFF) + delta);
        int b = clamp((rgb & 0xFF) + delta);
        return (r << 16) | (g << 8) | b;
    }

    static int clamp(int c) {
        return Math.max(0, Math.min(255, c));
    }

    /** rgb 0 means transparent. */
    static void set(int x, int y, int rgb) {
        IMG.setRGB(x, y, rgb == 0 ? 0 : 0xFF000000 | rgb);
    }

    static BufferedImage scale(BufferedImage src, int factor) {
        BufferedImage out = new BufferedImage(src.getWidth() * factor, src.getHeight() * factor, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < out.getHeight(); y++) {
            for (int x = 0; x < out.getWidth(); x++) out.setRGB(x, y, src.getRGB(x / factor, y / factor));
        }
        return out;
    }
}
