package goblinlabour.job;

import goblinlabour.GoblinLabour;
import goblinlabour.entity.GoblinEntity;
import goblinlabour.home.HomeRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

import java.util.List;

/**
 * The rules a goblin follows when it breaks blocks: which tool from its hotbar to use, whether a block can be
 * broken at all (vanilla tool requirements, no fluids, never inside a home), how fast (player formula), where the
 * drops go (its own inventory), plus free torches and cobblestone against water and lava.
 */
public final class Mining {
    public static final int TORCH_LIGHT = 8;
    public static final int TORCH_SPACING_SQ = 4 * 4;

    private Mining() {
    }

    /** Why a block cannot be broken; {@code OK} means it can. */
    public enum Verdict { OK, NOTHING, NEEDS_TOOL, PROTECTED }

    public static Verdict verdict(ServerLevel level, BlockPos pos, BlockState state, GoblinEntity goblin) {
        if (state.isAir() || state.getBlock() instanceof LiquidBlock || !state.getFluidState().isEmpty() && state.canBeReplaced()) {
            return Verdict.NOTHING;
        }
        if (state.getDestroySpeed(level, pos) < 0.0f) return Verdict.NOTHING;
        if (state.is(GoblinLabour.GOBLIN_STRAW_BED) || state.is(GoblinLabour.GOBLIN_SCAFFOLD) || state.is(Blocks.COBBLESTONE_STAIRS)
                || state.is(Blocks.TORCH) || state.is(Blocks.WALL_TORCH) || HomeRegistry.isProtected(level, pos)) return Verdict.PROTECTED;
        if (state.requiresCorrectToolForDrops() && bestToolSlot(goblin, state) < 0) return Verdict.NEEDS_TOOL;
        return Verdict.OK;
    }

    /**
     * Hotbar slot of the fastest tool for this block, or -1 for the bare hand. When the block needs a correct tool,
     * only correct tools count and -1 means "cannot".
     */
    public static int bestToolSlot(GoblinEntity goblin, BlockState state) {
        boolean needCorrect = state.requiresCorrectToolForDrops();
        int best = -1;
        float bestSpeed = needCorrect ? 0.0f : 1.0f;
        SimpleContainer inv = goblin.getInventory();
        for (int i = 0; i < GoblinEntity.HOTBAR_SIZE; i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.isEmpty()) continue;
            if (needCorrect && !stack.isCorrectToolForDrops(state)) continue;
            float speed = stack.getDestroySpeed(state);
            if (speed > bestSpeed) {
                bestSpeed = speed;
                best = i;
            }
        }
        return best;
    }

    /** Progress (0..1) added per tick, the player's formula without the tool durability part. */
    public static float progressPerTick(ServerLevel level, BlockPos pos, BlockState state, GoblinEntity goblin, ItemStack tool) {
        float hardness = state.getDestroySpeed(level, pos);
        if (hardness <= 0.0f) return 1.0f;
        float speed = tool.isEmpty() ? 1.0f : tool.getDestroySpeed(state);
        if (speed > 1.0f) {
            int efficiency = EnchantmentHelper.getItemEnchantmentLevel(enchantment(level, Enchantments.EFFICIENCY), tool);
            if (efficiency > 0) speed += efficiency * efficiency + 1;
        }
        MobEffectInstance haste = goblin.getEffect(MobEffects.HASTE);
        if (haste != null) speed *= 1.0f + (haste.getAmplifier() + 1) * 0.2f;
        MobEffectInstance fatigue = goblin.getEffect(MobEffects.MINING_FATIGUE);
        if (fatigue != null) {
            speed *= switch (fatigue.getAmplifier()) {
                case 0 -> 0.3f;
                case 1 -> 0.09f;
                case 2 -> 0.0027f;
                default -> 8.1e-4f;
            };
        }
        if (goblin.isEyeInFluid(FluidTags.WATER)
                && EnchantmentHelper.getEnchantmentLevel(enchantment(level, Enchantments.AQUA_AFFINITY), goblin) == 0) {
            speed /= 5.0f;
        }
        if (!goblin.onGround()) speed /= 5.0f;
        boolean correct = !state.requiresCorrectToolForDrops() || tool.isCorrectToolForDrops(state);
        return speed / hardness / (correct ? 30.0f : 100.0f);
    }

    private static Holder<Enchantment> enchantment(ServerLevel level, net.minecraft.resources.ResourceKey<Enchantment> key) {
        return level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(key);
    }

    /** Breaks the block: drops into the goblin's storage (overflow on the ground), break effect, then seals fluids. */
    public static void harvest(ServerLevel level, BlockPos pos, BlockState state, GoblinEntity goblin, ItemStack tool) {
        List<ItemStack> drops = Block.getDrops(state, level, pos, level.getBlockEntity(pos), goblin, tool);
        level.levelEvent(2001, pos, Block.getId(state));
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        for (ItemStack drop : drops) {
            ItemStack rest = goblin.storeInStorage(drop);
            if (!rest.isEmpty()) Block.popResource(level, pos, rest);
        }
        sealFluids(level, pos);
    }

    /** Cobblestone (free) into every neighbouring fluid block that could run into the freshly opened hole. */
    public static void sealFluids(ServerLevel level, BlockPos pos) {
        for (Direction direction : Direction.values()) {
            BlockPos side = pos.relative(direction);
            seal(level, side);
        }
        // a waterlogged block leaves its fluid behind
        seal(level, pos);
    }

    private static void seal(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        FluidState fluid = state.getFluidState();
        if (fluid.isEmpty()) return;
        if (state.getBlock() instanceof LiquidBlock || state.canBeReplaced()) {
            if (HomeRegistry.isProtected(level, pos)) return;
            level.setBlock(pos, Blocks.COBBLESTONE.defaultBlockState(), 3);
        }
    }

    /** True if there is lava directly below or beside the block (checked before opening the block underneath). */
    public static boolean lavaAdjacent(ServerLevel level, BlockPos pos) {
        for (Direction direction : Direction.values()) {
            if (level.getFluidState(pos.relative(direction)).is(FluidTags.LAVA)) return true;
        }
        return false;
    }

    /**
     * Places a free torch where the goblin stands if it is dark there, at least four blocks from the last one and
     * outside any home. Returns true if a torch was placed.
     */
    public static boolean placeTorchIfDark(ServerLevel level, GoblinEntity goblin) {
        BlockPos pos = goblin.blockPosition();
        if (level.getMaxLocalRawBrightness(pos) >= TORCH_LIGHT) return false;
        BlockPos last = goblin.lastTorchPos();
        if (last != null && last.distSqr(pos) < TORCH_SPACING_SQ) return false;
        if (HomeRegistry.isProtected(level, pos)) return false;
        if (!level.getBlockState(pos).isAir()) return false;

        BlockState torch = null;
        BlockPos at = pos;
        // walls first (head height, then feet), floor only when no wall is around
        for (BlockPos candidate : new BlockPos[]{pos.above(), pos}) {
            if (!level.getBlockState(candidate).isAir()) continue;
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                BlockPos wall = candidate.relative(direction);
                if (level.getBlockState(wall).isFaceSturdy(level, wall, direction.getOpposite())) {
                    torch = Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, direction.getOpposite());
                    at = candidate;
                    break;
                }
            }
            if (torch != null) break;
        }
        if (torch == null && level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP)) {
            torch = Blocks.TORCH.defaultBlockState();
        }
        if (torch == null) return false;
        level.setBlock(at, torch, 3);
        goblin.setLastTorchPos(at);
        return true;
    }
}
