package goblinlabour.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * The Goblin Handbook: a short in-game guide to the mod, shown in the vanilla book screen. The pages are
 * translatable ({@code goblinlabour.book.page.N}), so they follow the player's language. The client registers the
 * screen opener; on the server the item does nothing.
 */
public class GoblinHandbookItem extends Item {
    public static final int PAGE_COUNT = 11;
    private static Consumer<Player> opener = player -> { };

    public GoblinHandbookItem(Properties properties) {
        super(properties);
    }

    /** Set by the client entry point: opens the book screen for the player. */
    public static void setOpener(Consumer<Player> clientOpener) {
        opener = clientOpener;
    }

    public static List<Component> pages() {
        List<Component> pages = new ArrayList<>(PAGE_COUNT);
        for (int i = 1; i <= PAGE_COUNT; i++) pages.add(Component.translatable("goblinlabour.book.page." + i));
        return pages;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide()) opener.accept(player);
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        tooltip.accept(Component.translatable("goblinlabour.handbook.hint").withStyle(ChatFormatting.GRAY));
    }
}
