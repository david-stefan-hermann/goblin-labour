package goblinlabour.item;

import goblinlabour.GoblinLabour;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

import java.util.function.Consumer;

/** The smelted meat pack. Right-click a Goblin Straw Bed with it to spawn (or restore) a goblin. */
public class GoblinBlankItem extends Item {
    public GoblinBlankItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        GoblinData data = stack.get(GoblinLabour.GOBLIN_DATA);
        if (data == null) {
            tooltip.accept(Component.translatable("goblinlabour.blank.fresh").withStyle(ChatFormatting.GRAY));
        } else {
            tooltip.accept(Component.translatable("goblinlabour.blank.name", data.name()).withStyle(ChatFormatting.GREEN));
            tooltip.accept(Component.translatable("goblinlabour.blank.tools", data.toolCount()).withStyle(ChatFormatting.GRAY));
        }
        tooltip.accept(Component.translatable("goblinlabour.blank.hint").withStyle(ChatFormatting.DARK_GRAY));
    }
}
