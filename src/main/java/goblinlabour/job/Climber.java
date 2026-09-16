package goblinlabour.job;

import goblinlabour.GoblinLabour;
import goblinlabour.block.GoblinScaffoldBlock;
import goblinlabour.entity.GoblinEntity;
import goblinlabour.home.HomeRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.TorchBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

/**
 * Goblin scaffold climbing, shared by the work loop (high logs, shafts up) and the climb out of pits. Goblins climb
 * like a player on scaffolding: put a block into the block they stand in and hold "jump" inside it. They come down
 * by sneaking through the column, which slows the fall like a ladder. The blocks stay where they are and vanish by
 * themselves (see {@link goblinlabour.block.GoblinScaffoldBlock}), so another goblin can use the same column.
 */
public final class Climber {
    /** Tallest column a goblin builds. */
    public static final int MAX_HEIGHT = 160;
    private static final int PLACE_COOLDOWN = 4;
    /** Horizontal distance (squared) from the block centre that counts as standing in the middle of a column. */
    private static final double CENTRED_SQ = 0.15 * 0.15;
    /** Sneaking down that gets no lower for this long (caught on a neighbouring column) ends at the column's foot. */
    private static final int DESCEND_STUCK_TICKS = 60;
    /** How far below its feet a goblin in the air looks for the column it jumped off (a jump rises 1.25 blocks). */
    private static final int AIRBORNE_LOOK_DOWN = 3;

    private final GoblinEntity goblin;
    private int cooldown;
    private int lastDescendTick = -1000;
    private double descendLowest;
    private int descendStuck;

    public Climber(GoblinEntity goblin) {
        this.goblin = goblin;
    }

    public static boolean isScaffold(BlockState state) {
        return state.is(GoblinLabour.GOBLIN_SCAFFOLD);
    }

    /** True while the goblin is up on a column: inside it with more scaffold below, or standing on its top. */
    public static boolean isUp(Level level, GoblinEntity goblin) {
        return columnUnder(level, goblin) != null;
    }

    /**
     * The block (at feet height) of the scaffold column the goblin is up on, or null when it stands on solid ground.
     * Its own block first; a goblin at the edge of a column top can have its middle over the next block. A goblin in
     * the air (jumping on the top of its column) looks a few blocks further down: at the top of a jump there is only
     * air right below its feet, and it is still up there.
     */
    @Nullable
    public static BlockPos columnUnder(Level level, GoblinEntity goblin) {
        BlockPos feet = goblin.blockPosition();
        if (isScaffold(level.getBlockState(feet.below()))) return feet;
        // shrunk a little: pressed against a wall the box edge sits a hair inside the wall's block column
        AABB box = goblin.getBoundingBox().deflate(0.01, 0.0, 0.01);
        int depth = goblin.onGround() ? 1 : AIRBORNE_LOOK_DOWN;
        for (int drop = 1; drop <= depth; drop++) {
            BlockPos column = null;
            boolean open = true;
            int y = feet.getY() - drop;
            for (int x = Mth.floor(box.minX); x <= Mth.floor(box.maxX); x++) {
                for (int z = Mth.floor(box.minZ); z <= Mth.floor(box.maxZ); z++) {
                    BlockPos below = new BlockPos(x, y, z);
                    BlockState state = level.getBlockState(below);
                    if (isScaffold(state)) {
                        if (column == null) column = below.above(drop);
                        open = false;
                    } else if (!state.getCollisionShape(level, below).isEmpty()) {
                        return null; // partly on solid ground
                    }
                }
            }
            if (!open) return column;
        }
        return null;
    }

