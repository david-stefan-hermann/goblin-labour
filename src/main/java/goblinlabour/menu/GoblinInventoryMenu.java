package goblinlabour.menu;

import goblinlabour.GoblinLabour;
import goblinlabour.entity.GoblinEntity;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * The goblin's 18 slots laid out like a player inventory: one row of storage, below it the nine tool slots
 * ("hotbar"), then the player's own inventory. Only tools go into the tool row. The portrait lives in a side panel
 * on the left (see GoblinInventoryScreen).
 */
public class GoblinInventoryMenu extends AbstractContainerMenu {
    public static final int WIDTH = 176;
    public static final int HEIGHT = 156;
    public static final int STORAGE_Y = 18;
    public static final int TOOLS_Y = 48;
    public static final int PLAYER_Y = 74;
    public static final int PLAYER_HOTBAR_Y = 132;
    public static final int SIDE_X = -68;
    public static final int SIDE_WIDTH = 66;
    public static final int SIDE_HEIGHT = 70;

    public final GoblinMenuData data;
    @Nullable private final GoblinEntity goblin;
    private final Container inventory;

    /** Client side: the container is a placeholder, contents arrive through the normal slot sync. */
    public GoblinInventoryMenu(int containerId, Inventory playerInventory, GoblinMenuData data) {
        this(containerId, playerInventory, data, null, new SimpleContainer(GoblinEntity.INVENTORY_SIZE));
    }

    public GoblinInventoryMenu(int containerId, Inventory playerInventory, GoblinMenuData data,
                               @Nullable GoblinEntity goblin, Container inventory) {
        super(GoblinLabour.GOBLIN_MENU, containerId);
        this.data = data;
        this.goblin = goblin;
        this.inventory = inventory;

        for (int col = 0; col < GoblinEntity.INVENTORY_SIZE - GoblinEntity.HOTBAR_SIZE; col++) {
            addSlot(new Slot(inventory, GoblinEntity.HOTBAR_SIZE + col, 8 + col * 18, STORAGE_Y));
        }
        for (int col = 0; col < GoblinEntity.HOTBAR_SIZE; col++) {
            addSlot(new ToolSlot(inventory, col, 8 + col * 18, TOOLS_Y));
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInventory, 9 + row * 9 + col, 8 + col * 18, PLAYER_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInventory, col, 8 + col * 18, PLAYER_HOTBAR_Y));
        }
    }

    @Nullable
    public GoblinEntity goblin() {
        return goblin;
    }

    public static boolean isTool(ItemStack stack) {
        return stack.is(ItemTags.PICKAXES) || stack.is(ItemTags.AXES) || stack.is(ItemTags.SHOVELS)
                || stack.is(ItemTags.HOES) || stack.is(ItemTags.SWORDS);
    }

    public boolean isToolSlot(Slot slot) {
        return slot instanceof ToolSlot;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack result = stack.copy();
        int storageEnd = GoblinEntity.INVENTORY_SIZE - GoblinEntity.HOTBAR_SIZE;
        int toolsEnd = storageEnd + GoblinEntity.HOTBAR_SIZE;
        int playerEnd = toolsEnd + 36;
        if (index < toolsEnd) {
            if (!moveItemStackTo(stack, toolsEnd, playerEnd, true)) return ItemStack.EMPTY;
        } else {
            boolean moved = isTool(stack) && moveItemStackTo(stack, storageEnd, toolsEnd, false);
            if (!moved && !moveItemStackTo(stack, 0, storageEnd, false)) return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.set(ItemStack.EMPTY);
        else slot.setChanged();
        return result;
    }

    @Override
    public boolean stillValid(Player player) {
        return goblin == null || (goblin.isAlive() && player.distanceToSqr(goblin) <= 64.0);
    }

    private static final class ToolSlot extends Slot {
        ToolSlot(Container container, int index, int x, int y) {
            super(container, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return isTool(stack);
        }
    }
}
