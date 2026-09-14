package goblinlabour.entity.ai;

import goblinlabour.GoblinLabour;
import goblinlabour.block.GoblinBedBlockEntity;
import goblinlabour.entity.GoblinEntity;
import goblinlabour.home.HomeRegistry;
import goblinlabour.job.Job;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;

/**
 * Off duty (job REST): potter about inside the flat most of the time (about nine tenths) and lie down in bed for
 * a short nap now and then. Pottering works like a vanilla stroll: a short walk to a spot a few blocks away, then a
 * pause of several seconds in which the goblin looks around or at someone nearby, then the next walk. A goblin that
 * is outside its flat walks straight home. Healing is fast in bed and slow while wandering. While waiting for
 * storage room the goblin only lies down.
 */
public class RestGoal extends Goal {
    private static final double AT_BED_SQ = 1.2 * 1.2;
    private static final int WANDER_MIN = 1200, WANDER_MAX = 2000;
    private static final int SLEEP_MIN = 120, SLEEP_MAX = 220;
    private static final int HEAL_TICKS_BED = 20;
    private static final int HEAL_TICKS_AWAKE = 100;
    /** Standing still between two walks. */
    private static final int PAUSE_MIN = 60, PAUSE_MAX = 260;
    private static final int WALK_TIMEOUT = 200;
    private static final double STROLL_SPEED = 0.7, HOME_SPEED = 1.0;
    private static final int STROLL_MIN_DIST = 2, STROLL_MAX_DIST = 6;
    private static final float LOOK_CHANCE = 0.01f;
    private static final double LOOK_AT_RANGE = 6.0;

    private enum Mode { WANDER, SLEEP }

    private final GoblinEntity goblin;
    private GoblinBedBlockEntity bed;
    private Mode mode = Mode.WANDER;
    private int modeTicks;
    private int healTimer;
    private boolean walking;
    private int walkTicks;
    private int pauseTicks;
    private int lookTicks;
    @Nullable private LivingEntity lookEntity;
    @Nullable private Vec3 lookPoint;
    private String lastStroll = "-";

