import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.InputStream;
import java.util.zip.ZipFile;

/**
 * Generates the Goblin Chest textures (single, left and right half) from the vanilla chest textures in the Minecraft
 * client jar: the wood turns mossy green, the lock becomes a fang, the inside of the lid and of the box become a
 * mouth, and the free area below the vanilla layout (v 43 and down) holds the teeth and the tongue that
 * {@code GoblinChestLayers} adds to the model. The UV layout of the vanilla parts is unchanged, so the vanilla chest
 * item renderer can draw the closed chest with these textures.
 * Run from the project root: java tools/MakeChestTextures.java
 */
public class MakeChestTextures {
    static final String CLIENT_JAR = System.getProperty("user.home") + "/.gradle/caches/fabric-loom/26.2/minecraft-client.jar";
    static final String OUT = "src/main/resources/assets/goblinlabour/textures/entity/chest/";

    static final int ROOF = 0x7A2630, ROOF_RIDGE = 0x5E1A22;
    static final int FLOOR = 0x5A171D, FLOOR_EDGE = 0x43100F;
    static final int IVORY = 0xEFE8D2, IVORY_SHADE = 0xD2C6A4, IVORY_DARK = 0xB3A583;
    static final int TONGUE = 0xC8606E, TONGUE_LINE = 0xA3485A;

    public static void main(String[] args) throws Exception {
        new File(OUT).mkdirs();
        try (ZipFile jar = new ZipFile(CLIENT_JAR)) {
            write(jar, "normal", "goblin", 14);
            write(jar, "normal_left", "goblin_left", 15);
            write(jar, "normal_right", "goblin_right", 15);
        }
    }

    /** {@code width} is the box width of this texture's lid and bottom: 14 for a single chest, 15 for a half. */
    static void write(ZipFile jar, String vanilla, String name, int width) throws Exception {
        BufferedImage src;
        try (InputStream in = jar.getInputStream(jar.getEntry("assets/minecraft/textures/entity/chest/" + vanilla + ".png"))) {
            src = ImageIO.read(in);
        }
        BufferedImage img = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 64; y++) {
            for (int x = 0; x < 64; x++) {
                int argb = src.getRGB(x, y);
                if ((argb >>> 24) == 0) continue;
                img.setRGB(x, y, 0xFF000000 | mossy(argb));
            }
        }
        // cube layout: DOWN face at (u + depth, v), UP face at (u + depth + width, v); depth is 14 for lid and box
        mouth(src, img, 14, 0, width, 14, true);            // underside of the lid: the palate
        mouth(src, img, 14 + width, 19, width, 14, false);  // top of the box: the floor of the mouth
        fang(img, width == 14 ? 6 : 4);                      // the lock
        teeth(img);
        tongue(img);
        ImageIO.write(img, "png", new File(OUT + name + ".png"));
        preview(img, name);
        System.out.println("wrote " + OUT + name + ".png");
    }

    /** Oak brown to moss green; the dark iron bands get a slight green tint. */
    static int mossy(int argb) {
        int r = (argb >> 16) & 0xFF, g = (argb >> 8) & 0xFF, b = argb & 0xFF;
        float[] hsb = Color.RGBtoHSB(r, g, b, null);
        if (hsb[1] > 0.25f) {
            float hue = 0.25f + (hsb[0] - 0.08f) * 0.6f;
            return Color.HSBtoRGB(hue, Math.min(1.0f, hsb[1] * 0.72f), hsb[2] * 0.88f) & 0xFFFFFF;
        }
        return rgb((int) (r * 0.82), (int) (g * 0.95), (int) (b * 0.78));
    }

    /** The dark inside of a face becomes mouth, the light border around it stays wood. */
    static void mouth(BufferedImage src, BufferedImage img, int u, int v, int w, int h, boolean palate) {
        for (int y = v; y < v + h; y++) {
            for (int x = u; x < u + w; x++) {
                int argb = src.getRGB(x, y);
                float[] hsb = Color.RGBtoHSB((argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF, null);
                if (hsb[2] > 0.3f) continue;
                int color;
                if (palate) {
                    color = (y - v) % 3 == 1 ? ROOF_RIDGE : ROOF;
                } else {
                    boolean edge = x <= u + 1 || x >= u + w - 2 || y <= v + 1 || y >= v + h - 2;
                    color = edge ? FLOOR_EDGE : FLOOR;
                }
                img.setRGB(x, y, 0xFF000000 | color);
            }
        }
    }

    /** The lock area (u 0..size, v 0..5) in ivory with a shaded edge. */
    static void fang(BufferedImage img, int size) {
        for (int y = 0; y < 5; y++) {
            for (int x = 0; x < size; x++) {
                int color = (y == 4 || x == 0) ? IVORY_SHADE : IVORY;
                img.setRGB(x, y, 0xFF000000 | color);
            }
        }
    }

    /** Teeth texture area: u 0..16, v 43..48 (upper tooth 0,43; upper fang 4,43; lower tooth 8,43; lower fang 12,43). */
    static void teeth(BufferedImage img) {
        for (int y = 43; y < 48; y++) {
            for (int x = 0; x < 16; x++) {
                int color = y == 47 ? IVORY_DARK : (x % 4 == 0 ? IVORY_SHADE : IVORY);
                img.setRGB(x, y, 0xFF000000 | color);
            }
        }
    }

    /** Tongue texture area: u 0..26, v 48..56, a pink box with a groove down the middle. */
    static void tongue(BufferedImage img) {
        for (int y = 48; y < 56; y++) {
            for (int x = 0; x < 26; x++) {
                int color = (x == 10 || x == 23) ? TONGUE_LINE : TONGUE;
                img.setRGB(x, y, 0xFF000000 | color);
            }
        }
    }

    static int rgb(int r, int g, int b) {
        return (Math.min(255, r) << 16) | (Math.min(255, g) << 8) | Math.min(255, b);
    }

    static void preview(BufferedImage img, String name) throws Exception {
        int f = 8;
        BufferedImage out = new BufferedImage(64 * f, 64 * f, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 64 * f; y++) {
            for (int x = 0; x < 64 * f; x++) {
                int c = img.getRGB(x / f, y / f);
                if ((c >>> 24) == 0) c = ((x / f + y / f) % 2 == 0) ? 0xFF202020 : 0xFF303030;
                out.setRGB(x, y, c);
            }
        }
        new File("build").mkdirs();
        ImageIO.write(out, "png", new File("build/chest-texture-preview-" + name + ".png"));
    }
}
