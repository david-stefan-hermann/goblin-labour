package goblinlabour.ring;

import goblinlabour.GoblinLabour;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Where a Goblin Ring keeps its double chest of loot: the vanilla {@code minecraft:container} component on the ring
 * item itself, so the loot travels with the ring. A ring gets an id ({@code goblinlabour:ring_id}) the first time it
 * is used; everything that reads or writes the loot looks the ring up by that id in its holder's inventory, so a ring
 * moved to another slot keeps working and a ring that left the inventory is never written to.
 */
public final class RingInventory {
    public static final int SIZE = 54;

    private RingInventory() {
    }

    public static UUID ensureId(ItemStack ring) {
        UUID id = ring.get(GoblinLabour.RING_ID);
        if (id == null) {
            id = UUID.randomUUID();
            ring.set(GoblinLabour.RING_ID, id);
        }
        return id;
    }

    public static boolean isRing(ItemStack stack, UUID id) {
        return stack.is(GoblinLabour.GOBLIN_RING) && id.equals(stack.get(GoblinLabour.RING_ID));
    }

    /** The ring with this id in the player's inventory (hands and armour slots included) or on the cursor, or null. */
    @Nullable
    public static ItemStack find(Player player, UUID id) {
        Inventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (isRing(stack, id)) return stack;
        }
        ItemStack carried = player.containerMenu.getCarried();
        return isRing(carried, id) ? carried : null;
    }

    /** Like {@link #open} for the ring's two book slots. */
    @Nullable
    public static RingBooks books(Player player, UUID id) {
        if (player.containerMenu instanceof RingMenu menu && menu.ringId().equals(id) && menu.books() != null) return menu.books();
        ItemStack ring = find(player, id);
        return ring == null ? null : new RingBooks(player, id, ring);
    }

    /**
     * The ring's loot for this player: the container of the ring screen if the player has it open (so goblins and
     * player see the same stacks), otherwise a fresh view that writes every change back into the item. Null when the
     * player does not carry the ring.
     */
    @Nullable
    public static RingContainer open(Player player, UUID id) {
        if (player.containerMenu instanceof RingMenu menu && menu.ringId().equals(id)) return menu.ring();
        ItemStack ring = find(player, id);
        return ring == null ? null : new RingContainer(player, id, ring);
    }
}
