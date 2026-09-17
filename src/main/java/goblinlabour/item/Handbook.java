package goblinlabour.item;

import goblinlabour.GoblinLabour;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;

import java.util.List;

/**
 * The content of the Goblin Handbook, as data: chapters of short sections. The screen lays the sections out and turns
 * the pages itself (see {@code goblinlabour.client.HandbookScreen}). Texts are translation keys
 * ({@code goblinlabour.handbook.<chapter>.<n>}); a name in curly braces is shown in gold.
 */
public final class Handbook {
    private Handbook() {
    }

    /** One piece of a chapter. */
    public sealed interface Section permits Heading, Text, Warning, ItemLine, Recipe {
    }

    /** A small heading inside a chapter. */
    public record Heading(String key) implements Section {
    }

    public record Text(String key) implements Section {
    }

    /** Text in red: what kills a goblin, what it cannot do. */
    public record Warning(String key) implements Section {
    }

    /** An item's icon with a line or two about it. */
    public record ItemLine(ItemStack icon, String key) implements Section {
    }

    /** What goes in (with counts) and what comes out. */
    public record Recipe(List<ItemStack> in, ItemStack out) implements Section {
    }

    public record Chapter(String id, ItemStack icon, List<Section> sections) {
        public String titleKey() {
            return "goblinlabour.handbook.chapter." + id;
        }
    }

    /** Built when the book opens: item stacks need the registries. */
    public static List<Chapter> chapters() {
        return List.of(
                new Chapter("basics", stack(GoblinLabour.GOBLIN_HEAD), List.of(
                        text("basics", 1),
                        new Recipe(List.of(stack(Items.ROTTEN_FLESH, 9)), stack(GoblinLabour.GOBLIN_MEAT_PACK)),
                        line(GoblinLabour.GOBLIN_BLANK, "basics", 2),
                        new Recipe(List.of(stack(Items.HAY_BLOCK, 6)), stack(GoblinLabour.GOBLIN_STRAW_BED_ITEM)),
                        line(GoblinLabour.GOBLIN_STRAW_BED_ITEM, "basics", 3),
                        heading("basics", 4),
                        new Recipe(List.of(stack(GoblinLabour.GOBLIN_BLANK), stack(Items.TORCH)), stack(GoblinLabour.GOBLIN_HEAD)),
                        line(GoblinLabour.GOBLIN_HEAD, "basics", 5),
                        heading("basics", 6),
                        new Recipe(List.of(stack(Items.CHEST), stack(GoblinLabour.GOBLIN_MEAT_PACK)), stack(GoblinLabour.GOBLIN_CHEST_ITEM)),
                        line(GoblinLabour.GOBLIN_CHEST_ITEM, "basics", 7),
                        new Recipe(List.of(stack(GoblinLabour.GOBLIN_CHEST_ITEM), stack(Items.DYE.red())),
                                stack(GoblinLabour.GOBLIN_CHEST_ITEMS.get(DyeColor.RED))),
                        text("basics", 14),
                        heading("basics", 8),
                        line(Items.IRON_PICKAXE, "basics", 9),
                        line(Items.TORCH, "basics", 10),
                        line(GoblinLabour.GOBLIN_SCAFFOLD_ITEM, "basics", 11),
                        new Warning("goblinlabour.handbook.basics.12"),
                        text("basics", 13))),
                new Chapter("jobs", stack(GoblinLabour.GOBLIN_STRAW_BED_ITEM), List.of(
                        text("jobs", 1),
                        line(GoblinLabour.GOBLIN_STRAW_BED_ITEM, "jobs", 2),
                        line(Items.IRON_AXE, "jobs", 3),
                        line(Items.OAK_SAPLING, "jobs", 4),
                        line(Items.IRON_HOE, "jobs", 5),
                        line(Items.BUNDLE, "jobs", 6),
                        line(GoblinLabour.GOBLIN_CHEST_ITEM, "jobs", 7))),
                new Chapter("staff", stack(GoblinLabour.GOBLIN_STAFF), List.of(
                        new Recipe(List.of(stack(GoblinLabour.GOBLIN_BLANK), stack(Items.STICK, 2)), stack(GoblinLabour.GOBLIN_STAFF)),
                        line(GoblinLabour.GOBLIN_STAFF, "staff", 1),
                        text("staff", 2),
                        text("staff", 3),
                        heading("staff", 4),
                        text("staff", 5),
                        text("staff", 6),
                        text("staff", 7))),
                new Chapter("ring", stack(GoblinLabour.GOBLIN_RING), List.of(
                        new Recipe(List.of(stack(Items.GOLD_INGOT, 4), stack(Items.DIAMOND), stack(GoblinLabour.GOBLIN_BLANK, 3)), stack(GoblinLabour.GOBLIN_RING)),
                        line(GoblinLabour.GOBLIN_RING, "ring", 1),
                        line(Items.DIAMOND_ORE, "ring", 2),
                        line(Items.STONE, "ring", 3),
                        line(GoblinLabour.GOBLIN_STAFF, "ring", 4),
                        line(Items.ENCHANTED_BOOK, "ring", 5),
                        line(GoblinLabour.GOBLIN_CHEST_ITEM, "ring", 6))),
                new Chapter("farming", stack(Items.IRON_HOE), List.of(
                        text("farming", 1),
                        line(Items.IRON_HOE, "farming", 2),
                        new Warning("goblinlabour.handbook.farming.3"),
                        line(Items.COCOA_BEANS, "farming", 4),
                        line(Items.BUCKET, "farming", 5),
                        new Recipe(List.of(stack(Items.IRON_INGOT, 7), stack(Items.GLASS), stack(Items.BUCKET)), stack(GoblinLabour.MILK_CHURN_ITEM)),
                        line(GoblinLabour.MILK_CHURN_ITEM, "farming", 6),
                        new Recipe(List.of(stack(Items.IRON_INGOT, 8), stack(Items.BUCKET)), stack(GoblinLabour.MILK_CAN_EXPANSION_ITEM)),
                        line(GoblinLabour.MILK_CAN_EXPANSION_ITEM, "farming", 9),
                        line(Items.SHEARS, "farming", 7),
                        new ItemLine(treetap(), "goblinlabour.handbook.farming.8"))));
    }

    private static Text text(String chapter, int n) {
        return new Text("goblinlabour.handbook." + chapter + "." + n);
    }

    private static Heading heading(String chapter, int n) {
        return new Heading("goblinlabour.handbook." + chapter + "." + n);
    }

    private static ItemLine line(ItemLike item, String chapter, int n) {
        return new ItemLine(stack(item), "goblinlabour.handbook." + chapter + "." + n);
    }

    private static ItemStack stack(ItemLike item) {
        return new ItemStack(item);
    }

    private static ItemStack stack(ItemLike item, int count) {
        return new ItemStack(item, count);
    }

    /** Tech Reborn's treetap when Tech Reborn is installed, a stick otherwise. */
    private static ItemStack treetap() {
        return BuiltInRegistries.ITEM.getOptional(Identifier.fromNamespaceAndPath("techreborn", "treetap"))
                .map(ItemStack::new).orElseGet(() -> new ItemStack(Items.STICK));
    }
}
