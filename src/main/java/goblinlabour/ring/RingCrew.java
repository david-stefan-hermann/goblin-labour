package goblinlabour.ring;

import goblinlabour.GoblinLabour;
import goblinlabour.GoblinNames;
import goblinlabour.GoblinSounds;
import goblinlabour.entity.GoblinEntity;
import goblinlabour.entity.ai.CrewGoal;
import goblinlabour.home.HomeRegistry;
import goblinlabour.job.Mining;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.fabricmc.fabric.api.tag.convention.v2.ConventionalBlockTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The mining crews of Goblin Rings (server side, never saved). Right-clicking a ring calls three goblins to the player
 * for five minutes, or sends a running crew home early. The crew also leaves when the player logs out, dies, changes
 * dimension or no longer carries the ring. Crew goblins are goblin entities in a mode of their own (see
 * {@link CrewGoal}): no bed, no storage (loot goes straight into the ring), diamond tools, not saved with the world.
 */
public final class RingCrew {
    public static final int CREW_SIZE = 3;
    public static final int DURATION_TICKS = 5 * 60 * 20;
    /** Horizontal distance a crew goblin may be away from its player: one chunk. */
    public static final double LEASH = 8.0;
    /** Ores, items and stand spots are only taken this close to the player, so the work never pulls a goblin off the leash. */
    public static final int WORK_RADIUS = 6;
    public static final int CHEST_RANGE = 16;
    /** Ticks before a crew picks up what its own player threw (the 40 tick pick-up delay covers the first half). */
    private static final int OWNER_GRACE = 60;
    private static final int ORE_SCAN_INTERVAL = 20;
    private static final int ORE_SCAN_DY = 6;
    /** A vein: at most this many connected ore blocks, reaching this far (horizontally) from the player. */
    private static final int MAX_VEIN = 64, VEIN_REACH = WORK_RADIUS + 4;
    /** A vein that loses no block for this long is walled in by stone the crew does not dig: it is given up. */
    private static final int VEIN_PATIENCE = 600;
    /**
     * Blocks the crew helps dig when the player breaks one: world-generated ground. There is no telling a generated
     * block from a placed one, so the tag stands in for it; a datapack can change it.
     */
    public static final TagKey<Block> NATURAL = TagKey.create(Registries.BLOCK, GoblinLabour.id("natural"));
    /** How long the crew keeps helping after the player's last natural block. */
    private static final int ASSIST_TICKS = 400;
    private static final int WARN_TICKS = 30 * 20;
    private static final int FULL_NOTICE_TICKS = 60 * 20;

    /** Running crews by ring id. */
    private static final Map<UUID, Session> SESSIONS = new ConcurrentHashMap<>();

    private RingCrew() {
    }

    /** One called crew: its player, its goblins, the time left and what the goblins have claimed between them. */
    public static final class Session {
        private final UUID ringId;
        private final ServerPlayer owner;
        private final ServerLevel level;
        private final List<GoblinEntity> goblins = new ArrayList<>();
        private long endTick;
        private boolean warned;
        private long nextFullNotice;
        /** Exposed ores around the player, refreshed every second. */
        private List<BlockPos> ores = List.of();
        /** The ore vein the crew is taking out: every ore block connected to the first one, hidden ones included. */
        private final Set<BlockPos> vein = new HashSet<>();
        private long veinProgress;
        /** The last natural block the player broke, and until when the crew helps around it. */
        @Nullable private BlockPos assistFocus;
        private long assistUntil;
        /** The goblin carrying the loot to a chest; one at a time, the others go on working. */
        @Nullable private UUID porter;
        private final Map<BlockPos, UUID> blockClaims = new HashMap<>();
        private final Map<Integer, UUID> itemClaims = new HashMap<>();
        /** Where each goblin is walking to or standing about (stroll, aside, return), so two never pick the same spot. */
        private final Map<UUID, BlockPos> spots = new HashMap<>();

        private Session(UUID ringId, ServerPlayer owner, ServerLevel level, long endTick) {
            this.ringId = ringId;
            this.owner = owner;
            this.level = level;
            this.endTick = endTick;
        }

