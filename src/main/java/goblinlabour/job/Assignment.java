package goblinlabour.job;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * A dig order given with the Goblin Staff: a shaft down or up from the clicked block, or a tunnel into the clicked
 * wall. Stored on every bed whose goblin was selected when the order was given, so several goblins can share it.
 */
public record Assignment(Kind kind, BlockPos origin, Direction direction, int width, int height, int length,
                         int targetY, boolean stairs) {

    public enum Kind implements StringRepresentable {
        DIG_DOWN("dig_down", Job.MINE_DOWN),
        DIG_UP("dig_up", Job.MINE_UP),
        TUNNEL("tunnel", Job.MINE_AHEAD);

        public static final Codec<Kind> CODEC = StringRepresentable.fromEnum(Kind::values);
        private final String id;
        private final Job job;

        Kind(String id, Job job) {
            this.id = id;
            this.job = job;
        }

        @Override
        public String getSerializedName() {
            return id;
        }

        public Job job() {
            return job;
        }

        public boolean isShaft() {
            return this != TUNNEL;
        }
    }

    public static final Codec<Assignment> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Kind.CODEC.fieldOf("kind").forGetter(Assignment::kind),
            BlockPos.CODEC.fieldOf("origin").forGetter(Assignment::origin),
            Direction.CODEC.optionalFieldOf("direction", Direction.NORTH).forGetter(Assignment::direction),
            Codec.INT.fieldOf("width").forGetter(Assignment::width),
            Codec.INT.optionalFieldOf("height", 3).forGetter(Assignment::height),
            Codec.INT.optionalFieldOf("length", 16).forGetter(Assignment::length),
            Codec.INT.fieldOf("targetY").forGetter(Assignment::targetY),
            Codec.BOOL.optionalFieldOf("stairs", true).forGetter(Assignment::stairs)
    ).apply(instance, Assignment::new));

    /** Horizontal footprint of a shaft: width x width centred on the origin (width is odd). */
    public BoundingBox shaftFootprint() {
        int half = width / 2;
        return new BoundingBox(origin.getX() - half, origin.getY(), origin.getZ() - half,
                origin.getX() + half, origin.getY(), origin.getZ() + half);
    }

    /** Highest layer of a shaft. */
    public int topY() {
        return kind == Kind.DIG_UP ? targetY : origin.getY();
    }

    /** Lowest layer of a shaft. */
    public int bottomY() {
        return kind == Kind.DIG_UP ? origin.getY() : targetY;
    }

    /** Two shafts collide when their columns and height ranges overlap and they are not the same order. */
    public boolean collidesWith(Assignment other) {
        if (equals(other) || !kind.isShaft() || !other.kind.isShaft()) return false;
        BoundingBox a = shaftFootprint();
        BoundingBox b = other.shaftFootprint();
        boolean columns = a.minX() <= b.maxX() && a.maxX() >= b.minX() && a.minZ() <= b.maxZ() && a.maxZ() >= b.minZ();
        boolean heights = bottomY() <= other.topY() && topY() >= other.bottomY();
        return columns && heights;
    }
}
