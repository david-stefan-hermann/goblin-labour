package goblinlabour.menu;

import goblinlabour.GoblinLabour;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.DyeColor;

/**
 * The Goblin Chest's menu: vanilla's chest menu with the slots moved for the maw screen ({@code GoblinChestScreen}),
 * where the chest's slots sit in the open mouth between two rows of teeth and the player's inventory on the chest's
 * front below ({@link GoblinChestLayout}). It has to stay a {@link ChestMenu}: the chest counts a player as looking
 * inside, and keeps its lid open, only while that player's open menu is one. It also carries the chest's colour, which
 * the screen is painted in.
 */
public class GoblinChestMenu extends ChestMenu {
    /** What the client needs to build the screen: the row count (3 or 6) and the chest's colour. */
    public record OpeningData(int rows, DyeColor color) {
        public static final StreamCodec<ByteBuf, OpeningData> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, OpeningData::rows, DyeColor.STREAM_CODEC, OpeningData::color, OpeningData::new);
    }

    private final DyeColor color;

    public GoblinChestMenu(int containerId, Inventory inventory, Container container, int rows, DyeColor color) {
        super(GoblinLabour.GOBLIN_CHEST_MENU, containerId, inventory, container, rows);
        this.color = color;
        for (int i = 0; i < slots.size(); i++) {
            Slot slot = slots.get(i);
            int index = slot.getContainerSlot();
            int x, y;
            if (slot.container == inventory) {
                boolean hotbar = index < Inventory.getSelectionSize();
                int j = hotbar ? index : index - Inventory.getSelectionSize();
                x = 8 + j % 9 * 18;
                y = hotbar ? GoblinChestLayout.hotbarFrameY(rows) + 1 : GoblinChestLayout.playerFrameY(rows) + 1 + j / 9 * 18;
            } else {
                x = 8 + index % 9 * 18;
                y = GoblinChestLayout.CHEST_FRAME_Y + 1 + index / 9 * 18;
            }
            Slot moved = new Slot(slot.container, index, x, y);
            moved.index = i;
            slots.set(i, moved);
        }
    }

    /** Client side: the slots arrive through the normal menu sync, row count and colour with the opening data. */
    public static GoblinChestMenu client(int containerId, Inventory inventory, OpeningData data) {
        return new GoblinChestMenu(containerId, inventory, new SimpleContainer(data.rows() * 9), data.rows(), data.color());
    }

    public DyeColor color() {
        return color;
    }
}
