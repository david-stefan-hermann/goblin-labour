import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * Generates the mod icon: a goblin head on a wooden disc inside a ring of iron chain links, 32x32 pixel art
 * written at 8x (256 px) to assets/goblinlabour/icon.png.
 * Run from the project root: java tools/MakeLogo.java
 */
public class MakeLogo {
    static final String OUT_FILE = "src/main/resources/assets/goblinlabour/icon.png";
    static final int N = 32;
    static final int SCALE = 8;

    static final int SKIN = 0x6BB04E, SKIN_D = 0x55913D, SKIN_DD = 0x3F6E2D, SKIN_L = 0x86C96A;
    static final int EYE = 0xF2D14B, PUPIL = 0x1C1C1C, MOUTH = 0x2B1B12, FANG = 0xEDE6D2, EAR = 0xA7715F;
    static final int OUTLINE = 0x16200F, HAIR = 0x2E3024;
    static final int WOOD = 0x9A6A34, WOOD_D = 0x6A4520, WOOD_DD = 0x4A2E14;
    static final int IRON = 0x8A9194, IRON_D = 0x5A6164;

    public static void main(String[] args) throws Exception {
        int[][] c = new int[N][N];
        circle(c, 15.5, 15.5, 11.5, WOOD_DD);
        circle(c, 15.5, 15.5, 10.5, WOOD);
        for (int y = 7; y < 26; y += 4) {
            for (int x = 0; x < N; x++) if (get(c, x, y) == WOOD) set(c, x, y, WOOD_D);
        }
        chain(c, 12);
        c = outline(c);
        int[][] head = new int[N][N];
        goblinHead(head, 1);
        overlay(c, outline(head));

        BufferedImage img = new BufferedImage(N * SCALE, N * SCALE, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < N * SCALE; y++) for (int x = 0; x < N * SCALE; x++) img.setRGB(x, y, c[y / SCALE][x / SCALE]);
        File file = new File(OUT_FILE);
        file.getParentFile().mkdirs();
        ImageIO.write(img, "png", file);
        System.out.println("Icon written: " + file.getPath());
    }

    /** Iron chain around the disc: links alternate between seen from the front (a ring) and edge-on (a bar). */
    static void chain(int[][] c, int links) {
        for (int i = 0; i < links; i++) {
            double a = 2 * Math.PI * i / links;
            double lx = 15.5 + Math.cos(a) * 13.0, ly = 15.5 + Math.sin(a) * 13.0;
            if (i % 2 == 0) {
                for (int y = 0; y < N; y++) for (int x = 0; x < N; x++) {
                    double d = Math.hypot(x - lx, y - ly);
                    if (d <= 1.2) c[y][x] = 0;
                    else if (d <= 2.6) set(c, x, y, y > ly ? IRON_D : IRON);
                }
            } else {
                double tx = -Math.sin(a), ty = Math.cos(a);
                line(c, (int) Math.round(lx - tx * 2.2), (int) Math.round(ly - ty * 2.2),
                        (int) Math.round(lx + tx * 2.2), (int) Math.round(ly + ty * 2.2), IRON);
                line(c, (int) Math.round(lx - tx * 2.2 + Math.cos(a)), (int) Math.round(ly - ty * 2.2 + Math.sin(a)),
                        (int) Math.round(lx + tx * 2.2 + Math.cos(a)), (int) Math.round(ly + ty * 2.2 + Math.sin(a)), IRON_D);
            }
        }
    }

    /** Head 14x14 at x 9..22, y 8..21 shifted down by dy, with long ears reaching the canvas edges. */
    static void goblinHead(int[][] c, int dy) {
        tri(c, 10, 11 + dy, 10, 16 + dy, 1, 6 + dy, SKIN);
        tri(c, 21, 11 + dy, 21, 16 + dy, 30, 6 + dy, SKIN);
        tri(c, 9, 12 + dy, 9, 15 + dy, 4, 9 + dy, EAR);
        tri(c, 22, 12 + dy, 22, 15 + dy, 27, 9 + dy, EAR);
        roundRect(c, 9, 8 + dy, 22, 21 + dy, 2, SKIN);
        for (int y = 9 + dy; y <= 21 + dy; y++) if (get(c, 22, y) != 0) set(c, 22, y, SKIN_D);
        for (int x = 9; x <= 22; x++) if (get(c, x, 21 + dy) != 0) set(c, x, 21 + dy, SKIN_D);
        set(c, 11, 9 + dy, SKIN_L);
        set(c, 12, 9 + dy, SKIN_L);
        set(c, 10, 10 + dy, SKIN_L);
        set(c, 12, 8 + dy, HAIR);
        set(c, 16, 8 + dy, HAIR);
        set(c, 19, 8 + dy, HAIR);
        // angry brows slanting towards the nose
        line(c, 10, 11 + dy, 14, 12 + dy, SKIN_DD);
        line(c, 17, 12 + dy, 21, 11 + dy, SKIN_DD);
        rect(c, 11, 13 + dy, 13, 14 + dy, EYE);
        rect(c, 13, 13 + dy, 13, 14 + dy, PUPIL);
        rect(c, 18, 13 + dy, 20, 14 + dy, EYE);
        rect(c, 18, 13 + dy, 18, 14 + dy, PUPIL);
        // hooked nose
        rect(c, 15, 12 + dy, 16, 15 + dy, SKIN_L);
        set(c, 16, 15 + dy, SKIN);
        rect(c, 14, 16 + dy, 17, 17 + dy, SKIN);
        rect(c, 17, 16 + dy, 17, 17 + dy, SKIN_D);
        set(c, 14, 17 + dy, SKIN_DD);
        set(c, 17, 17 + dy, SKIN_DD);
        // mouth with tusks
        rect(c, 11, 19 + dy, 20, 19 + dy, MOUTH);
        rect(c, 12, 18 + dy, 12, 19 + dy, FANG);
        rect(c, 19, 18 + dy, 19, 19 + dy, FANG);
    }

