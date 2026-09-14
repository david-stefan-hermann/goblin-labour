package goblinlabour.entity;

import com.mojang.serialization.Codec;
import goblinlabour.job.Job;
import net.minecraft.util.StringRepresentable;
import org.jetbrains.annotations.Nullable;

/**
 * The goblin's skin tint, chosen by its job: lumberjacks are green, farmers yellowish, miners greyish, collectors
 * bluish. Each style has {@link #LOOKS} textures with different clothes and trinkets; which one a goblin wears is
 * derived from its name.
 */
public enum GoblinStyle implements StringRepresentable {
    LUMBERJACK("lumberjack"),
    FARMER("farmer"),
    MINER("miner"),
    COLLECTOR("collector");

    public static final Codec<GoblinStyle> CODEC = StringRepresentable.fromEnum(GoblinStyle::values);
    public static final int LOOKS = 4;

    private final String id;

    GoblinStyle(String id) {
        this.id = id;
    }

    @Override
    public String getSerializedName() {
        return id;
    }

    /** The style a job gives its goblin, or null if the goblin keeps its current one (resting). */
    @Nullable
    public static GoblinStyle forJob(Job job) {
        return switch (job) {
            case CHOP -> LUMBERJACK;
            case FARM -> FARMER;
            case MINE_DOWN, MINE_UP, MINE_AHEAD -> MINER;
            case REST -> null;
        };
    }

    public static GoblinStyle byOrdinal(int ordinal) {
        GoblinStyle[] values = values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : LUMBERJACK;
    }

    /** Which of the {@link #LOOKS} a goblin wears: random per goblin, but stable across death, respawn and blanks. */
    public static int lookFor(String name) {
        return Math.floorMod(name.hashCode(), LOOKS);
    }
}
