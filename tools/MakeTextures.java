import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Map;

/**
 * Generates the 16x16 item textures and the 128x128 mod icon from character maps.
 * Run from the project root: java tools/MakeTextures.java
 */
public class MakeTextures {
    static final String ASSETS = "src/main/resources/assets/goblinlabour/";

    static final String[] HEAD = {
            "................",
            "................",
            "....GGGGGGGG....",
            "...GGGGGGGGGG...",
            "..EGGGGGGGGGGE..",
            ".EEGGGGGGGGGGEE.",
            "EEEGYYGGGGYYGEEE",
            "EEEGYBGGGGBYGEEE",
            ".EEGGGGGGGGGGEE.",
            "..EGGGGNNGGGGE..",
            "...GGGGGGGGGG...",
            "...GGDDDDDDGG...",
            "....GDWDDWDG....",
            ".....GGGGGG.....",
            "................",
            "................",
    };
    static final Map<Character, Integer> HEAD_COLORS = Map.of(
            'G', 0x6BB04E, 'E', 0x55913D, 'Y', 0xF2D14B, 'B', 0x1C1C1C, 'N', 0x3E6E2C, 'D', 0x2B1B12, 'W', 0xEDE6D2);

    static final String[] MEAT = {
            "................",
            "................",
            ".....RRRRRR.....",
            "....RrRRRRrR....",
            "...RRRRSSRRRR...",
            "...RrRRSSRRrR...",
            "..RRRSSSSSSRRR..",
            "..RRRSSSSSSRRR..",
            "...RrRRSSRRrR...",
            "...RRRRSSRRRR...",
            "....RrRRRRrR....",
            ".....RRRRRR.....",
            "................",
            "................",
            "................",
            "................",
    };
    static final Map<Character, Integer> MEAT_COLORS = Map.of('R', 0x8E3A2F, 'r', 0xB4574A, 'S', 0xD8C08A);

    static final String[] BLANK = {
            "................",
            "......PPPP......",
            ".....PPPPPP.....",
            "....pPPPPPPp....",
            "....pPKPPKPp....",
            ".....PPPPPP.....",
            "......PPPP......",
            ".....PPPPPP.....",
            "....PPPPPPPP....",
            "...PPPPPPPPPP...",
            "...PPPPPPPPPP...",
            "....PPPPPPPP....",
            ".....PP..PP.....",
            ".....PP..PP.....",
            "................",
            "................",
    };
    static final Map<Character, Integer> BLANK_COLORS = Map.of('P', 0xA8C48A, 'p', 0x8FA872, 'K', 0x3A3A2A);

    static final String[] STAFF = {
            "............GGG.",
            "...........GGGGG",
            "...........GYGYG",
            "...........GGGGG",
            "............GDG.",
            "...........SS...",
            "..........SS....",
            ".........SS.....",
            "........SS......",
            ".......SS.......",
            "......SS........",
            ".....SS.........",
            "....SS..........",
            "...SS...........",
            "..SS............",
            ".SS.............",
    };
    static final Map<Character, Integer> STAFF_COLORS = Map.of('G', 0x6BB04E, 'Y', 0xF2D14B, 'D', 0x2B1B12, 'S', 0x8A6236);

    static final String[] BOOK = {
            "................",
            "...SCCCCCCCCC...",
            "..SDCCCCCCCCCP..",
            "..SDCCCCCCCCCPp.",
            "..SDCCCYYYCCCPp.",
            "..SDCCYYYYYCCPp.",
            "..SDCCYBYBYCCPp.",
            "..SDCCYYYYYCCPp.",
            "..SDCCCYYYCCCPp.",
            "..SDCCCCCCCCCPp.",
            "..SDCCCCCCCCCPp.",
            "..SDCCCCCCCCCPp.",
            "..SDCCCCCCCCCPp.",
            "...SPPPPPPPPPp..",
            "....pppppppppp..",
            "................",
    };
    static final Map<Character, Integer> BOOK_COLORS = Map.of(
            'S', 0x1F4A26, 'D', 0x2F6B38, 'C', 0x3E8E4B, 'Y', 0x8CC63F, 'B', 0x1C1C1C, 'P', 0xEDE6D2, 'p', 0xC9BFA3);

    public static void main(String[] args) throws Exception {
        save(render(BOOK, BOOK_COLORS), ASSETS + "textures/item/goblin_handbook.png");
        save(render(STAFF, STAFF_COLORS), ASSETS + "textures/item/goblin_staff.png");
        BufferedImage head = render(HEAD, HEAD_COLORS);
        save(head, ASSETS + "textures/item/goblin_head.png");
        // the mod icon (icon.png) comes from tools/MakeLogo.java
        save(render(MEAT, MEAT_COLORS), ASSETS + "textures/item/goblin_meat_pack.png");
        save(render(BLANK, BLANK_COLORS), ASSETS + "textures/item/goblin_blank.png");
        System.out.println("Textures written");
    }

    static BufferedImage render(String[] rows, Map<Character, Integer> colors) {
        int size = rows.length;
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                char c = rows[y].charAt(x);
                if (c == '.') continue;
                Integer rgb = colors.get(c);
                if (rgb == null) throw new IllegalArgumentException("No colour for '" + c + "'");
                img.setRGB(x, y, 0xFF000000 | rgb);
            }
        }
        // darken opaque pixels that touch a transparent one: a cheap outline
        BufferedImage out = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                int argb = img.getRGB(x, y);
                if ((argb >>> 24) == 0) continue;
                boolean edge = false;
                for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                    int nx = x + d[0], ny = y + d[1];
                    if (nx < 0 || ny < 0 || nx >= size || ny >= size || (img.getRGB(nx, ny) >>> 24) == 0) edge = true;
                }
                out.setRGB(x, y, edge ? darken(argb, 0.55f) : argb);
            }
        }
        return out;
    }

    static int darken(int argb, float f) {
        int r = (int) (((argb >> 16) & 0xFF) * f);
        int g = (int) (((argb >> 8) & 0xFF) * f);
        int b = (int) ((argb & 0xFF) * f);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    static BufferedImage scale(BufferedImage src, int factor) {
        BufferedImage out = new BufferedImage(src.getWidth() * factor, src.getHeight() * factor, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < out.getHeight(); y++) {
            for (int x = 0; x < out.getWidth(); x++) {
                out.setRGB(x, y, src.getRGB(x / factor, y / factor));
            }
        }
        return out;
    }

    static void save(BufferedImage img, String path) throws Exception {
        File file = new File(path);
        file.getParentFile().mkdirs();
        ImageIO.write(img, "png", file);
    }
}
