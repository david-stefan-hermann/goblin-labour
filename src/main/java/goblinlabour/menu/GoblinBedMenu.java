package goblinlabour.menu;

import goblinlabour.GoblinLabour;
import goblinlabour.block.GoblinBedBlockEntity;
import goblinlabour.job.Job;
import goblinlabour.job.JobConfig;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Bed settings: rest, chop or farm and the radius for the latter two. Dig orders come from the Goblin Staff.
 * No slots; the state travels as ContainerData and the buttons arrive as {@link #clickMenuButton} ids.
 */
public class GoblinBedMenu extends AbstractContainerMenu {
    public static final int WIDTH = 176;
    public static final int HEIGHT = 100;

    public static final int DATA_JOB = 0;
    public static final int DATA_RADIUS = 1;
    public static final int DATA_STATUS = 2;
    public static final int DATA_HAS_GOBLIN = 3;
    public static final int DATA_COUNT = 4;

    public static final int BUTTON_REST = 0;
    public static final int BUTTON_CHOP = 1;
    public static final int BUTTON_FARM = 2;
    public static final int BUTTON_RADIUS_DOWN = 10;
    public static final int BUTTON_RADIUS_UP = 11;

    public final GoblinBedMenuData data;
    @Nullable private final GoblinBedBlockEntity bed;
    private final ContainerData values;

    public GoblinBedMenu(int containerId, Inventory playerInventory, GoblinBedMenuData data) {
        this(containerId, playerInventory, data, null, new SimpleContainerData(DATA_COUNT));
    }

    public GoblinBedMenu(int containerId, Inventory playerInventory, GoblinBedMenuData data,
                         @Nullable GoblinBedBlockEntity bed, ContainerData values) {
        super(GoblinLabour.BED_MENU, containerId);
        this.data = data;
        this.bed = bed;
        this.values = values;
        addDataSlots(values);
    }

    public Job job() {
        Job[] jobs = Job.values();
        int i = values.get(DATA_JOB);
        return i >= 0 && i < jobs.length ? jobs[i] : Job.REST;
    }

    public int radius() {
        return values.get(DATA_RADIUS);
    }

    public GoblinBedBlockEntity.Status status() {
        GoblinBedBlockEntity.Status[] all = GoblinBedBlockEntity.Status.values();
        int i = values.get(DATA_STATUS);
        return i >= 0 && i < all.length ? all[i] : GoblinBedBlockEntity.Status.EMPTY;
    }

    public boolean hasGoblin() {
        return values.get(DATA_HAS_GOBLIN) != 0;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (bed == null) return false;
        JobConfig config = bed.getJob();
        switch (id) {
            case BUTTON_REST -> config = config.withJob(Job.REST);
            case BUTTON_CHOP -> config = config.withJob(Job.CHOP);
            case BUTTON_FARM -> config = config.withJob(Job.FARM);
            case BUTTON_RADIUS_DOWN -> config = config.withLength(config.length() - 4);
            case BUTTON_RADIUS_UP -> config = config.withLength(config.length() + 4);
            default -> {
                return false;
            }
        }
        if (config.job() != bed.getJob().job() && !config.job().needsAssignment()) bed.setAssignment(null);
        bed.setJob(config);
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return bed == null || (!bed.isRemoved() && player.distanceToSqr(Vec3.atCenterOf(bed.getBlockPos())) <= 64.0);
    }

    /** Live view of the bed for the vanilla menu data sync. */
    public static ContainerData liveData(GoblinBedBlockEntity bed) {
        return new ContainerData() {
            @Override
            public int get(int index) {
                JobConfig job = bed.getJob();
                return switch (index) {
                    case DATA_JOB -> job.job().ordinal();
                    case DATA_RADIUS -> job.length();
                    case DATA_STATUS -> bed.getStatus().ordinal();
                    case DATA_HAS_GOBLIN -> bed.hasGoblin() ? 1 : 0;
                    default -> 0;
                };
            }

            @Override
            public void set(int index, int value) {
                // client side only receives
            }

            @Override
            public int getCount() {
                return DATA_COUNT;
            }
        };
    }
}