    /**
     * One tick of climbing up the column the goblin stands in, placing a block when there is none. Leaves in the
     * way are broken, an own torch in the column is taken along. Returns false when there is no room above, the
     * spot is inside a home or the column is as high as it gets.
     */
    public boolean climb(ServerLevel level) {
        goblin.setShiftKeyDown(false);
        goblin.getNavigation().stop();
        BlockPos feet = goblin.blockPosition();
        BlockState feetState = level.getBlockState(feet);
        // stay in the middle of the column: at its edge the hitbox catches on the blocks next to it and the goblin
        // hangs in place while holding jump
        double dx = feet.getX() + 0.5 - goblin.getX(), dz = feet.getZ() + 0.5 - goblin.getZ();
        boolean centred = dx * dx + dz * dz < CENTRED_SQ;
        if (!centred) goblin.getMoveControl().setWantedPosition(feet.getX() + 0.5, goblin.getY(), feet.getZ() + 0.5, 0.4);
        if (isScaffold(feetState)) {
            goblin.getJumpControl().jump();
            return true;
        }
        if (!goblin.onGround() || !centred) return true; // still landing on the last block, or stepping to the middle
        if (cooldown-- > 0) return true;
        cooldown = PLACE_COOLDOWN;
        // the goblin rises through the block above its feet and needs its head free one further up
        for (BlockPos above : new BlockPos[]{feet.above(), feet.above(2)}) {
            BlockState aboveState = level.getBlockState(above);
            if (aboveState.getCollisionShape(level, above).isEmpty() || isScaffold(aboveState)) continue;
            if (!aboveState.is(BlockTags.LEAVES) || HomeRegistry.isProtected(level, above)) return false;
            level.destroyBlock(above, true, goblin, 512);
        }
        if (HomeRegistry.isProtected(level, feet)) return false;
        if (!feetState.canBeReplaced()) {
            if (!(feetState.getBlock() instanceof TorchBlock)) return false;
            level.destroyBlock(feet, true, goblin, 512); // own torch in the column: take it along, it gets re-placed later
        }
        if (columnBelow(level, feet) >= MAX_HEIGHT) return false;
        BlockState scaffold = ((GoblinScaffoldBlock) GoblinLabour.GOBLIN_SCAFFOLD).placementState(level, feet);
        if (scaffold == null) return false; // nothing below would hold it (vanilla stability rules)
        level.setBlock(feet, scaffold, 3);
        level.playSound(null, feet, SoundEvents.SCAFFOLDING_PLACE, SoundSource.BLOCKS, 0.8f, 1.0f);
        goblin.swing(InteractionHand.MAIN_HAND);
        goblin.getJumpControl().jump();
        return true;
    }

    /**
     * Sneaks down through the column, centred on it. Returns true while the goblin is still on the way down. A goblin
     * that gets no lower for a few seconds (its hitbox caught on a neighbouring column) is set down at the foot.
     */
    public boolean descend(ServerLevel level) {
        goblin.getNavigation().stop();
        BlockPos column = columnUnder(level, goblin);
        if (column == null) {
            goblin.setShiftKeyDown(false);
            return false;
        }
        if (goblin.tickCount - lastDescendTick > 5) {
            descendLowest = goblin.getY();
            descendStuck = 0;
        }
        lastDescendTick = goblin.tickCount;
        goblin.setShiftKeyDown(true);
        goblin.getMoveControl().setWantedPosition(column.getX() + 0.5, goblin.getY(), column.getZ() + 0.5, 0.5);
        if (goblin.getY() < descendLowest - 0.5) {
            descendLowest = goblin.getY();
            descendStuck = 0;
        } else if (++descendStuck > DESCEND_STUCK_TICKS) {
            settle(level);
            lastDescendTick = -1000;
        }
        return true;
    }

    /** When a job ends mid-climb: stop sneaking and set the goblin down at the foot of its column. */
    public void settle(ServerLevel level) {
        goblin.setShiftKeyDown(false);
        BlockPos column = columnUnder(level, goblin);
        if (column == null) return;
        BlockPos.MutableBlockPos cursor = column.mutable();
        for (int i = 0; i < MAX_HEIGHT && isScaffold(level.getBlockState(cursor.below())); i++) {
            cursor.move(0, -1, 0);
        }
        goblin.snapTo(cursor.getX() + 0.5, cursor.getY(), cursor.getZ() + 0.5, goblin.getYRot(), goblin.getXRot());
        goblin.getNavigation().stop();
    }

    /** Scaffold blocks directly below {@code pos}. */
    private static int columnBelow(Level level, BlockPos pos) {
        BlockPos.MutableBlockPos cursor = pos.mutable();
        int n = 0;
        while (n < MAX_HEIGHT && isScaffold(level.getBlockState(cursor.move(0, -1, 0)))) n++;
        return n;
    }
}
