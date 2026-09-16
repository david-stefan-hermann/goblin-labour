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

    /** Milk Churn: a grey metal tank with dark bands, rivets and a glass window (half transparent). */
    static final String[] CHURN_SIDE = {
            "DDDDDDDDDDDDDDDD",
            "dRdddddddddddRdd",
            "MMMMMMMMMMMMMMMM",
            "MmMMMMFFFFMMMMmM",
            "MmMMMMFgGFMMMMmM",
            "MmMMMMFGGFMMMMmM",
            "MmMMMMFgGFMMMMmM",
            "DDDDDDFGGFDDDDDD",
            "dRddddFGGFdddRdd",
            "MmMMMMFGgFMMMMmM",
            "MmMMMMFGGFMMMMmM",
            "MmMMMMFgGFMMMMmM",
            "MmMMMMFFFFMMMMmM",
            "MMMMMMMMMMMMMMMM",
            "DDDDDDDDDDDDDDDD",
            "dRdddddddddddRdd",
    };
    static final String[] CHURN_TOP = {
            "DDDDDDDDDDDDDDDD",
            "DMMMMMMMMMMMMMMD",
            "DMmmmmmmmmmmmmMD",
            "DMmMMMMMMMMMMmMD",
            "DMmMMMMddMMMMmMD",
            "DMmMMMdHHdMMMmMD",
            "DMmMMdHhhHdMMmMD",
            "DMmMdHhFFhHdMmMD",
            "DMmMdHhFFhHdMmMD",
            "DMmMMdHhhHdMMmMD",
            "DMmMMMdHHdMMMmMD",
            "DMmMMMMddMMMMmMD",
            "DMmMMMMMMMMMMmMD",
            "DMmmmmmmmmmmmmMD",
            "DMMMMMMMMMMMMMMD",
            "DDDDDDDDDDDDDDDD",
    };
    static final String[] CHURN_BOTTOM = {
            "DDDDDDDDDDDDDDDD",
            "DddddddddddddddD",
            "DdMMMMMMMMMMMMdD",
            "DdMddddddddddMdD",
            "DdMdMMMMMMMMdMdD",
            "DdMdMddddddMdMdD",
            "DdMdMdMMMMdMdMdD",
            "DdMdMdMddMdMdMdD",
            "DdMdMdMddMdMdMdD",
            "DdMdMdMMMMdMdMdD",
            "DdMdMddddddMdMdD",
            "DdMdMMMMMMMMdMdD",
            "DdMddddddddddMdD",
            "DdMMMMMMMMMMMMdD",
            "DddddddddddddddD",
            "DDDDDDDDDDDDDDDD",
    };
    /** ARGB: the glass is half transparent. */
    static final Map<Character, Integer> CHURN_COLORS = Map.of(
            'M', 0xFF9AA0A6, 'm', 0xFFB7BCC1, 'D', 0xFF5C6166, 'd', 0xFF484C50, 'R', 0xFFD0D4D8,
            'F', 0xFF3E4246, 'G', 0x78C4CCD4, 'g', 0x9CE6ECF0, 'H', 0xFF6E7378, 'h', 0xFF83898E);

    /** The milk the churn shows behind its glass: cream with a few darker flecks. */
    static final String[] CHURN_MILK = {
            "WWWWWWWWWWWWWWWW",
            "WWWWWWWWWWwWWWWW",
            "WWwWWWWWWWWWWWWW",
            "WWWWWWWWWWWWWWwW",
            "WWWWWWwWWWWWWWWW",
            "WWWWWWWWWWWWWWWW",
            "WwWWWWWWWWWwWWWW",
            "WWWWWWWWWWWWWWWW",
            "WWWWWwWWWWWWWWWW",
            "WWWWWWWWWWWWWwWW",
            "WWWWWWWWwWWWWWWW",
            "WWwWWWWWWWWWWWWW",
            "WWWWWWWWWWWWWWWW",
            "WWWWWWWWWWwWWWWW",
            "WWWWwWWWWWWWWWWW",
            "WWWWWWWWWWWWWWwW",
    };
    static final Map<Character, Integer> CHURN_MILK_COLORS = Map.of('W', 0xFFF4F1E6, 'w', 0xFFE3DECB);

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

    /** Gold ring with a goblin-eye gem; drawn with its own outline (the automatic one would darken the thin band). */
    static final String[] RING = {
            "................",
            "......OOOO......",
            ".....OGGGGO.....",
            ".....OYYBYO.....",
            ".....OGGGGO.....",
            "......OAAO......",
            "....OOAAAAOO....",
            "...OAAaOOaAAO...",
            "..OAAO....OAAO..",
            "..OAO......OAO..",
            "..OAO......OAO..",
            "..OAaO....OaAO..",
            "...OAAaOOaAAO...",
            "....OOAAAAOO....",
            "......OOOO......",
            "................",
    };
    static final Map<Character, Integer> RING_COLORS = Map.of(
            'O', 0x5A3F0E, 'A', 0xF2C443, 'a', 0xB8861C, 'G', 0x4E9A3A, 'Y', 0xF2D14B, 'B', 0x1C1C1C);

    public static void main(String[] args) throws Exception {
        save(render(RING, RING_COLORS, false), ASSETS + "textures/item/goblin_ring.png");
        save(render(BOOK, BOOK_COLORS), ASSETS + "textures/item/goblin_handbook.png");
        save(render(STAFF, STAFF_COLORS), ASSETS + "textures/item/goblin_staff.png");
        BufferedImage head = render(HEAD, HEAD_COLORS);
        save(head, ASSETS + "textures/item/goblin_head.png");
        // the mod icon (icon.png) comes from tools/MakeLogo.java
        save(render(MEAT, MEAT_COLORS), ASSETS + "textures/item/goblin_meat_pack.png");
        save(render(BLANK, BLANK_COLORS), ASSETS + "textures/item/goblin_blank.png");
        save(renderArgb(CHURN_SIDE, CHURN_COLORS), ASSETS + "textures/block/milk_churn_side.png");
        save(renderArgb(CHURN_TOP, CHURN_COLORS), ASSETS + "textures/block/milk_churn_top.png");
        save(renderArgb(CHURN_BOTTOM, CHURN_COLORS), ASSETS + "textures/block/milk_churn_bottom.png");
        save(renderArgb(CHURN_MILK, CHURN_MILK_COLORS), ASSETS + "textures/block/milk_churn_milk.png");
        System.out.println("Textures written");
    }

    /** Like {@link #render}, but the colours carry their own alpha and there is no outline. */
    static BufferedImage renderArgb(String[] rows, Map<Character, Integer> colors) {
        int size = rows.length;
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                char c = rows[y].charAt(x);
                if (c == '.') continue;
                Integer argb = colors.get(c);
                if (argb == null) throw new IllegalArgumentException("No colour for '" + c + "'");
                img.setRGB(x, y, argb);
            }
        }
        return img;
    }

    static BufferedImage render(String[] rows, Map<Character, Integer> colors) {
        return render(rows, colors, true);
    }

    static BufferedImage render(String[] rows, Map<Character, Integer> colors, boolean outline) {
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
        if (!outline) return img;
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
