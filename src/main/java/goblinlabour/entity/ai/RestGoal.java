package goblinlabour.entity.ai;

import goblinlabour.block.GoblinBedBlockEntity;
import goblinlabour.entity.GoblinEntity;
import goblinlabour.home.HomeRegistry;
import goblinlabour.job.Job;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * Off duty (job REST): potter about inside the flat most of the time (about nine tenths) and lie down in bed for
 * a short nap now and then. Healing is fast in bed and slow while wandering. While waiting for storage room the
 * goblin only lies down.
 */
public class RestGoal extends Goal {
    private static final double AT_BED_SQ = 1.2 * 1.2;
    private static final int WANDER_MIN = 1200, WANDER_MAX = 2000;
    private static final int SLEEP_MIN = 120, SLEEP_MAX = 220;
    private static final int HEAL_TICKS_BED = 20;
    private static final int HEAL_TICKS_AWAKE = 100;

    private enum Mode { WANDER, SLEEP }

    private final GoblinEntity goblin;
    private GoblinBedBlockEntity bed;
    private Mode mode = Mode.WANDER;
    private int modeTicks;
    private int healTimer;
    private BlockPos wanderTarget;
    private int wanderRetarget;

    public RestGoal(GoblinEntity goblin) {
        this.goblin = goblin;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
    }

    private boolean wantsRest() {
        GoblinBedBlockEntity bed = goblin.bed();
        if (bed == null || !bed.owns(goblin.getUUID())) return false;
        this.bed = bed;
        return (bed.getJob().job() == Job.REST && !goblin.runner().hasPendingDeposit()) || goblin.runner().isWaitingForRoom();
    }

    @Override
    public boolean canUse() {
        return !goblin.isNoAi() && goblin.recoverPos() == null && wantsRest();
    }

    @Override
    public boolean canContinueToUse() {
        return !goblin.isNoAi() && goblin.recoverPos() == null && wantsRest();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void start() {
        mode = Mode.WANDER;
        modeTicks = goblin.getRandom().nextInt(WANDER_MIN, WANDER_MAX);
        wanderTarget = null;
    }

    @Override
    public void stop() {
        goblin.setPose(Pose.STANDING);
        bed = null;
    }

    @Override
    public void tick() {
        boolean waiting = goblin.runner().isWaitingForRoom();
        if (!waiting && --modeTicks <= 0) {
            RandomSource random = goblin.getRandom();
            if (mode == Mode.SLEEP) {
                mode = Mode.WANDER;
                modeTicks = random.nextInt(WANDER_MIN, WANDER_MAX);
                wanderTarget = null;
            } else {
                mode = Mode.SLEEP;
                modeTicks = random.nextInt(SLEEP_MIN, SLEEP_MAX);
            }
        }
        if (waiting || mode == Mode.SLEEP) sleepTick(waiting);
        else wanderTick();
    }

    private void sleepTick(boolean waiting) {
        BlockPos pos = bed.getBlockPos();
        Vec3 center = new Vec3(pos.getX() + 0.5, pos.getY() + 0.5625, pos.getZ() + 0.5);
        if (goblin.position().distanceToSqr(center) > AT_BED_SQ) {
            goblin.setPose(Pose.STANDING);
            if (goblin.tickCount % 10 == 0) goblin.getNavigation().moveTo(center.x, center.y, center.z, 1.0);
            return;
        }
        goblin.getNavigation().stop();
        if (goblin.position().distanceToSqr(center) > 0.01) {
            goblin.snapTo(center.x, center.y, center.z, goblin.getBedOrientation().getOpposite().toYRot(), 0.0f);
        }
        goblin.setDeltaMovement(Vec3.ZERO);
        goblin.setPose(Pose.SLEEPING);
        bed.setStatus(waiting ? GoblinBedBlockEntity.Status.WAITING_FULL : GoblinBedBlockEntity.Status.RESTING);
        heal(HEAL_TICKS_BED);
    }

    private void wanderTick() {
        goblin.setPose(Pose.STANDING);
        bed.setStatus(GoblinBedBlockEntity.Status.RESTING);
        ServerLevel level = (ServerLevel) goblin.level();
        if (wanderTarget == null || --wanderRetarget <= 0 || goblin.getNavigation().isDone()) {
            wanderTarget = randomSpotInFlat(level);
            wanderRetarget = 100;
            if (wanderTarget != null) {
                goblin.getNavigation().moveTo(wanderTarget.getX() + 0.5, wanderTarget.getY(), wanderTarget.getZ() + 0.5, 0.6);
            }
        }
        heal(HEAL_TICKS_AWAKE);
    }

    private void heal(int everyTicks) {
        if (++healTimer < everyTicks) return;
        healTimer = 0;
        if (goblin.getHealth() < goblin.getMaxHealth()) goblin.heal(1.0f);
    }

    /** A random standable block inside the flat, or null after a few misses. */
    private BlockPos randomSpotInFlat(ServerLevel level) {
        BoundingBox box = HomeRegistry.flatBox(level, bed.getBlockPos());
        RandomSource random = goblin.getRandom();
        for (int i = 0; i < 8; i++) {
            int x = random.nextInt(box.minX(), box.maxX() + 1);
            int z = random.nextInt(box.minZ(), box.maxZ() + 1);
            for (int y = box.maxY(); y >= box.minY(); y--) {
                BlockPos pos = new BlockPos(x, y, z);
                if (level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()
                        && level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty()
                        && !level.getBlockState(pos.below()).getCollisionShape(level, pos.below()).isEmpty()) {
                    return pos;
                }
            }
        }
        return null;
    }
}
