package goblinlabour.job;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

/**
 * What a goblin does. REST, CHOP and FARM are set on the bed; MINE_DOWN, MINE_UP and MINE_AHEAD come from a
 * Goblin Staff order ({@link Assignment}). REST is the off switch.
 */
public enum Job implements StringRepresentable {
    REST("rest"),
    MINE_DOWN("mine_down"),
    MINE_AHEAD("mine_ahead"),
    CHOP("chop"),
    FARM("farm"),
    MINE_UP("mine_up");

    public static final Codec<Job> CODEC = StringRepresentable.fromEnum(Job::values);

    private final String id;

    Job(String id) {
        this.id = id;
    }

    @Override
    public String getSerializedName() {
        return id;
    }

    public String translationKey() {
        return "goblinlabour.job." + id;
    }

    /** Jobs that need a staff order with coordinates. */
    public boolean needsAssignment() {
        return this == MINE_DOWN || this == MINE_UP || this == MINE_AHEAD;
    }

    public static Job byId(String id) {
        for (Job job : values()) {
            if (job.id.equals(id)) return job;
        }
        return REST;
    }
}
