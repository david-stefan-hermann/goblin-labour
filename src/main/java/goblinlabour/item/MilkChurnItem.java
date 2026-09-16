package goblinlabour.item;

import goblinlabour.GoblinLabour;
import goblinlabour.block.MilkChurnBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.block.Block;

import java.util.function.Consumer;

/** The Milk Churn as an item: a broken churn keeps its milk (see {@link GoblinLabour#MILK}) and shows it here. */
public class MilkChurnItem extends BlockItem {
    public MilkChurnItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        int milk = stack.getOrDefault(GoblinLabour.MILK, 0);
        if (milk > 0) {
            tooltip.accept(Component.translatable("goblinlabour.churn.milk", milk, MilkChurnBlockEntity.CAPACITY)
                    .withStyle(ChatFormatting.GRAY));
        }
    }
}
