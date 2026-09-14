package goblinlabour.menu;

import goblinlabour.entity.GoblinEntity;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;

public record GoblinMenuProvider(GoblinEntity goblin) implements ExtendedMenuProvider<GoblinMenuData> {
    @Override
    public GoblinMenuData getScreenOpeningData(ServerPlayer player) {
        return new GoblinMenuData(goblin.getId());
    }

    @Override
    public Component getDisplayName() {
        return Component.literal(goblin.goblinName());
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new GoblinInventoryMenu(containerId, playerInventory, new GoblinMenuData(goblin.getId()), goblin, goblin.getInventory());
    }
}
