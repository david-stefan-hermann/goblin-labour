package goblinlabour.entity;

import goblinlabour.GoblinSounds;
import net.minecraft.sounds.SoundEvent;

import goblinlabour.GoblinLabour;
import goblinlabour.GoblinSpeech;
import goblinlabour.block.GoblinBedBlock;
import goblinlabour.block.GoblinBedBlockEntity;
import goblinlabour.block.GoblinScaffoldBlock;
import goblinlabour.entity.ai.ClimbOutGoal;
import goblinlabour.entity.ai.FollowStaffGoal;
import goblinlabour.job.Climber;
import goblinlabour.entity.ai.RecoverItemsGoal;
import goblinlabour.entity.ai.RestGoal;
import goblinlabour.entity.ai.WorkGoal;
import goblinlabour.item.GoblinData;
import goblinlabour.job.JobRunner;
import goblinlabour.menu.GoblinMenuProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * A goblin worker. Bound to one {@link GoblinBedBlockEntity}; has a 36-slot inventory whose first nine slots
 * (the "hotbar") hold its tools. Monsters ignore it and it ignores them.
 */
public class GoblinEntity extends PathfinderMob {
    private static final EntityDataAccessor<Optional<BlockPos>> DATA_BED =
            SynchedEntityData.defineId(GoblinEntity.class, EntityDataSerializers.OPTIONAL_BLOCK_POS);
    /** {@link GoblinStyle} ordinal; the bed decides it, clients pick the texture from it. */
    private static final EntityDataAccessor<Byte> DATA_STYLE =
            SynchedEntityData.defineId(GoblinEntity.class, EntityDataSerializers.BYTE);

    public static final int INVENTORY_SIZE = 18;
    public static final int HOTBAR_SIZE = GoblinData.HOTBAR_SIZE;

    private final SimpleContainer inventory = new SimpleContainer(INVENTORY_SIZE);
    /** Created lazily: registerGoals() runs from the Mob constructor, before field initialisers. */
    private JobRunner runner;
    private Climber climber;
    /** Kept for the status command's debug line; set in registerGoals (runs from the Mob constructor). */
    private RestGoal restGoal;
    private int bedCheckTimer;
    @Nullable private BlockPos lastTorchPos;
    @Nullable private BlockPos recoverPos;
    private long recoverUntil;
    @Nullable private java.util.UUID following;

