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
