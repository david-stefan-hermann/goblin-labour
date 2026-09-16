package goblinlabour.job;

import goblinlabour.entity.GoblinEntity;
import goblinlabour.home.HomeRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.animal.cow.AbstractCow;
import net.minecraft.world.entity.animal.sheep.Sheep;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CaveVines;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A farmer does whatever its tool row has the tools for, within {@code length} blocks of the bed (outside homes),
 * always the nearest thing first:
 * <ul>
 * <li>hoe: harvest ripe crops, nether wart, pumpkins, melons and wart blocks and replant them; harvest ripe cocoa and
 * replant it on the same side of the log (with cocoa beans in the tool row it also plants cocoa on free sides of
 * jungle logs); pick sweet berries and glow berries; cut sugar cane, bamboo and cactus down to the bottom block.
 * Harvesting needs the hoe: without one the farmer complains when something is ripe.</li>
 * <li>bucket: milk grown cows (each cow once every five minutes); the milk goes into a Milk Churn of the home.</li>
 * <li>shears: shear sheep.</li>
 * <li>treetap (Tech Reborn): tap rubber logs that have sap.</li>
 * </ul>
 * Tools never wear out. Endless: the goblin stays on duty and checks again when there is nothing to do.
 */
public final class FarmJob implements JobTask {
    public static final FarmJob INSTANCE = new FarmJob();
    private static final int VERTICAL = 3;
    /** Cocoa pods and sap spots sit higher up the trunks. */
    private static final int TRUNK_HEIGHT = 12;
    /** A cow gives milk again after five minutes. */
    private static final int MILK_COOLDOWN = 6000;
    /** An animal the goblin could not get to is left alone for a minute. */
    private static final int ANIMAL_SKIP = 1200;
    private static final Identifier RUBBER_LOG = Identifier.fromNamespaceAndPath("techreborn", "rubber_log");
    private static final Identifier SAP = Identifier.fromNamespaceAndPath("techreborn", "sap");

    /** When each cow was milked last, and animals the goblins could not reach (until when). */
    private static final Map<UUID, Long> MILKED = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> ANIMALS_SKIPPED = new ConcurrentHashMap<>();

    private FarmJob() {
    }

    @Override
    public boolean endless() {
        return true;
    }

    @Override
    public BlockPos entryPoint(JobHost bedEntity, JobConfig config) {
        return bedEntity.getBlockPos().relative(config.direction(), 6);
    }

    /** Tool row items only a farmer uses: bucket, shears, treetap, cocoa beans. */
    public static boolean isFarmTool(ItemStack stack) {
        return stack.is(Items.BUCKET) || stack.is(Items.SHEARS) || stack.is(Items.COCOA_BEANS) || isTreetap(stack);
    }

    /** Tech Reborn's treetap and electric treetap, without a dependency on Tech Reborn. */
    static boolean isTreetap(ItemStack stack) {
        return !stack.isEmpty() && BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath().endsWith("treetap");
    }

    /** The farmer's tools, from its tool row (EMPTY when missing). */
    private record Tools(ItemStack hoe, ItemStack bucket, ItemStack shears, ItemStack treetap, boolean beans) {
        static Tools of(GoblinEntity goblin) {
            ItemStack hoe = ItemStack.EMPTY, bucket = ItemStack.EMPTY, shears = ItemStack.EMPTY, treetap = ItemStack.EMPTY;
            boolean beans = false;
            SimpleContainer inv = goblin.getInventory();
            for (int i = 0; i < GoblinEntity.HOTBAR_SIZE; i++) {
                ItemStack stack = inv.getItem(i);
                if (stack.is(ItemTags.HOES) && hoe.isEmpty()) hoe = stack;
                else if (stack.is(Items.BUCKET) && bucket.isEmpty()) bucket = stack;
                else if (stack.is(Items.SHEARS) && shears.isEmpty()) shears = stack;
                else if (isTreetap(stack) && treetap.isEmpty()) treetap = stack;
                else if (stack.is(Items.COCOA_BEANS)) beans = true;
            }
            return new Tools(hoe, bucket, shears, treetap, beans);
        }
    }

