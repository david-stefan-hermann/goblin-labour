package goblinlabour.item;

import bcf.guidebook.Book;
import bcf.guidebook.BookTheme;
import bcf.guidebook.Chapter;
import bcf.guidebook.Section.Break;
import bcf.guidebook.Section.Heading;
import bcf.guidebook.Section.ItemLine;
import bcf.guidebook.Section.Note;
import bcf.guidebook.Section.Quote;
import bcf.guidebook.Section.Recipe;
import bcf.guidebook.Section.Text;
import goblinlabour.GoblinLabour;
import goblinlabour.block.GoblinScaffoldBlock;
import goblinlabour.block.MilkCanExpansionBlockEntity;
import goblinlabour.block.MilkChurnBlockEntity;
import goblinlabour.home.HomeZone;
import goblinlabour.job.ChopJob;
import goblinlabour.job.FarmJob;
import goblinlabour.ring.RingCrew;
import goblinlabour.staff.StaffSelection;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;

import java.util.List;

/**
 * The content of the guide book, as data: chapters of sections, which the screen of bcf-guide-book-lib wraps and
 * breaks into pages. Texts are translation keys {@code goblinlabour.book.<key>} in the language files. What goes into
 * which chapter is in STANDARDS.md of bcf-mod-template.
 */
public final class GuideBook {
    /** The goblin green of the mod's other screens (see {@code goblinlabour.client.GoblinUi}) around a pale page. */
    public static final Book BOOK = Book.of(GoblinLabour.MOD_ID, BookTheme.builder()
            .panel(0xFF6FA35F, 0xFFB9E2A5, 0xFF3B5E30).tabs(0xFF4E7A44, 0xFF8FC47E)
            .page(0xFFEEF2DD, 0xFF9DB68A).ink(0xFF1F4F16, 0xFF2E6B1F, 0xFF14260F, 0xFF2E4A27)
            .slots(0xFFDDE5C6, 0xFF9DB68A, 0xFF4E6B42)
            .buttons(0xFF2F8A44, 0xFF43B25A, 0xFF55705A, 0xFF163C1E, 0xFF7FD68F, 0xFF1E5A2C, 0xFFFFFFFF, 0xFFC9D6C6)
            .build(), GuideBook::chapters);

    private static final int TICKS_PER_MINUTE = 20 * 60;

    private GuideBook() {
    }

    public static String key(String name) {
        return BOOK.key(name);
    }

