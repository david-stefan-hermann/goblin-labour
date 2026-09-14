package goblinlabour.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The block goblins stack under themselves to reach high logs and to climb out of pits. Behaves like vanilla
 * scaffolding: you can stand on top of it, climb inside it (it is in the {@code minecraft:climbable} tag) and sneak
 * to drop through it. Not craftable, drops nothing. Goblins never break it; every block removes itself two minutes
 * after a goblin last touched its column, so a column stays up while goblins are using it.
 */
public class GoblinScaffoldBlock extends Block {
    public static final MapCodec<GoblinScaffoldBlock> CODEC = simpleCodec(GoblinScaffoldBlock::new);
    /** Two minutes. */
    public static final int LIFETIME_TICKS = 2400;
    private static final int COLUMN_SCAN = 256;
    private static final int PRUNE_ABOVE = 8192;

    /** Game time of the last goblin touch per block position, per dimension. Lost on restart, see {@link #tick}. */
    private static final Map<ResourceKey<Level>, Map<Long, Long>> TOUCHED = new ConcurrentHashMap<>();

    private static final VoxelShape SHAPE_STABLE = Shapes.or(
            Block.box(0, 14, 0, 16, 16, 16),
            Block.box(0, 0, 0, 2, 16, 2),
            Block.box(14, 0, 0, 16, 16, 2),
            Block.box(0, 0, 14, 2, 16, 16),
            Block.box(14, 0, 14, 16, 16, 16));

    public GoblinScaffoldBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<GoblinScaffoldBlock> codec() {
        return CODEC;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE_STABLE;
    }

    @Override
    protected VoxelShape getInteractionShape(BlockState state, BlockGetter level, BlockPos pos) {
        return Shapes.block();
    }

    /** Solid only for whoever stands on top and is not sneaking; inside it there is no collision, like scaffolding. */
    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        if (context.isAbove(Shapes.block(), pos, true) && !context.isDescending()) return SHAPE_STABLE;
        return Shapes.empty();
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        if (!(level instanceof ServerLevel serverLevel)) return;
        touched(serverLevel).put(pos.asLong(), serverLevel.getGameTime());
        level.scheduleTick(pos, this, LIFETIME_TICKS);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        Map<Long, Long> touched = touched(level);
        long now = level.getGameTime();
        Long last = touched.get(pos.asLong());
        if (last == null || last > now) {
            // unknown after a restart (or from another world with the same dimension): count as touched just now
            touched.put(pos.asLong(), now);
            level.scheduleTick(pos, this, LIFETIME_TICKS);
            return;
        }
        long remaining = last + LIFETIME_TICKS - now;
        if (remaining > 0) {
            level.scheduleTick(pos, this, (int) remaining);
        } else {
            touched.remove(pos.asLong());
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        }
    }

    /** A goblin touched the block at {@code pos}: the whole column it belongs to starts its two minutes again. */
    public static void touchColumn(ServerLevel level, BlockPos pos) {
        Map<Long, Long> touched = touched(level);
        long now = level.getGameTime();
        if (touched.size() > PRUNE_ABOVE) touched.values().removeIf(t -> t + LIFETIME_TICKS < now);
        BlockPos.MutableBlockPos cursor = pos.mutable();
        for (int i = 0; i < COLUMN_SCAN && level.getBlockState(cursor).getBlock() instanceof GoblinScaffoldBlock; i++) {
            touched.put(cursor.asLong(), now);
            cursor.move(0, -1, 0);
        }
        cursor.set(pos).move(0, 1, 0);
        for (int i = 0; i < COLUMN_SCAN && level.getBlockState(cursor).getBlock() instanceof GoblinScaffoldBlock; i++) {
            touched.put(cursor.asLong(), now);
            cursor.move(0, 1, 0);
        }
    }

    private static Map<Long, Long> touched(ServerLevel level) {
        return TOUCHED.computeIfAbsent(level.dimension(), k -> new ConcurrentHashMap<>());
    }
}
