package goblinlabour.ring;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;

import java.util.UUID;

/** A ring's 54 loot slots, loaded from the ring item; every change is written back into the ring (see {@link RingInventory}). */
public class RingContainer extends SimpleContainer {
    private final Player player;
    private final UUID id;

    RingContainer(Player player, UUID id, ItemStack ring) {
        super(RingInventory.SIZE);
        this.player = player;
        this.id = id;
        ring.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).copyInto(getItems());
    }

    @Override
    public void setChanged() {
        super.setChanged();
        ItemStack ring = RingInventory.find(player, id);
        if (ring != null) ring.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(getItems()));
    }

    @Override
    public boolean stillValid(Player viewer) {
        return viewer == player && RingInventory.find(player, id) != null;
    }

    /** Rings, shulker boxes and other container items do not go into a ring. */
    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return stack.getItem().canFitInsideContainerItems();
    }
}
