package goblinlabour.job;

import goblinlabour.block.GoblinBedBlockEntity;
import goblinlabour.entity.GoblinEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/** One kind of work. Stateless where possible: the world itself is the progress record. */
public interface JobTask {
    /**
     * Result of looking for the next thing to do: a block to break, a block to place ({@code place} != null,
     * e.g. a stair step), or nothing (DONE / NEEDS_TOOL).
     */
    record Pick(@Nullable BlockPos target, Mining.Verdict verdict, @Nullable BlockState place) {
        public static final Pick DONE = new Pick(null, Mining.Verdict.NOTHING, null);
        public static final Pick NEEDS_TOOL = new Pick(null, Mining.Verdict.NEEDS_TOOL, null);

        public static Pick of(BlockPos pos) {
            return new Pick(pos, Mining.Verdict.OK, null);
        }

        public static Pick place(BlockPos pos, BlockState state) {
            return new Pick(pos, Mining.Verdict.OK, state);
        }

        public boolean isPlacement() {
            return place != null;
        }
    }

    /** The next block to break or place, or DONE / NEEDS_TOOL. {@code skipped} are blocks the goblin could not reach. */
    Pick pick(ServerLevel level, GoblinEntity goblin, GoblinBedBlockEntity bed, JobConfig config, Set<BlockPos> skipped);

    /** Called after a block was broken; replant, ... */
    default void afterBreak(ServerLevel level, GoblinEntity goblin, GoblinBedBlockEntity bed, JobConfig config, BlockPos broken) {
    }

    /** A point outside the home the job starts at, or null when unknown (no "no exit" check then). */
    @Nullable
    BlockPos entryPoint(GoblinBedBlockEntity bed, JobConfig config);

    /** Jobs that never finish by themselves (chop, farm) keep the goblin on duty when nothing is ripe right now. */
    default boolean endless() {
        return false;
    }

    /** Called once when the job reports DONE. */
    default void onDone(ServerLevel level, GoblinBedBlockEntity bed) {
    }

    @Nullable
    static JobTask of(Job job) {
        return switch (job) {
            case MINE_DOWN -> MineDownJob.INSTANCE;
            case MINE_UP -> MineUpJob.INSTANCE;
            case MINE_AHEAD -> MineAheadJob.INSTANCE;
            case CHOP -> ChopJob.INSTANCE;
            case FARM -> FarmJob.INSTANCE;
            case COLLECT -> CollectJob.INSTANCE;
            case REST -> null;
        };
    }
}
