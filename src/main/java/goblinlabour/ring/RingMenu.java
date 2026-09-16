package goblinlabour.ring;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;

import java.util.UUID;

/**
 * The ring screen: a vanilla double chest screen over the ring's loot. The client only knows a plain 9x6 chest menu;
 * the server refuses every click that would move the open ring itself or put a container item (another ring, a
 * shulker box) into the loot slots.
 */
public class RingMenu extends ChestMenu {
    private final RingContainer ring;
    private final UUID ringId;

    public RingMenu(int containerId, Inventory playerInventory, RingContainer ring, UUID ringId) {
        super(MenuType.GENERIC_9x6, containerId, playerInventory, ring, 6);
        this.ring = ring;
        this.ringId = ringId;
    }

    public RingContainer ring() {
        return ring;
    }

    public UUID ringId() {
        return ringId;
    }

    @Override
    public void clicked(int slotIndex, int button, ContainerInput input, Player player) {
        if (refused(slotIndex, button, input, player)) {
            sendAllDataToRemote(); // undo what the client already showed
            return;
        }
        super.clicked(slotIndex, button, input, player);
    }

    private boolean refused(int slotIndex, int button, ContainerInput input, Player player) {
        boolean lootSlot = slotIndex >= 0 && slotIndex < RingInventory.SIZE;
        ItemStack carried = getCarried();
        if (input == ContainerInput.QUICK_CRAFT && !fits(carried)) return true; // dragging a container item around
        if (slotIndex >= 0 && slotIndex < slots.size()) {
            ItemStack inSlot = slots.get(slotIndex).getItem();
            if (RingInventory.isRing(inSlot, ringId)) return true; // the open ring stays where it is
            if (!lootSlot && input == ContainerInput.QUICK_MOVE && !fits(inSlot)) return true;
        }
        if (lootSlot && input == ContainerInput.PICKUP && !fits(carried)) return true;
        if (input == ContainerInput.SWAP) {
            ItemStack swapped = player.getInventory().getItem(button); // hotbar 0-8, offhand 40
            if (RingInventory.isRing(swapped, ringId)) return true;
            return lootSlot && !fits(swapped);
        }
        return false;
    }

    private static boolean fits(ItemStack stack) {
        return stack.isEmpty() || stack.getItem().canFitInsideContainerItems();
    }

    @Override
    public boolean stillValid(Player player) {
        return ring.stillValid(player);
    }
}
