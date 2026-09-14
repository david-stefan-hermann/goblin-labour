import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * Generates the 64x64 goblin entity textures goblin_<style>_<look>.png: one skin tint per job style (lumberjack
 * green, farmer yellowish, miner greyish, collector bluish) times four looks
 * (0 plain leather vest; 1 patched dark vest, gold earrings, scar; 2 sackcloth tunic, rope belt, bandage, nose ring;
 * 3 fur collar, bone necklace, one fang, topknot). Box positions and sizes must match GoblinModel.createBodyLayer,
 * file names GoblinRenderer. Also writes build/goblin-texture-preview.png (rows = styles, columns = looks).
 * Run from the project root: java tools/MakeGoblinTexture.java
 */
public class MakeGoblinTexture {
    static final String DIR = "src/main/resources/assets/goblinlabour/textures/entity/";
    static final String[] STYLES = {"lumberjack", "farmer", "miner", "collector"};
    static final int[] STYLE_SKIN = {0x6BB04E, 0x8FAE4C, 0x88967F, 0x5FA590};
    static final int LOOKS = 4;

    static final int EYE = 0xF2D14B, PUPIL = 0x1C1C1C, MOUTH = 0x2B1B12, FANG = 0xEDE6D2, EAR_INNER = 0xA7715F;
    static final int LEATHER = 0x6B4A2B, LEATHER_DARK = 0x4E3520, STITCH = 0x8C6A45, PATCH = 0x8A6A45;
    static final int BELT = 0x3A2A1C, BUCKLE = 0xB8A060, CLOTH = 0x8A6F4A, CLOTH_DARK = 0x6E5638, CLAW = 0xD9D2B0;
    static final int GOLD = 0xE0B83A, GOLD_DARK = 0x9C7A22, SCAR = 0xC79A86, HAIR = 0x2E3024;
    static final int BURLAP = 0x9C8560, BURLAP_DARK = 0x7A6545, ROPE = 0xB89B63, BANDAGE = 0xD8CFB8;
    static final int FUR = 0x8A7B66, FUR_DARK = 0x6A5C4A, BONE = 0xE3DAC0, PAINT = 0x8E3A2F;

    static final String[][] FACES = {
            {
                    "GGGGhGGG",
                    "GhGGGGGG",
                    "DDdGGdDD",
                    "gYBGGBYg",
                    "gGGddGGg",
                    "dMFMMFMd",
                    "ddGGGGdd",
            }, {
                    "GGGGhGGG",
                    "GhGGGGsG",
                    "DDdGGsDD",
                    "gYBGGBYg",
                    "gGGddGsg",
                    "dMFMMFMd",
                    "ddGGGGdd",
            }, {
                    "GGGGhGGG",
                    "GhGGGGhG",
                    "DDDDDDDD",
                    "gYBGGBYg",
                    "gGGddGGg",
                    "dFMMMMFd",
                    "ddGGGGdd",
            }, {
                    "GGGGhGGG",
                    "GhGGGGhG",
                    "DDdGGdDD",
                    "gYBGGBYg",
                    "gGGddGGg",
                    "dMFMMMMd",
                    "ddGGGGdd",
            },
    };

