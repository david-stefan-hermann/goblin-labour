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
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The screen of the Milk Can and of the Milk Can Expansion, shaped like the block (see {@code MilkCanPicture}): in
 * the body the bucket slot above the output slot, the tank gauge to their right, and the player's inventory below
 * ({@link MilkCanLayout}).
 */
public class MilkChurnMenu extends AbstractContainerMenu {
    private final Container churn;
    private final ContainerData data;
    private final int bodyY;

    /** Client side, Milk Can: the slots and the milk level arrive through the normal menu sync. */
    public static MilkChurnMenu can(int containerId, Inventory playerInventory, BlockPos pos) {
        return new MilkChurnMenu(GoblinLabour.MILK_CHURN_MENU, containerId, playerInventory, new SimpleContainer(2),
                new SimpleContainerData(2), MilkCanLayout.CAN_BODY_Y);
    }

    /** Client side, Milk Can Expansion. */
    public static MilkChurnMenu expansion(int containerId, Inventory playerInventory, BlockPos pos) {
        return new MilkChurnMenu(GoblinLabour.MILK_CAN_EXPANSION_MENU, containerId, playerInventory, new SimpleContainer(2),
                new SimpleContainerData(2), MilkCanLayout.EXPANSION_BODY_Y);
    }

    public MilkChurnMenu(MenuType<?> type, int containerId, Inventory playerInventory, Container churn, ContainerData data, int bodyY) {
        super(type, containerId);
        this.churn = churn;
        this.data = data;
        this.bodyY = bodyY;
        addSlot(new Slot(churn, MilkChurnBlockEntity.INPUT, MilkCanLayout.SLOT_X, MilkCanLayout.inputY(bodyY)) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.is(Items.BUCKET) || stack.is(Items.MILK_BUCKET);
            }
        });
        addSlot(new Slot(churn, MilkChurnBlockEntity.OUTPUT, MilkCanLayout.SLOT_X, MilkCanLayout.outputY(bodyY)) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
            }
        });
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInventory, 9 + row * 9 + col, 8 + col * 18, MilkCanLayout.playerY(bodyY) + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInventory, col, 8 + col * 18, MilkCanLayout.hotbarY(bodyY)));
        }
        addDataSlots(data);
    }

    public int bodyY() {
        return bodyY;
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
