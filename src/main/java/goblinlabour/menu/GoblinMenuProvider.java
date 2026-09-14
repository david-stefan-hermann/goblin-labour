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
        return GoblinMenuData.of(goblin);
    }

    /** "Grubnak the Lumberjack": the name plus the trade the goblin's colour shows. */
    @Override
    public Component getDisplayName() {
        return Component.translatable("gui.goblinlabour.goblin_title", goblin.goblinName(),
                Component.translatable("goblinlabour.trade." + goblin.getStyle().getSerializedName()));
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new GoblinInventoryMenu(containerId, playerInventory, GoblinMenuData.of(goblin), goblin, goblin.getInventory());
    }
}
