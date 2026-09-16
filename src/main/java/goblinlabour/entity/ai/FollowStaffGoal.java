package goblinlabour.entity.ai;

import goblinlabour.entity.GoblinEntity;
import goblinlabour.ring.RingCrew;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.UUID;

/** A goblin picked with the staff trails the player who picked it until it gets an order or is deselected. */
public class FollowStaffGoal extends Goal {
    private static final double STOP_SQ = 3.0 * 3.0;
    private static final double TELEPORT_SQ = 24.0 * 24.0;
    private static final double LOSE_SQ = 96.0 * 96.0;

    private final GoblinEntity goblin;
    private Player player;

    public FollowStaffGoal(GoblinEntity goblin) {
        this.goblin = goblin;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        UUID id = goblin.following();
        if (id == null || goblin.isNoAi()) return false;
        Player player = goblin.level().getPlayerByUUID(id);
        RingCrew.Session crew = goblin.crew();
        if (player == null && crew != null && crew.owner().getUUID().equals(id)) player = crew.owner(); // a crew knows its player
        if (player == null || !player.isAlive() || goblin.distanceToSqr(player) > LOSE_SQ) {
            goblin.setFollowing(null);
            return false;
        }
        this.player = player;
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void stop() {
        goblin.getNavigation().stop();
        player = null;
    }

    @Override
    public void tick() {
        goblin.getLookControl().setLookAt(player, 30.0f, 30.0f);
        double dist = goblin.distanceToSqr(player);
        if (dist <= STOP_SQ) {
            goblin.getNavigation().stop();
            return;
        }
        if (dist > TELEPORT_SQ && goblin.level() instanceof ServerLevel level) {
            goblin.blinkTo(level, BlockPos.containing(player.position()));
            return;
        }
        if (goblin.tickCount % 10 == 0) {
            Vec3 p = player.position();
            goblin.getNavigation().moveTo(p.x, p.y, p.z, 1.15);
        }
    }
}
