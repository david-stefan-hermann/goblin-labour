package goblinlabour.menu;

import goblinlabour.GoblinLabour;
import goblinlabour.block.MilkChurnBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** The Milk Churn screen: input slot above the output slot, the tank gauge beside them, then the player's inventory. */
public class MilkChurnMenu extends AbstractContainerMenu {
    public static final int WIDTH = 176;
    public static final int SLOT_X = 44;
    public static final int INPUT_Y = 20;
    public static final int OUTPUT_Y = 58;
    public static final int TANK_X = 98;
    public static final int TANK_Y = 18;
    public static final int TANK_WIDTH = 16;
    public static final int TANK_HEIGHT = 58;
    public static final int PLAYER_Y = TANK_Y + TANK_HEIGHT + 22;
    public static final int HOTBAR_Y = PLAYER_Y + 58;
    public static final int HEIGHT = HOTBAR_Y + 24;

    private final Container churn;
    private final ContainerData data;

    /** Client side: the slots and the milk level arrive through the normal menu sync. */
    public MilkChurnMenu(int containerId, Inventory playerInventory, BlockPos pos) {
        this(containerId, playerInventory, new SimpleContainer(2), new SimpleContainerData(2));
    }

    public MilkChurnMenu(int containerId, Inventory playerInventory, Container churn, ContainerData data) {
        super(GoblinLabour.MILK_CHURN_MENU, containerId);
        this.churn = churn;
        this.data = data;
        addSlot(new Slot(churn, MilkChurnBlockEntity.INPUT, SLOT_X, INPUT_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.is(Items.BUCKET) || stack.is(Items.MILK_BUCKET);
            }
        });
        addSlot(new Slot(churn, MilkChurnBlockEntity.OUTPUT, SLOT_X, OUTPUT_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
            }
        });
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInventory, 9 + row * 9 + col, 8 + col * 18, PLAYER_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInventory, col, 8 + col * 18, HOTBAR_Y));
        }
        addDataSlots(data);
    }

    public int milk() {
        return data.get(0);
    }

    public int capacity() {
        return Math.max(1, data.get(1));
    }

    public boolean isInputSlot(Slot slot) {
        return slot.container == churn && slot.getContainerSlot() == MilkChurnBlockEntity.INPUT;
    }

    public boolean isChurnSlot(Slot slot) {
        return slot.container == churn;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack result = stack.copy();
        int churnEnd = 2, playerEnd = churnEnd + 36;
        if (index < churnEnd) {
            if (!moveItemStackTo(stack, churnEnd, playerEnd, true)) return ItemStack.EMPTY;
        } else if (stack.is(Items.BUCKET) || stack.is(Items.MILK_BUCKET)) {
            if (!moveItemStackTo(stack, MilkChurnBlockEntity.INPUT, MilkChurnBlockEntity.INPUT + 1, false)) return ItemStack.EMPTY;
        } else {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.set(ItemStack.EMPTY);
        else slot.setChanged();
        return result;
    }

    @Override
    public boolean stillValid(Player player) {
        return churn.stillValid(player);
    }
}
