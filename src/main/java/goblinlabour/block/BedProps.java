package goblinlabour.block;

import goblinlabour.entity.GoblinStyle;
import net.minecraft.util.StringRepresentable;

/**
 * What lies under a straw bed: nothing while the goblin rests, otherwise tools and loot of its trade. The block
 * model picks one of four arrangements per bed position (see tools/MakeBedModels.java).
 */
public enum BedProps implements StringRepresentable {
    NONE("none"),
    LUMBERJACK("lumberjack"),
    FARMER("farmer"),
    MINER("miner"),
    COLLECTOR("collector");

    private final String id;

    BedProps(String id) {
        this.id = id;
    }

    @Override
    public String getSerializedName() {
        return id;
    }

    public static BedProps of(GoblinStyle style) {
        return switch (style) {
            case LUMBERJACK -> LUMBERJACK;
            case FARMER -> FARMER;
            case MINER -> MINER;
            case COLLECTOR -> COLLECTOR;
        };
    }
}
