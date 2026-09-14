package goblinlabour.block;

import goblinlabour.GoblinLabour;
import goblinlabour.GoblinNames;
import goblinlabour.GoblinSpeech;
import goblinlabour.entity.GoblinEntity;
import goblinlabour.entity.GoblinStyle;
import goblinlabour.home.HomeRegistry;
import goblinlabour.item.GoblinData;
import goblinlabour.job.JobConfig;
import goblinlabour.job.Assignment;
import goblinlabour.job.Job;
import net.minecraft.world.level.block.Block;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * Owns exactly one goblin and its job. Keeps a copy of the goblin's name, hotbar and health so the goblin can be
 * respawned after death (or restored into a blank when the bed is broken).
 */
public class GoblinBedBlockEntity extends BlockEntity {
    public static final int RESPAWN_TICKS = 100;
    /** A living goblin that has not been seen for this long is considered lost and gets respawned. */
    public static final int LOST_TICKS = 600;

    public enum Status { EMPTY, IDLE, WORKING, RESTING, WAITING_FULL, NO_EXIT, BLOCKED, RESPAWNING, WAITING_MARKER }
    /** How long a respawned goblin keeps looking for the items it dropped. */
    public static final int RECOVER_TICKS = 2400;

    @Nullable private UUID owner;
    @Nullable private UUID goblinUuid;
    private String goblinName = "";
    private NonNullList<ItemStack> hotbar = NonNullList.withSize(GoblinData.HOTBAR_SIZE, ItemStack.EMPTY);
    private float goblinHealth = GoblinData.MAX_HEALTH;
    private long respawnAt = -1;
    private int missingTicks;
    private int tickCounter;
    private Status status = Status.EMPTY;
    @Nullable private JobConfig job;
    @Nullable private Assignment assignment;
    @Nullable private BlockPos deathPos;
    /** Set by the last job that was not REST; the goblin (also after respawning) takes its texture tint from it. */
    private GoblinStyle style = GoblinStyle.LUMBERJACK;

    public GoblinBedBlockEntity(BlockPos pos, BlockState state) {
        super(GoblinLabour.GOBLIN_BED_BLOCK_ENTITY, pos, state);
    }

    // ---- registry ------------------------------------------------------------------------------------------------

