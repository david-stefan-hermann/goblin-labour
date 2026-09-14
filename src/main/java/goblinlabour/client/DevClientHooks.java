package goblinlabour.client;

import goblinlabour.GoblinLabour;
import goblinlabour.dev.DevHooks;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;

/**
 * With -Dgoblinlabour.dev.screenshot=<name> the client hides the GUI, waits a few seconds after joining a world,
 * saves screenshots/<name>.png and quits. Used by `./gradlew runClient -PdevScreenshot`.
 */
public final class DevClientHooks {
    private static int ticksInWorld;
    private static boolean done;

    private DevClientHooks() {
    }

    public static void init() {
        String name = System.getProperty(DevHooks.SCREENSHOT_PROPERTY);
        if (name == null) return;
        GoblinLabour.LOGGER.info("Dev screenshot active: {}", name);
        ClientTickEvents.END_CLIENT_TICK.register(minecraft -> tick(minecraft, name));
    }

    private static void tick(Minecraft minecraft, String name) {
        if (done || minecraft.level == null || minecraft.player == null) return;
        ticksInWorld++;
        if (ticksInWorld == 60) openBook(minecraft, 0);
        if (ticksInWorld == 80) staffClick(minecraft);
        if (ticksInWorld == 200) openBook(minecraft, 1);
        if (ticksInWorld == 100) grab(minecraft, name + ".png");
        if (ticksInWorld == 260) grab(minecraft, name + "-b.png");
        if (ticksInWorld == 280) {
            done = true;
            minecraft.stop();
        }
    }

    /** -Dgoblinlabour.dev.book=N: shows handbook page N (1-based) plus {@code offset} in the book screen. */
    private static void openBook(Minecraft minecraft, int offset) {
        String raw = System.getProperty("goblinlabour.dev.book");
        if (raw == null) return;
        int first = Math.max(0, Integer.parseInt(raw.trim()) - 1 + offset);
        java.util.List<net.minecraft.network.chat.Component> pages = goblinlabour.item.GoblinHandbookItem.pages();
        if (first >= pages.size()) return;
        minecraft.gui.setScreen(new net.minecraft.client.gui.screens.inventory.BookViewScreen(
                new net.minecraft.client.gui.screens.inventory.BookViewScreen.BookAccess(pages.subList(first, pages.size()))));
    }

    /** -Dgoblinlabour.dev.staffclick: left-clicks the nearest goblin with whatever the player holds (staff test). */
    private static void staffClick(Minecraft minecraft) {
        if (System.getProperty("goblinlabour.dev.staffclick") == null || minecraft.gameMode == null) return;
        goblinlabour.entity.GoblinEntity nearest = null;
        double best = Double.MAX_VALUE;
        for (net.minecraft.world.entity.Entity entity : minecraft.level.entitiesForRendering()) {
            if (entity instanceof goblinlabour.entity.GoblinEntity goblin) {
                double d = goblin.distanceToSqr(minecraft.player);
                if (d < best) {
                    best = d;
                    nearest = goblin;
                }
            }
        }
        if (nearest != null) {
            minecraft.gameMode.attack(minecraft.player, nearest);
            GoblinLabour.LOGGER.info("Dev: staff-clicked {}", nearest.goblinName());
        }
    }

    private static void grab(Minecraft minecraft, String file) {
        Screenshot.grab(minecraft.gameDirectory, file, minecraft.gameRenderer.mainRenderTarget(), 1, message ->
                GoblinLabour.LOGGER.info("Screenshot: {}", message.getString()));
    }
}
