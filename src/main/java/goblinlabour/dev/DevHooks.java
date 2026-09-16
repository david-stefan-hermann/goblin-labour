package goblinlabour.dev;

import goblinlabour.GoblinLabour;
import goblinlabour.block.GoblinBedBlockEntity;
import goblinlabour.entity.GoblinEntity;
import goblinlabour.menu.GoblinBedMenuProvider;
import goblinlabour.menu.GoblinMenuProvider;
import net.minecraft.core.BlockPos;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Relative;

import java.util.Set;

/**
 * Development helpers, only active with -Dgoblinlabour.dev.view=x,y,z,yaw,pitch (set by `-PdevScreenshot`).
 * One second after the first player is online the server teleports every player to that view point so the
 * client can take a screenshot.
 */
public final class DevHooks {
    public static final String VIEW_PROPERTY = "goblinlabour.dev.view";
    public static final String SCREENSHOT_PROPERTY = "goblinlabour.dev.screenshot";

    private DevHooks() {
    }

    public static void initServer() {
        double[] view = parseView();
        if (view == null) return;
        GoblinLabour.LOGGER.info("Dev view point active: {}", System.getProperty(VIEW_PROPERTY));
        int[] countdown = {-1};
        boolean[] done = {false};
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (done[0]) return;
            if (countdown[0] < 0) {
                if (!server.getPlayerList().getPlayers().isEmpty()) countdown[0] = 20;
                return;
            }
            if (countdown[0]-- != 0) return;
            done[0] = true;
            BlockPos menuBed = parseMenuBed();
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                player.teleportTo(server.overworld(), view[0], view[1], view[2], Set.<Relative>of(),
                        (float) view[3], (float) view[4], false);
                GoblinLabour.LOGGER.info("Dev view: teleported {}", player.getName().getString());
                if (menuEndsWith(",ringstaff")) {
                    devRingStaff(server.overworld(), player);
                    continue;
                }
                if (menuEndsWith(",churn") && menuBed != null
                        && server.overworld().getBlockEntity(menuBed) instanceof goblinlabour.block.MilkChurnBlockEntity churn) {
                    player.openMenu(churn); // -Dgoblinlabour.dev.menu=x,y,z,churn: the Milk Churn screen at x,y,z
                    GoblinLabour.LOGGER.info("Dev view: opened the Milk Churn at {}", menuBed);
                    continue;
                }
                if (menuEndsWith(",ring") || menuEndsWith(",ringmenu")) {
                    devRing(server.overworld(), player, menuEndsWith(",ringmenu"));
                    continue;
                }
                if (menuBed != null && server.overworld().getBlockEntity(menuBed) instanceof GoblinBedBlockEntity bed) {
                    if (menuIsHoldStaff()) {
                        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new net.minecraft.world.item.ItemStack(GoblinLabour.GOBLIN_STAFF));
                        GoblinLabour.LOGGER.info("Dev view: handed over the staff");
                    } else if (menuIsBed()) {
                        player.openMenu(new GoblinBedMenuProvider(bed));
                        GoblinLabour.LOGGER.info("Dev view: opened the bed menu at {}", menuBed);
                    } else if (menuIsStaff()) {
                        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new net.minecraft.world.item.ItemStack(GoblinLabour.GOBLIN_STAFF));
                        net.minecraft.core.BlockPos pos = menuBed.south(8);
                        int surface = server.overworld().getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, pos.getX(), pos.getZ()) - 1;
                        player.openMenu(new goblinlabour.menu.StaffMenuProvider(new goblinlabour.menu.StaffMenuData(pos, net.minecraft.core.Direction.UP,
                                goblinlabour.job.Assignment.Kind.DIG_DOWN, 2, server.overworld().getMinY(), surface, server.overworld().getMaxY())));
                        GoblinLabour.LOGGER.info("Dev view: opened the staff menu");
                    } else {
                        GoblinEntity goblin = bed.findGoblin(server.overworld());
                        if (goblin != null) {
                            player.openMenu(new GoblinMenuProvider(goblin));
                            GoblinLabour.LOGGER.info("Dev view: opened the inventory of {}", goblin.goblinName());
                        }
                    }
                }
            }
        });
    }

    public static final String MENU_PROPERTY = "goblinlabour.dev.menu";

    /** Bed position from -Dgoblinlabour.dev.menu=x,y,z; the joining player gets that goblin's inventory opened. */
    public static BlockPos parseMenuBed() {
        String raw = System.getProperty(MENU_PROPERTY);
        if (raw == null) return null;
        String[] parts = raw.split(",");
        if (parts.length < 3) return null;
        try {
            return new BlockPos(Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[1].trim()), Integer.parseInt(parts[2].trim()));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static boolean menuEndsWith(String suffix) {
        String raw = System.getProperty(MENU_PROPERTY);
        return raw != null && raw.endsWith(suffix);
    }

    /**
     * -Dgoblinlabour.dev.menu=x,y,z,ringstaff: a ring in the last hotbar slot, the staff in hand, the crew called and
     * lined up (frozen) three blocks ahead of the player, so the client can click one through the crosshair.
     */
    private static void devRingStaff(net.minecraft.server.level.ServerLevel level, ServerPlayer player) {
        net.minecraft.world.item.ItemStack ring = new net.minecraft.world.item.ItemStack(GoblinLabour.GOBLIN_RING);
        java.util.UUID id = goblinlabour.ring.RingInventory.ensureId(ring);
        player.getInventory().setItem(8, ring);
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new net.minecraft.world.item.ItemStack(GoblinLabour.GOBLIN_STAFF));
        goblinlabour.ring.RingCrew.toggle(level, player, player.getInventory().getItem(8));
        goblinlabour.ring.RingCrew.Session session = goblinlabour.ring.RingCrew.of(id);
        if (session == null) return;
        double yaw = Math.toRadians(player.getYRot());
        double lx = -Math.sin(yaw), lz = Math.cos(yaw);
        int i = 0;
        for (GoblinEntity goblin : session.goblins()) {
            double side = (i++ - 1) * 1.6;
            goblin.setNoAi(true);
            goblin.snapTo(player.getX() + lx * 3.0 - lz * side, player.getY(), player.getZ() + lz * 3.0 + lx * side,
                    player.getYRot() + 180.0f, 0.0f);
        }
        GoblinLabour.LOGGER.info("Dev view: ring crew lined up for the staff");
    }

    /** -Dgoblinlabour.dev.menu=x,y,z,ring hands over a ring with some loot and calls its crew; ...,ringmenu opens the ring. */
    private static void devRing(net.minecraft.server.level.ServerLevel level, ServerPlayer player, boolean openMenu) {
        net.minecraft.world.item.ItemStack ring = new net.minecraft.world.item.ItemStack(GoblinLabour.GOBLIN_RING);
        java.util.UUID id = goblinlabour.ring.RingInventory.ensureId(ring);
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ring);
        goblinlabour.ring.RingContainer loot = goblinlabour.ring.RingInventory.open(player, id);
        if (loot != null) {
            loot.addItem(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.RAW_IRON, 37));
            loot.addItem(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.COAL, 64));
            loot.addItem(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.COAL, 21));
            loot.addItem(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.RAW_COPPER, 52));
            loot.addItem(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.REDSTONE, 18));
            loot.addItem(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.LAPIS_LAZULI, 11));
            loot.addItem(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.RAW_GOLD, 6));
            loot.addItem(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND, 3));
            loot.addItem(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.COBBLED_DEEPSLATE, 40));
            loot.setChanged();
        }
        goblinlabour.ring.RingBooks books = goblinlabour.ring.RingInventory.books(player, id);
        if (books != null) { // one book slot filled, one empty
            var fortune = level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT)
                    .getOrThrow(net.minecraft.world.item.enchantment.Enchantments.FORTUNE);
            books.setItem(0, net.minecraft.world.item.enchantment.EnchantmentHelper.createBook(
                    new net.minecraft.world.item.enchantment.EnchantmentInstance(fortune, 3)));
            books.setChanged();
        }
        net.minecraft.world.item.ItemStack held = player.getMainHandItem();
        if (openMenu) goblinlabour.item.GoblinRingItem.openMenu(player, held);
        else goblinlabour.ring.RingCrew.toggle(level, player, held);
        GoblinLabour.LOGGER.info("Dev view: ring handed over ({})", openMenu ? "screen open" : "crew called");
    }

    /** -Dgoblinlabour.dev.menu=x,y,z,bed opens the bed screen instead of the goblin inventory. */
    private static boolean menuIsStaff() {
        String raw = System.getProperty(MENU_PROPERTY);
        return raw != null && raw.endsWith(",staff");
    }

    private static boolean menuIsHoldStaff() {
        String raw = System.getProperty(MENU_PROPERTY);
        return raw != null && raw.endsWith(",holdstaff");
    }

    private static boolean menuIsBed() {
        String raw = System.getProperty(MENU_PROPERTY);
        return raw != null && raw.endsWith(",bed");
    }

    /** x, y, z, yaw, pitch or null when the property is absent or malformed. */
    public static double[] parseView() {
        String raw = System.getProperty(VIEW_PROPERTY);
        if (raw == null) return null;
        String[] parts = raw.split(",");
        if (parts.length != 5) return null;
        try {
            double[] out = new double[5];
            for (int i = 0; i < 5; i++) out[i] = Double.parseDouble(parts[i].trim());
            return out;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
