package goblinlabour.item;

import goblinlabour.ring.RingContainer;
import goblinlabour.ring.RingCrew;
import goblinlabour.ring.RingInventory;
import goblinlabour.ring.RingMenu;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

import java.util.UUID;
import java.util.function.Consumer;

/**
 * The Goblin Ring. Right-click calls a mining crew of three goblins for five minutes (or sends it home early, see
 * {@link RingCrew}); sneak + right-click opens the ring's own double chest of loot (see {@link RingInventory}).
 */
public class GoblinRingItem extends Item {
    private static final int USE_COOLDOWN = 20;

    public GoblinRingItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer serverPlayer)) return InteractionResult.SUCCESS;
        ItemStack ring = player.getItemInHand(hand);
        if (player.isSecondaryUseActive()) {
            openMenu(serverPlayer, ring);
        } else {
            RingCrew.toggle(serverLevel, serverPlayer, ring);
            player.getCooldowns().addCooldown(ring, USE_COOLDOWN);
        }
        return InteractionResult.SUCCESS;
    }

    public static void openMenu(ServerPlayer player, ItemStack ring) {
        UUID id = RingInventory.ensureId(ring);
        RingContainer container = RingInventory.open(player, id);
        if (container == null) return;
        player.openMenu(new SimpleMenuProvider((containerId, inventory, viewer) -> new RingMenu(containerId, inventory, container, id),
                ring.getHoverName()));
    }

    /** A ring burnt in lava or blown up spills its loot, like a shulker box. */
    @Override
    public void onDestroyed(ItemEntity entity) {
        ItemContainerContents contents = entity.getItem().set(DataComponents.CONTAINER, ItemContainerContents.EMPTY);
        if (contents != null) ItemUtils.onContainerDestroyed(entity, contents.nonEmptyItemCopyStream());
    }

    @Override
    public boolean canFitInsideContainerItems() {
        return false;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        tooltip.accept(Component.translatable("goblinlabour.ring.hint1").withStyle(ChatFormatting.GRAY));
        tooltip.accept(Component.translatable("goblinlabour.ring.hint2").withStyle(ChatFormatting.GRAY));
    }
}
