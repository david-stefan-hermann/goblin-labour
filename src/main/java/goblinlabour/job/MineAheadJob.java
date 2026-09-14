package goblinlabour.job;

import goblinlabour.block.GoblinBedBlockEntity;
import goblinlabour.entity.GoblinEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * A straight tunnel into the wall the staff was pointed at: the clicked block is the bottom centre of the
 * cross-section ({@code width} wide, {@code height} high), {@code length} blocks deep, worked slice by slice.
 */
public final class MineAheadJob implements JobTask {
    public static final MineAheadJob INSTANCE = new MineAheadJob();

    private MineAheadJob() {
    }

    @Override
    @Nullable
    public BlockPos entryPoint(GoblinBedBlockEntity bed, JobConfig config) {
        Assignment a = bed.getAssignment();
        return a == null ? null : a.origin().relative(a.direction().getOpposite());
    }

    @Override
    public void onDone(ServerLevel level, GoblinBedBlockEntity bed) {
        bed.setAssignment(null);
    }

    @Override
    public Pick pick(ServerLevel level, GoblinEntity goblin, GoblinBedBlockEntity bed, JobConfig config, Set<BlockPos> skipped) {
        Assignment a = bed.getAssignment();
        if (a == null || a.kind() != Assignment.Kind.TUNNEL) return Pick.DONE;
        Direction dir = a.direction();
        Direction side = dir.getClockWise();
        int w = a.width();
        int half = (w - 1) / 2;
        int h = a.height();
        BlockPos start = a.origin();
        Vec3 me = goblin.position();
        boolean sawToolProblem = false;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        for (int d = 0; d < a.length(); d++) {
            BlockPos slice = start.relative(dir, d);
            BlockPos best = null;
            double bestDist = Double.MAX_VALUE;
            boolean sliceHasWork = false;
            boolean sawSkipped = false;
            for (int s = -half; s < w - half; s++) {
                for (int y = 0; y < h; y++) {
                    cursor.set(slice.getX() + side.getStepX() * s, slice.getY() + y, slice.getZ() + side.getStepZ() * s);
                    BlockState state = level.getBlockState(cursor);
                    Mining.Verdict verdict = Mining.verdict(level, cursor, state, goblin);
                    if (verdict == Mining.Verdict.NEEDS_TOOL) {
                        sawToolProblem = true;
                        sliceHasWork = true;
                        continue;
                    }
                    if (verdict != Mining.Verdict.OK) continue;
                    sliceHasWork = true;
                    if (skipped.contains(cursor)) {
                        sawSkipped = true;
                        continue;
                    }
                    double dist = me.distanceToSqr(Vec3.atCenterOf(cursor));
                    if (dist < bestDist) {
                        bestDist = dist;
                        best = cursor.immutable();
                    }
                }
            }
            if (best != null) return Pick.of(best);
            if (sawSkipped) return Pick.RETRY; // never leave a slice behind unfinished
            if (sliceHasWork) return Pick.NEEDS_TOOL;
        }
        return sawToolProblem ? Pick.NEEDS_TOOL : Pick.DONE;
    }
}
