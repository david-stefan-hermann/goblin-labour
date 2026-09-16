package goblinlabour.staff;

import goblinlabour.GoblinLabour;
import goblinlabour.GoblinSounds;
import goblinlabour.GoblinSpeech;
import goblinlabour.block.GoblinBedBlockEntity;
import goblinlabour.entity.GoblinEntity;
import goblinlabour.home.HomeRegistry;
import goblinlabour.job.Assignment;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

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
    private static final Map<UUID, Set<UUID>> SELECTED = new ConcurrentHashMap<>();

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
            // a ring crew listens to the player whose ring called it
            if (goblin.crew() != null && !goblin.crew().owner().getUUID().equals(player.getUUID())) return InteractionResult.FAIL;
            toggle(serverLevel, player, goblin);
            return InteractionResult.FAIL;
        });
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
