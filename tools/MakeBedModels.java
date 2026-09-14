import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Generates the straw bed block models: the bed, four "props" arrangements per goblin style (things lying under the
 * bed frame, between the legs) and the multipart blockstate, which picks one arrangement at random per bed position.
 * Item textures used here must also be listed in src/main/resources/assets/minecraft/atlases/blocks.json, where they
 * are added to the block atlas under goblinlabour:block/props/&lt;name&gt; (block models reject item-atlas sprites).
 * Run from the project root: java tools/MakeBedModels.java
 */
public class MakeBedModels {
    static final String ASSETS = "src/main/resources/assets/goblinlabour/";
    static final String[] FACINGS = {"north", "east", "south", "west"};
    static final String[] STYLES = {"lumberjack", "farmer", "miner", "collector"};
    static final int VARIANTS = 4;

    public static void main(String[] args) throws Exception {
        write("models/block/goblin_straw_bed.json", bed());
        for (String style : STYLES) {
            for (int i = 0; i < VARIANTS; i++) {
                write("models/block/bed_props/" + style + "_" + i + ".json", props(style, i).json());
            }
        }
        write("blockstates/goblin_straw_bed.json", blockstate());
        System.out.println("Bed models written");
    }

    // ---- the bed ----

    static String bed() {
        return """
                {
                  "parent": "minecraft:block/block",
                  "textures": {
                    "particle": "minecraft:block/hay_block_side",
                    "planks": "minecraft:block/spruce_planks",
                    "hay_side": "minecraft:block/hay_block_side",
                    "hay_top": "minecraft:block/hay_block_top",
                    "wool": "minecraft:block/white_wool"
                  },
                  "elements": [
                    { "from": [0, 0, 0], "to": [2, 3, 2], "faces": { "north": {"texture": "#planks"}, "south": {"texture": "#planks"}, "east": {"texture": "#planks"}, "west": {"texture": "#planks"}, "down": {"texture": "#planks"} } },
                    { "from": [14, 0, 0], "to": [16, 3, 2], "faces": { "north": {"texture": "#planks"}, "south": {"texture": "#planks"}, "east": {"texture": "#planks"}, "west": {"texture": "#planks"}, "down": {"texture": "#planks"} } },
                    { "from": [0, 0, 14], "to": [2, 3, 16], "faces": { "north": {"texture": "#planks"}, "south": {"texture": "#planks"}, "east": {"texture": "#planks"}, "west": {"texture": "#planks"}, "down": {"texture": "#planks"} } },
                    { "from": [14, 0, 14], "to": [16, 3, 16], "faces": { "north": {"texture": "#planks"}, "south": {"texture": "#planks"}, "east": {"texture": "#planks"}, "west": {"texture": "#planks"}, "down": {"texture": "#planks"} } },
                    { "from": [0, 3, 0], "to": [16, 6, 16], "faces": { "north": {"texture": "#planks"}, "south": {"texture": "#planks"}, "east": {"texture": "#planks"}, "west": {"texture": "#planks"}, "down": {"texture": "#planks"} } },
                    { "from": [0, 6, 6], "to": [16, 9, 16], "faces": { "south": {"texture": "#hay_side"}, "east": {"texture": "#hay_side"}, "west": {"texture": "#hay_side"}, "up": {"texture": "#hay_top"} } },
                    { "from": [0, 6, 0], "to": [3, 9, 6], "faces": { "north": {"texture": "#hay_side"}, "west": {"texture": "#hay_side"}, "up": {"texture": "#hay_top"} } },
                    { "from": [13, 6, 0], "to": [16, 9, 6], "faces": { "north": {"texture": "#hay_side"}, "east": {"texture": "#hay_side"}, "up": {"texture": "#hay_top"} } },
                    { "from": [3, 6, 0], "to": [13, 9, 2], "faces": { "north": {"texture": "#hay_side"}, "up": {"texture": "#hay_top"} } },
                    { "from": [3, 6, 2], "to": [13, 9, 6], "faces": { "up": {"texture": "#wool"} } }
                  ]
                }
                """;
    }

