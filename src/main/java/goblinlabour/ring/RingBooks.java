package goblinlabour.ring;

import goblinlabour.GoblinLabour;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;

import java.util.UUID;

/**
 * A ring's two book slots, stored in the {@code goblinlabour:ring_books} component on the ring item. Every change is
 * written back into the ring and hands the enchantments on to the tools of a crew that is out (see
 * {@link RingCrew.Session#refreshTools}).
 */
public class RingBooks extends SimpleContainer {
    public static final int SIZE = 2;

    private final Player player;
    private final UUID id;

    RingBooks(Player player, UUID id, ItemStack ring) {
        super(SIZE);
        this.player = player;
        this.id = id;
        ring.getOrDefault(GoblinLabour.RING_BOOKS, ItemContainerContents.EMPTY).copyInto(getItems());
    }

    @Override
    public void setChanged() {
        super.setChanged();
        ItemStack ring = RingInventory.find(player, id);
        if (ring != null) ring.set(GoblinLabour.RING_BOOKS, ItemContainerContents.fromItems(getItems()));
        RingCrew.Session crew = RingCrew.of(id);
        if (crew != null) crew.refreshTools();
    }

    @Override
    public boolean stillValid(Player viewer) {
        return viewer == player && RingInventory.find(player, id) != null;
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return stack.is(Items.ENCHANTED_BOOK);
    }

    @Override
    public int getMaxStackSize() {
        return 1;
    }
}
