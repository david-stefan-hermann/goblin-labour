package goblinlabour.menu;

import net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;

public record StaffMenuProvider(StaffMenuData data) implements ExtendedMenuProvider<StaffMenuData> {
    @Override
    public StaffMenuData getScreenOpeningData(ServerPlayer player) {
        return data;
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable(data.kind().job().translationKey());
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new StaffMenu(containerId, playerInventory, data);
    }
}
