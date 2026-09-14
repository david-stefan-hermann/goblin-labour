package goblinlabour.home;

import goblinlabour.block.GoblinChestBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Positions of all loaded goblin beds per dimension. Beds register when their block entity is added to a level and
 * unregister when it is removed or unloaded, so a lookup never touches unloaded chunks. Zones of touching beds are
 * merged into flats (see {@link HomeZone#mergeBoxes}); the merged boxes are cached until a bed comes or goes.
 */
public final class HomeRegistry {
    private static final Map<ResourceKey<Level>, Set<BlockPos>> BEDS = new ConcurrentHashMap<>();
    private static final Map<ResourceKey<Level>, List<BoundingBox>> FLATS = new ConcurrentHashMap<>();

    private HomeRegistry() {
    }

    public static void add(Level level, BlockPos pos) {
        BEDS.computeIfAbsent(level.dimension(), k -> ConcurrentHashMap.newKeySet()).add(pos.immutable());
        FLATS.remove(level.dimension());
    }

    public static void remove(Level level, BlockPos pos) {
        Set<BlockPos> set = BEDS.get(level.dimension());
        if (set != null) set.remove(pos);
        FLATS.remove(level.dimension());
    }

    public static Set<BlockPos> beds(Level level) {
        return BEDS.getOrDefault(level.dimension(), Collections.emptySet());
    }

    /** Merged home boxes of all loaded beds in this level. */
    public static List<BoundingBox> flats(Level level) {
        return FLATS.computeIfAbsent(level.dimension(), k -> HomeZone.mergeBoxes(beds(level)));
    }

    /** True if {@code pos} lies inside any (merged) home in this level. */
    public static boolean isProtected(Level level, BlockPos pos) {
        for (BoundingBox box : flats(level)) {
            if (box.isInside(pos)) return true;
        }
        return false;
    }

    /** The merged box the given bed belongs to (its own zone if it stands alone). */
    public static BoundingBox flatBox(Level level, BlockPos bed) {
        for (BoundingBox box : flats(level)) {
            if (box.isInside(bed)) return box;
        }
        return HomeZone.of(bed);
    }

    /** Goblin chests inside the bed's flat (loaded chunks only; both halves of a double chest). */
    public static List<BlockPos> goblinChests(ServerLevel level, BlockPos bed) {
        BoundingBox box = flatBox(level, bed);
        List<BlockPos> chests = new ArrayList<>();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int x = box.minX(); x <= box.maxX(); x++) {
            for (int z = box.minZ(); z <= box.maxZ(); z++) {
                if (!level.isLoaded(cursor.set(x, box.minY(), z))) continue;
                for (int y = box.minY(); y <= box.maxY(); y++) {
                    cursor.set(x, y, z);
                    if (level.getBlockState(cursor).getBlock() instanceof GoblinChestBlock) chests.add(cursor.immutable());
                }
            }
        }
        return chests;
    }
}