    public GoblinEntity(EntityType<? extends GoblinEntity> type, Level level) {
        super(type, level);
        setPersistenceRequired();
        setCanPickUpLoot(false);
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            setDropChance(slot, 0.0f);
        }
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, GoblinData.MAX_HEALTH)
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.STEP_HEIGHT, 1.0)
                .add(Attributes.FOLLOW_RANGE, 48.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_BED, Optional.empty());
        builder.define(DATA_STYLE, (byte) GoblinStyle.LUMBERJACK.ordinal());
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new ClimbOutGoal(this));
        goalSelector.addGoal(1, new RecoverItemsGoal(this));
        goalSelector.addGoal(1, new FollowStaffGoal(this));
        goalSelector.addGoal(2, new WorkGoal(this));
        restGoal = new RestGoal(this);
        goalSelector.addGoal(3, restGoal);
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 1.0));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 6.0f));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        GroundPathNavigation navigation = new GroundPathNavigation(this, level);
        navigation.setCanOpenDoors(true);
        navigation.setCanFloat(true);
        return navigation;
    }

    // ---- peaceful ------------------------------------------------------------------------------------------------

    /** Hostile mobs use this in their targeting conditions; false makes them ignore the goblin. */
    @Override
    public boolean canBeSeenAsEnemy() {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    // ---- voice ---------------------------------------------------------------------------------------------------

    @Override
    protected SoundEvent getAmbientSound() {
        return GoblinSounds.AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return GoblinSounds.HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return GoblinSounds.DEATH;
    }

    @Override
    public int getAmbientSoundInterval() {
        return 240;
    }

    // ---- bed binding ---------------------------------------------------------------------------------------------

    public Optional<BlockPos> getBedPos() {
        return entityData.get(DATA_BED);
    }

    public void bindToBed(BlockPos bedPos, GoblinData data) {
        entityData.set(DATA_BED, Optional.of(bedPos));
        setCustomName(Component.literal(data.name()));
        setCustomNameVisible(true);
        NonNullList<ItemStack> hotbar = data.hotbarCopy();
        for (int i = 0; i < HOTBAR_SIZE; i++) {
            inventory.setItem(i, hotbar.get(i));
        }
        setHealth(Math.max(1.0f, Math.min(getMaxHealth(), data.health())));
    }

    public String goblinName() {
        Component name = getCustomName();
        return name == null ? "Goblin" : name.getString();
    }

    public GoblinStyle getStyle() {
        return GoblinStyle.byOrdinal(entityData.get(DATA_STYLE));
    }

    public void setStyle(GoblinStyle style) {
        entityData.set(DATA_STYLE, (byte) style.ordinal());
    }

    @Nullable
    public GoblinBedBlockEntity bed() {
        Optional<BlockPos> pos = getBedPos();
        if (pos.isEmpty()) return null;
        return level().getBlockEntity(pos.get()) instanceof GoblinBedBlockEntity bed ? bed : null;
    }

    public JobRunner runner() {
        if (runner == null) runner = new JobRunner(this);
        return runner;
    }

    public String restDebug() {
        return restGoal == null ? "rest: -" : restGoal.debug();
    }

    public Climber climber() {
        if (climber == null) climber = new Climber(this);
        return climber;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide() || isDeadOrDying()) return; // a dying goblin must not re-collect its own drops
        if (bedCheckTimer % 10 == 0) {
            collectNearbyItems((ServerLevel) level());
            touchScaffolds((ServerLevel) level());
        }
        if (++bedCheckTimer < 20) return;
        bedCheckTimer = 0;
        checkBed((ServerLevel) level());
    }

    /** Despawns (dropping a blank) when the bed is gone or has adopted another goblin. */
    private void checkBed(ServerLevel level) {
        Optional<BlockPos> bedPos = getBedPos();
        if (bedPos.isEmpty()) return;
        if (!level.isLoaded(bedPos.get())) return;
        GoblinBedBlockEntity bed = bed();
        if (bed != null && bed.owns(getUUID())) {
            if (bed.getStyle() != getStyle()) setStyle(bed.getStyle());
            return;
        }
        if (bed == null) {
            GoblinSpeech.say(level, goblinName(), GoblinSpeech.BED_GONE);
            ItemStack blank = new ItemStack(GoblinLabour.GOBLIN_BLANK);
            blank.set(GoblinLabour.GOBLIN_DATA, new GoblinData(goblinName(), hotbarCopy(), getHealth()));
            Containers.dropItemStack(level, getX(), getY(), getZ(), blank);
            dropStorage();
        }
        discard();
    }

    @Override
    public Direction getBedOrientation() {
        Optional<BlockPos> bedPos = getBedPos();
        if (bedPos.isPresent()) {
            BlockState state = level().getBlockState(bedPos.get());
            if (state.getBlock() instanceof GoblinBedBlock) return state.getValue(GoblinBedBlock.FACING);
        }
        return super.getBedOrientation();
    }

    /** Minions Remastered's "blink": teleport next to the target when the path is hopeless (never inside a home). */
    public void blinkTo(ServerLevel level, BlockPos target) {
        BlockPos bed = getBedPos().orElse(null);
        if (bed != null && goblinlabour.home.HomeZone.contains(bed, blockPosition())) return;
        BlockPos spot = null;
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            for (int dy = 1; dy >= -1; dy--) {
                BlockPos candidate = target.relative(direction).above(dy);
                if (level.getBlockState(candidate).getCollisionShape(level, candidate).isEmpty()
                        && level.getBlockState(candidate.above()).getCollisionShape(level, candidate.above()).isEmpty()
                        && !level.getBlockState(candidate.below()).getCollisionShape(level, candidate.below()).isEmpty()) {
                    spot = candidate;
                    break;
                }
            }
            if (spot != null) break;
        }
        if (spot == null) return;
        level.sendParticles(ParticleTypes.POOF, getX(), getY() + 0.5, getZ(), 8, 0.2, 0.3, 0.2, 0.02);
        snapTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, getYRot(), getXRot());
        getNavigation().stop();
        level.sendParticles(ParticleTypes.POOF, getX(), getY() + 0.5, getZ(), 8, 0.2, 0.3, 0.2, 0.02);
    }

    // ---- inventory -----------------------------------------------------------------------------------------------

    public SimpleContainer getInventory() {
        return inventory;
    }

    public NonNullList<ItemStack> hotbarCopy() {
        NonNullList<ItemStack> list = NonNullList.withSize(HOTBAR_SIZE, ItemStack.EMPTY);
        for (int i = 0; i < HOTBAR_SIZE; i++) {
            list.set(i, inventory.getItem(i).copy());
        }
        return list;
    }

    /** Puts a stack into the storage rows (never the tool row). Returns what did not fit. */
    public ItemStack storeInStorage(ItemStack stack) {
        ItemStack rest = stack.copy();
        for (int pass = 0; pass < 2 && !rest.isEmpty(); pass++) {
            for (int i = HOTBAR_SIZE; i < INVENTORY_SIZE && !rest.isEmpty(); i++) {
                ItemStack slot = inventory.getItem(i);
                if (pass == 0) {
                    if (slot.isEmpty() || !ItemStack.isSameItemSameComponents(slot, rest)) continue;
                    int room = Math.min(slot.getMaxStackSize(), inventory.getMaxStackSize()) - slot.getCount();
                    if (room <= 0) continue;
                    int move = Math.min(room, rest.getCount());
                    slot.grow(move);
                    rest.shrink(move);
                } else if (slot.isEmpty()) {
                    inventory.setItem(i, rest.copy());
                    rest = ItemStack.EMPTY;
                }
            }
        }
        inventory.setChanged();
        return rest;
    }

    public boolean hasStorageItems() {
        for (int i = HOTBAR_SIZE; i < INVENTORY_SIZE; i++) {
            if (!inventory.getItem(i).isEmpty()) return true;
        }
        return false;
    }

    public boolean isStorageFull() {
        for (int i = HOTBAR_SIZE; i < INVENTORY_SIZE; i++) {
            if (inventory.getItem(i).isEmpty()) return false;
        }
        return true;
    }

    /** Drops everything outside the hotbar on the ground (death, bed broken). */
    public void dropStorage() {
        if (!(level() instanceof ServerLevel serverLevel)) return;
        for (int i = HOTBAR_SIZE; i < INVENTORY_SIZE; i++) {
            ItemStack stack = inventory.removeItemNoUpdate(i);
            if (!stack.isEmpty()) Containers.dropItemStack(serverLevel, getX(), getY(), getZ(), stack);
        }
    }

    /** The player this goblin was picked by with the staff (server side), or null. */
    public void setFollowing(@Nullable java.util.UUID player) {
        following = player;
        setGlowingTag(player != null);
    }

    @Nullable
    public java.util.UUID following() {
        return following;
    }

    /** Items lying within reach go into the storage (a broken chest spills its contents, for example). */
    private void collectNearbyItems(ServerLevel level) {
        if (isStorageFull()) return;
        for (net.minecraft.world.entity.item.ItemEntity item : level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                getBoundingBox().inflate(2.0, 1.0, 2.0), net.minecraft.world.entity.item.ItemEntity::isAlive)) {
            if (item.hasPickUpDelay() || !canStore(item.getItem())) continue;
            pickUp(item);
            if (isStorageFull()) return;
        }
    }

    /** Takes as much of the item as fits into the storage, with the vanilla pick-up animation. */
    public void pickUp(net.minecraft.world.entity.item.ItemEntity item) {
        ItemStack stack = item.getItem();
        int before = stack.getCount();
        ItemStack rest = storeInStorage(stack);
        int taken = before - rest.getCount();
        if (taken <= 0) return;
        take(item, taken);
        if (rest.isEmpty()) item.discard();
        else item.setItem(rest);
    }

    /** True if at least part of the stack fits into the storage rows. */
    public boolean canStore(ItemStack stack) {
        for (int i = HOTBAR_SIZE; i < INVENTORY_SIZE; i++) {
            ItemStack slot = inventory.getItem(i);
            if (slot.isEmpty()) return true;
            if (ItemStack.isSameItemSameComponents(slot, stack)
                    && slot.getCount() < Math.min(slot.getMaxStackSize(), inventory.getMaxStackSize())) return true;
        }
        return false;
    }

    /** Goblin scaffold the goblin stands in or on keeps its whole column from vanishing (see GoblinScaffoldBlock). */
    private void touchScaffolds(ServerLevel level) {
        net.minecraft.world.phys.AABB box = getBoundingBox().inflate(0.05);
        int lastX = Integer.MIN_VALUE, lastZ = Integer.MIN_VALUE;
        for (BlockPos pos : BlockPos.betweenClosed(net.minecraft.util.Mth.floor(box.minX), net.minecraft.util.Mth.floor(box.minY),
                net.minecraft.util.Mth.floor(box.minZ), net.minecraft.util.Mth.floor(box.maxX), net.minecraft.util.Mth.floor(box.maxY),
                net.minecraft.util.Mth.floor(box.maxZ))) {
            if (pos.getX() == lastX && pos.getZ() == lastZ) continue; // column already refreshed
            if (level.getBlockState(pos).getBlock() instanceof GoblinScaffoldBlock) {
                GoblinScaffoldBlock.touchColumn(level, pos);
                lastX = pos.getX();
                lastZ = pos.getZ();
            }
        }
    }

    /** Where the goblin died last; it walks back there after respawning to pick up its dropped storage. */
    public void setRecoverPos(@Nullable BlockPos pos, long until) {
        recoverPos = pos == null ? null : pos.immutable();
        recoverUntil = until;
    }

    @Nullable
    public BlockPos recoverPos() {
        return recoverPos;
    }

    public long recoverUntil() {
        return recoverUntil;
    }

    public void clearRecover() {
        recoverPos = null;
    }

    @Nullable
    public BlockPos lastTorchPos() {
        return lastTorchPos;
    }

    public void setLastTorchPos(BlockPos pos) {
        lastTorchPos = pos.immutable();
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (!level().isClientSide()) {
            player.openMenu(new GoblinMenuProvider(this));
        }
        return InteractionResult.SUCCESS;
    }

    // ---- death ---------------------------------------------------------------------------------------------------

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (level().isClientSide()) return;
        GoblinBedBlockEntity bed = bed();
        if (bed != null) bed.onGoblinDied(this);
        dropStorage();
    }

    // ---- persistence ---------------------------------------------------------------------------------------------

    @Override
    protected void addAdditionalSaveData(ValueOutput out) {
        super.addAdditionalSaveData(out);
        out.storeNullable("bed", BlockPos.CODEC, getBedPos().orElse(null));
        out.storeNullable("lastTorch", BlockPos.CODEC, lastTorchPos);
        out.store("style", GoblinStyle.CODEC, getStyle());
        List<ItemStack> items = new ArrayList<>(INVENTORY_SIZE);
        for (int i = 0; i < INVENTORY_SIZE; i++) items.add(inventory.getItem(i));
        out.store("inventory", ItemStack.OPTIONAL_CODEC.listOf(), items);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput in) {
        super.readAdditionalSaveData(in);
        entityData.set(DATA_BED, in.read("bed", BlockPos.CODEC));
        lastTorchPos = in.read("lastTorch", BlockPos.CODEC).orElse(null);
        in.read("style", GoblinStyle.CODEC).ifPresent(this::setStyle);
        List<ItemStack> items = in.read("inventory", ItemStack.OPTIONAL_CODEC.listOf()).orElse(List.of());
        for (int i = 0; i < INVENTORY_SIZE; i++) {
            inventory.setItem(i, i < items.size() ? items.get(i) : ItemStack.EMPTY);
        }
    }
}