    static boolean ripe(BlockState state) {
        Block block = state.getBlock();
        if (block instanceof CropBlock crop) return crop.isMaxAge(state);
        if (block instanceof NetherWartBlock) return state.getValue(NetherWartBlock.AGE) >= NetherWartBlock.MAX_AGE;
        if (block instanceof CocoaBlock) return state.getValue(CocoaBlock.AGE) >= CocoaBlock.MAX_AGE;
        return state.is(Blocks.PUMPKIN) || state.is(Blocks.MELON) || state.is(BlockTags.WART_BLOCKS);
    }

    /**
     * Sugar cane and bamboo grow in stalks; everything but the bottom block is harvested, from the lowest one up (one
     * cut brings the rest down). Cactus is cut from the top down instead, see {@link #afterBreak}.
     */
    private static boolean stalk(BlockState state) {
        return state.is(Blocks.SUGAR_CANE) || state.is(Blocks.BAMBOO);
    }

    /** The nearest thing to do so far. */
    private static final class Best {
        Pick pick;
        double dist = Double.MAX_VALUE;

        void offer(Pick candidate, Vec3 me, Vec3 point) {
            double d = me.distanceToSqr(point);
            if (d < dist) {
                dist = d;
                pick = candidate;
            }
        }
    }

    @Override
    public Pick pick(ServerLevel level, GoblinEntity goblin, JobHost bedEntity, JobConfig config, Set<BlockPos> skipped) {
        BlockPos bed = bedEntity.getBlockPos();
        int r = config.length();
        long now = level.getGameTime();
        Vec3 me = goblin.position();
        Tools tools = Tools.of(goblin);
        boolean hoe = !tools.hoe().isEmpty();
        boolean needsHoe = false;
        Best best = new Best();

        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int x = bed.getX() - r; x <= bed.getX() + r; x++) {
            for (int z = bed.getZ() - r; z <= bed.getZ() + r; z++) {
                if (!level.isLoaded(cursor.set(x, bed.getY(), z))) continue;
                boolean stalkTaken = false;
                BlockPos cactusTop = null;
                for (int y = bed.getY() - VERTICAL; y <= bed.getY() + TRUNK_HEIGHT; y++) {
                    cursor.set(x, y, z);
                    BlockState state = level.getBlockState(cursor);
                    if (state.isAir()) {
                        if (hoe && tools.beans() && y <= bed.getY() + VERTICAL) offerCocoaSpot(level, cursor, skipped, best, me);
                        continue;
                    }
                    boolean low = y <= bed.getY() + VERTICAL;
                    if (low && state.is(Blocks.CACTUS) && level.getBlockState(cursor.below()).is(Blocks.CACTUS)) {
                        cactusTop = cursor.immutable(); // the highest one wins
                        continue;
                    }
                    if (!tools.treetap().isEmpty() && hasSap(state)) {
                        if (usable(level, cursor, skipped)) {
                            BlockPos pos = cursor.immutable();
                            best.offer(Pick.use(pos, new SapUse(pos, tools.treetap())), me, Vec3.atCenterOf(pos));
                        }
                        continue;
                    }
                    Pick harvest = null;
                    if (ripe(state) && (low || state.getBlock() instanceof CocoaBlock)) {
                        harvest = Pick.of(cursor.immutable());
                    } else if (low && !stalkTaken && stalk(state) && level.getBlockState(cursor.below()).is(state.getBlock())) {
                        stalkTaken = true; // the lowest one: everything above comes down with it
                        harvest = Pick.of(cursor.immutable());
                    } else if (low && state.getBlock() instanceof SweetBerryBushBlock && state.getValue(SweetBerryBushBlock.AGE) >= SweetBerryBushBlock.MAX_AGE) {
                        BlockPos pos = cursor.immutable();
                        harvest = Pick.use(pos, new BerryUse(pos, tools.hoe()));
                    } else if (state.getBlock() instanceof CaveVines && CaveVines.hasGlowBerries(state)) {
                        BlockPos pos = cursor.immutable();
                        harvest = Pick.use(pos, new GlowBerryUse(pos, tools.hoe()));
                    }
                    if (harvest == null) continue;
                    boolean possible = harvest.use() != null ? usable(level, cursor, skipped)
                            : Mining.verdict(level, cursor, state, goblin) == Mining.Verdict.OK && !skipped.contains(cursor);
                    if (!possible) continue;
                    if (!hoe) {
                        needsHoe = true;
                        continue;
                    }
                    best.offer(harvest, me, Vec3.atCenterOf(cursor));
                }
                if (cactusTop != null && Mining.verdict(level, cactusTop, level.getBlockState(cactusTop), goblin) == Mining.Verdict.OK
                        && !skipped.contains(cactusTop)) {
                    if (hoe) best.offer(Pick.of(cactusTop), me, Vec3.atCenterOf(cactusTop));
                    else needsHoe = true;
                }
            }
        }