        public UUID ringId() {
            return ringId;
        }

        public ServerPlayer owner() {
            return owner;
        }

        public List<GoblinEntity> goblins() {
            return goblins;
        }

        public List<BlockPos> ores() {
            return ores;
        }

        public Set<BlockPos> vein() {
            return vein;
        }

        /** The player broke a natural block here: the crew helps dig around it for the next 20 seconds. */
        public void assist(BlockPos pos) {
            assistFocus = pos.immutable();
            assistUntil = level.getGameTime() + ASSIST_TICKS;
        }

        /**
         * Gives every crew goblin a fresh diamond pickaxe, shovel and axe carrying the enchantments of the ring's books:
         * each enchantment onto every tool it fits, the higher level where both books have it. Called when the crew
         * comes out and whenever the book slots change. Efficiency speeds up the digging, Fortune and Silk Touch change
         * the drops (both are read from the tool in Mining).
         */
        public void refreshTools() {
            ItemStack ring = RingInventory.find(owner, ringId);
            List<ItemStack> books = ring == null ? List.of()
                    : ring.getOrDefault(GoblinLabour.RING_BOOKS, ItemContainerContents.EMPTY).nonEmptyItemCopyStream().toList();
            for (GoblinEntity goblin : goblins) {
                ItemStack[] tools = {new ItemStack(Items.DIAMOND_PICKAXE), new ItemStack(Items.DIAMOND_SHOVEL), new ItemStack(Items.DIAMOND_AXE)};
                for (ItemStack tool : tools) {
                    for (ItemStack book : books) {
                        ItemEnchantments stored = EnchantmentHelper.getEnchantmentsForCrafting(book);
                        EnchantmentHelper.updateEnchantments(tool, mutable -> {
                            for (Object2IntMap.Entry<Holder<Enchantment>> entry : stored.entrySet()) {
                                if (entry.getKey().value().canEnchant(tool)) mutable.upgrade(entry.getKey(), entry.getIntValue());
                            }
                        });
                    }
                }
                for (int i = 0; i < tools.length; i++) goblin.getInventory().setItem(i, tools[i]);
            }
        }

        /** Where the crew helps the player dig right now, or null once the player has stopped for a while. */
        @Nullable
        public BlockPos assistFocus() {
            return assistFocus != null && level.getGameTime() < assistUntil ? assistFocus : null;
        }

        /**
         * Starts a vein at an ore a goblin is about to mine: floods out over all 26 neighbours to every connected ore
         * block (mixed veins count), whether exposed or not, up to MAX_VEIN blocks within VEIN_REACH of the player.
         */
        public void startVein(BlockPos start) {
            vein.clear();
            veinProgress = level.getGameTime();
            Deque<BlockPos> open = new ArrayDeque<>();
            vein.add(start.immutable());
            open.add(start.immutable());
            while (!open.isEmpty() && vein.size() < MAX_VEIN) {
                BlockPos pos = open.poll();
                for (int dx = -1; dx <= 1 && vein.size() < MAX_VEIN; dx++) {
                    for (int dy = -1; dy <= 1 && vein.size() < MAX_VEIN; dy++) {
                        for (int dz = -1; dz <= 1 && vein.size() < MAX_VEIN; dz++) {
                            BlockPos next = pos.offset(dx, dy, dz);
                            if (vein.contains(next) || !inVeinReach(next) || !level.isLoaded(next)) continue;
                            if (!level.getBlockState(next).is(ConventionalBlockTags.ORES) || HomeRegistry.isProtected(level, next)) continue;
                            vein.add(next.immutable());
                            open.add(next.immutable());
                        }
                    }
                }
            }
        }

        private boolean inVeinReach(BlockPos pos) {
            double dx = pos.getX() + 0.5 - owner.getX(), dz = pos.getZ() + 0.5 - owner.getZ();
            return dx * dx + dz * dz <= VEIN_REACH * VEIN_REACH && Math.abs(pos.getY() - owner.getBlockY()) <= ORE_SCAN_DY;
        }

