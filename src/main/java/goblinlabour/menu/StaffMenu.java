package goblinlabour.menu;

import goblinlabour.GoblinLabour;
import goblinlabour.job.Assignment;
import goblinlabour.staff.StaffSelection;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

/**
 * Settings screen of a staff order. Everything is edited client-side; the "Start" button sends the whole order
 * packed into one menu-button id (width 3 bits, height 3 bits, length 7 bits, stairs 1 bit, targetY 10 bits with an
 * offset of 128, plus a marker bit), so no custom packet is needed.
 */
public class StaffMenu extends AbstractContainerMenu {
    public static final int WIDTH = 176;
    public static final int HEIGHT = 130;
    public static final int START_FLAG = 1 << 24;
    public static final int Y_OFFSET = 128;

    public final StaffMenuData data;

    public StaffMenu(int containerId, Inventory playerInventory, StaffMenuData data) {
        super(GoblinLabour.STAFF_MENU, containerId);
        this.data = data;
    }

    public static int encode(int width, int height, int length, boolean stairs, int targetY) {
        int y = Mth.clamp(targetY + Y_OFFSET, 0, 1023);
        return START_FLAG | (width & 7) | ((height & 7) << 3) | ((length & 127) << 6) | ((stairs ? 1 : 0) << 13) | (y << 14);
    }

    public Assignment decode(int id) {
        int width = id & 7;
        int height = (id >> 3) & 7;
        int length = (id >> 6) & 127;
        boolean stairs = ((id >> 13) & 1) != 0;
        int targetY = ((id >> 14) & 1023) - Y_OFFSET;
        return switch (data.kind()) {
            case DIG_DOWN -> new Assignment(Assignment.Kind.DIG_DOWN, data.pos(), Direction.NORTH, oddWidth(width), 0, 0,
                    Mth.clamp(targetY, data.minY(), data.pos().getY()), stairs);
            case DIG_UP -> new Assignment(Assignment.Kind.DIG_UP, data.pos(), Direction.NORTH, oddWidth(width), 0, 0,
                    Mth.clamp(targetY, data.pos().getY(), data.maxY()), stairs);
            case TUNNEL -> new Assignment(Assignment.Kind.TUNNEL, data.pos(), data.face().getOpposite(),
                    Mth.clamp(width, 1, 5), Mth.clamp(height, 2, 5), Mth.clamp(length, 4, 96), data.pos().getY(), false);
        };
    }

    private static int oddWidth(int width) {
        int w = Mth.clamp(width, 1, 5);
        return w % 2 == 0 ? w + 1 : w;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if ((id & START_FLAG) == 0) return false;
        if (player.level() instanceof ServerLevel level) {
            player.sendSystemMessage(StaffSelection.assign(level, player, decode(id)));
        }
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return player.getMainHandItem().is(GoblinLabour.GOBLIN_STAFF) || player.getOffhandItem().is(GoblinLabour.GOBLIN_STAFF);
    }
}