    static int skin, skinDark, skinDeep, skinLight, brow, vest;
    static int look;
    static BufferedImage img;
    static Random rng;

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
        new File(DIR).mkdirs();
        BufferedImage preview = new BufferedImage(64 * 4 * LOOKS, 64 * 4 * STYLES.length, BufferedImage.TYPE_INT_ARGB);
        Graphics2D sheet = preview.createGraphics();
        for (int s = 0; s < STYLES.length; s++) {
            skin = STYLE_SKIN[s];
            skinDark = shade(skin, 0.82f);
            skinDeep = shade(skin, 0.66f);
            skinLight = shade(skin, 1.13f);
            brow = shade(skin, 0.44f);
            for (int l = 0; l < LOOKS; l++) {
                look = l;
                vest = look == 1 ? 0x553A22 : LEATHER;
                img = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
                rng = new Random(1337 + l); // same grain for a look in every style
                head();
                body();
                arm(new Box(0, 24, 2, 7, 2), true);
                arm(new Box(8, 24, 2, 7, 2), false);
                leg(new Box(16, 24, 2, 4, 2), new Box(16, 30, 2, 1, 1));
                leg(new Box(24, 24, 2, 4, 2), new Box(24, 30, 2, 1, 1));
                ears();
                nose();
                loincloth();
                ImageIO.write(img, "png", new File(DIR + "goblin_" + STYLES[s] + "_" + l + ".png"));
                sheet.drawImage(scale(img, 4), l * 256, s * 256, null);
            }
        }
        sheet.dispose();
        File file = new File("build/goblin-texture-preview.png");
        file.getParentFile().mkdirs();
        ImageIO.write(preview, "png", file);
        System.out.println("Goblin textures written: " + STYLES.length * LOOKS);
    }

    static void head() {
        Box head = new Box(0, 0, 8, 7, 6);
        for (int[] face : head.all()) fill(face, skin, 10);
        fill(head.bottom(), skinDark, 6);
        // a few dark hairs and warts on the scalp
        int[] top = head.top();
        if (look != 3) { // look 3 has a shaved scalp under its topknot
            for (int i = 0; i < 5; i++) set(top[0] + rng.nextInt(top[2]), top[1] + rng.nextInt(top[3]), HAIR);
        }
        set(top[0] + 2, top[1] + 4, skinDeep);
        paint(head.front(), FACES[look]);
        // jaw shadow along the bottom row of the sides and back, a wart on one cheek
        for (int[] face : new int[][]{head.right(), head.left(), head.back()}) {
            for (int x = 0; x < face[2]; x++) set(face[0] + x, face[1] + face[3] - 1, vary(skinDark, 6));
        }
        set(head.right()[0] + 1, head.right()[1] + 4, skinDeep);
        int[] back = head.back();
        for (int x = 0; x < back[2]; x++) set(back[0] + x, back[1], vary(skinDark, 6));
        // taller skull (model part "head_cap", look 2): skin with the scalp hairs on top
        Box cap = new Box(0, 40, 8, 1, 6);
        for (int[] face : cap.all()) fill(face, skin, 10);
        int[] capTop = cap.top();
        for (int i = 0; i < 5; i++) set(capTop[0] + rng.nextInt(capTop[2]), capTop[1] + rng.nextInt(capTop[3]), HAIR);
        // tusks (model part "tusks", look 0)
        Box tusk = new Box(28, 40, 1, 1, 1);
        for (int[] face : tusk.all()) fill(face, FANG, 6);
        fill(tusk.bottom(), shade(FANG, 0.8f), 4);
        if (look == 3) {
            // topknot geometry (GoblinModel "topknot"): a hair knot with a bone ring, and a braid with a bone bead
            Box knot = new Box(28, 0, 2, 2, 2);
            Box braid = new Box(36, 0, 1, 4, 1);
            for (int[] face : knot.all()) fill(face, HAIR, 10);
            for (int[] face : knot.sides()) {
                set(face[0], face[1] + 1, BONE);
                set(face[0] + 1, face[1] + 1, shade(BONE, 0.85f));
            }
            for (int[] face : braid.all()) fill(face, HAIR, 8);
            for (int[] face : braid.sides()) {
                set(face[0], face[1] + 1, shade(HAIR, 1.6f));
                set(face[0], face[1] + 3, BONE);
            }
        }
    }

    static void body() {
        Box skinBox = new Box(0, 14, 6, 6, 4);
        for (int[] face : skinBox.all()) fill(face, skin, 10);
        // belly shows through the open vest
        paint(skinBox.front(), new String[]{
                "gGGGGg",
                "GdGGdG",
                "GGhlGG",
                "GlldlG",
                "gGllGg",
                "gggggg",
        });
        if (look == 3) {
            int[] front = skinBox.front();
            for (int x = 1; x < 5; x++) set(front[0] + x, front[1] + 1, x % 2 == 0 ? BONE : FUR_DARK);
            set(front[0] + 2, front[1] + 2, BONE);
            set(front[0] + 3, front[1] + 2, shade(BONE, 0.85f));
        }

        Box over = new Box(20, 14, 6, 6, 4);
        if (look == 2) {
            // closed sackcloth tunic with a V neck
            for (int[] face : over.all()) fill(face, BURLAP, 12);
            clear(over.bottom());
            paint(over.front(), new String[]{
                    "CC..CC",
                    "CCcCCC",
                    "CCCCCC",
                    "CCCCcC",
                    "CcCCCC",
                    "cccccc",
            });
            int[] back = over.back();
            set(back[0] + 1, back[1] + 2, BURLAP_DARK);
            set(back[0] + 4, back[1] + 3, BURLAP_DARK);
            for (int[] face : new int[][]{over.right(), over.left(), back}) {
                for (int x = 0; x < face[2]; x++) set(face[0] + x, face[1] + face[3] - 1, vary(BURLAP_DARK, 6));
            }
        } else {
            for (int[] face : over.all()) fill(face, vest, 12);
            clear(over.bottom());
            paint(over.front(), new String[]{
                    "LS..SL",
                    "LS..SL",
                    "LS..SL",
                    "LLS.SL",
                    "LLS.LL",
                    "kkk.kk",
            });
            int[] back = over.back();
            for (int y = 0; y < back[3]; y++) set(back[0] + 2 + (y % 2), back[1] + y, STITCH);
            for (int[] face : new int[][]{over.right(), over.left(), back}) {
                for (int x = 0; x < face[2]; x++) set(face[0] + x, face[1] + face[3] - 1, vary(shade(vest, 0.73f), 6));
            }
            if (look == 1) {
                // a lighter leather patch on the back, stitched at the corners
                for (int y = 1; y < 3; y++) {
                    for (int x = 3; x < 5; x++) set(back[0] + x, back[1] + y, vary(PATCH, 6));
                }
                set(back[0] + 3, back[1] + 1, STITCH);
                set(back[0] + 4, back[1] + 2, STITCH);
            }
            if (look == 3) {
                // fur collar over the shoulders
                int[] top = over.top();
                for (int y = 0; y < top[3]; y++) {
                    for (int x = 0; x < top[2]; x++) {
                        if (x != 2 && x != 3) set(top[0] + x, top[1] + y, vary(FUR, 12));
                    }
                }
                int[] front = over.front();
                for (int x : new int[]{0, 1, 4, 5}) set(front[0] + x, front[1], vary(FUR, 12));
                for (int[] face : new int[][]{over.right(), over.left(), back}) {
                    for (int x = 0; x < face[2]; x++) set(face[0] + x, face[1], vary(rng.nextBoolean() ? FUR : FUR_DARK, 8));
                }
            }
        }
        if (look != 2) {
            int[] top = over.top();
            for (int y = 0; y < top[3]; y++) {
                set(top[0] + 2, top[1] + y, 0);
                set(top[0] + 3, top[1] + y, 0);
            }
        } else {
            int[] top = over.top();
            for (int y = 1; y < 3; y++) {
                set(top[0] + 2, top[1] + y, 0);
                set(top[0] + 3, top[1] + y, 0);
            }
        }

        Box belt = new Box(40, 18, 6, 1, 4);
        int beltColor = look == 2 ? ROPE : BELT;
        for (int[] face : belt.all()) fill(face, beltColor, 8);
        int[] front = belt.front();
        int knot = look == 2 ? shade(ROPE, 0.72f) : BUCKLE;
        set(front[0] + 2, front[1], knot);
        set(front[0] + 3, front[1], knot);
    }

    static void arm(Box arm, boolean right) {
        for (int[] face : arm.all()) fill(face, skin, 10);
        fill(arm.bottom(), skinDark, 6);
        for (int[] face : arm.sides()) {
            for (int x = 0; x < face[2]; x++) {
                if (look == 2) {
                    if (right) {
                        for (int y = 1; y < 5; y++) set(face[0] + x, face[1] + y, (x + y) % 3 == 0 ? shade(BANDAGE, 0.82f) : vary(BANDAGE, 8));
                    }
                } else if (look == 3) {
                    set(face[0] + x, face[1] + 4, (x % 2 == 0) ? BONE : FUR_DARK); // bone bracelet
                } else {
                    set(face[0] + x, face[1] + 3, vary(LEATHER, 10)); // leather wrap on the forearm
                    set(face[0] + x, face[1] + 4, vary(LEATHER_DARK, 8));
                }
                set(face[0] + x, face[1] + 6, vary(skinDark, 6)); // knuckles
            }
        }
        int[] front = arm.front();
        set(front[0], front[1] + 6, CLAW);
        set(front[0] + 1, front[1] + 6, CLAW);
    }

    static void leg(Box leg, Box foot) {
        for (int[] face : leg.all()) fill(face, skinDark, 8);
        for (int[] face : leg.sides()) {
            for (int x = 0; x < face[2]; x++) set(face[0] + x, face[1], vary(skinDeep, 6));
        }
        if (look == 2) {
            // cloth foot wraps
            for (int[] face : foot.all()) fill(face, BURLAP_DARK, 10);
            for (int[] face : leg.sides()) {
                for (int x = 0; x < face[2]; x++) set(face[0] + x, face[1] + 3, vary(BURLAP_DARK, 10));
            }
            return;
        }
        for (int[] face : foot.all()) fill(face, skinDark, 8);
        int[] front = foot.front();
        set(front[0], front[1], CLAW);
        set(front[0] + 1, front[1], CLAW);
    }

    static void ears() {
        Box[] parts = {new Box(32, 24, 2, 3, 1), new Box(32, 28, 2, 2, 1), new Box(32, 31, 2, 1, 1)};
        for (Box part : parts) {
            for (int[] face : part.all()) fill(face, skin, 10);
        }
        // pinkish inner ear on the forward face, fading towards the tip
        int[] base = parts[0].front();
        set(base[0], base[1] + 1, EAR_INNER);
        set(base[0] + 1, base[1] + 1, EAR_INNER);
        set(base[0] + 1, base[1] + 2, skinDark);
        int[] mid = parts[1].front();
        set(mid[0], mid[1] + 1, EAR_INNER);
        set(mid[0] + 1, mid[1] + 1, skinDark);
        int[] tip = parts[2].front();
        set(tip[0] + 1, tip[1], skinDark);
        // outer column of the base below the notch (model part "left_ear_notched", look 1)
        Box notchBase = new Box(32, 40, 1, 2, 1);
        for (int[] face : notchBase.all()) fill(face, skin, 10);
        int[] notchFront = notchBase.front();
        set(notchFront[0], notchFront[1], EAR_INNER);
        set(notchFront[0], notchFront[1] + 1, skinDark);
        if (look == 1) {
            // gold hoop through the lobe
            int[] back = parts[0].back();
            int[] bottom = parts[0].bottom();
            set(base[0] + 1, base[1] + 2, GOLD);
            set(back[0], back[1] + 2, GOLD);
            set(bottom[0], bottom[1], GOLD_DARK);
            set(bottom[0] + 1, bottom[1], GOLD);
            set(notchFront[0], notchFront[1] + 1, GOLD);
            set(notchBase.back()[0], notchBase.back()[1] + 1, GOLD);
            set(notchBase.bottom()[0], notchBase.bottom()[1], GOLD);
        } else if (look == 3) {
            int[] back = parts[1].back();
            set(mid[0] + 1, mid[1], BONE);
            set(back[0], back[1], BONE);
        }
    }

    static void nose() {
        Box nose = new Box(40, 24, 2, 2, 2);
        Box tip = new Box(40, 28, 2, 1, 1);
        for (int[] face : nose.all()) fill(face, skin, 8);
        for (int[] face : tip.all()) fill(face, skinDark, 6);
        int[] top = nose.top();
        set(top[0], top[1], skinLight);
        set(top[0] + 1, top[1], skinLight);
        int[] under = tip.bottom();
        set(under[0], under[1], skinDeep);
        set(under[0] + 1, under[1], skinDeep);
        if (look == 2) {
            int[] front = tip.front();
            set(under[0], under[1], GOLD);
            set(under[0] + 1, under[1], GOLD_DARK);
            set(front[0] + 1, front[1], GOLD);
        }
    }

    static void loincloth() {
        int base = switch (look) {
            case 2 -> CLOTH_DARK;
            case 3 -> FUR;
            default -> CLOTH;
        };
        for (int[] face : new int[][]{new Box(48, 24, 4, 3, 0).front(), new Box(48, 24, 4, 3, 0).back(),
                new Box(48, 27, 4, 3, 0).front(), new Box(48, 27, 4, 3, 0).back()}) {
            fill(face, base, 12);
            for (int x = 0; x < face[2]; x++) set(face[0] + x, face[1], vary(shade(base, 0.8f), 6));
            if (look == 1) {
                for (int x = 0; x < face[2]; x++) set(face[0] + x, face[1] + 1, vary(PAINT, 8)); // red stripe
            } else if (look == 3) {
                set(face[0] + rng.nextInt(face[2]), face[1] + 1, FUR_DARK); // spots on the hide
                set(face[0] + rng.nextInt(face[2]), face[1] + 2, FUR_DARK);
            }
            // frayed hem
            set(face[0] + 1 + rng.nextInt(2), face[1] + face[3] - 1, 0);
        }
    }

    // ---- painting helpers ----

    static void paint(int[] face, String[] rows) {
        Map<Character, Integer> colors = new HashMap<>();
        colors.put('G', skin);
        colors.put('g', skinDark);
        colors.put('d', skinDeep);
        colors.put('h', skinLight);
        colors.put('l', skinLight);
        colors.put('D', brow);
        colors.put('Y', EYE);
        colors.put('B', PUPIL);
        colors.put('M', MOUTH);
        colors.put('F', FANG);
        colors.put('L', vest);
        colors.put('k', shade(vest, 0.73f));
        colors.put('S', STITCH);
        colors.put('s', SCAR);
        colors.put('R', PAINT);
        colors.put('C', BURLAP);
        colors.put('c', BURLAP_DARK);
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
        int delta = amount == 0 ? 0 : rng.nextInt(2 * amount + 1) - amount;
        int r = clamp(((rgb >> 16) & 0xFF) + delta);
        int g = clamp(((rgb >> 8) & 0xFF) + delta);
        int b = clamp((rgb & 0xFF) + delta);
        return (r << 16) | (g << 8) | b;
    }

    static int shade(int rgb, float factor) {
        int r = clamp(Math.round(((rgb >> 16) & 0xFF) * factor));
        int g = clamp(Math.round(((rgb >> 8) & 0xFF) * factor));
        int b = clamp(Math.round((rgb & 0xFF) * factor));
        return (r << 16) | (g << 8) | b;
    }

    static int clamp(int c) {
        return Math.max(0, Math.min(255, c));
    }

    /** rgb 0 means transparent. */
    static void set(int x, int y, int rgb) {
        img.setRGB(x, y, rgb == 0 ? 0 : 0xFF000000 | rgb);
    }

    static BufferedImage scale(BufferedImage src, int factor) {
        BufferedImage out = new BufferedImage(src.getWidth() * factor, src.getHeight() * factor, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < out.getHeight(); y++) {
            for (int x = 0; x < out.getWidth(); x++) out.setRGB(x, y, src.getRGB(x / factor, y / factor));
        }
        return out;
    }
}
