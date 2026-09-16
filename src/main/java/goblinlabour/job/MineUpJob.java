package goblinlabour.job;

import goblinlabour.entity.GoblinEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * A square shaft straight up from the ceiling block the staff was pointed at, layer by layer up to the target
 * height (the surface by default). The goblin stacks scaffold under itself to keep up with the ceiling and comes
 * back down on it afterwards. Same stair pattern as the shaft down, counted from the top.
 */
public final class MineUpJob implements JobTask {
    public static final MineUpJob INSTANCE = new MineUpJob();

    private MineUpJob() {
    }

    @Override
    @Nullable
    public BlockPos entryPoint(JobHost bed, JobConfig config) {
        Assignment a = bed.getAssignment();
        if (a == null) return null;
        // on the floor below the ceiling; an order without a floor (saved before floors existed) right under it
        return a.floorY() == Assignment.NO_FLOOR ? a.origin().below() : new BlockPos(a.origin().getX(), a.bottomY(), a.origin().getZ());
    }

    @Override
    public void onDone(ServerLevel level, JobHost bed) {
        bed.setAssignment(null);
    }

    @Override
    public Pick pick(ServerLevel level, GoblinEntity goblin, JobHost bed, JobConfig config, Set<BlockPos> skipped) {
        Assignment a = bed.getAssignment();
        if (a == null || a.kind() != Assignment.Kind.DIG_UP) return Pick.DONE;
        int topY = Math.min(a.topY(), level.getMaxY());
        return MineDownJob.scanLayers(level, goblin, a.shaftFootprint(), topY, a.bottomY(), a.stairs(), skipped, false);
    }
}