        /** Drops vein blocks that are no ore any more; gives the vein up when it has lost nothing for a while. */
        private void pruneVein() {
            if (vein.isEmpty()) return;
            int before = vein.size();
            vein.removeIf(pos -> !level.getBlockState(pos).is(ConventionalBlockTags.ORES));
            long now = level.getGameTime();
            if (vein.size() < before) veinProgress = now;
            else if (now - veinProgress > VEIN_PATIENCE) vein.clear(); // what is left is walled in
        }

        public long ticksLeft() {
            return endTick - level.getGameTime();
        }

        public void setTicksLeft(int ticks) {
            endTick = level.getGameTime() + ticks;
            warned = ticks <= WARN_TICKS;
        }

        public boolean mayCarry(GoblinEntity goblin) {
            return porter == null || porter.equals(goblin.getUUID());
        }

        public void setPorter(GoblinEntity goblin) {
            porter = goblin.getUUID();
        }

        public boolean isClaimed(BlockPos pos, GoblinEntity goblin) {
            UUID by = blockClaims.get(pos);
            return by != null && !by.equals(goblin.getUUID());
        }

        public boolean isClaimed(ItemEntity item, GoblinEntity goblin) {
            UUID by = itemClaims.get(item.getId());
            return by != null && !by.equals(goblin.getUUID());
        }

        public void claim(BlockPos pos, GoblinEntity goblin) {
            blockClaims.put(pos.immutable(), goblin.getUUID());
        }

        public void claim(ItemEntity item, GoblinEntity goblin) {
            itemClaims.put(item.getId(), goblin.getUUID());
        }

        /** Drops the goblin's claims (ore, stand spot, item, walking spot) and its porter role. */
        public void releaseAll(GoblinEntity goblin) {
            UUID id = goblin.getUUID();
            blockClaims.values().removeIf(id::equals);
            itemClaims.values().removeIf(id::equals);
            spots.remove(id);
            if (id.equals(porter)) porter = null;
        }

        /** The goblin now heads for (or stands at) this spot; null gives its spot up. */
        public void holdSpot(GoblinEntity goblin, @Nullable BlockPos spot) {
            if (spot == null) spots.remove(goblin.getUUID());
            else spots.put(goblin.getUUID(), spot.immutable());
        }

        /** Where the other goblins of the crew stand and where they are heading. */
        public Set<BlockPos> spotsOfOthers(GoblinEntity goblin) {
            Set<BlockPos> taken = new HashSet<>();
            UUID id = goblin.getUUID();
            for (Map.Entry<UUID, BlockPos> entry : spots.entrySet()) {
                if (!entry.getKey().equals(id)) taken.add(entry.getValue());
            }
            for (GoblinEntity mate : goblins) {
                if (mate != goblin && mate.isAlive()) taken.add(mate.blockPosition());
            }
            return taken;
        }

        public void notifyFull() {
            long now = level.getGameTime();
            if (now < nextFullNotice) return;
            nextFullNotice = now + FULL_NOTICE_TICKS;
            owner.sendOverlayMessage(Component.translatable("goblinlabour.ring.full"));
        }

