package goblinlabour.job;

import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Progress towards a point, for noticing a goblin that walks without getting anywhere. Only getting closer counts:
 * jittering at the end of a path, bobbing in a scaffold block or being shoved about is no progress.
 */
public final class Headway {
    /** A point that moved less than this (squared) is still the same point, so its count goes on. */
    private static final double SAME_POINT_SQ = 0.25;
    private static final double MIN_GAIN = 0.3;

    @Nullable private Vec3 point;
    private double best;
    private int stuckTicks;

    /** One tick at {@code position} towards {@code point}: the ticks in a row without headway (0 right after some). */
    public int tick(Vec3 position, Vec3 point) {
        double distance = position.distanceTo(point);
        if (this.point == null || this.point.distanceToSqr(point) > SAME_POINT_SQ) {
            this.point = point;
            best = distance;
            stuckTicks = 0;
        }
        if (distance < best - MIN_GAIN) {
            best = distance;
            stuckTicks = 0;
            return 0;
        }
        return ++stuckTicks;
    }

    /** The next {@link #tick} starts over, whatever point it gets. */
    public void reset() {
        point = null;
        stuckTicks = 0;
    }

    /** The next {@link #tick} starts over for its point; the count so far stays readable until then. */
    public void forgetPoint() {
        point = null;
    }

    public void clearTicks() {
        stuckTicks = 0;
    }

    /** After a blink: measure from where the goblin is now. */
    public void restartFrom(Vec3 position, Vec3 point) {
        best = position.distanceTo(point);
    }

    public int stuckTicks() {
        return stuckTicks;
    }
}
