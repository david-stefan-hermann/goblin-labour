package goblinlabour.menu;

import goblinlabour.block.GoblinBedBlockEntity;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;

public record GoblinBedMenuProvider(GoblinBedBlockEntity bed) implements ExtendedMenuProvider<GoblinBedMenuData> {
    private GoblinBedMenuData data() {
        return new GoblinBedMenuData(bed.getBlockPos(), bed.getGoblinName());
    }

    @Override
    public GoblinBedMenuData getScreenOpeningData(ServerPlayer player) {
        return data();
    }

    @Override
    public Component getDisplayName() {
        return bed.hasGoblin() ? Component.literal(bed.getGoblinName()) : Component.translatable("block.goblinlabour.goblin_straw_bed");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new GoblinBedMenu(containerId, playerInventory, data(), bed, GoblinBedMenu.liveData(bed));
    }
}
