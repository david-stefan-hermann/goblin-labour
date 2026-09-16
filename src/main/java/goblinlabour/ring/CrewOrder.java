package goblinlabour.ring;

import goblinlabour.block.GoblinBedBlockEntity;
import goblinlabour.entity.GoblinEntity;
import goblinlabour.job.Assignment;
import goblinlabour.job.Job;
import goblinlabour.job.JobConfig;
import goblinlabour.job.JobHost;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.jetbrains.annotations.Nullable;

/**
 * The staff order of one ring crew goblin, standing in for the bed a crew goblin does not have: the work loop
 * ({@link goblinlabour.job.JobRunner}) reads and clears the order here exactly as it does on a bed. Not saved, like
 * the crew itself.
 */
public final class CrewOrder implements JobHost {
    private final GoblinEntity goblin;
    private JobConfig job = JobConfig.rest(Direction.NORTH);
    @Nullable private Assignment assignment;
    private GoblinBedBlockEntity.Status status = GoblinBedBlockEntity.Status.IDLE;

    public CrewOrder(GoblinEntity goblin) {
        this.goblin = goblin;
    }

    /** A new staff order; replaces the one the goblin had. */
    public void give(Assignment order) {
        assignment = order;
        setJob(job.withJob(order.kind().job()));
    }

    public boolean active() {
        return assignment != null && job.job() != Job.REST;
    }

    public GoblinBedBlockEntity.Status status() {
        return status;
    }

    @Override
    public BlockPos getBlockPos() {
        return assignment != null ? assignment.origin() : goblin.blockPosition();
    }

    @Override
    public JobConfig getJob() {
        return job;
    }

    @Override
    public void setJob(JobConfig job) {
        this.job = job;
        goblin.runner().onJobChanged();
    }

    @Override
    @Nullable
    public Assignment getAssignment() {
        return assignment;
    }

    @Override
    public void setAssignment(@Nullable Assignment assignment) {
        this.assignment = assignment;
    }

    @Override
    public void setStatus(GoblinBedBlockEntity.Status status) {
        this.status = status;
    }

    @Override
    public boolean hasHome() {
        return false;
    }
}