        /** Ore blocks around the player with at least one open side; whether a goblin can see one is its own check. */
        private void scanOres() {
            BlockPos center = owner.blockPosition();
            List<BlockPos> found = new ArrayList<>();
            BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
            int r = WORK_RADIUS;
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (dx * dx + dz * dz > r * r) continue;
                    if (!level.isLoaded(cursor.set(center.getX() + dx, center.getY(), center.getZ() + dz))) continue;
                    for (int dy = -ORE_SCAN_DY; dy <= ORE_SCAN_DY; dy++) {
                        cursor.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
                        if (!level.getBlockState(cursor).is(ConventionalBlockTags.ORES) || !exposed(level, cursor)) continue;
                        if (HomeRegistry.isProtected(level, cursor)) continue;
                        found.add(cursor.immutable());
                    }
                }
            }
            ores = found;
        }
    }

    @Nullable
    public static Session of(UUID ringId) {
        return SESSIONS.get(ringId);
    }

    /** Right-click with a ring: send its crew home, or call a new one (a player has at most one crew). */
    public static void toggle(ServerLevel level, ServerPlayer player, ItemStack ring) {
        UUID ringId = RingInventory.ensureId(ring);
        Session running = SESSIONS.get(ringId);
        if (running != null) {
            end(running, "goblinlabour.ring.dismissed");
            return;
        }
        for (Session other : List.copyOf(SESSIONS.values())) {
            if (other.owner.getUUID().equals(player.getUUID())) end(other, null);
        }
        Session session = new Session(ringId, player, level, level.getGameTime() + DURATION_TICKS);
        Set<BlockPos> taken = new HashSet<>();
        for (int i = 0; i < CREW_SIZE; i++) {
            BlockPos spot = spotNearOwner(level, player, level.getRandom(), taken);
            if (spot == null) continue;
            taken.add(spot);
            GoblinEntity goblin = new GoblinEntity(GoblinLabour.GOBLIN, level);
            goblin.snapTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, level.getRandom().nextFloat() * 360.0f, 0.0f);
            goblin.joinCrew(session, GoblinNames.random(level.getRandom()));
            if (!level.addFreshEntity(goblin)) continue;
            session.goblins.add(goblin);
            poof(level, goblin);
        }
        if (session.goblins.isEmpty()) {
            player.sendOverlayMessage(Component.translatable("goblinlabour.ring.no_room"));
            return;
        }
        SESSIONS.put(ringId, session);
        session.refreshTools();
        session.scanOres();
        level.playSound(null, player.getX(), player.getY(), player.getZ(), GoblinSounds.YES, SoundSource.NEUTRAL, 1.0f, 1.0f);
        player.sendOverlayMessage(Component.translatable("goblinlabour.ring.summoned", session.goblins.size(), DURATION_TICKS / 1200));
    }

    /** Server tick: ends crews whose time is up or whose player is gone, warns 30 s before the end, rescans ores. */
    public static void tick(MinecraftServer server) {
        if (SESSIONS.isEmpty()) return;
        for (Session session : List.copyOf(SESSIONS.values())) {
            ServerPlayer owner = session.owner;
            session.goblins.removeIf(goblin -> goblin.isRemoved() || !goblin.isAlive());
            if (owner.isRemoved() || owner.hasDisconnected() || !owner.isAlive() || owner.level() != session.level
                    || RingInventory.find(owner, session.ringId) == null || session.goblins.isEmpty()) {
                end(session, "goblinlabour.ring.dismissed");
                continue;
            }
            long left = session.ticksLeft();
            if (left <= 0) {
                end(session, "goblinlabour.ring.expired");
                continue;
            }
            if (!session.warned && left <= WARN_TICKS) {
                session.warned = true;
                owner.sendOverlayMessage(Component.translatable("goblinlabour.ring.ending", left / 20));
            }
            if (session.level.getGameTime() % ORE_SCAN_INTERVAL == 0) {
                session.scanOres();
                session.pruneVein();
            }
        }
    }

    /** Fabric's after-break hook: a player with a crew who digs a natural block gets help around it. */
    public static void afterBlockBreak(Level level, Player player, BlockPos pos, BlockState state, @Nullable BlockEntity blockEntity) {
        if (level.isClientSide() || !state.is(NATURAL)) return;
        for (Session session : SESSIONS.values()) {
            if (session.owner.getUUID().equals(player.getUUID())) session.assist(pos);
        }
    }

    /** Server stop: the crews vanish (their goblins are not saved anyway). */
    public static void endAll() {
        for (Session session : List.copyOf(SESSIONS.values())) end(session, null);
    }

    private static void end(Session session, @Nullable String message) {
        SESSIONS.remove(session.ringId, session);
        for (GoblinEntity goblin : session.goblins) {
            if (goblin.isRemoved()) continue;
            poof(session.level, goblin);
            goblin.discard();
        }
        session.goblins.clear();
        if (message != null && !session.owner.hasDisconnected()) session.owner.sendOverlayMessage(Component.translatable(message));
    }

    private static void poof(ServerLevel level, GoblinEntity goblin) {
        GoblinEntity.poof(level, goblin.getX(), goblin.getY(), goblin.getZ());
    }

    /** Puts as much of the item as fits into the crew's ring, with the pick-up animation. False when nothing was taken. */
    public static boolean store(GoblinEntity goblin, ItemEntity item) {
        Session session = goblin.crew();
        if (session == null || !collectable(item, session.owner)) return false;
        RingContainer ring = RingInventory.open(session.owner, session.ringId);
        if (ring == null) return false;
        ItemStack stack = item.getItem();
        int before = stack.getCount();
        ItemStack rest = ring.addItem(stack.copy());
        int taken = before - rest.getCount();
        if (taken <= 0) {
            session.notifyFull();
            return false;
        }
        ring.setChanged();
        goblin.take(item, taken);
        if (rest.isEmpty()) item.discard();
        else item.setItem(rest);
        return true;
    }

    /** True if at least part of the stack fits into the ring of the goblin's crew. */
    public static boolean canStore(GoblinEntity goblin, ItemStack stack) {
        Session session = goblin.crew();
        RingContainer ring = session == null ? null : RingInventory.open(session.owner, session.ringId);
        return ring != null && stack.getItem().canFitInsideContainerItems() && ring.canAddItem(stack);
    }

    /** True when the ring of the goblin's crew has no empty slot left (or the ring is gone). */
    public static boolean isFull(GoblinEntity goblin) {
        Session session = goblin.crew();
        RingContainer ring = session == null ? null : RingInventory.open(session.owner, session.ringId);
        if (ring == null) return true;
        for (int i = 0; i < ring.getContainerSize(); i++) {
            if (ring.getItem(i).isEmpty()) return false;
        }
        return true;
    }

    /** Loose items a crew takes: no container items, nothing a player threw on purpose. */
    public static boolean collectable(ItemEntity item, Player owner) {
        if (!item.getItem().getItem().canFitInsideContainerItems()) return false;
        // what the ring's owner threw is left alone for a moment, so a misclick can be picked back up
        return item.getOwner() != owner || item.getAge() >= OWNER_GRACE;
    }

    /**
     * A free spot 2 to 4 blocks from the player, out of the player's way and in the player's sight (its ground
     * block can be seen, so it is not in the next cave over). Null after a number of misses.
     */
    @Nullable
    public static BlockPos spotNearOwner(ServerLevel level, Player owner, RandomSource random, Set<BlockPos> taken) {
        int feetY = owner.blockPosition().getY();
        Vec3 eyes = owner.getEyePosition();
        for (int attempt = 0; attempt < 32; attempt++) {
            double angle = random.nextDouble() * Mth.TWO_PI;
            double r = 2.0 + random.nextDouble() * 2.0;
            int x = Mth.floor(owner.getX() + Math.cos(angle) * r);
            int z = Mth.floor(owner.getZ() + Math.sin(angle) * r);
            for (int y = feetY + 2; y >= feetY - 3; y--) {
                BlockPos spot = new BlockPos(x, y, z);
                if (!CrewGoal.standable(level, spot)) continue;
                if (!nearTaken(spot, taken) && CrewGoal.clearOfWay(owner, Vec3.atBottomCenterOf(spot)) && Mining.canSee(level, eyes, spot.below())) {
                    return spot;
                }
                break;
            }
        }
        return null;
    }

    /**
     * Whether a spot is taken: a crew mate stands or heads there, or in one of the eight blocks around it. Checking the
     * block alone lets two goblins pick neighbouring blocks and end up standing in each other.
     */
    public static boolean nearTaken(BlockPos spot, Set<BlockPos> taken) {
        for (BlockPos other : taken) {
            if (Math.abs(other.getX() - spot.getX()) <= 1 && Math.abs(other.getZ() - spot.getZ()) <= 1
                    && Math.abs(other.getY() - spot.getY()) <= 1) {
                return true;
            }
        }
        return false;
    }

    public static boolean exposed(ServerLevel level, BlockPos pos) {
        for (Direction direction : Direction.values()) {
            BlockPos side = pos.relative(direction);
            if (level.getBlockState(side).getCollisionShape(level, side).isEmpty()) return true;
        }
        return false;
    }
}
