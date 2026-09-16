package goblinlabour.job;

import goblinlabour.block.GoblinBedBlockEntity;
import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;

/**
 * What a goblin works for: its bed ({@link GoblinBedBlockEntity}), or for a ring crew goblin its staff order
 * ({@link goblinlabour.ring.CrewOrder}). Holds the job, the staff order and the status the work loop reports.
 */
public interface JobHost {
    /** Where the work is centred: the bed, or the origin of a crew goblin's order. */
    BlockPos getBlockPos();

    JobConfig getJob();

    void setJob(JobConfig job);

    @Nullable
    Assignment getAssignment();

    void setAssignment(@Nullable Assignment assignment);

    void setStatus(GoblinBedBlockEntity.Status status);

    /** False for a ring crew: no home to leave (no exit check) and no flat whose chests it unloads into. */
    default boolean hasHome() {
        return true;
    }

    /**
     * The job to take up once a staff order is over. A ring crew goblin rests and lets its crew carry on; a bed
     * hands back the job the order interrupted (see {@link GoblinBedBlockEntity#afterOrder}).
     */
    default JobConfig afterOrder(JobConfig current) {
        return current.withJob(Job.REST);
    }
}
