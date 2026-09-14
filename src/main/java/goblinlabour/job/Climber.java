package goblinlabour.job;

import goblinlabour.GoblinLabour;
import goblinlabour.entity.GoblinEntity;
import goblinlabour.home.HomeRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.TorchBlock;
import net.minecraft.world.level.block.state.BlockState;

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

    private final GoblinEntity goblin;
    private int cooldown;

    public Climber(GoblinEntity goblin) {
        this.goblin = goblin;
    }

    public static boolean isScaffold(BlockState state) {
        return state.is(GoblinLabour.GOBLIN_SCAFFOLD);
    }

    /** True while the goblin is up on a column: inside it with more scaffold below, or standing on its top. */
    public static boolean isUp(Level level, GoblinEntity goblin) {
        return isScaffold(level.getBlockState(goblin.blockPosition().below()));
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
        BlockPos head = feet.above(2);
        BlockState headState = level.getBlockState(head);
        if (!headState.getCollisionShape(level, head).isEmpty() && !isScaffold(headState)) {
            if (!headState.is(BlockTags.LEAVES) || HomeRegistry.isProtected(level, head)) return false;
            level.destroyBlock(head, true, goblin, 512);
        }
        if (HomeRegistry.isProtected(level, feet)) return false;
        if (!feetState.canBeReplaced()) {
            if (!(feetState.getBlock() instanceof TorchBlock)) return false;
            level.destroyBlock(feet, true, goblin, 512); // own torch in the column: take it along, it gets re-placed later
        }
        if (columnBelow(level, feet) >= MAX_HEIGHT) return false;
        level.setBlock(feet, GoblinLabour.GOBLIN_SCAFFOLD.defaultBlockState(), 3);
        level.playSound(null, feet, SoundEvents.SCAFFOLDING_PLACE, SoundSource.BLOCKS, 0.8f, 1.0f);
        goblin.swing(InteractionHand.MAIN_HAND);
        goblin.getJumpControl().jump();
        return true;
    }

    /** Sneaks down through the column, centred on it. Returns true while the goblin is still on the way down. */
    public boolean descend(ServerLevel level) {
        goblin.getNavigation().stop();
        if (!isUp(level, goblin)) {
            goblin.setShiftKeyDown(false);
            return false;
        }
        goblin.setShiftKeyDown(true);
        BlockPos feet = goblin.blockPosition();
        goblin.getMoveControl().setWantedPosition(feet.getX() + 0.5, goblin.getY(), feet.getZ() + 0.5, 0.5);
        return true;
    }

    /** When a job ends mid-climb: stop sneaking and set the goblin down at the foot of its column. */
    public void settle(ServerLevel level) {
        goblin.setShiftKeyDown(false);
        if (!isUp(level, goblin)) return;
        BlockPos.MutableBlockPos cursor = goblin.blockPosition().mutable();
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
