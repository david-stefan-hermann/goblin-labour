package goblinlabour.item;

import goblinlabour.GoblinLabour;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.block.Block;

import java.util.function.Consumer;

/**
 * The Milk Can or the Milk Can Expansion as an item: a broken one keeps its milk (see {@link GoblinLabour#MILK}) and
 * shows it here against its capacity (mB).
 */
public class MilkChurnItem extends BlockItem {
    private final int capacity;

    public MilkChurnItem(Block block, int capacity, Properties properties) {
        super(block, properties);
        this.capacity = capacity;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        int milk = stack.getOrDefault(GoblinLabour.MILK, 0);
        if (milk > 0) {
            tooltip.accept(Component.translatable("goblinlabour.churn.milk", milk, capacity)
                    .withStyle(ChatFormatting.GRAY));
        }
    }
}