        if (!tools.bucket().isEmpty() || !tools.shears().isEmpty()) {
            AABB area = new AABB(bed).inflate(r, VERTICAL + 1, r);
            ANIMALS_SKIPPED.values().removeIf(until -> until < now);
            if (!tools.bucket().isEmpty() && goblin.canStore(new ItemStack(Items.MILK_BUCKET))) {
                for (AbstractCow cow : level.getEntitiesOfClass(AbstractCow.class, area, cow -> cow.isAlive() && !cow.isBaby())) {
                    Long milked = MILKED.get(cow.getUUID());
                    if (milked != null && now - milked < MILK_COOLDOWN) continue;
                    if (!animalReachable(level, cow.getUUID(), cow.blockPosition())) continue;
                    best.offer(Pick.use(cow.blockPosition(), new MilkUse(cow, tools.bucket())), me, cow.position());
                }
            }
            if (!tools.shears().isEmpty()) {
                for (Sheep sheep : level.getEntitiesOfClass(Sheep.class, area, sheep -> sheep.isAlive() && sheep.readyForShearing())) {
                    if (!animalReachable(level, sheep.getUUID(), sheep.blockPosition())) continue;
                    best.offer(Pick.use(sheep.blockPosition(), new ShearUse(sheep, tools.shears())), me, sheep.position());
                }
            }
        }