    // ---- pixel helpers (0 = transparent) ----

    static void set(int[][] c, int x, int y, int rgb) {
        if (x >= 0 && y >= 0 && x < N && y < N) c[y][x] = 0xFF000000 | rgb;
    }

    static int get(int[][] c, int x, int y) {
        if (x < 0 || y < 0 || x >= N || y >= N) return 0;
        return c[y][x] == 0 ? 0 : c[y][x] & 0xFFFFFF;
    }

    static void rect(int[][] c, int x0, int y0, int x1, int y1, int rgb) {
        for (int y = y0; y <= y1; y++) for (int x = x0; x <= x1; x++) set(c, x, y, rgb);
    }

    static void roundRect(int[][] c, int x0, int y0, int x1, int y1, int r, int rgb) {
        for (int y = y0; y <= y1; y++) for (int x = x0; x <= x1; x++) {
            double cx = Math.max(x0 + r, Math.min(x1 - r, x)), cy = Math.max(y0 + r, Math.min(y1 - r, y));
            if ((x - cx) * (x - cx) + (y - cy) * (y - cy) <= r * r + 0.5) set(c, x, y, rgb);
        }
    }

    static void circle(int[][] c, double cx, double cy, double r, int rgb) {
        for (int y = 0; y < N; y++) for (int x = 0; x < N; x++) {
            if ((x - cx) * (x - cx) + (y - cy) * (y - cy) <= r * r) set(c, x, y, rgb);
        }
    }

    static void tri(int[][] c, double ax, double ay, double bx, double by, double cx, double cy, int rgb) {
        for (int y = 0; y < N; y++) for (int x = 0; x < N; x++) {
            double d1 = (x - bx) * (ay - by) - (ax - bx) * (y - by);
            double d2 = (x - cx) * (by - cy) - (bx - cx) * (y - cy);
            double d3 = (x - ax) * (cy - ay) - (cx - ax) * (y - ay);
            boolean neg = d1 < 0 || d2 < 0 || d3 < 0, pos = d1 > 0 || d2 > 0 || d3 > 0;
            if (!(neg && pos)) set(c, x, y, rgb);
        }
    }

    static void line(int[][] c, int x0, int y0, int x1, int y1, int rgb) {
        int dx = Math.abs(x1 - x0), dy = -Math.abs(y1 - y0), sx = x0 < x1 ? 1 : -1, sy = y0 < y1 ? 1 : -1, err = dx + dy;
        while (true) {
            set(c, x0, y0, rgb);
            if (x0 == x1 && y0 == y1) return;
            int e2 = 2 * err;
            if (e2 >= dy) { err += dy; x0 += sx; }
            if (e2 <= dx) { err += dx; y0 += sy; }
        }
    }

    static void overlay(int[][] dst, int[][] src) {
        for (int y = 0; y < N; y++) for (int x = 0; x < N; x++) if (src[y][x] != 0) dst[y][x] = src[y][x];
    }

    /** One-pixel dark outline around everything opaque. */
    static int[][] outline(int[][] c) {
        int[][] out = new int[N][N];
        for (int y = 0; y < N; y++) for (int x = 0; x < N; x++) {
            if (c[y][x] != 0) out[y][x] = c[y][x];
            else if (get(c, x - 1, y) != 0 || get(c, x + 1, y) != 0 || get(c, x, y - 1) != 0 || get(c, x, y + 1) != 0) {
                out[y][x] = 0xFF000000 | OUTLINE;
            }
        }
        return out;
    }
}
