package goblinlabour.block;

import goblinlabour.GoblinLabour;
import goblinlabour.menu.MilkChurnMenu;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * The Milk Churn's tank (milk in mB, up to ten buckets) and its two slots: a milk bucket in the input is poured in and
 * comes out empty, an empty bucket in the input is filled and comes out as a milk bucket. Hoppers put buckets in from
 * the top and the sides and take them out below. Farmer goblins pour their milk in directly (see {@link #pour}).
 */
public class MilkChurnBlockEntity extends BlockEntity implements WorldlyContainer, ExtendedMenuProvider<BlockPos> {
    public static final int CAPACITY = 10_000;
    public static final int BUCKET = 1000;
    public static final int INPUT = 0;
    public static final int OUTPUT = 1;
    private static final int[] TOP_AND_SIDES = {INPUT};
    private static final int[] BOTTOM = {OUTPUT};

    private final NonNullList<ItemStack> items = NonNullList.withSize(2, ItemStack.EMPTY);
    private int milk;

    /** Milk and capacity for the screen's gauge. */
    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return index == 0 ? milk : CAPACITY;
        }

        @Override
        public void set(int index, int value) {
            if (index == 0) milk = value;
        }

        @Override
        public int getCount() {
            return 2;
        }
    };

    public MilkChurnBlockEntity(BlockPos pos, BlockState state) {
        super(GoblinLabour.MILK_CHURN_BLOCK_ENTITY, pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, MilkChurnBlockEntity churn) {
        churn.process();
    }

    private void process() {
        ItemStack in = items.get(INPUT);
        ItemStack out = items.get(OUTPUT);
        if (in.is(Items.MILK_BUCKET) && milk + BUCKET <= CAPACITY
                && (out.isEmpty() || out.is(Items.BUCKET) && out.getCount() < out.getMaxStackSize())) {
            milk += BUCKET;
            in.shrink(1);
            if (out.isEmpty()) items.set(OUTPUT, new ItemStack(Items.BUCKET));
            else out.grow(1);
            setChanged();
        } else if (in.is(Items.BUCKET) && milk >= BUCKET && out.isEmpty()) {
            milk -= BUCKET;
            in.shrink(1);
            items.set(OUTPUT, new ItemStack(Items.MILK_BUCKET));
            setChanged();
        }
    }

    public int getMilk() {
        return milk;
    }

    public void setMilk(int milk) {
        this.milk = Math.clamp(milk, 0, CAPACITY);
        setChanged();
    }

    /** How many whole buckets still fit. */
    public int room() {
        return (CAPACITY - milk) / BUCKET;
    }

    /** Pours up to {@code buckets} milk buckets in; returns how many went in (their buckets are used up). */
    public int pour(int buckets) {
        int poured = Math.min(buckets, room());
        if (poured <= 0) return 0;
        milk += poured * BUCKET;
        setChanged();
        return poured;
    }

    /** Fills an empty bucket from the tank; false when there is not a bucket's worth of milk. */
    public boolean drawBucket() {
        if (milk < BUCKET) return false;
        milk -= BUCKET;
        setChanged();
        return true;
    }

    /** Comparator output: 0 empty, 1 to 15 by the fill level. */
    public int signal() {
        return milk == 0 ? 0 : 1 + milk * 14 / CAPACITY;
    }

    // ---- menu ----------------------------------------------------------------------------------------------------

    @Override
    public BlockPos getScreenOpeningData(ServerPlayer player) {
        return worldPosition;
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.goblinlabour.milk_churn");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new MilkChurnMenu(containerId, playerInventory, this, data);
    }

    // ---- container -----------------------------------------------------------------------------------------------

    @Override
    public int getContainerSize() {
        return items.size();
    }

    @Override
    public boolean isEmpty() {
        return items.stream().allMatch(ItemStack::isEmpty);
    }

    @Override
    public ItemStack getItem(int slot) {
        return items.get(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int count) {
        ItemStack removed = ContainerHelper.removeItem(items, slot, count);
        if (!removed.isEmpty()) setChanged();
        return removed;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return ContainerHelper.takeItem(items, slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        items.set(slot, stack);
        stack.limitSize(getMaxStackSize(stack));
        setChanged();
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return slot == INPUT && (stack.is(Items.BUCKET) || stack.is(Items.MILK_BUCKET));
    }

    @Override
    public boolean stillValid(Player player) {
        return Container.stillValidBlockEntity(this, player);
    }

    @Override
    public void clearContent() {
        items.clear();
    }

    @Override
    public int[] getSlotsForFace(Direction side) {
        return side == Direction.DOWN ? BOTTOM : TOP_AND_SIDES;
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, Direction side) {
        return canPlaceItem(slot, stack);
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
        return slot == OUTPUT;
    }

    // ---- persistence ---------------------------------------------------------------------------------------------

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level != null) Containers.dropContents(level, pos, items);
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        out.putInt("milk", milk);
        ContainerHelper.saveAllItems(out, items);
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        milk = Math.clamp(in.getIntOr("milk", 0), 0, CAPACITY);
        items.clear();
        ContainerHelper.loadAllItems(in, items);
    }
}
