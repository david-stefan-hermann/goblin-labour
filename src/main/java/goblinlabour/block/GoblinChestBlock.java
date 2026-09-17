package goblinlabour.block;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import goblinlabour.GoblinLabour;
import goblinlabour.menu.GoblinChestMenu;
import goblinlabour.menu.GoblinChestMenuProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.Container;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.Map;

/**
 * A chest for goblin loot: vanilla chest behaviour (27 slots, double chests, comparators, hoppers), its own block
 * entity type so it gets its own renderer (with teeth). Goblins unload only into these.
 *
 * <p>It comes in the sixteen dye colours, one block each like shulker boxes, so only chests of one colour join into a
 * double chest. Green is the plain {@code goblin_chest} the recipe makes; a dye turns any of them into another.
 */
public class GoblinChestBlock extends ChestBlock {
    public static final MapCodec<GoblinChestBlock> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            DyeColor.CODEC.fieldOf("color").forGetter(GoblinChestBlock::color),
            propertiesCodec()
    ).apply(instance, GoblinChestBlock::new));

    /**
     * The chest's own hull instead of vanilla's 14x14x14 box: the goblin chest fills its block from side to side
     * (plinth, rim and corner posts all reach the block edge) and its lid ends at the top of the block. Only the
     * studs and the horns stick out, and those stay out of the shape because a block shape cannot leave its block.
     * Both halves of a double chest fill their own block, so all types share this shape.
     */
    private static final Map<Direction, VoxelShape> SHAPES = Shapes.rotateHorizontal(Block.box(0, 0, 1, 16, 16, 15));

    private final DyeColor color;

    public GoblinChestBlock(DyeColor color, Properties properties) {
        super(() -> GoblinLabour.GOBLIN_CHEST_BLOCK_ENTITY, SoundEvents.CHEST_OPEN, SoundEvents.CHEST_CLOSE, properties);
        this.color = color;
    }

    public DyeColor color() {
        return color;
    }

    /** The registry name: {@code goblin_chest} for green, {@code <colour>_goblin_chest} for the dyed ones. */
    public static String name(DyeColor color) {
        return color == DyeColor.GREEN ? "goblin_chest" : color.getSerializedName() + "_goblin_chest";
    }

    /**
     * The texture in the chest atlas ({@code textures/entity/chest/<name>.png}, the double chest's with
     * {@code _double}): {@code goblin} for green, {@code goblin_<colour>} for the others.
     */
    public static String texture(DyeColor color) {
        return color == DyeColor.GREEN ? "goblin" : "goblin_" + color.getSerializedName();
    }

    @Override
    public MapCodec<? extends ChestBlock> codec() {
        return CODEC;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(FACING));
    }

    /**
     * Vanilla's chest menu provider, but opening the maw screen ({@link GoblinChestMenu}). {@code getContainer} joins
     * the two halves of a double chest in the right order and returns null when the lid is blocked, like vanilla.
     */
    @Override
    protected MenuProvider getMenuProvider(BlockState state, Level level, BlockPos pos) {
        Container container = ChestBlock.getContainer(this, state, level, pos, false);
        if (container == null) return null;
        Component title = level.getBlockEntity(pos) instanceof GoblinChestBlockEntity chest ? chest.getDisplayName() : getName();
        return new GoblinChestMenuProvider(container, container.getContainerSize() / 9, color, title);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new GoblinChestBlockEntity(pos, state);
    }
}