    // ---- props ----

    /**
     * Arrangements are laid out in x/z 2..14 (between the legs), y 0..3, flat items at y 0.1, and then pushed
     * {@link #FOOT_SHIFT} px towards the foot end (+z, away from the pillow), so part of each arrangement sticks out
     * from under the frame where a standing player can see it. The head end usually stands against a wall.
     */
    static final double FOOT_SHIFT = 5;
    /** Height of the extruded items in model pixels. */
    static final double ITEM_THICKNESS = 1;
    static final String CLIENT_JAR = System.getProperty("user.home") + "/.gradle/caches/fabric-loom/26.2/minecraft-client.jar";
    static final Map<String, BufferedImage> VANILLA_TEXTURES = new HashMap<>();

    /** A texture such as "item/diamond" from the Minecraft client jar that Loom downloaded. */
    static BufferedImage vanillaTexture(String path) {
        return VANILLA_TEXTURES.computeIfAbsent(path, key -> {
            try (ZipFile jar = new ZipFile(CLIENT_JAR)) {
                ZipEntry entry = jar.getEntry("assets/minecraft/textures/" + key + ".png");
                if (entry == null) throw new IllegalArgumentException("No texture " + key + " in " + CLIENT_JAR);
                try (InputStream in = jar.getInputStream(entry)) {
                    return ImageIO.read(in);
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }

    static boolean opaque(BufferedImage image, int x, int y) {
        return x >= 0 && y >= 0 && x < image.getWidth() && y < image.getHeight() && (image.getRGB(x, y) >>> 24) != 0;
    }

    static Props props(String style, int variant) {
        Props p = new Props();
        switch (style + variant) {
            case "lumberjack0" -> p.plane("item/wooden_axe", 3, 3, 11, 11, 22.5).plane("item/stick", 8, 8, 14, 14, -22.5);
            case "lumberjack1" -> p.log(2, 3, 14, 6, 0).log(3, 8, 13, 11, 22.5);
            case "lumberjack2" -> p.plane("item/stick", 2, 3, 9, 10, 45).plane("item/stick", 7, 6, 14, 13, -22.5).plane("item/stick", 4, 8, 10, 14, 0);
            case "lumberjack3" -> p.plane("item/stone_axe", 7, 2, 14, 9, -45).log(2, 9, 12, 12, 0);
            case "farmer0" -> p.plane("item/wheat", 2, 2, 10, 10, 22.5).plane("item/wheat", 6, 6, 14, 14, -22.5);
            case "farmer1" -> p.plane("item/carrot", 3, 3, 9, 9, 45).plane("item/potato", 8, 4, 13, 9, 0).plane("item/potato", 5, 9, 10, 14, 22.5);
            case "farmer2" -> p.cube("block/hay_block_side", "block/hay_block_top", 3, 0, 4, 8, 3, 9, 0).plane("item/wheat_seeds", 8, 8, 14, 14, 22.5);
            case "farmer3" -> p.plane("item/wooden_hoe", 2, 3, 10, 11, -22.5).plane("item/beetroot", 8, 7, 14, 13, 0);
            case "miner0" -> p.plane("item/diamond", 3, 3, 8, 8, 22.5).plane("item/diamond", 9, 5, 13, 9, -22.5).plane("item/redstone", 5, 9, 11, 14, 0);
            case "miner1" -> p.plane("item/emerald", 3, 4, 8, 9, -22.5).plane("item/emerald", 8, 8, 13, 13, 45).plane("item/diamond", 9, 3, 13, 7, 0);
            case "miner2" -> p.plane("item/redstone", 2, 2, 9, 9, 0).plane("item/redstone", 7, 7, 14, 14, 22.5).plane("item/emerald", 3, 9, 7, 13, -22.5);
            case "miner3" -> p.cube("block/diamond_ore", "block/diamond_ore", 3, 0, 3, 6, 3, 6, 0).plane("item/emerald", 8, 3, 13, 8, 22.5).plane("item/redstone", 5, 8, 11, 14, -22.5);
            case "collector0" -> p.plane("item/bundle", 3, 3, 12, 12, 22.5);
            case "collector1" -> p.cube("block/barrel_side", "block/barrel_top", 3, 0, 4, 8, 3, 9, 0).plane("item/string", 8, 8, 14, 14, -22.5);
            case "collector2" -> p.plane("item/red_bundle", 2, 3, 9, 10, -22.5).plane("item/brown_bundle", 7, 6, 14, 13, 22.5);
            case "collector3" -> p.cube("block/barrel_side", "block/barrel_top", 8, 0, 3, 13, 3, 8, 22.5).plane("item/bundle", 3, 7, 10, 14, 0).plane("item/leather", 3, 2, 8, 7, 45);
            default -> throw new IllegalArgumentException(style + variant);
        }
        return p;
    }

    static class Props {
        final Map<String, String> textures = new LinkedHashMap<>();
        final List<String> elements = new ArrayList<>();

        String ref(String path) {
            String key = path.substring(path.indexOf('/') + 1);
            // block models may only use block-atlas sprites, and 26.2 sends every item/ path to the item atlas, so the
            // item textures are registered again as goblinlabour:block/props/<name> (assets/minecraft/atlases/blocks.json)
            String id = path.startsWith("item/") ? "goblinlabour:block/props/" + key : "minecraft:" + path;
            textures.putIfAbsent(key, id);
            return "#" + key;
        }

        /**
         * An item lying on the floor, extruded like a held item: every opaque texture pixel becomes a box
         * {@link #ITEM_THICKNESS} px high, with side faces only where the neighbouring pixel is transparent.
         * The pixels are read from the vanilla client jar.
         */
        Props plane(String texture, double x1, double z1, double x2, double z2, double angle) {
            z1 += FOOT_SHIFT;
            z2 += FOOT_SHIFT;
            String tex = ref(texture);
            BufferedImage image = vanillaTexture(texture);
            int size = image.getWidth();
            double sx = (x2 - x1) / size;
            double sz = (z2 - z1) / size;
            String rotation = rotation(x1, z1, x2, z2, angle);
            for (int py = 0; py < size; py++) {
                for (int px = 0; px < size; px++) {
                    if (!opaque(image, px, py)) continue;
                    String face = String.format(Locale.ROOT, "{\"texture\": \"%s\", \"uv\": [%s, %s, %s, %s]}", tex,
                            num(px * 16.0 / size), num(py * 16.0 / size), num((px + 1) * 16.0 / size), num((py + 1) * 16.0 / size));
                    List<String> faces = new ArrayList<>();
                    faces.add("\"up\": " + face);
                    if (!opaque(image, px, py - 1)) faces.add("\"north\": " + face);
                    if (!opaque(image, px, py + 1)) faces.add("\"south\": " + face);
                    if (!opaque(image, px - 1, py)) faces.add("\"west\": " + face);
                    if (!opaque(image, px + 1, py)) faces.add("\"east\": " + face);
                    double ex = x1 + px * sx;
                    double ez = z1 + py * sz;
                    elements.add(String.format(Locale.ROOT, "{ \"from\": [%s, 0, %s], \"to\": [%s, %s, %s]%s, \"faces\": { %s } }",
                            num(ex), num(ez), num(ex + sx), num(ITEM_THICKNESS), num(ez + sz), rotation, String.join(", ", faces)));
                }
            }
            return this;
        }

        /** A miniature block with the whole texture on each face. */
        Props cube(String side, String top, double x1, double y1, double z1, double x2, double y2, double z2, double angle) {
            z1 += FOOT_SHIFT;
            z2 += FOOT_SHIFT;
            String s = ref(side);
            String t = ref(top);
            String face = "{\"texture\": \"%s\", \"uv\": [0, 0, 16, 16]}";
            elements.add(String.format(Locale.ROOT,
                    "{ \"from\": [%s, %s, %s], \"to\": [%s, %s, %s]%s, \"faces\": { \"north\": %s, \"south\": %s, \"east\": %s, \"west\": %s, \"up\": %s } }",
                    num(x1), num(y1), num(z1), num(x2), num(y2), num(z2), rotation(x1, z1, x2, z2, angle),
                    face.formatted(s), face.formatted(s), face.formatted(s), face.formatted(s), face.formatted(t)));
            return this;
        }

        /** A small oak log, 3 px thick, lying along the x axis. */
        Props log(double x1, double z1, double x2, double z2, double angle) {
            z1 += FOOT_SHIFT;
            z2 += FOOT_SHIFT;
            String bark = ref("block/oak_log");
            String end = ref("block/oak_log_top");
            String along = "{\"texture\": \"%s\", \"uv\": [0, 0, 16, 16], \"rotation\": 90}".formatted(bark);
            String cap = "{\"texture\": \"%s\", \"uv\": [0, 0, 16, 16]}".formatted(end);
            elements.add(String.format(Locale.ROOT,
                    "{ \"from\": [%s, 0, %s], \"to\": [%s, 3, %s]%s, \"faces\": { \"north\": %s, \"south\": %s, \"up\": %s, \"east\": %s, \"west\": %s } }",
                    num(x1), num(z1), num(x2), num(z2), rotation(x1, z1, x2, z2, angle), along, along, along, cap, cap));
            return this;
        }

        String json() {
            StringBuilder out = new StringBuilder("{\n  \"ambientocclusion\": false,\n  \"textures\": {\n");
            String first = textures.values().iterator().next();
            out.append("    \"particle\": \"").append(first).append("\"");
            for (Map.Entry<String, String> entry : textures.entrySet()) {
                out.append(",\n    \"").append(entry.getKey()).append("\": \"").append(entry.getValue()).append("\"");
            }
            out.append("\n  },\n  \"elements\": [\n    ");
            out.append(String.join(",\n    ", elements));
            return out.append("\n  ]\n}\n").toString();
        }
    }

    static String rotation(double x1, double z1, double x2, double z2, double angle) {
        if (angle == 0) return "";
        return String.format(Locale.ROOT, ", \"rotation\": {\"origin\": [%s, 0, %s], \"axis\": \"y\", \"angle\": %s}",
                num((x1 + x2) / 2), num((z1 + z2) / 2), num(angle));
    }

    static String num(double value) {
        return value == Math.rint(value) ? Integer.toString((int) value) : Double.toString(value);
    }

    // ---- blockstate ----

    static String blockstate() {
        List<String> parts = new ArrayList<>();
        for (int f = 0; f < FACINGS.length; f++) {
            String y = f == 0 ? "" : ", \"y\": " + f * 90;
            parts.add("{ \"when\": {\"facing\": \"" + FACINGS[f] + "\"}, \"apply\": {\"model\": \"goblinlabour:block/goblin_straw_bed\"" + y + "} }");
            for (String style : STYLES) {
                List<String> models = new ArrayList<>();
                for (int i = 0; i < VARIANTS; i++) {
                    models.add("{\"model\": \"goblinlabour:block/bed_props/" + style + "_" + i + "\"" + y + "}");
                }
                parts.add("{ \"when\": {\"facing\": \"" + FACINGS[f] + "\", \"props\": \"" + style + "\"}, \"apply\": ["
                        + String.join(", ", models) + "] }");
            }
        }
        return "{\n  \"multipart\": [\n    " + String.join(",\n    ", parts) + "\n  ]\n}\n";
    }

    static void write(String path, String content) throws Exception {
        File file = new File(ASSETS + path);
        file.getParentFile().mkdirs();
        Files.writeString(file.toPath(), content, StandardCharsets.UTF_8);
    }
}
