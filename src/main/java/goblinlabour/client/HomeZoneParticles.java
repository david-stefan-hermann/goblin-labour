package goblinlabour.client;

import goblinlabour.GoblinLabour;
import goblinlabour.block.GoblinBedBlockEntity;
import goblinlabour.home.HomeZone;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * While a Goblin Head is held, marks the edges of every (merged) home within a few chunks with static block-marker
 * particles showing the emerald item, so the 11 x 11 x 5 boxes are readable in the world.
 */
public final class HomeZoneParticles {
    private static final int CHUNK_RADIUS = 3;
    private static final int INTERVAL_TICKS = 30;
    private static final double MAX_DISTANCE_SQ = 48 * 48;

    private HomeZoneParticles() {
    }

    public static void tick(Minecraft minecraft) {
        ClientLevel level = minecraft.level;
        if (level == null || minecraft.player == null) return;
        if (level.getGameTime() % INTERVAL_TICKS != 0) return;
        if (!minecraft.player.getMainHandItem().is(GoblinLabour.GOBLIN_HEAD)
                && !minecraft.player.getOffhandItem().is(GoblinLabour.GOBLIN_HEAD)) return;

        List<BlockPos> beds = new ArrayList<>();
        int px = minecraft.player.chunkPosition().x();
        int pz = minecraft.player.chunkPosition().z();
        for (int cx = px - CHUNK_RADIUS; cx <= px + CHUNK_RADIUS; cx++) {
            for (int cz = pz - CHUNK_RADIUS; cz <= pz + CHUNK_RADIUS; cz++) {
                LevelChunk chunk = level.getChunkSource().getChunk(cx, cz, ChunkStatus.FULL, false);
                if (chunk == null) continue;
                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    if (be instanceof GoblinBedBlockEntity bed
                            && minecraft.player.distanceToSqr(Vec3.atCenterOf(bed.getBlockPos())) <= MAX_DISTANCE_SQ) {
                        beds.add(bed.getBlockPos());
                    }
                }
            }
        }
        BlockParticleOption marker = new BlockParticleOption(ParticleTypes.BLOCK_MARKER, GoblinLabour.HOME_MARKER.defaultBlockState());
        for (BoundingBox box : HomeZone.mergeBoxes(beds)) {
            outline(level, box, marker);
        }
    }

    /** A marker on every second block of the twelve edges of the box (corners always). */
    private static void outline(ClientLevel level, BoundingBox box, BlockParticleOption marker) {
        int[] xs = {box.minX(), box.maxX()};
        int[] ys = {box.minY(), box.maxY()};
        int[] zs = {box.minZ(), box.maxZ()};
        for (int y : ys) for (int z : zs) for (int x = box.minX(); x <= box.maxX(); x += 2) mark(level, marker, x, y, z);
        for (int y : ys) for (int x : xs) for (int z = box.minZ() + 2; z < box.maxZ(); z += 2) mark(level, marker, x, y, z);
        for (int x : xs) for (int z : zs) for (int y = box.minY() + 2; y < box.maxY(); y += 2) mark(level, marker, x, y, z);
    }

    private static void mark(ClientLevel level, BlockParticleOption marker, int x, int y, int z) {
        level.addParticle(marker, x + 0.5, y + 0.5, z + 0.5, 0.0, 0.0, 0.0);
    }
}
