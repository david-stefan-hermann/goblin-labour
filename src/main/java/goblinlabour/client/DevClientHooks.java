package goblinlabour.client;

import bcf.guidebook.client.GuideBookScreen;
import goblinlabour.GoblinLabour;
import goblinlabour.dev.DevHooks;
import goblinlabour.item.GuideBook;
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
        if (ticksInWorld == 80) staffPick(minecraft);
        if (ticksInWorld == 120) staffChest(minecraft);
        if (ticksInWorld == 200) openBook(minecraft, 1);
        if (ticksInWorld == 100) grab(minecraft, name + ".png");
        if (ticksInWorld == 260) grab(minecraft, name + "-b.png");
        scaffoldTest(minecraft);
        if (ticksInWorld == (System.getProperty(SCAFFOLD_PROPERTY) == null ? 280 : SCAFFOLD_END)) {
            done = true;
            minecraft.stop();
        }
    }

    /** -Dgoblinlabour.dev.book=N: shows guide book page N (1-based) plus {@code offset}. */
    private static void openBook(Minecraft minecraft, int offset) {
        String raw = System.getProperty("goblinlabour.dev.book");
        if (raw == null) return;
        minecraft.gui.setScreen(new GuideBookScreen(GuideBook.BOOK, Math.max(0, Integer.parseInt(raw.trim()) - 1 + offset)));
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

    /** -Dgoblinlabour.dev.staffpick: left-clicks the goblin under the crosshair, if any (whether it can be picked at all). */
    private static void staffPick(Minecraft minecraft) {
        if (System.getProperty("goblinlabour.dev.staffpick") == null || minecraft.gameMode == null) return;
        if (minecraft.hitResult instanceof net.minecraft.world.phys.EntityHitResult hit
                && hit.getEntity() instanceof goblinlabour.entity.GoblinEntity goblin) {
            minecraft.gameMode.attack(minecraft.player, goblin);
            GoblinLabour.LOGGER.info("Dev: crosshair picked {}", goblin.goblinName());
        } else {
            GoblinLabour.LOGGER.info("Dev: crosshair on {}", minecraft.hitResult == null ? "nothing" : minecraft.hitResult.getType());
        }
    }

    private static final String SCAFFOLD_PROPERTY = "goblinlabour.dev.scaffold";
    /** Past the scaffold lifetime counted from the scene setup, which runs before the client even starts. */
    private static final int SCAFFOLD_END = 2720;
    private static double scaffoldY;

    /**
     * -Dgoblinlabour.dev.scaffold=x,y,z (top block of a goblin scaffold column, the player standing on it): sneaks
     * for 40 ticks (vanilla scaffolding lets the player sink through), jumps for 60 ticks (climbs back up), then stays
     * on the column past its lifetime and logs whether the column is still there.
     */
    private static void scaffoldTest(Minecraft minecraft) {
        String raw = System.getProperty(SCAFFOLD_PROPERTY);
        if (raw == null) return;
        net.minecraft.client.player.LocalPlayer player = minecraft.player;
        if (ticksInWorld >= 100 && ticksInWorld <= 230 && ticksInWorld % 5 == 0) {
            GoblinLabour.LOGGER.info("Dev: scaffold trace {} y={} vy={} ground={} climb={} jump={} shift={} hcol={} flying={} at {}",
                    ticksInWorld, player.getY(), player.getDeltaMovement().y, player.onGround(), player.onClimbable(),
                    minecraft.options.keyJump.isDown(), player.isShiftKeyDown(), player.horizontalCollision,
                    player.getAbilities().flying, minecraft.level.getBlockState(player.blockPosition()));
        }
        switch (ticksInWorld) {
            case 110 -> player.getAbilities().flying = false;
            case 120 -> {
                scaffoldY = player.getY();
                minecraft.options.keyShift.setDown(true);
            }
            case 160 -> {
                minecraft.options.keyShift.setDown(false);
                GoblinLabour.LOGGER.info("Dev: scaffold sneak from y={} to y={} (in {})", scaffoldY, player.getY(),
                        minecraft.level.getBlockState(player.blockPosition()).getBlock());
                scaffoldY = player.getY();
                minecraft.options.keyJump.setDown(true);
            }
            case 220 -> {
                minecraft.options.keyJump.setDown(false);
                GoblinLabour.LOGGER.info("Dev: scaffold jump from y={} to y={}", scaffoldY, player.getY());
            }
            case SCAFFOLD_END - 20 -> {
                String[] p = raw.split(",");
                net.minecraft.core.BlockPos top = new net.minecraft.core.BlockPos(Integer.parseInt(p[0].trim()),
                        Integer.parseInt(p[1].trim()), Integer.parseInt(p[2].trim()));
                GoblinLabour.LOGGER.info("Dev: scaffold column after {} ticks: top={} player y={}", ticksInWorld,
                        minecraft.level.getBlockState(top).getBlock(), player.getY());
            }
            default -> {
            }
        }
    }

    /** -Dgoblinlabour.dev.staffchest: left-clicks the block under the crosshair (assigning it as the deposit chest). */
    private static void staffChest(Minecraft minecraft) {
        if (System.getProperty("goblinlabour.dev.staffchest") == null || minecraft.gameMode == null) return;
        if (minecraft.hitResult instanceof net.minecraft.world.phys.BlockHitResult hit) {
            minecraft.gameMode.startDestroyBlock(hit.getBlockPos(), hit.getDirection());
            GoblinLabour.LOGGER.info("Dev: staff-clicked the block at {}", hit.getBlockPos().toShortString());
        } else {
            GoblinLabour.LOGGER.info("Dev: crosshair on no block for the chest click");
        }
    }

    private static void grab(Minecraft minecraft, String file) {
        Screenshot.grab(minecraft.gameDirectory, file, minecraft.gameRenderer.mainRenderTarget(), 1, message ->
                GoblinLabour.LOGGER.info("Screenshot: {}", message.getString()));
    }
}
