package goblinlabour.home;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * The "home" around a goblin bed: 5 blocks in every horizontal direction and 2 up/down (11 x 11 x 5, bed in the
 * middle). Goblins never mine, place torches or cobblestone inside any home; copper chests inside the home are
 * where they unload. Homes of beds that touch or overlap are merged into one box (a "flat").
 */
public final class HomeZone {
    public static final int RADIUS_XZ = 5;
    public static final int RADIUS_Y = 2;

    private HomeZone() {
    }

    public static BoundingBox of(BlockPos bed) {
        return new BoundingBox(bed.getX() - RADIUS_XZ, bed.getY() - RADIUS_Y, bed.getZ() - RADIUS_XZ,
                bed.getX() + RADIUS_XZ, bed.getY() + RADIUS_Y, bed.getZ() + RADIUS_XZ);
    }

    /** Outer edges of a box in world coordinates (max side is exclusive, so the box wraps whole blocks). */
    public static AABB aabbOf(BoundingBox box) {
        return new AABB(box.minX(), box.minY(), box.minZ(), box.maxX() + 1, box.maxY() + 1, box.maxZ() + 1);
    }

    public static boolean contains(BlockPos bed, BlockPos pos) {
        return Math.abs(pos.getX() - bed.getX()) <= RADIUS_XZ
                && Math.abs(pos.getZ() - bed.getZ()) <= RADIUS_XZ
                && Math.abs(pos.getY() - bed.getY()) <= RADIUS_Y;
    }

    /**
     * Merges the zones of the given beds: zones that touch or overlap (transitively) become one bounding box.
     * Used on the server for protection and chest lookup and on the client for the goblin-head display.
     */
    public static List<BoundingBox> mergeBoxes(Collection<BlockPos> beds) {
        List<BoundingBox> boxes = new ArrayList<>();
        for (BlockPos bed : beds) boxes.add(of(bed));
        boolean merged = true;
        while (merged) {
            merged = false;
            outer:
            for (int i = 0; i < boxes.size(); i++) {
                for (int j = i + 1; j < boxes.size(); j++) {
                    if (boxes.get(i).inflatedBy(1).intersects(boxes.get(j))) {
                        BoundingBox union = boxes.get(i).encapsulate(boxes.get(j));
                        boxes.set(i, union);
                        boxes.remove(j);
                        merged = true;
                        break outer;
                    }
                }
            }
        }
        return boxes;
    }
}
