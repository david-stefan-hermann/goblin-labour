package goblinlabour.menu;

import net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.DyeColor;

/**
 * Opens a single or double Goblin Chest in the maw screen; the client learns the row count and the chest's colour
 * with the opening data.
 */
public record GoblinChestMenuProvider(Container container, int rows, DyeColor color, Component title)
        implements ExtendedMenuProvider<GoblinChestMenu.OpeningData> {
    @Override
    public GoblinChestMenu.OpeningData getScreenOpeningData(ServerPlayer player) {
        return new GoblinChestMenu.OpeningData(rows, color);
    }

    @Override
    public Component getDisplayName() {
        return title;
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new GoblinChestMenu(containerId, inventory, container, rows, color);
    }
}
