package goblinlabour.job;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;

/**
 * Job settings stored on the bed. {@code width} is the shaft/tunnel width, {@code length} the tunnel length or
 * the chop/farm radius, {@code stairs} whether a mine-down shaft gets a cobblestone stair, {@code direction} where
 * the job area lies relative to the bed (defaults to the bed's facing).
 */
public record JobConfig(Job job, int width, int length, boolean stairs, Direction direction) {
    public static final int MIN_WIDTH = 1;
    public static final int MAX_WIDTH = 5;
    public static final int MIN_LENGTH = 4;
    public static final int MAX_LENGTH = 64;

    public static final Codec<JobConfig> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Job.CODEC.fieldOf("job").forGetter(JobConfig::job),
            Codec.INT.optionalFieldOf("width", 3).forGetter(JobConfig::width),
            Codec.INT.optionalFieldOf("length", 16).forGetter(JobConfig::length),
            Codec.BOOL.optionalFieldOf("stairs", true).forGetter(JobConfig::stairs),
            Direction.CODEC.optionalFieldOf("direction", Direction.NORTH).forGetter(JobConfig::direction)
    ).apply(instance, JobConfig::new));

    public JobConfig {
        width = Mth.clamp(width, MIN_WIDTH, MAX_WIDTH);
        length = Mth.clamp(length, MIN_LENGTH, MAX_LENGTH);
        if (direction.getAxis().isVertical()) direction = Direction.NORTH;
        if (job == Job.MINE_DOWN && width % 2 == 0) width = Math.min(MAX_WIDTH, width + 1); // shafts are centred on the marker
    }

    public static JobConfig rest(Direction facing) {
        return new JobConfig(Job.REST, 3, 16, true, facing);
    }

    public JobConfig withJob(Job job) {
        return new JobConfig(job, width, length, stairs, direction);
    }

    public JobConfig withWidth(int width) {
        return new JobConfig(job, width, length, stairs, direction);
    }

    public JobConfig withLength(int length) {
        return new JobConfig(job, width, length, stairs, direction);
    }

    public JobConfig withStairs(boolean stairs) {
        return new JobConfig(job, width, length, stairs, direction);
    }

    public JobConfig withDirection(Direction direction) {
        return new JobConfig(job, width, length, stairs, direction);
    }
}
