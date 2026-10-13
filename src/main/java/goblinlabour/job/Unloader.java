package goblinlabour.job;

import goblinlabour.GoblinSounds;
import goblinlabour.GoblinSpeech;
import goblinlabour.block.MilkChurnBlockEntity;
import goblinlabour.entity.GoblinEntity;
import goblinlabour.home.HomeRegistry;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageUtil;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * A bed goblin's trip home with a full storage: milk buckets into a Milk Can of the flat first, everything else into
 * the flat's goblin chests, lifting the lid for a moment first. One per {@link JobRunner}.
 */
final class Unloader {
    private static final double CHEST_REACH_SQ = 2.5 * 2.5;
    private static final int CHEST_OPEN_TICKS = 16;

    /** What a deposit tick came to. */
    enum Outcome { BUSY, NOTHING_FITS, NOTHING_TO_DO }

    private final GoblinEntity goblin;
    private final Reach reach;
    /** The runner's skipped blocks: a chest the goblin could not walk to is left alone for a while. */
    private final Map<BlockPos, Long> skipped;
    @Nullable private BlockPos chestTarget;
    /** The chest the goblin holds open while unloading, and when it lets go. */
    @Nullable private BlockPos openChest;
    private long chestCloseAt;
    /** Something went into a can or chest on this trip: from then on the trip is finished, not dropped. */
    private boolean unloaded;

    Unloader(GoblinEntity goblin, Reach reach, Map<BlockPos, Long> skipped) {
        this.goblin = goblin;
        this.reach = reach;
        this.skipped = skipped;
    }

    /** A new deposit run: look for a chest again. */
    void restart() {
        chestTarget = null;
        unloaded = false;
    }

    /** Whether this trip has put anything into a can or chest yet (it is still on its way until then). */
    boolean hasUnloaded() {
        return unloaded;
    }