    @Override
    public void setLevel(Level level) {
        super.setLevel(level);
        if (!level.isClientSide()) HomeRegistry.add(level, worldPosition);
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level != null && !level.isClientSide()) HomeRegistry.remove(level, worldPosition);
    }

    // ---- ticking -------------------------------------------------------------------------------------------------

    public static void serverTick(Level level, BlockPos pos, BlockState state, GoblinBedBlockEntity bed) {
        if (!(level instanceof ServerLevel serverLevel)) return;
        if (++bed.tickCounter % 20 != 0) return;
        bed.updateProps();
        if (bed.goblinUuid == null) return;

        GoblinEntity goblin = bed.findGoblin(serverLevel);
        if (goblin != null) {
            bed.missingTicks = 0;
            bed.mirror(goblin);
            if (bed.status == Status.EMPTY || bed.status == Status.RESPAWNING) bed.setStatus(Status.IDLE);
            return;
        }

        long now = serverLevel.getGameTime();
        if (bed.respawnAt >= 0) {
            if (now >= bed.respawnAt) {
                bed.spawnGoblin(serverLevel, bed.toData());
                GoblinSpeech.say(serverLevel, bed.goblinName, GoblinSpeech.RESPAWNED);
            }
            return;
        }
        bed.missingTicks += 20;
        if (bed.missingTicks >= LOST_TICKS) {
            GoblinLabour.LOGGER.info("Goblin {} at {} lost, respawning", bed.goblinName, pos);
            bed.spawnGoblin(serverLevel, bed.toData());
        }
    }

    @Nullable
    public GoblinEntity findGoblin(ServerLevel level) {
        if (goblinUuid == null) return null;
        Entity entity = level.getEntity(goblinUuid);
        return entity instanceof GoblinEntity goblin && goblin.isAlive() ? goblin : null;
    }

    /** Copies the goblin's hotbar and health so death/bed-break can restore them. */
    private void mirror(GoblinEntity goblin) {
        hotbar = goblin.hotbarCopy();
        goblinHealth = goblin.getHealth();
        setChanged();
    }

    // ---- spawning ------------------------------------------------------------------------------------------------

    public boolean trySpawnFromBlank(ServerLevel level, ItemStack blank, Player player) {
        if (findGoblin(level) != null || respawnAt >= 0) {
            player.sendSystemMessage(Component.translatable("goblinlabour.bed.occupied", goblinName));
            return false;
        }
        GoblinData data = blank.get(GoblinLabour.GOBLIN_DATA);
        if (data == null) data = GoblinData.fresh(GoblinNames.random(level.getRandom()));
        spawnGoblin(level, data);
        blank.consume(1, player);
        GoblinSpeech.say(level, goblinName, GoblinSpeech.SPAWNED);
        return true;
    }

    public GoblinEntity spawnGoblin(ServerLevel level, GoblinData data) {
        BlockPos pos = getBlockPos();
        GoblinEntity goblin = GoblinLabour.GOBLIN.spawn(level, pos, EntitySpawnReason.TRIGGERED);
        if (goblin == null) throw new IllegalStateException("Could not spawn goblin at " + pos);
        float yaw = getBlockState().getValue(GoblinBedBlock.FACING).getOpposite().toYRot();
        goblin.snapTo(pos.getX() + 0.5, pos.getY() + 0.5625, pos.getZ() + 0.5, yaw, 0.0f);
        goblin.setYBodyRot(yaw);
        goblin.setYHeadRot(yaw);
        goblin.bindToBed(pos, data);
        goblin.setStyle(style);
        if (deathPos != null) {
            goblin.setRecoverPos(deathPos, level.getGameTime() + RECOVER_TICKS);
            deathPos = null;
        }

        goblinUuid = goblin.getUUID();
        goblinName = data.name();
        hotbar = data.hotbarCopy();
        goblinHealth = data.health();
        respawnAt = -1;
        missingTicks = 0;
        setStatus(Status.IDLE);
        return goblin;
    }

    public void onGoblinDied(GoblinEntity goblin) {
        if (!goblin.getUUID().equals(goblinUuid)) return;
        hotbar = goblin.hotbarCopy();
        goblinHealth = GoblinData.MAX_HEALTH;
        deathPos = goblin.blockPosition();
        respawnAt = goblin.level().getGameTime() + RESPAWN_TICKS;
        setStatus(Status.RESPAWNING);
    }

    /** True if this bed owns the given goblin; goblins whose bed says no despawn themselves. */
    public boolean owns(UUID uuid) {
        return uuid.equals(goblinUuid);
    }

    // ---- removal -------------------------------------------------------------------------------------------------

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (!(level instanceof ServerLevel serverLevel) || goblinUuid == null) return;
        GoblinEntity goblin = findGoblin(serverLevel);
        if (goblin != null) {
            mirror(goblin);
            goblin.dropStorage();
            goblin.discard();
        }
        ItemStack blank = new ItemStack(GoblinLabour.GOBLIN_BLANK);
        blank.set(GoblinLabour.GOBLIN_DATA, toData());
        Containers.dropItemStack(serverLevel, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, blank);
        goblinUuid = null;
    }

    // ---- job -----------------------------------------------------------------------------------------------------

    public JobConfig getJob() {
        if (job == null) job = JobConfig.rest(getBlockState().getValue(GoblinBedBlock.FACING));
        return job;
    }

    public void setJob(JobConfig job) {
        this.job = job;
        GoblinStyle jobStyle = GoblinStyle.forJob(job.job());
        if (jobStyle != null) style = jobStyle;
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        if (level instanceof ServerLevel serverLevel) {
            GoblinEntity goblin = findGoblin(serverLevel);
            if (goblin != null) {
                goblin.setStyle(style);
                goblin.runner().onJobChanged();
            }
        }
        updateProps();
    }

    /** Tools and loot under the bed show the goblin's trade; a resting goblin (or an empty bed) has nothing there. */
    private void updateProps() {
        if (level == null || level.isClientSide()) return;
        BlockState state = getBlockState();
        if (!state.hasProperty(GoblinBedBlock.PROPS)) return;
        BedProps wanted = goblinUuid == null || getJob().job() == Job.REST ? BedProps.NONE : BedProps.of(style);
        if (state.getValue(GoblinBedBlock.PROPS) != wanted) {
            level.setBlock(worldPosition, state.setValue(GoblinBedBlock.PROPS, wanted), Block.UPDATE_ALL);
        }
    }

    public GoblinStyle getStyle() {
        return style;
    }

    @Nullable
    public Assignment getAssignment() {
        return assignment;
    }

    public void setAssignment(@Nullable Assignment assignment) {
        this.assignment = assignment;
        setChanged();
    }

    // ---- data ----------------------------------------------------------------------------------------------------

    public GoblinData toData() {
        return new GoblinData(goblinName, List.copyOf(hotbar), goblinHealth);
    }

    public void setOwner(UUID owner) {
        this.owner = owner;
        setChanged();
    }

    @Nullable
    public UUID getOwner() {
        return owner;
    }

    public String getGoblinName() {
        return goblinName;
    }

    public boolean hasGoblin() {
        return goblinUuid != null;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        if (this.status != status) {
            this.status = status;
            setChanged();
            if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    public Component describe() {
        if (goblinUuid == null) return Component.translatable("goblinlabour.bed.empty");
        return Component.translatable("goblinlabour.bed.status", goblinName,
                Component.translatable("goblinlabour.status." + status.name().toLowerCase()),
                Component.translatable(getJob().job().translationKey()));
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        out.storeNullable("owner", UUIDUtil.CODEC, owner);
        out.storeNullable("goblin", UUIDUtil.CODEC, goblinUuid);
        out.putString("name", goblinName);
        out.store("hotbar", ItemStack.OPTIONAL_CODEC.listOf(), List.copyOf(hotbar));
        out.putFloat("health", goblinHealth);
        out.putLong("respawnAt", respawnAt);
        out.putString("status", status.name());
        out.storeNullable("job", JobConfig.CODEC, job);
        out.storeNullable("order", Assignment.CODEC, assignment);
        out.storeNullable("deathPos", BlockPos.CODEC, deathPos);
        out.store("style", GoblinStyle.CODEC, style);
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        owner = in.read("owner", UUIDUtil.CODEC).orElse(null);
        goblinUuid = in.read("goblin", UUIDUtil.CODEC).orElse(null);
        goblinName = in.getStringOr("name", "");
        hotbar = new GoblinData(goblinName, in.read("hotbar", ItemStack.OPTIONAL_CODEC.listOf()).orElse(List.of()),
                GoblinData.MAX_HEALTH).hotbarCopy();
        goblinHealth = in.getFloatOr("health", GoblinData.MAX_HEALTH);
        respawnAt = in.getLongOr("respawnAt", -1);
        try {
            status = Status.valueOf(in.getStringOr("status", Status.EMPTY.name()));
        } catch (IllegalArgumentException e) {
            status = Status.EMPTY;
        }
        job = in.read("job", JobConfig.CODEC).orElse(null);
        assignment = in.read("order", Assignment.CODEC).orElse(null);
        deathPos = in.read("deathPos", BlockPos.CODEC).orElse(null);
        style = in.read("style", GoblinStyle.CODEC).orElse(GoblinStyle.LUMBERJACK);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