    /** Debug summary for the status command. */
    public String debug() {
        return "rest: mode=" + mode + " walking=" + walking + " pause=" + pauseTicks + " stroll=" + lastStroll;
    }

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
        walking = false;
        pauseTicks = goblin.getRandom().nextInt(20, PAUSE_MIN);
        lookTicks = 0;
    }

    @Override
    public void stop() {
        goblin.setPose(Pose.STANDING);
        goblin.getNavigation().stop();
        walking = false;
        lookEntity = null;
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
                walking = false;
                pauseTicks = random.nextInt(20, PAUSE_MIN);
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
        heal(HEAL_TICKS_AWAKE);
        ServerLevel level = (ServerLevel) goblin.level();
        PathNavigation navigation = goblin.getNavigation();
        BoundingBox flat = HomeRegistry.flatBox(level, bed.getBlockPos());

        if (!flat.isInside(goblin.blockPosition())) {
            // away from home: no dawdling, walk back
            if (goblin.tickCount % 20 == 0 || navigation.isDone()) {
                BlockPos home = randomSpotInFlat(level, flat);
                if (home != null) navigation.moveTo(home.getX() + 0.5, home.getY(), home.getZ() + 0.5, HOME_SPEED);
            }
            walking = false;
            return;
        }

        if (walking) {
            if (navigation.isDone() || ++walkTicks > WALK_TIMEOUT) {
                navigation.stop();
                walking = false;
                pauseTicks = goblin.getRandom().nextInt(PAUSE_MIN, PAUSE_MAX);
            }
            return;
        }
        if (--pauseTicks > 0) {
            idleLook();
            return;
        }
        Path path = strollPath(level, flat);
        if (path != null && navigation.moveTo(path, STROLL_SPEED)) {
            walking = true;
            walkTicks = 0;
            lookTicks = 0;
            lookEntity = null;
            lastStroll = "to " + path.getTarget().toShortString();
        } else {
            lastStroll = path == null ? "no path" : "moveTo refused";
            pauseTicks = goblin.getRandom().nextInt(20, 60);
        }
    }

    /** Vanilla-style idling: now and then look at a player or goblin nearby, or just somewhere else for a moment. */
    private void idleLook() {
        RandomSource random = goblin.getRandom();
        if (lookTicks > 0) {
            lookTicks--;
            if (lookEntity != null && lookEntity.isAlive() && goblin.distanceToSqr(lookEntity) < LOOK_AT_RANGE * LOOK_AT_RANGE * 2) {
                goblin.getLookControl().setLookAt(lookEntity, 10.0f, goblin.getMaxHeadXRot());
            } else if (lookPoint != null) {
                goblin.getLookControl().setLookAt(lookPoint);
            }
            return;
        }
        lookEntity = null;
        lookPoint = null;
        if (random.nextFloat() < LOOK_CHANCE) {
            LivingEntity nearest = nearestToLookAt();
            if (nearest != null) {
                lookEntity = nearest;
                lookTicks = 40 + random.nextInt(40);
                return;
            }
        }
        if (random.nextFloat() < LOOK_CHANCE) {
            double angle = random.nextDouble() * Mth.TWO_PI;
            lookPoint = new Vec3(goblin.getX() + Math.cos(angle), goblin.getEyeY(), goblin.getZ() + Math.sin(angle));
            lookTicks = 20 + random.nextInt(20);
        }
    }

    @Nullable
    private LivingEntity nearestToLookAt() {
        Player player = goblin.level().getNearestPlayer(goblin, LOOK_AT_RANGE);
        if (player != null && !player.isSpectator()) return player;
        LivingEntity best = null;
        double bestDist = LOOK_AT_RANGE * LOOK_AT_RANGE;
        for (GoblinEntity other : goblin.level().getEntitiesOfClass(GoblinEntity.class, goblin.getBoundingBox().inflate(LOOK_AT_RANGE))) {
            if (other == goblin) continue;
            double d = goblin.distanceToSqr(other);
            if (d < bestDist) {
                bestDist = d;
                best = other;
            }
        }
        return best;
    }

    /** A short walk: a reachable standable spot in the flat, a few blocks away from where the goblin stands. */
    @Nullable
    private Path strollPath(ServerLevel level, BoundingBox flat) {
        RandomSource random = goblin.getRandom();
        BlockPos here = goblin.blockPosition();
        for (int i = 0; i < 10; i++) {
            int x = Mth.clamp(here.getX() + random.nextInt(-STROLL_MAX_DIST, STROLL_MAX_DIST + 1), flat.minX(), flat.maxX());
            int z = Mth.clamp(here.getZ() + random.nextInt(-STROLL_MAX_DIST, STROLL_MAX_DIST + 1), flat.minZ(), flat.maxZ());
            int dx = x - here.getX(), dz = z - here.getZ();
            if (dx * dx + dz * dz < STROLL_MIN_DIST * STROLL_MIN_DIST) continue;
            BlockPos spot = standableIn(level, flat, x, z, here.getY() + 2, here.getY() - 2);
            if (spot == null) continue;
            Path path = goblin.getNavigation().createPath(spot, 0);
            if (path != null && path.canReach()) return path;
        }
        return null;
    }

    /** A random standable block anywhere in the flat, or null after a few misses. */
    @Nullable
    private BlockPos randomSpotInFlat(ServerLevel level, BoundingBox flat) {
        RandomSource random = goblin.getRandom();
        for (int i = 0; i < 8; i++) {
            BlockPos spot = standableIn(level, flat, random.nextInt(flat.minX(), flat.maxX() + 1),
                    random.nextInt(flat.minZ(), flat.maxZ() + 1), flat.maxY(), flat.minY());
            if (spot != null) return spot;
        }
        return null;
    }

    /** The highest standable block of column x/z between the two heights (within the flat), not on a straw bed. */
    @Nullable
    private static BlockPos standableIn(ServerLevel level, BoundingBox flat, int x, int z, int fromY, int toY) {
        for (int y = Math.min(fromY, flat.maxY()); y >= Math.max(toY, flat.minY()); y--) {
            BlockPos pos = new BlockPos(x, y, z);
            BlockPos below = pos.below();
            if (level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()
                    && level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty()
                    && !level.getBlockState(below).getCollisionShape(level, below).isEmpty()
                    && !level.getBlockState(below).is(GoblinLabour.GOBLIN_STRAW_BED)) {
                return pos;
            }
        }
        return null;
    }

    private void heal(int everyTicks) {
        if (++healTimer < everyTicks) return;
        healTimer = 0;
        if (goblin.getHealth() < goblin.getMaxHealth()) goblin.heal(1.0f);
    }
}
