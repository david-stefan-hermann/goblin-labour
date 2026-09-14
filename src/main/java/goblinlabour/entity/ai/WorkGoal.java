package goblinlabour.entity.ai;

import goblinlabour.block.GoblinBedBlockEntity;
import goblinlabour.entity.GoblinEntity;
import goblinlabour.job.Job;
import goblinlabour.job.JobConfig;
import goblinlabour.job.JobRunner;
import goblinlabour.job.JobTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

/** Runs the bed's job (or a pending unload to the chests) through the goblin's {@link JobRunner}. */
public class WorkGoal extends Goal {
    private final GoblinEntity goblin;
    private GoblinBedBlockEntity bed;
    private JobConfig config;
    private JobTask task;

    public WorkGoal(GoblinEntity goblin) {
        this.goblin = goblin;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
    }

    @Override
    public boolean canUse() {
        if (goblin.isNoAi() || !(goblin.level() instanceof ServerLevel level)) return false;
        GoblinBedBlockEntity bed = goblin.bed();
        if (bed == null || !bed.owns(goblin.getUUID())) return false;
        JobConfig config = bed.getJob();
        JobTask task = JobTask.of(config.job());
        if (!goblin.runner().wantsToWork(level, task != null)) return false;
        this.bed = bed;
        this.config = config;
        this.task = task;
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        if (goblin.isNoAi() || !(goblin.level() instanceof ServerLevel level)) return false;
        if (goblin.bed() != bed || bed.isRemoved()) return false;
        if (!bed.getJob().equals(config)) return false;
        return goblin.runner().wantsToWork(level, task != null);
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void stop() {
        goblin.runner().stop((ServerLevel) goblin.level());
        bed = null;
        config = null;
        task = null;
    }

    @Override
    public void tick() {
        goblin.runner().tick((ServerLevel) goblin.level(), bed, config, task);
    }

    /** Unused import guard so the REST constant stays referenced for readers. */
    static boolean isRest(JobConfig config) {
        return config.job() == Job.REST;
    }
}
