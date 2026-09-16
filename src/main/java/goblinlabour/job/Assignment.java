package goblinlabour.job;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * A dig order given with the Goblin Staff: a shaft down or up from the clicked block, or a tunnel into the clicked
 * wall. Stored on every bed whose goblin was selected when the order was given, so several goblins can share it.
 */
public record Assignment(Kind kind, BlockPos origin, Direction direction, int width, int height, int length,
                         int targetY, boolean stairs, int floorY) {

    /** {@link #floorY} of an order without a floor of its own: shafts down, tunnels, orders saved before it existed. */
    public static final int NO_FLOOR = Integer.MIN_VALUE;
    /** How far below a clicked ceiling the floor of a shaft up is looked for. */
    private static final int FLOOR_SEARCH = 16;

    public Assignment(Kind kind, BlockPos origin, Direction direction, int width, int height, int length, int targetY,
                      boolean stairs) {
        this(kind, origin, direction, width, height, length, targetY, stairs, NO_FLOOR);
    }

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
            Codec.BOOL.optionalFieldOf("stairs", true).forGetter(Assignment::stairs),
            Codec.INT.optionalFieldOf("floorY", NO_FLOOR).forGetter(Assignment::floorY)
    ).apply(instance, Assignment::new));

    /**
     * A shaft up is dug from the clicked ceiling, but the goblin starts on the floor below it: the air in between gets
     * the stair steps too, or the stairs would hang in the air above the floor and nobody could reach them. The floor
     * is the first block with collision below the ceiling (up to 16 down); none found, the shaft starts at the ceiling.
     */
    public Assignment withFloorFrom(Level level) {
        if (kind != Kind.DIG_UP) return this;
        BlockPos.MutableBlockPos cursor = origin.mutable();
        int floor = origin.getY();
        for (int i = 1; i <= FLOOR_SEARCH; i++) {
            cursor.setY(origin.getY() - i);
            if (!level.getBlockState(cursor).getCollisionShape(level, cursor).isEmpty()) {
                floor = cursor.getY() + 1;
                break;
            }
        }
        return new Assignment(kind, origin, direction, width, height, length, targetY, stairs, floor);
    }

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

    /** Lowest layer of a shaft: for a shaft up the floor the goblin starts on (see {@link #withFloorFrom}). */
    public int bottomY() {
        if (kind != Kind.DIG_UP) return targetY;
        return floorY == NO_FLOOR ? origin.getY() : Math.min(floorY, origin.getY());
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
