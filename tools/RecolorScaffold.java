import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * Makes the Goblin Scaffold textures from vanilla's scaffolding textures: every yellow-green (bamboo) pixel is
 * turned brown, the existing browns stay. Run from the project root:
 *   java tools/RecolorScaffold.java <dir-with-vanilla-scaffolding_*.png>
 */
public class RecolorScaffold {
    static final String OUT = "src/main/resources/assets/goblinlabour/textures/block/";

    public static void main(String[] args) throws Exception {
        String src = args.length > 0 ? args[0] : "C:/jtmp/van/assets/minecraft/textures/block/";
        for (String part : new String[]{"top", "side", "bottom"}) {
            BufferedImage in = ImageIO.read(new File(src, "scaffolding_" + part + ".png"));
            BufferedImage out = new BufferedImage(in.getWidth(), in.getHeight(), BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < in.getHeight(); y++) {
                for (int x = 0; x < in.getWidth(); x++) {
                    out.setRGB(x, y, brown(in.getRGB(x, y)));
                }
            }
            File file = new File(OUT, "goblin_scaffold_" + part + ".png");
            file.getParentFile().mkdirs();
            ImageIO.write(out, "png", file);
            System.out.println("wrote " + file);
        }
    }

    /** Hue 40..170 (yellow, olive, green) becomes wood brown; everything else is kept. */
    static int brown(int argb) {
        int alpha = argb >>> 24;
        if (alpha == 0) return argb;
        float[] hsb = Color.RGBtoHSB((argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF, null);
        float hue = hsb[0] * 360f;
        if (hue < 40f || hue > 170f) return argb;
        float newHue = 26f / 360f;
        float sat = Math.min(0.58f, hsb[1] * 0.95f);
        float bri = hsb[2] * 0.82f;
        int rgb = Color.HSBtoRGB(newHue, sat, bri) & 0xFFFFFF;
        return (alpha << 24) | rgb;
    }
}
