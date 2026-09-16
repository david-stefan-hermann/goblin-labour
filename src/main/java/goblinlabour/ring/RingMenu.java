package goblinlabour.ring;

import goblinlabour.GoblinLabour;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * The ring screen: the ring's loot as a double chest (slots 0 to 53), a side panel on the left with a crew goblin's
 * portrait and two slots for enchanted books (54 and 55), then the player's inventory. The server refuses every
 * click that would move the open ring itself or put a container item (another ring, a shulker box) into the loot.
 */
public class RingMenu extends AbstractContainerMenu {
    public static final int WIDTH = 176;
    public static final int LOOT_Y = 18;
    public static final int ROWS = 6;
    public static final int PLAYER_Y = LOOT_Y + ROWS * 18 + 14;
    public static final int HOTBAR_Y = PLAYER_Y + 58;
    public static final int HEIGHT = HOTBAR_Y + 24;
    /** The side panel, left of the main panel: portrait on top, the two book slots below. */
    public static final int SIDE_X = -68;
    public static final int SIDE_WIDTH = 66;
    public static final int SIDE_HEIGHT = 104;
    public static final int BOOKS_Y = 80;
    public static final int BOOKS_END = RingInventory.SIZE + RingBooks.SIZE;

    private final Container ring;
    private final Container books;
    private final UUID ringId;

    /** Client side: placeholder containers, the contents arrive through the normal slot sync. */
    public RingMenu(int containerId, Inventory playerInventory, RingMenuData data) {
        this(containerId, playerInventory, new SimpleContainer(RingInventory.SIZE), new SimpleContainer(RingBooks.SIZE), data.ringId());
    }

    public RingMenu(int containerId, Inventory playerInventory, Container ring, Container books, UUID ringId) {
        super(GoblinLabour.RING_MENU, containerId);
        this.ring = ring;
        this.books = books;
        this.ringId = ringId;
        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(ring, row * 9 + col, 8 + col * 18, LOOT_Y + row * 18));
            }
        }
        for (int i = 0; i < RingBooks.SIZE; i++) {
            addSlot(new BookSlot(books, i, SIDE_X + 14 + i * 22, BOOKS_Y));
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInventory, 9 + row * 9 + col, 8 + col * 18, PLAYER_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInventory, col, 8 + col * 18, HOTBAR_Y));
        }
    }

    /** The ring's loot, when this is the server side of the screen (see {@link RingInventory#open}). */
    @Nullable
    public RingContainer ring() {
        return ring instanceof RingContainer container ? container : null;
    }

    /** The ring's book slots, when this is the server side of the screen (see {@link RingInventory#books}). */
    @Nullable
    public RingBooks books() {
        return books instanceof RingBooks container ? container : null;
    }

    public UUID ringId() {
        return ringId;
    }

    public boolean isBookSlot(Slot slot) {
        return slot instanceof BookSlot;
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
            if (slotIndex >= BOOKS_END && input == ContainerInput.QUICK_MOVE && !fits(inSlot)) return true;
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
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack result = stack.copy();
        int playerEnd = BOOKS_END + 36;
        if (index < BOOKS_END) {
            if (!moveItemStackTo(stack, BOOKS_END, playerEnd, true)) return ItemStack.EMPTY;
        } else {
            // from the player: an enchanted book into a free book slot first, everything else into the loot
            boolean moved = stack.is(Items.ENCHANTED_BOOK) && moveItemStackTo(stack, RingInventory.SIZE, BOOKS_END, false);
            if (!moved && !moveItemStackTo(stack, 0, RingInventory.SIZE, false)) return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.set(ItemStack.EMPTY);
        else slot.setChanged();
        return result;
    }

    @Override
    public boolean stillValid(Player player) {
        return ring.stillValid(player);
    }

    /** Takes one enchanted book. */
    private static final class BookSlot extends Slot {
        BookSlot(Container container, int index, int x, int y) {
            super(container, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return stack.is(Items.ENCHANTED_BOOK);
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }
    }
}
