package goblinlabour.menu;

import goblinlabour.entity.GoblinEntity;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Sent to the client when a goblin inventory opens: which entity it belongs to (for the portrait), how many storage
 * rows to show and whether the second row (the collector's backpack) takes items.
 */
public record GoblinMenuData(int entityId, int storageRows, boolean backpack) {
    public static final StreamCodec<RegistryFriendlyByteBuf, GoblinMenuData> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, GoblinMenuData::entityId,
            ByteBufCodecs.VAR_INT, GoblinMenuData::storageRows,
            ByteBufCodecs.BOOL, GoblinMenuData::backpack,
            GoblinMenuData::new
    );

    /** Two rows for a collector, and also for a goblin that left the job with items still in the backpack row. */
    public static GoblinMenuData of(GoblinEntity goblin) {
        boolean backpack = goblin.hasBackpack();
        boolean leftovers = false;
        int start = GoblinEntity.HOTBAR_SIZE + GoblinEntity.STORAGE_SIZE;
        for (int i = start; i < GoblinEntity.INVENTORY_SIZE; i++) {
            if (!goblin.getInventory().getItem(i).isEmpty()) leftovers = true;
        }
        return new GoblinMenuData(goblin.getId(), backpack || leftovers ? 2 : 1, backpack);
    }
}
