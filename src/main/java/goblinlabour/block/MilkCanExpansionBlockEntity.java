package goblinlabour.block;

import goblinlabour.GoblinLabour;
import goblinlabour.menu.MilkCanLayout;
import goblinlabour.menu.MilkChurnMenu;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidConstants;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.base.SingleVariantStorage;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentMap;
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
 * The Milk Can Expansion's tank: twenty buckets of milk, filled by a Milk Can standing on it (see
 * {@link MilkChurnBlockEntity#serverTick}) and by milk buckets, emptied into buckets. It is a fluid storage in
 * Fabric's transfer API ({@code FluidStorage.SIDED}, registered in {@link GoblinLabour}), so pipes and Refined
 * Storage importers take the milk out, and put milk in, from every side. The two bucket slots work like the can's:
 * a milk bucket in the input is poured in and comes out empty, an empty bucket is filled; hoppers put buckets in from
 * the top and the sides and take them out below.
 *
 * <p>The tank counts in droplets like the transfer API (81 per mB, 81000 per bucket); the screen and the item show mB.
 */
public class MilkCanExpansionBlockEntity extends BlockEntity implements WorldlyContainer, ExtendedMenuProvider<BlockPos> {
    public static final int CAPACITY_BUCKETS = 20;
    /** In mB, like the Milk Can's. */
    public static final int CAPACITY = CAPACITY_BUCKETS * MilkChurnBlockEntity.BUCKET;
    private static final long DROPLETS_PER_MB = FluidConstants.BUCKET / MilkChurnBlockEntity.BUCKET;
    private static final int[] TOP_AND_SIDES = {MilkChurnBlockEntity.INPUT};
    private static final int[] BOTTOM = {MilkChurnBlockEntity.OUTPUT};

    private final NonNullList<ItemStack> items = NonNullList.withSize(2, ItemStack.EMPTY);

    /** Only milk goes in; whatever takes milk out through the transfer API marks the block as changed. */
    public final SingleVariantStorage<FluidVariant> tank = new SingleVariantStorage<>() {
        @Override
        protected FluidVariant getBlankVariant() {
            return FluidVariant.blank();
        }

        @Override
        protected long getCapacity(FluidVariant variant) {
            return CAPACITY_BUCKETS * FluidConstants.BUCKET;
        }

        @Override
        protected boolean canInsert(FluidVariant variant) {
            return variant.isOf(GoblinLabour.MILK_FLUID);
        }

        @Override
        protected void onFinalCommit() {
            setChanged();
        }
    };

    /** Milk and capacity in mB for the screen's gauge (menu data travels as shorts, so not in droplets). */
    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return index == 0 ? getMilk() : CAPACITY;
        }

        @Override
        public void set(int index, int value) {
        }

        @Override
        public int getCount() {
            return 2;
        }
    };

    public MilkCanExpansionBlockEntity(BlockPos pos, BlockState state) {
        super(GoblinLabour.MILK_CAN_EXPANSION_BLOCK_ENTITY, pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, MilkCanExpansionBlockEntity expansion) {
        expansion.process();
    }

    private void process() {
        ItemStack in = items.get(MilkChurnBlockEntity.INPUT);
        ItemStack out = items.get(MilkChurnBlockEntity.OUTPUT);
        if (in.is(Items.MILK_BUCKET) && room() >= MilkChurnBlockEntity.BUCKET
                && (out.isEmpty() || out.is(Items.BUCKET) && out.getCount() < out.getMaxStackSize())) {
            fill(MilkChurnBlockEntity.BUCKET);
            in.shrink(1);
            if (out.isEmpty()) items.set(MilkChurnBlockEntity.OUTPUT, new ItemStack(Items.BUCKET));
            else out.grow(1);
            setChanged();
        } else if (in.is(Items.BUCKET) && getMilk() >= MilkChurnBlockEntity.BUCKET && out.isEmpty()) {
            drain(MilkChurnBlockEntity.BUCKET);
            in.shrink(1);
            items.set(MilkChurnBlockEntity.OUTPUT, new ItemStack(Items.MILK_BUCKET));
            setChanged();
        }
    }

    /** The milk in whole mB. */
    public int getMilk() {
        return (int) (tank.amount / DROPLETS_PER_MB);
    }

    /** Sets the milk (mB), for the dev command and a placed item. */
    public void setMilk(int milk) {
        long droplets = Math.clamp(milk, 0, CAPACITY) * DROPLETS_PER_MB;
        tank.variant = droplets > 0 ? FluidVariant.of(GoblinLabour.MILK_FLUID) : FluidVariant.blank();
        tank.amount = droplets;
        setChanged();
    }

    /** How much milk (mB) still fits. */
    public int room() {
        return (int) ((tank.getCapacity() - tank.amount) / DROPLETS_PER_MB);
    }

    /** Pours up to {@code milk} mB in and returns how much went in. */
    public int fill(int milk) {
        int poured = Math.min(milk, room());
        if (poured <= 0) return 0;
        try (Transaction transaction = Transaction.openOuter()) {
            tank.insert(FluidVariant.of(GoblinLabour.MILK_FLUID), poured * DROPLETS_PER_MB, transaction);
            transaction.commit();
        }
        return poured;
    }

    /** Takes up to {@code milk} mB out and returns how much came out. */
    public int drain(int milk) {
        int drawn = Math.min(milk, getMilk());
        if (drawn <= 0) return 0;
        try (Transaction transaction = Transaction.openOuter()) {
            tank.extract(FluidVariant.of(GoblinLabour.MILK_FLUID), drawn * DROPLETS_PER_MB, transaction);
            transaction.commit();
        }
        return drawn;
    }

    /** Comparator output: 0 empty, 1 to 15 by the fill level. */
    public int signal() {
        return tank.amount == 0 ? 0 : 1 + (int) (tank.amount * 14 / tank.getCapacity());
    }

    // ---- menu ----------------------------------------------------------------------------------------------------

    @Override
    public BlockPos getScreenOpeningData(ServerPlayer player) {
        return worldPosition;
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.goblinlabour.milk_can_expansion");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new MilkChurnMenu(GoblinLabour.MILK_CAN_EXPANSION_MENU, containerId, playerInventory, this, data,
                MilkCanLayout.EXPANSION_BODY_Y);
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
        return slot == MilkChurnBlockEntity.INPUT && (stack.is(Items.BUCKET) || stack.is(Items.MILK_BUCKET));
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
        return slot == MilkChurnBlockEntity.OUTPUT;
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
        SingleVariantStorage.writeValue(tank, FluidVariant.CODEC, out);
        ContainerHelper.saveAllItems(out, items);
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        SingleVariantStorage.readValue(tank, FluidVariant.CODEC, FluidVariant::blank, in);
        tank.amount = Math.clamp(tank.amount, 0, tank.getCapacity());
        items.clear();
        ContainerHelper.loadAllItems(in, items);
    }

    // The milk travels with the item like the can's: the loot table copies it onto the drop, placing it brings it back.

    @Override
    protected void applyImplicitComponents(DataComponentGetter components) {
        super.applyImplicitComponents(components);
        setMilk(components.getOrDefault(GoblinLabour.MILK, 0));
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        if (getMilk() > 0) components.set(GoblinLabour.MILK, getMilk());
    }

    @Override
    public void removeComponentsFromTag(ValueOutput out) {
        super.removeComponentsFromTag(out);
        out.discard("variant");
        out.discard("amount");
    }
}
