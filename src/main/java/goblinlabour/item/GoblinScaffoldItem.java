package goblinlabour.item;

import goblinlabour.block.GoblinScaffoldBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Goblin scaffold in a player's hand, a port of vanilla {@code ScaffoldingBlockItem}: clicking a scaffold block builds
 * the column up (or, sneaking, sideways along the clicked face) instead of replacing the clicked block, sideways at
 * most {@link GoblinScaffoldBlock#STABILITY_MAX_DISTANCE} blocks.
 */
public class GoblinScaffoldItem extends BlockItem {
    public GoblinScaffoldItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    @Nullable
    public BlockPlaceContext updatePlacementContext(BlockPlaceContext context) {
        BlockPos pos = context.getClickedPos();
        Level level = context.getLevel();
        BlockState state = level.getBlockState(pos);
        if (!state.is(getBlock())) {
            return GoblinScaffoldBlock.getDistance(level, pos) == GoblinScaffoldBlock.STABILITY_MAX_DISTANCE ? null : context;
        }
        Direction direction;
        if (context.isSecondaryUseActive()) {
            direction = context.isInside() ? context.getClickedFace().getOpposite() : context.getClickedFace();
        } else {
            direction = context.getClickedFace() == Direction.UP ? context.getHorizontalDirection() : Direction.UP;
        }
        int sideways = 0;
        BlockPos.MutableBlockPos cursor = pos.mutable().move(direction);
        while (sideways < GoblinScaffoldBlock.STABILITY_MAX_DISTANCE) {
            if (!level.isClientSide() && !level.isInWorldBounds(cursor)) {
                int maxY = level.getMaxY();
                if (context.getPlayer() instanceof ServerPlayer player && cursor.getY() > maxY) {
                    player.sendBuildLimitMessage(true, maxY);
                }
                break;
            }
            state = level.getBlockState(cursor);
            if (!state.is(getBlock())) {
                return state.canBeReplaced(context) ? BlockPlaceContext.at(context, cursor, direction) : null;
            }
            cursor.move(direction);
            if (direction.getAxis().isHorizontal()) sideways++;
        }
        return null;
    }

    @Override
    protected boolean mustSurvive() {
        return false;
    }
}
