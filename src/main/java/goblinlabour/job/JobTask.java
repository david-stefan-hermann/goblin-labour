package goblinlabour.job;

import goblinlabour.entity.GoblinEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/** One kind of work. Stateless where possible: the world itself is the progress record. */
public interface JobTask {
    /**
     * Result of looking for the next thing to do: a block to break, a block to place ({@code place} != null,
     * e.g. a stair step), something to do in reach ({@code use} != null: milk a cow, pick berries), or nothing
     * (DONE / RETRY / NEEDS_TOOL / NEEDS_HOE, told apart by {@link #kind}).
     */
    record Pick(Kind kind, @Nullable BlockPos target, Mining.Verdict verdict, @Nullable BlockState place, @Nullable Use use) {
        public enum Kind {
            /** Something to break, place or use at {@code target}. */
            TARGET,
            DONE,
            /** Not done: the only work left is on blocks the goblin could not reach (skipped for a while). */
            RETRY,
            NEEDS_TOOL,
            /** Like NEEDS_TOOL, for a farmer with ripe crops around and no hoe. */
            NEEDS_HOE
        }

        public static final Pick DONE = new Pick(Kind.DONE, null, Mining.Verdict.NOTHING, null, null);
        public static final Pick NEEDS_TOOL = new Pick(Kind.NEEDS_TOOL, null, Mining.Verdict.NEEDS_TOOL, null, null);
        public static final Pick NEEDS_HOE = new Pick(Kind.NEEDS_HOE, null, Mining.Verdict.NEEDS_TOOL, null, null);
        public static final Pick RETRY = new Pick(Kind.RETRY, null, Mining.Verdict.NOTHING, null, null);

        public static Pick of(BlockPos pos) {
            return new Pick(Kind.TARGET, pos, Mining.Verdict.OK, null, null);
        }

        public static Pick place(BlockPos pos, BlockState state) {
            return new Pick(Kind.TARGET, pos, Mining.Verdict.OK, state, null);
        }

        /** {@code pos} is where the use happens now; a goblin that cannot get there skips that block for a while. */
        public static Pick use(BlockPos pos, Use use) {
            return new Pick(Kind.TARGET, pos, Mining.Verdict.OK, null, use);
        }

        public boolean isPlacement() {
            return place != null;
        }
    }

    /**
     * Something the goblin does to a block or an animal once it is in reach and has swung its tool a few times,
     * without breaking anything: milk a cow, shear a sheep, pick berries, tap sap.
     */
    interface Use {
        /** Where to walk to and look at (an animal moves); null when the use is no longer possible. */
        @Nullable
        Vec3 point(ServerLevel level);

        /** The tool the goblin holds while doing it. */
        ItemStack tool();

        /** Does it. */
        void apply(ServerLevel level, GoblinEntity goblin);

        /**
         * A use on a fixed block that may be high up: the goblin gets there like to a block it breaks (stand spot,
         * scaffold column) instead of only walking, and works from the break reach.
         */
        default boolean climbs() {
            return false;
        }

        /** The goblin could not get there: leave this one alone for a while (an animal is not a fixed block). */
        default void giveUp(ServerLevel level, long now) {
        }
    }

    /** The next block to break or place, or DONE / NEEDS_TOOL. {@code skipped} are blocks the goblin could not reach. */
    Pick pick(ServerLevel level, GoblinEntity goblin, JobHost bed, JobConfig config, Set<BlockPos> skipped);

    /** Called after a block was broken; replant, ... */
    default void afterBreak(ServerLevel level, GoblinEntity goblin, JobHost bed, JobConfig config, BlockPos broken) {
    }

    /** Called after a {@link Pick#place} went through, e.g. to take the planted sapling out of the storage. */
    default void afterPlace(ServerLevel level, GoblinEntity goblin, JobHost bed, JobConfig config, BlockPos pos, BlockState placed) {
    }

    /**
     * Where the goblin also picks up loose items it did not drop itself (only those {@link #wantsLoot} accepts), or
     * null. Its own drops are always picked up.
     */
    @Nullable
    default AABB lootArea(JobHost bed, JobConfig config) {
        return null;
    }

    default boolean wantsLoot(ItemStack stack) {
        return false;
    }

    /**
     * Where the goblin may stand and put up scaffold while it works (only x and z count), or null for anywhere. A
     * shaft dug upwards keeps the goblin inside the shaft: a cave cutting into its side is no place to work from.
     */
    @Nullable
    default BoundingBox standBounds(JobHost bed, JobConfig config) {
        return null;
    }

    /** A point outside the home the job starts at, or null when unknown (no "no exit" check then). */
    @Nullable
    BlockPos entryPoint(JobHost bed, JobConfig config);

    /** Jobs that never finish by themselves (chop, farm) keep the goblin on duty when nothing is ripe right now. */
    default boolean endless() {
        return false;
    }

    /** Called once when the job reports DONE. */
    default void onDone(ServerLevel level, JobHost bed) {
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
