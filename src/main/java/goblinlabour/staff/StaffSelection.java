package goblinlabour.staff;

import goblinlabour.GoblinLabour;
import goblinlabour.GoblinSounds;
import goblinlabour.GoblinSpeech;
import goblinlabour.block.GoblinBedBlockEntity;
import goblinlabour.entity.GoblinEntity;
import goblinlabour.home.HomeRegistry;
import goblinlabour.job.Assignment;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Which goblins a player has picked with the staff (left-click toggles). Selected goblins glow and follow the
 * player; a staff order goes to all of them at once and clears the selection. Server side only, not persisted.
 */
public final class StaffSelection {
    /** How far from its bed a goblin's chest may stand. */
    public static final int MAX_CHEST_DISTANCE = 48;
    private static final Map<UUID, Set<UUID>> SELECTED = new ConcurrentHashMap<>();

    /** Forgets it all when the server stops: in single player the next world runs in the same JVM. */
    public static void reset() {
        SELECTED.clear();
    }

    private StaffSelection() {
    }

    public static void init() {
        AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
            if (!(entity instanceof GoblinEntity goblin) || !player.getItemInHand(hand).is(GoblinLabour.GOBLIN_STAFF)) {
                return InteractionResult.PASS;
            }
            // Client: SUCCESS makes Fabric send the attack packet (anything else swallows the click and the server
            // never hears about it). Server: FAIL cancels the damage after the selection has been toggled.
            if (!(level instanceof ServerLevel serverLevel)) return InteractionResult.SUCCESS;
            if (FabricLoader.getInstance().isDevelopmentEnvironment()) {
                GoblinLabour.LOGGER.info("Dev: staff attack on {} reached the server", goblin.goblinName());
            }
            // a ring crew listens to the player whose ring called it
            if (goblin.crew() != null && !goblin.crew().owner().getUUID().equals(player.getUUID())) return InteractionResult.FAIL;
            toggle(serverLevel, player, goblin);
            return InteractionResult.FAIL;
        });

        // left-click a container with picked goblins: that is where they unload from now on
        AttackBlockCallback.EVENT.register((player, level, hand, pos, direction) -> {
            if (!player.getItemInHand(hand).is(GoblinLabour.GOBLIN_STAFF)) return InteractionResult.PASS;
            if (!(level instanceof ServerLevel serverLevel)) {
                // client: SUCCESS sends the packet and keeps a creative player from breaking the block at once
                return level.getBlockEntity(pos) != null ? InteractionResult.SUCCESS : InteractionResult.PASS;
            }
            return assignChest(serverLevel, player, pos);
        });
    }

    /**
     * A left-click with the staff on a container: every picked goblin unloads there from now on (the same click on the
     * same chest takes the assignment back). Passes the click on when nothing is picked or the block is no container.
     */
    private static InteractionResult assignChest(ServerLevel level, Player player, BlockPos pos) {
        List<GoblinEntity> goblins = selected(level, player);
        if (goblins.isEmpty()) return InteractionResult.PASS;
        if (ItemStorage.SIDED.find(level, pos, Direction.UP) == null) return InteractionResult.PASS;
        boolean clear = goblins.stream().anyMatch(goblin -> pos.equals(assignedChest(goblin)));
        int done = 0;
        for (GoblinEntity goblin : goblins) {
            if (goblin.crew() != null) {
                goblin.crew().setChest(clear ? null : pos.immutable()); // one chest for the whole crew
                done++;
                continue;
            }
            GoblinBedBlockEntity bed = goblin.bed();
            if (bed == null) continue;
            if (!clear && !bed.getBlockPos().closerThan(pos, MAX_CHEST_DISTANCE)) {
                player.sendOverlayMessage(Component.translatable("goblinlabour.staff.chest_too_far", bed.getGoblinName()));
                continue;
            }
            bed.setDepositChest(clear ? null : pos.immutable());
            done++;
        }
        if (done > 0) {
            if (FabricLoader.getInstance().isDevelopmentEnvironment()) {
                GoblinLabour.LOGGER.info("Dev: staff {} the chest at {} for {} goblin(s)", clear ? "cleared" : "assigned",
                        pos.toShortString(), done);
            }
            player.sendOverlayMessage(Component.translatable(clear
                    ? "goblinlabour.staff.chest_cleared" : "goblinlabour.staff.chest_assigned", done));
            GoblinEntity speaker = goblins.get(0);
            GoblinSpeech.say(level, speaker, GoblinSpeech.random(speaker.getRandom(), GoblinSpeech.YES), GoblinSounds.YES);
            clear(level, player); // like an order: the click ends the selection
        }
        return InteractionResult.FAIL; // never break the block with the staff
    }

    @Nullable
    private static BlockPos assignedChest(GoblinEntity goblin) {
        if (goblin.crew() != null) return goblin.crew().chest();
        GoblinBedBlockEntity bed = goblin.bed();
        return bed == null ? null : bed.depositChest();
    }

    public static void toggle(ServerLevel level, Player player, GoblinEntity goblin) {
        Set<UUID> set = SELECTED.computeIfAbsent(player.getUUID(), k -> new LinkedHashSet<>());
        if (set.remove(goblin.getUUID())) {
            goblin.setFollowing(null);
            GoblinSpeech.say(level, goblin, GoblinSpeech.STAY, GoblinSounds.NO);
        } else {
            set.add(goblin.getUUID());
            goblin.setFollowing(player.getUUID());
            GoblinSpeech.say(level, goblin, GoblinSpeech.random(goblin.getRandom(), GoblinSpeech.FOLLOW), GoblinSounds.YES);
        }
    }

    /** The player's living selected goblins in this level. */
    public static List<GoblinEntity> selected(ServerLevel level, Player player) {
        List<GoblinEntity> result = new ArrayList<>();
        Set<UUID> set = SELECTED.get(player.getUUID());
        if (set == null) return result;
        for (UUID id : set) {
            Entity entity = level.getEntity(id);
            if (entity instanceof GoblinEntity goblin && goblin.isAlive()) result.add(goblin);
        }
        return result;
    }

    public static int count(ServerLevel level, Player player) {
        return selected(level, player).size();
    }

    public static void clear(ServerLevel level, Player player) {
        for (GoblinEntity goblin : selected(level, player)) goblin.setFollowing(null);
        SELECTED.remove(player.getUUID());
    }

    /**
     * Gives the order to every selected goblin (its bed stores it) and clears the selection. Returns a message for
     * the player.
     */
    public static Component assign(ServerLevel level, Player player, Assignment order) {
        Assignment assignment = order.withFloorFrom(level); // before the collision check: the floor belongs to the shaft
        List<GoblinEntity> goblins = selected(level, player);
        if (goblins.isEmpty()) return Component.translatable("goblinlabour.staff.select_first");
        Set<BlockPos> ownBeds = new LinkedHashSet<>();
        for (GoblinEntity goblin : goblins) goblin.getBedPos().ifPresent(ownBeds::add);
        if (assignment.kind().isShaft()) {
            for (BlockPos other : HomeRegistry.beds(level)) {
                if (ownBeds.contains(other) || !(level.getBlockEntity(other) instanceof GoblinBedBlockEntity bed)) continue;
                Assignment theirs = bed.getAssignment();
                if (theirs != null && theirs.collidesWith(assignment)) {
                    return Component.translatable("goblinlabour.staff.shaft_taken", bed.getGoblinName());
                }
            }
        }
        if (HomeRegistry.isProtected(level, assignment.origin())) {
            return Component.translatable("goblinlabour.staff.in_home");
        }
        int started = 0;
        for (GoblinEntity goblin : goblins) {
            if (goblin.crew() != null) {
                goblin.crewOrder().give(assignment); // a ring crew goblin keeps its order itself
                started++;
                continue;
            }
            GoblinBedBlockEntity bed = goblin.bed();
            if (bed == null) continue;
            applyTo(bed, assignment);
            started++;
        }
        clear(level, player);
        if (started > 0) {
            GoblinEntity speaker = goblins.get(0);
            GoblinSpeech.say(level, speaker, GoblinSpeech.random(speaker.getRandom(), GoblinSpeech.YES), GoblinSounds.YES);
        }
        return Component.translatable("goblinlabour.staff.started", started, Component.translatable(assignment.kind().job().translationKey()));
    }

    public static void applyTo(GoblinBedBlockEntity bed, Assignment assignment) {
        bed.rememberJobBefore(bed.getJob()); // so the goblin goes back to chopping or farming when the order is done
        bed.setAssignment(assignment);
        bed.setJob(bed.getJob().withJob(assignment.kind().job()));
    }
}