    /** Built when the book opens: item stacks need the registries. The numbers are the code's own constants. */
    public static List<Chapter> chapters() {
        Heading crafting = new Heading(key("heading.crafting")), use = new Heading(key("heading.use"));
        Break turn = new Break(); // every thing: what it is and its recipe on the first page, its use on the second
        int home = 2 * HomeZone.RADIUS_XZ + 1, homeHeight = 2 * HomeZone.RADIUS_Y + 1;
        return List.of(
                new Chapter(key("start"), stack(GoblinLabour.GUIDE_BOOK), Chapter.Side.RIGHT, List.of(
                        new Text(key("start.text")), new Quote(key("start.motto")),
                        new Heading(key("heading.steps")),
                        line(GoblinLabour.GOBLIN_MEAT_PACK, "start.step1"),
                        line(GoblinLabour.GOBLIN_STRAW_BED_ITEM, "start.step2"),
                        line(Items.IRON_PICKAXE, "start.step3"),
                        line(GoblinLabour.GOBLIN_CHEST_ITEM, "start.step4"),
                        crafting, recipe("guide_book"), new Text(key("start.recipe")))),
                new Chapter("item.goblinlabour.goblin_blank", stack(GoblinLabour.GOBLIN_BLANK), Chapter.Side.LEFT, List.of(
                        new Text(key("blank.text")), new Quote(key("blank.motto")),
                        crafting, recipe("goblin_meat_pack"), new Text(key("blank.smelt")),
                        turn, use, new Text(key("blank.use")), new Text(key("blank.back")))),
                new Chapter("block.goblinlabour.goblin_straw_bed", stack(GoblinLabour.GOBLIN_STRAW_BED_ITEM), Chapter.Side.LEFT, List.of(
                        new Text(key("bed.text")), new Quote(key("bed.motto")),
                        crafting, recipe("goblin_straw_bed"),
                        turn, use, new Text(key("bed.use")), new Text(key("bed.bunk")),
                        new Heading(key("heading.home")), new Text(key("bed.home"), home, home, homeHeight))),
                new Chapter("block.goblinlabour.goblin_chest", stack(GoblinLabour.GOBLIN_CHEST_ITEM), Chapter.Side.LEFT, List.of(
                        new Text(key("chest.text")), new Quote(key("chest.motto")),
                        crafting, recipe("goblin_chest"),
                        turn, use, new Text(key("chest.use")),
                        new Heading(key("heading.colours")),
                        line(GoblinLabour.GOBLIN_CHEST_ITEMS.get(DyeColor.RED), "chest.dye"))),
                new Chapter("item.goblinlabour.goblin_head", stack(GoblinLabour.GOBLIN_HEAD), Chapter.Side.LEFT, List.of(
                        new Text(key("head.text")), new Quote(key("head.motto")),
                        crafting, recipe("goblin_head"),
                        turn, use, new Text(key("head.use")))),
                new Chapter("item.goblinlabour.goblin_staff", stack(GoblinLabour.GOBLIN_STAFF), Chapter.Side.LEFT, List.of(
                        new Text(key("staff.text")), new Quote(key("staff.motto")),
                        crafting, recipe("goblin_staff"),
                        turn, use, new Text(key("staff.use")), new Text(key("staff.order")),
                        new Heading(key("heading.orders")),
                        new Text(key("staff.defaults"), GoblinStaffItem.DEFAULT_SHAFT_WIDTH, GoblinStaffItem.DEFAULT_SHAFT_WIDTH,
                                GoblinStaffItem.DEFAULT_TUNNEL_WIDTH, GoblinStaffItem.DEFAULT_TUNNEL_HEIGHT,
                                GoblinStaffItem.DEFAULT_TUNNEL_LENGTH),
                        new Text(key("staff.tunnel")), new Text(key("staff.share")),
                        new Heading(key("heading.unloading")),
                        line(GoblinLabour.GOBLIN_CHEST_ITEM, "staff.chest", StaffSelection.MAX_CHEST_DISTANCE))),
                new Chapter("item.goblinlabour.goblin_ring", stack(GoblinLabour.GOBLIN_RING), Chapter.Side.LEFT, List.of(
                        new Text(key("ring.text"), RingCrew.CREW_SIZE, (int) RingCrew.LEASH,
                                RingCrew.DURATION_TICKS / TICKS_PER_MINUTE),
                        new Quote(key("ring.motto")),
                        crafting, recipe("goblin_ring"),
                        turn, use, new Text(key("ring.use")),
                        line(Items.DIAMOND_ORE, "ring.vein"),
                        line(Items.STONE, "ring.assist"),
                        line(GoblinLabour.GOBLIN_STAFF, "ring.staff"),
                        line(Items.ENCHANTED_BOOK, "ring.books"),
                        line(GoblinLabour.GOBLIN_CHEST_ITEM, "ring.chest", RingCrew.CHEST_RANGE))),
                new Chapter("block.goblinlabour.milk_churn", stack(GoblinLabour.MILK_CHURN_ITEM), Chapter.Side.LEFT, List.of(
                        new Text(key("can.text"), MilkChurnBlockEntity.CAPACITY / MilkChurnBlockEntity.BUCKET),
                        new Quote(key("can.motto")),
                        crafting, recipe("milk_churn"),
                        turn, use, new Text(key("can.use")), new Text(key("can.level")), new Text(key("can.hopper")))),
                new Chapter("block.goblinlabour.milk_can_expansion", stack(GoblinLabour.MILK_CAN_EXPANSION_ITEM), Chapter.Side.LEFT, List.of(
                        new Text(key("expansion.text"), MilkCanExpansionBlockEntity.CAPACITY_BUCKETS),
                        new Quote(key("expansion.motto")),
                        crafting, recipe("milk_can_expansion"),
                        turn, use, new Text(key("expansion.use")), new Text(key("expansion.pipes")))),
                new Chapter(key("jobs"), stack(Items.IRON_AXE), Chapter.Side.RIGHT, List.of(
                        new Text(key("jobs.text")), new Quote(key("jobs.motto")),
                        line(GoblinLabour.GOBLIN_STRAW_BED_ITEM, "jobs.rest"),
                        line(Items.IRON_AXE, "jobs.chop"),
                        line(Items.OAK_SAPLING, "jobs.saplings", (int) ChopJob.FREE_GAP),
                        line(Items.IRON_HOE, "jobs.farm"),
                        line(Items.BUNDLE, "jobs.collect"))),
                new Chapter(key("farming"), stack(Items.IRON_HOE), Chapter.Side.RIGHT, List.of(
                        new Text(key("farming.text")), new Quote(key("farming.motto")),
                        line(Items.IRON_HOE, "farming.hoe"),
                        new Note(key("farming.nohoe")),
                        line(Items.COCOA_BEANS, "farming.cocoa"),
                        line(Items.BUCKET, "farming.bucket", FarmJob.MILK_COOLDOWN / TICKS_PER_MINUTE),
                        line(Items.SHEARS, "farming.shears"),
                        new ItemLine(List.of(treetap()), key("farming.treetap")))),
                new Chapter(key("tools"), stack(Items.IRON_PICKAXE), Chapter.Side.RIGHT, List.of(
                        new Text(key("tools.text")), new Quote(key("tools.motto")),
                        line(Items.IRON_PICKAXE, "tools.row"),
                        line(Items.TORCH, "tools.torch"),
                        line(GoblinLabour.GOBLIN_SCAFFOLD_ITEM, "tools.scaffold",
                                GoblinScaffoldBlock.LIFETIME_TICKS / TICKS_PER_MINUTE),
                        new Heading(key("heading.death")),
                        new Note(key("tools.death")), new Text(key("tools.respawn")))));
    }

    private static ItemLine line(ItemLike item, String name, Object... args) {
        return new ItemLine(List.of(stack(item)), key(name), args);
    }

    private static ItemStack stack(ItemLike item) {
        return new ItemStack(item);
    }

    private static Recipe recipe(String name) {
        return BOOK.recipe(name);
    }

    /** Tech Reborn's treetap when Tech Reborn is installed, a stick otherwise. */
    private static ItemStack treetap() {
        return BuiltInRegistries.ITEM.getOptional(Identifier.fromNamespaceAndPath("techreborn", "treetap"))
                .map(ItemStack::new).orElseGet(() -> new ItemStack(Items.STICK));
    }
}