    /**
     * Walks to the nearest can or chest with room and unloads there. {@link Outcome#NOTHING_FITS} when the storage is
     * full and nothing takes anything, {@link Outcome#NOTHING_TO_DO} when there is no chest with room but the storage
     * is not full.
     */
    Outcome tick(ServerLevel level, JobHost bed) {
        if (chestTarget == null) {
            chestTarget = findChurn(level, bed); // milk first, the rest into the chests
            if (chestTarget == null) chestTarget = findChest(level, bed);
            if (chestTarget == null) return goblin.isStorageFull() ? Outcome.NOTHING_FITS : Outcome.NOTHING_TO_DO;
            reach.resetStuck();
        }
        Vec3 center = Vec3.atCenterOf(chestTarget);
        goblin.getLookControl().setLookAt(center);
        if (goblin.getEyePosition().distanceToSqr(center) > CHEST_REACH_SQ) {
            if (!reach.approach(level, center)) {
                skipped.put(chestTarget, level.getGameTime() + Reach.SKIP_TICKS);
                chestTarget = null;
            }
            return Outcome.BUSY;
        }
        goblin.getNavigation().stop();
        if (level.getBlockEntity(chestTarget) instanceof MilkChurnBlockEntity churn) {
            pourMilk(level, churn);
            unloaded = true;
            chestTarget = null;
            return Outcome.BUSY;
        }
        if (openChest == null) {
            // lift the lid (the jaw opens), rummage for a moment, then unload
            openChest(level, chestTarget);
            chestCloseAt = level.getGameTime() + CHEST_OPEN_TICKS;
            goblin.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT);
            return Outcome.BUSY;
        }
        if (level.getGameTime() < chestCloseAt) return Outcome.BUSY;
        goblin.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT);
        unloadInto(level, chestTarget);
        unloaded = true;
        closeChest(level);
        chestTarget = null;
        return Outcome.BUSY;
    }

    private void openChest(ServerLevel level, BlockPos pos) {
        openChest = pos;
        if (level.getBlockEntity(pos) instanceof ChestBlockEntity chest) {
            goblin.setOpenChest(pos);
            chest.startOpen(goblin);
        }
    }

    void closeChest(ServerLevel level) {
        if (openChest == null) return;
        if (level.getBlockEntity(openChest) instanceof ChestBlockEntity chest) chest.stopOpen(goblin);
        goblin.setOpenChest(null);
        openChest = null;
    }

    /** Nearest Milk Churn of the flat with room for a bucket, when the goblin carries milk; null otherwise. */
    @Nullable
    private BlockPos findChurn(ServerLevel level, JobHost bed) {
        if (countStored(Items.MILK_BUCKET) == 0) return null;
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        for (BlockPos pos : HomeRegistry.milkChurns(level, bed.getBlockPos())) {
            if (skipped.containsKey(pos) || !(level.getBlockEntity(pos) instanceof MilkChurnBlockEntity churn) || churn.room() == 0) continue;
            double d = goblin.distanceToSqr(Vec3.atCenterOf(pos));
            if (d < bestDist) {
                bestDist = d;
                best = pos;
            }
        }
        return best;
    }

    /** Pours the carried milk buckets into the churn; the buckets are used up, what does not fit goes to the chests. */
    private void pourMilk(ServerLevel level, MilkChurnBlockEntity churn) {
        int poured = churn.pour(countStored(Items.MILK_BUCKET));
        if (poured == 0) return;
        SimpleContainer inv = goblin.getInventory();
        int left = poured;
        for (int i = GoblinEntity.HOTBAR_SIZE; i < GoblinEntity.INVENTORY_SIZE && left > 0; i++) {
            ItemStack stack = inv.getItem(i);
            if (!stack.is(Items.MILK_BUCKET)) continue;
            int taken = Math.min(left, stack.getCount());
            stack.shrink(taken);
            left -= taken;
        }
        inv.setChanged();
        goblin.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT);
        level.playSound(null, churn.getBlockPos(), SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 1.0f, 1.0f);
    }

    /** How many of an item the goblin has in its storage (not the tool row). */
    private int countStored(Item item) {
        SimpleContainer inv = goblin.getInventory();
        int count = 0;
        for (int i = GoblinEntity.HOTBAR_SIZE; i < GoblinEntity.INVENTORY_SIZE; i++) {
            if (inv.getItem(i).is(item)) count += inv.getItem(i).getCount();
        }
        return count;
    }

    /**
     * The chest to unload into: the one the player assigned with the staff, or else the nearest goblin chest of the
     * flat that accepts at least one of the carried stacks. An assigned chest that is gone (or is no longer a
     * container) is forgotten with a word, and the flat's chests take over again.
     */
    @Nullable
    private BlockPos findChest(ServerLevel level, JobHost bed) {
        BlockPos assigned = bed.depositChest();
        if (assigned != null) {
            if (!level.isLoaded(assigned)) return null; // out of sight: wait, the assignment stands
            Storage<ItemVariant> storage = ItemStorage.SIDED.find(level, assigned, Direction.UP);
            if (storage != null) return skipped.containsKey(assigned) ? null : assigned;
            bed.clearDepositChest();
            GoblinSpeech.say(level, goblin, GoblinSpeech.CHEST_GONE, GoblinSounds.NO);
        }
        List<BlockPos> chests = HomeRegistry.goblinChests(level, bed.getBlockPos());
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        for (BlockPos chest : chests) {
            if (skipped.containsKey(chest)) continue;
            Storage<ItemVariant> storage = ItemStorage.SIDED.find(level, chest, Direction.UP);
            if (storage == null || !acceptsAnything(storage)) continue;
            double d = goblin.distanceToSqr(Vec3.atCenterOf(chest));
            if (d < bestDist) {
                bestDist = d;
                best = chest;
            }
        }
        return best;
    }

    private boolean acceptsAnything(Storage<ItemVariant> storage) {
        SimpleContainer inv = goblin.getInventory();
        for (int i = GoblinEntity.HOTBAR_SIZE; i < GoblinEntity.INVENTORY_SIZE; i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.isEmpty()) continue;
            if (StorageUtil.simulateInsert(storage, ItemVariant.of(stack), stack.getCount(), null) > 0) return true;
        }
        return false;
    }

    /** Everything in the storage (not the tool row) that fits goes into the chest. */
    private void unloadInto(ServerLevel level, BlockPos chest) {
        Storage<ItemVariant> storage = ItemStorage.SIDED.find(level, chest, Direction.UP);
        if (storage == null) return;
        SimpleContainer inv = goblin.getInventory();
        try (Transaction tx = Transaction.openOuter()) {
            for (int i = GoblinEntity.HOTBAR_SIZE; i < GoblinEntity.INVENTORY_SIZE; i++) {
                ItemStack stack = inv.getItem(i);
                if (stack.isEmpty()) continue;
                long inserted = storage.insert(ItemVariant.of(stack), stack.getCount(), tx);
                if (inserted > 0) {
                    stack.shrink((int) inserted);
                    if (stack.isEmpty()) inv.setItem(i, ItemStack.EMPTY);
                }
            }
            tx.commit();
        }
        inv.setChanged();
    }
}
