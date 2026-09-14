package goblinlabour.job;

import goblinlabour.block.GoblinBedBlockEntity;
import goblinlabour.entity.GoblinEntity;
import goblinlabour.home.HomeRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;

/**
 * A square shaft straight down from the block the staff was pointed at, one layer at a time down to the target
 * height (bedrock by default). The layer to work on is found by scanning down, so nothing needs to be remembered
 * between ticks or restarts, and several goblins can share one shaft. With {@code stairs} a cobblestone spiral
 * stair is left along the wall (Minions Remastered's {@code stairCheck}, blackstone replaced by cobblestone): a
 * layer is dug out first, then its stair steps are placed one by one, then the next layer follows.
 */
public final class MineDownJob implements JobTask {
    public static final MineDownJob INSTANCE = new MineDownJob();
    private static final List<Direction> DIRECTIONS = List.of(Direction.NORTH, Direction.WEST, Direction.SOUTH, Direction.EAST);
    private static final BlockState STAIRS = Blocks.COBBLESTONE_STAIRS.defaultBlockState();
    private static final BlockState CORNER = Blocks.COBBLESTONE.defaultBlockState();

    private MineDownJob() {
    }

    @Override
    @Nullable
    public BlockPos entryPoint(GoblinBedBlockEntity bed, JobConfig config) {
        Assignment a = bed.getAssignment();
        return a == null ? null : a.origin().above();
    }

    @Override
    public void onDone(ServerLevel level, GoblinBedBlockEntity bed) {
        bed.setAssignment(null);
    }

    @Override
    public Pick pick(ServerLevel level, GoblinEntity goblin, GoblinBedBlockEntity bed, JobConfig config, Set<BlockPos> skipped) {
        Assignment a = bed.getAssignment();
        if (a == null || a.kind() != Assignment.Kind.DIG_DOWN) return Pick.DONE;
        BoundingBox box = a.shaftFootprint();
        int topY = a.topY();
        int bottomY = Math.max(a.bottomY(), level.getMinY());
        return scanLayers(level, goblin, box, topY, bottomY, a.stairs(), skipped, true);
    }

    /**
     * Scans the layers between topY and bottomY (downwards when {@code downwards}, else upwards). Per layer: first
     * the nearest breakable block, then the first missing stair step (as a placement), then the next layer.
     */
    static Pick scanLayers(ServerLevel level, GoblinEntity goblin, BoundingBox box, int topY, int bottomY, boolean stairs,
                           Set<BlockPos> skipped, boolean downwards) {
        Vec3 me = goblin.position();
        boolean sawToolProblem = false;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int layers = topY - bottomY + 1;
        for (int i = 0; i < layers; i++) {
            int y = downwards ? topY - i : bottomY + i;
            BlockPos best = null;
            double bestDist = Double.MAX_VALUE;
            BlockPos missingStair = null;
            BlockState missingState = null;
            boolean layerHasWork = false;
            for (int x = box.minX(); x <= box.maxX(); x++) {
                for (int z = box.minZ(); z <= box.maxZ(); z++) {
                    cursor.set(x, y, z);
                    BlockState state = level.getBlockState(cursor);
                    if (stairs) {
                        BlockState want = stairFor(cursor, box, topY);
                        if (want != null) {
                            if (state == want) continue;
                            if (state.canBeReplaced() && !HomeRegistry.isProtected(level, cursor) && !skipped.contains(cursor)) {
                                if (missingStair == null) {
                                    missingStair = cursor.immutable();
                                    missingState = want;
                                }
                                continue;
                            }
                        }
                    }
                    Mining.Verdict verdict = Mining.verdict(level, cursor, state, goblin);
                    if (verdict == Mining.Verdict.NEEDS_TOOL) {
                        sawToolProblem = true;
                        layerHasWork = true;
                        continue;
                    }
                    if (verdict != Mining.Verdict.OK || skipped.contains(cursor)) continue;
                    layerHasWork = true;
                    double d = me.distanceToSqr(Vec3.atCenterOf(cursor));
                    if (d < bestDist) {
                        bestDist = d;
                        best = cursor.immutable();
                    }
                }
            }
            if (best != null) return Pick.of(best);
            if (layerHasWork) return Pick.NEEDS_TOOL; // only unbreakable-without-tool blocks left on this layer
            if (missingStair != null) return Pick.place(missingStair, missingState);
        }
        return sawToolProblem ? Pick.NEEDS_TOOL : Pick.DONE;
    }

    /**
     * The block that belongs at {@code pos} if it is part of the spiral stair, or null. Straight port of Minions
     * Remastered's stairCheck (clockwise, start direction north).
     */
    @Nullable
    static BlockState stairFor(BlockPos pos, BoundingBox box, int topY) {
        int maxX = box.maxX();
        int minX = box.minX();
        int sizeMinusOne = maxX - minX;
        if (sizeMinusOne <= 0) return null;
        int maxZ = box.maxZ();
        int minZ = box.minZ();
        int x = pos.getX();
        int z = pos.getZ();
        boolean xMax = maxX == x;
        boolean xMin = minX == x;
        boolean zMax = maxZ == z;
        boolean zMin = minZ == z;

        int depth = topY - pos.getY();
        int adjustedSize = sizeMinusOne - 1;
        if (adjustedSize <= 0) adjustedSize = 1;

        int index = (depth / adjustedSize) % 4;
        Direction dir = DIRECTIONS.get(index);
        int adjustedDepth = depth % adjustedSize;

        if (dir.getAxis() == Direction.Axis.X) {
            Direction faceDir = DIRECTIONS.get((DIRECTIONS.indexOf(dir) + 1) % 4);
            if (sizeMinusOne == 1) faceDir = dir;
            int dirX = dir.getStepX();
            if (dirX == 1) {
                if (xMax) {
                    if (sizeMinusOne > 1 && adjustedDepth == 0 && zMin) return CORNER;
                    if (z - 1 == minZ + adjustedDepth) return STAIRS.setValue(StairBlock.FACING, faceDir);
                }
            } else if (xMin) {
                if (sizeMinusOne > 1 && adjustedDepth == 0 && zMax) return CORNER;
                if (z + 1 == maxZ - adjustedDepth) return STAIRS.setValue(StairBlock.FACING, faceDir);
            }
        } else {
            Direction faceDir = DIRECTIONS.get((DIRECTIONS.indexOf(dir) + 3) % 4);
            if (sizeMinusOne == 1) faceDir = dir.getOpposite();
            int dirZ = dir.getStepZ();
            if (dirZ == 1) {
                if (zMin) {
                    if (sizeMinusOne > 1 && adjustedDepth == 0 && xMin) return CORNER;
                    if (x - 1 == minX + adjustedDepth) return STAIRS.setValue(StairBlock.FACING, faceDir);
                }
            } else if (zMax) {
                if (sizeMinusOne > 1 && adjustedDepth == 0 && xMax) return CORNER;
                if (x + 1 == maxX - adjustedDepth) return STAIRS.setValue(StairBlock.FACING, faceDir);
            }
        }
        return null;
    }
}