        if (best.pick != null) return best.pick;
        return needsHoe ? Pick.NEEDS_HOE : Pick.DONE;
    }

    private static boolean usable(ServerLevel level, BlockPos pos, Set<BlockPos> skipped) {
        return !skipped.contains(pos) && !HomeRegistry.isProtected(level, pos);
    }

    private static boolean animalReachable(ServerLevel level, UUID animal, BlockPos pos) {
        return !ANIMALS_SKIPPED.containsKey(animal) && !HomeRegistry.isProtected(level, pos);
    }

    /** An air block beside a jungle log where a cocoa pod would grow: planted with beans from the tool row. */
    private static void offerCocoaSpot(ServerLevel level, BlockPos.MutableBlockPos cursor, Set<BlockPos> skipped, Best best, Vec3 me) {
        if (!usable(level, cursor, skipped)) return;
        for (Direction side : Direction.Plane.HORIZONTAL) {
            if (!level.getBlockState(cursor.relative(side)).is(BlockTags.JUNGLE_LOGS)) continue;
            BlockState pod = Blocks.COCOA.defaultBlockState().setValue(CocoaBlock.FACING, side);
            if (!pod.canSurvive(level, cursor)) continue;
            BlockPos pos = cursor.immutable();
            best.offer(Pick.place(pos, pod), me, Vec3.atCenterOf(pos));
            return;
        }
    }

    /** A Tech Reborn rubber log with sap on one side (its "hassap" property), looked up by name. */
    private static boolean hasSap(BlockState state) {
        if (!BuiltInRegistries.BLOCK.getKey(state.getBlock()).equals(RUBBER_LOG)) return false;
        Property<?> property = state.getBlock().getStateDefinition().getProperty("hassap");
        return property instanceof BooleanProperty sap && state.getValue(sap);
    }

    /**
     * Replants a harvested crop, nether wart or cocoa pod with a seed from the fresh drops or the storage. A cut cactus
     * block goes straight into the storage: dropped, it would land on the cactus below, which destroys items.
     */
    @Override
    public void afterBreak(ServerLevel level, GoblinEntity goblin, JobHost bedEntity, JobConfig config, BlockPos broken) {
        BlockState harvested = goblin.runner().lastBrokenState();
        if (harvested == null) return;
        Block crop = harvested.getBlock();
        if (crop == Blocks.CACTUS) {
            for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, new AABB(broken).inflate(0.5), i -> i.isAlive() && i.getItem().is(Items.CACTUS))) {
                goblin.pickUp(item);
            }
            return;
        }
        BlockState state;
        if (crop instanceof CocoaBlock) {
            state = crop.defaultBlockState().setValue(CocoaBlock.FACING, harvested.getValue(CocoaBlock.FACING));
        } else if (crop instanceof CropBlock || crop instanceof NetherWartBlock) {
            state = crop.defaultBlockState();
        } else {
            return;
        }
        if (!level.getBlockState(broken).isAir() || HomeRegistry.isProtected(level, broken) || !state.canSurvive(level, broken)) return;
        // the seeds just popped out of the crop; a seed from the storage does as well
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, new AABB(broken).inflate(1.0), ItemEntity::isAlive)) {
            ItemStack stack = item.getItem();
            if (Block.byItem(stack.getItem()) != crop) continue;
            level.setBlock(broken, state, 3);
            if (stack.getCount() <= 1) {
                item.discard();
            } else {
                item.setItem(stack.copyWithCount(stack.getCount() - 1));
            }
            return;
        }
        SimpleContainer inv = goblin.getInventory();
        for (int i = GoblinEntity.HOTBAR_SIZE; i < GoblinEntity.INVENTORY_SIZE; i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.isEmpty() || Block.byItem(stack.getItem()) != crop) continue;
            level.setBlock(broken, state, 3);
            stack.shrink(1);
            inv.setChanged();
            return;
        }
    }

    /** A cocoa pod planted on a free side of a jungle log: its bean comes out of the tool row. */
    @Override
    public void afterPlace(ServerLevel level, GoblinEntity goblin, JobHost bed, JobConfig config, BlockPos pos, BlockState placed) {
        SimpleContainer inv = goblin.getInventory();
        for (int i = 0; i < GoblinEntity.HOTBAR_SIZE; i++) {
            ItemStack stack = inv.getItem(i);
            if (!stack.is(Items.COCOA_BEANS)) continue;
            stack.shrink(1);
            inv.setChanged();
            return;
        }
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3); // the last bean went elsewhere meanwhile
    }

    /** The field: stalks and berries drop their items here, wool falls off the sheep here. */
    @Override
    public AABB lootArea(JobHost bedEntity, JobConfig config) {
        BlockPos bed = bedEntity.getBlockPos();
        int r = config.length() + 2;
        return new AABB(bed.getX() - r, bed.getY() - VERTICAL, bed.getZ() - r, bed.getX() + r + 1, bed.getY() + TRUNK_HEIGHT, bed.getZ() + r + 1);
    }

    @Override
    public boolean wantsLoot(ItemStack stack) {
        Item item = stack.getItem();
        return stack.is(ItemTags.WOOL) || item == Items.WHEAT || item == Items.WHEAT_SEEDS || item == Items.CARROT
                || item == Items.POTATO || item == Items.POISONOUS_POTATO || item == Items.BEETROOT || item == Items.BEETROOT_SEEDS
                || item == Items.PUMPKIN || item == Items.MELON_SLICE || item == Items.NETHER_WART || item == Items.COCOA_BEANS
                || item == Items.SWEET_BERRIES || item == Items.GLOW_BERRIES || item == Items.SUGAR_CANE || item == Items.BAMBOO
                || item == Items.CACTUS || BuiltInRegistries.ITEM.getKey(item).equals(SAP);
    }

    // ---- uses ----------------------------------------------------------------------------------------------------

    /** Picks a ripe sweet berry bush like a player does: 2 to 3 berries, the bush goes back to its first berries. */
    private record BerryUse(BlockPos pos, ItemStack tool) implements Use {
        @Override
        public @Nullable Vec3 point(ServerLevel level) {
            BlockState state = level.getBlockState(pos);
            boolean ripe = state.getBlock() instanceof SweetBerryBushBlock && state.getValue(SweetBerryBushBlock.AGE) >= SweetBerryBushBlock.MAX_AGE;
            return ripe ? Vec3.atCenterOf(pos) : null;
        }

        @Override
        public void apply(ServerLevel level, GoblinEntity goblin) {
            BlockState state = level.getBlockState(pos);
            if (!(state.getBlock() instanceof SweetBerryBushBlock)) return;
            Block.popResource(level, pos, new ItemStack(Items.SWEET_BERRIES, 2 + level.getRandom().nextInt(2)));
            level.playSound(null, pos, SoundEvents.SWEET_BERRY_BUSH_PICK_BERRIES, SoundSource.BLOCKS, 1.0f, 0.8f + level.getRandom().nextFloat() * 0.4f);
            BlockState picked = state.setValue(SweetBerryBushBlock.AGE, 1);
            level.setBlock(pos, picked, 2);
            level.gameEvent(GameEvent.BLOCK_CHANGE, pos, GameEvent.Context.of(goblin, picked));
        }
    }

    /** Picks glow berries off cave vines, as a player's click does. */
    private record GlowBerryUse(BlockPos pos, ItemStack tool) implements Use {
        @Override
        public @Nullable Vec3 point(ServerLevel level) {
            return CaveVines.hasGlowBerries(level.getBlockState(pos)) ? Vec3.atCenterOf(pos) : null;
        }

        @Override
        public void apply(ServerLevel level, GoblinEntity goblin) {
            BlockState state = level.getBlockState(pos);
            if (CaveVines.hasGlowBerries(state)) CaveVines.use(goblin, state, level, pos);
        }
    }

    /** Milks a grown cow into the goblin's storage; the bucket stays in the tool row. */
    private record MilkUse(AbstractCow cow, ItemStack tool) implements Use {
        @Override
        public @Nullable Vec3 point(ServerLevel level) {
            return cow.isAlive() && !cow.isBaby() ? cow.position().add(0.0, cow.getBbHeight() * 0.5, 0.0) : null;
        }

        @Override
        public void apply(ServerLevel level, GoblinEntity goblin) {
            if (!cow.isAlive() || !goblin.canStore(new ItemStack(Items.MILK_BUCKET))) return;
            goblin.storeInStorage(new ItemStack(Items.MILK_BUCKET));
            level.playSound(null, cow.getX(), cow.getY(), cow.getZ(), SoundEvents.COW_MILK, SoundSource.NEUTRAL, 1.0f, 1.0f);
            MILKED.put(cow.getUUID(), level.getGameTime());
        }

        @Override
        public void giveUp(ServerLevel level, long now) {
            ANIMALS_SKIPPED.put(cow.getUUID(), now + ANIMAL_SKIP);
        }
    }

    /** Shears a sheep; the wool drops and is picked up like any loot of the field. */
    private record ShearUse(Sheep sheep, ItemStack tool) implements Use {
        @Override
        public @Nullable Vec3 point(ServerLevel level) {
            return sheep.isAlive() && sheep.readyForShearing() ? sheep.position().add(0.0, sheep.getBbHeight() * 0.5, 0.0) : null;
        }

        @Override
        public void apply(ServerLevel level, GoblinEntity goblin) {
            if (sheep.isAlive() && sheep.readyForShearing()) sheep.shear(level, SoundSource.NEUTRAL, tool);
        }

        @Override
        public void giveUp(ServerLevel level, long now) {
            ANIMALS_SKIPPED.put(sheep.getUUID(), now + ANIMAL_SKIP);
        }
    }

    /** Taps a Tech Reborn rubber log: its sap spot dries up (and refills by itself), one sap drops. */
    private record SapUse(BlockPos pos, ItemStack tool) implements Use {
        @Override
        public @Nullable Vec3 point(ServerLevel level) {
            return hasSap(level.getBlockState(pos)) ? Vec3.atCenterOf(pos) : null;
        }

        @Override
        public void apply(ServerLevel level, GoblinEntity goblin) {
            BlockState state = level.getBlockState(pos);
            if (!hasSap(state) || !(state.getBlock().getStateDefinition().getProperty("hassap") instanceof BooleanProperty sap)) return;
            level.setBlock(pos, state.setValue(sap, false), 3);
            BuiltInRegistries.ITEM.getOptional(SAP).ifPresent(item -> Block.popResource(level, pos, new ItemStack(item)));
            level.playSound(null, pos, SoundEvents.HONEY_BLOCK_SLIDE, SoundSource.BLOCKS, 0.6f, 1.2f);
        }
    }
}
