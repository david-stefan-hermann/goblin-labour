package goblinlabour.item;

import goblinlabour.job.Assignment;
import goblinlabour.menu.StaffMenuData;
import goblinlabour.menu.StaffMenuProvider;
import goblinlabour.staff.StaffSelection;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.function.Consumer;

/**
 * The Goblin Staff. Left-click goblins to select them (they glow and follow). Right-click a block to give the
 * selected goblins an order at once with default settings; sneak + right-click opens the settings screen first.
 * Floor (top face) = dig down, ceiling (bottom face) = dig up, wall = tunnel into the wall.
 */
public class GoblinStaffItem extends Item {
    public static final int DEFAULT_SHAFT_WIDTH = 3;
    public static final int DEFAULT_TUNNEL_WIDTH = 3;
    public static final int DEFAULT_TUNNEL_HEIGHT = 3;
    /** Two chunks. */
    public static final int DEFAULT_TUNNEL_LENGTH = 32;

    public GoblinStaffItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (!(context.getLevel() instanceof ServerLevel level)) return InteractionResult.SUCCESS;
        Player player = context.getPlayer();
        if (player == null) return InteractionResult.PASS;
        if (StaffSelection.count(level, player) == 0) {
            player.sendSystemMessage(Component.translatable("goblinlabour.staff.select_first"));
            return InteractionResult.SUCCESS;
        }
        BlockPos pos = context.getClickedPos();
        Direction face = context.getClickedFace();
        Assignment.Kind kind = face == Direction.UP ? Assignment.Kind.DIG_DOWN
                : face == Direction.DOWN ? Assignment.Kind.DIG_UP : Assignment.Kind.TUNNEL;
        int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING, pos.getX(), pos.getZ()) - 1;
        StaffMenuData data = new StaffMenuData(pos, face, kind, StaffSelection.count(level, player),
                level.getMinY(), surfaceY, level.getMaxY());
        if (player.isSecondaryUseActive()) {
            player.openMenu(new StaffMenuProvider(data));
        } else {
            Assignment assignment = defaultAssignment(data);
            player.sendSystemMessage(StaffSelection.assign(level, player, assignment));
        }
        return InteractionResult.SUCCESS;
    }

    /** What a plain right-click starts: 3x3 shaft with stairs to bedrock / the surface, or a 3x3 tunnel of 32. */
    public static Assignment defaultAssignment(StaffMenuData data) {
        return switch (data.kind()) {
            case DIG_DOWN -> new Assignment(Assignment.Kind.DIG_DOWN, data.pos(), Direction.NORTH, DEFAULT_SHAFT_WIDTH, 0, 0, data.minY(), true);
            case DIG_UP -> new Assignment(Assignment.Kind.DIG_UP, data.pos(), Direction.NORTH, DEFAULT_SHAFT_WIDTH, 0, 0,
                    Math.max(data.surfaceY(), data.pos().getY()), true);
            case TUNNEL -> new Assignment(Assignment.Kind.TUNNEL, data.pos(), data.face().getOpposite(), DEFAULT_TUNNEL_WIDTH,
                    DEFAULT_TUNNEL_HEIGHT, DEFAULT_TUNNEL_LENGTH, data.pos().getY(), false);
        };
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        tooltip.accept(Component.translatable("goblinlabour.staff.hint1").withStyle(ChatFormatting.GRAY));
        tooltip.accept(Component.translatable("goblinlabour.staff.hint2").withStyle(ChatFormatting.GRAY));
        tooltip.accept(Component.translatable("goblinlabour.staff.hint3").withStyle(ChatFormatting.DARK_GRAY));
    }
}
