package goblinlabour.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import goblinlabour.GoblinNames;
import goblinlabour.block.GoblinBedBlockEntity;
import goblinlabour.entity.GoblinEntity;
import goblinlabour.item.GoblinData;
import goblinlabour.job.Assignment;
import goblinlabour.job.Job;
import goblinlabour.job.JobConfig;
import goblinlabour.staff.StaffSelection;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.commands.arguments.item.ItemArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.permissions.Permission.HasCommandLevel;
import net.minecraft.server.permissions.PermissionLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * /goblinlabour spawn <bed> [name]                          spawns a fresh goblin on the bed (no blank needed)
 * /goblinlabour status <bed>                                bed status and the goblin's inventory
 * /goblinlabour job <bed> <rest|chop|farm|collect> [radius] sets a bed job
 * /goblinlabour dig <bed> <down|up> <origin> <width> <targetY> <stairs>   staff order without a staff
 * /goblinlabour tunnel <bed> <origin> <north|south|east|west> <width> <height> <length>
 * /goblinlabour tool <bed> <slot> <item>                    puts an item into the goblin's slot 0-17
 * /goblinlabour fillstorage <bed> <item>                    fills the storage row(s) with full stacks
 * /goblinlabour lid <chest> <true|false>                    opens or closes a chest lid (screenshots)
 * Operator level 2. Used by the RCON test scripts.
 */
public final class GoblinCommand {
    private GoblinCommand() {
    }

    public static void init() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> register(dispatcher, registryAccess));
    }

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context) {
        dispatcher.register(Commands.literal("goblinlabour")
                .requires(source -> source.permissions().hasPermission(new HasCommandLevel(PermissionLevel.GAMEMASTERS)))
                .then(Commands.literal("spawn")
                        .then(Commands.argument("bed", BlockPosArgument.blockPos())
                                .executes(ctx -> spawn(ctx.getSource(), BlockPosArgument.getLoadedBlockPos(ctx, "bed"), null))
                                .then(Commands.argument("name", StringArgumentType.word())
                                        .executes(ctx -> spawn(ctx.getSource(), BlockPosArgument.getLoadedBlockPos(ctx, "bed"),
                                                StringArgumentType.getString(ctx, "name"))))))
                .then(Commands.literal("status")
                        .then(Commands.argument("bed", BlockPosArgument.blockPos())
                                .executes(ctx -> status(ctx.getSource(), BlockPosArgument.getLoadedBlockPos(ctx, "bed")))))
                .then(Commands.literal("job")
                        .then(Commands.argument("bed", BlockPosArgument.blockPos())
                                .then(Commands.argument("job", StringArgumentType.word())
                                        .executes(ctx -> job(ctx.getSource(), BlockPosArgument.getLoadedBlockPos(ctx, "bed"),
                                                StringArgumentType.getString(ctx, "job"), -1))
                                        .then(Commands.argument("radius", IntegerArgumentType.integer(4, 64))
                                                .executes(ctx -> job(ctx.getSource(), BlockPosArgument.getLoadedBlockPos(ctx, "bed"),
                                                        StringArgumentType.getString(ctx, "job"), IntegerArgumentType.getInteger(ctx, "radius")))))))
                .then(Commands.literal("dig")
                        .then(Commands.argument("bed", BlockPosArgument.blockPos())
                                .then(Commands.argument("kind", StringArgumentType.word())
                                        .then(Commands.argument("origin", BlockPosArgument.blockPos())
                                                .then(Commands.argument("width", IntegerArgumentType.integer(1, 5))
                                                        .then(Commands.argument("targetY", IntegerArgumentType.integer(-64, 320))
                                                                .then(Commands.argument("stairs", BoolArgumentType.bool())
                                                                        .executes(ctx -> dig(ctx.getSource(), BlockPosArgument.getLoadedBlockPos(ctx, "bed"),
                                                                                StringArgumentType.getString(ctx, "kind"),
                                                                                BlockPosArgument.getLoadedBlockPos(ctx, "origin"),
                                                                                IntegerArgumentType.getInteger(ctx, "width"),
                                                                                IntegerArgumentType.getInteger(ctx, "targetY"),
                                                                                BoolArgumentType.getBool(ctx, "stairs"))))))))))
                .then(Commands.literal("tunnel")
                        .then(Commands.argument("bed", BlockPosArgument.blockPos())
                                .then(Commands.argument("origin", BlockPosArgument.blockPos())
                                        .then(Commands.argument("direction", StringArgumentType.word())
                                                .then(Commands.argument("width", IntegerArgumentType.integer(1, 5))
                                                        .then(Commands.argument("height", IntegerArgumentType.integer(2, 5))
                                                                .then(Commands.argument("length", IntegerArgumentType.integer(4, 96))
                                                                        .executes(ctx -> tunnel(ctx.getSource(), BlockPosArgument.getLoadedBlockPos(ctx, "bed"),
                                                                                BlockPosArgument.getLoadedBlockPos(ctx, "origin"),
                                                                                StringArgumentType.getString(ctx, "direction"),
                                                                                IntegerArgumentType.getInteger(ctx, "width"),
                                                                                IntegerArgumentType.getInteger(ctx, "height"),
                                                                                IntegerArgumentType.getInteger(ctx, "length"))))))))))
                .then(Commands.literal("lid")
                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                .then(Commands.argument("open", BoolArgumentType.bool())
                                        .executes(ctx -> lid(ctx.getSource(), BlockPosArgument.getLoadedBlockPos(ctx, "pos"),
                                                BoolArgumentType.getBool(ctx, "open"))))))
                .then(Commands.literal("fillstorage")
                        .then(Commands.argument("bed", BlockPosArgument.blockPos())
                                .then(Commands.argument("item", ItemArgument.item(context))
                                        .executes(ctx -> fill(ctx.getSource(), BlockPosArgument.getLoadedBlockPos(ctx, "bed"),
                                                ItemArgument.getItem(ctx, "item").createItemStack(1))))))
                .then(Commands.literal("tool")
                        .then(Commands.argument("bed", BlockPosArgument.blockPos())
                                .then(Commands.argument("slot", IntegerArgumentType.integer(0, GoblinEntity.INVENTORY_SIZE - 1))
                                        .then(Commands.argument("item", ItemArgument.item(context))
                                                .executes(ctx -> tool(ctx.getSource(), BlockPosArgument.getLoadedBlockPos(ctx, "bed"),
                                                        IntegerArgumentType.getInteger(ctx, "slot"),
                                                        ItemArgument.getItem(ctx, "item").createItemStack(1))))))));
    }

    private static GoblinBedBlockEntity bed(CommandSourceStack source, BlockPos pos) {
        if (source.getLevel().getBlockEntity(pos) instanceof GoblinBedBlockEntity bed) return bed;
        source.sendFailure(Component.literal("No goblin bed at " + pos.toShortString()));
        return null;
    }

    private static int spawn(CommandSourceStack source, BlockPos pos, String name) {
        ServerLevel level = source.getLevel();
        GoblinBedBlockEntity bed = bed(source, pos);
        if (bed == null) return 0;
        if (bed.findGoblin(level) != null) {
            source.sendFailure(Component.literal(bed.getGoblinName() + " already lives at " + pos.toShortString()));
            return 0;
        }
        String goblinName = name != null ? name : GoblinNames.random(level.getRandom());
        bed.spawnGoblin(level, GoblinData.fresh(goblinName));
        source.sendSuccess(() -> Component.literal("Spawned " + goblinName + " at " + pos.toShortString()), true);
        return 1;
    }

    private static int status(CommandSourceStack source, BlockPos pos) {
        GoblinBedBlockEntity bed = bed(source, pos);
        if (bed == null) return 0;
        source.sendSuccess(bed::describe, false);
        GoblinEntity goblin = bed.findGoblin(source.getLevel());
        if (goblin != null) {
            StringBuilder sb = new StringBuilder("inventory:");
            for (int i = 0; i < GoblinEntity.INVENTORY_SIZE; i++) {
                ItemStack stack = goblin.getInventory().getItem(i);
                if (!stack.isEmpty()) sb.append(' ').append(i).append('=').append(stack.getCount()).append('x').append(stack.getItem());
            }
            sb.append(" | health=").append(goblin.getHealth()).append(" pos=").append(goblin.blockPosition().toShortString());
            sb.append(" onGround=").append(goblin.onGround()).append(" | ").append(goblin.runner().debug())
                    .append(" | ").append(goblin.restDebug()).append(" | ").append(goblin.goalDebug());
            if (bed.getAssignment() != null) sb.append(" | order=").append(bed.getAssignment());
            source.sendSuccess(() -> Component.literal(sb.toString()), false);
        }
        return 1;
    }

    private static int job(CommandSourceStack source, BlockPos pos, String jobId, int radius) {
        GoblinBedBlockEntity bed = bed(source, pos);
        if (bed == null) return 0;
        Job job = Job.byId(jobId);
        if (job.needsAssignment()) {
            source.sendFailure(Component.literal("Use /goblinlabour dig or tunnel for " + jobId));
            return 0;
        }
        JobConfig config = bed.getJob().withJob(job);
        if (radius > 0) config = config.withLength(radius);
        bed.setAssignment(null);
        bed.setJob(config);
        JobConfig set = config;
        source.sendSuccess(() -> Component.literal("Job at " + pos.toShortString() + ": " + set), true);
        return 1;
    }

    private static int dig(CommandSourceStack source, BlockPos pos, String kindId, BlockPos origin, int width, int targetY, boolean stairs) {
        GoblinBedBlockEntity bed = bed(source, pos);
        if (bed == null) return 0;
        Assignment.Kind kind = kindId.equalsIgnoreCase("up") ? Assignment.Kind.DIG_UP : Assignment.Kind.DIG_DOWN;
        int w = width % 2 == 0 ? width + 1 : width;
        Assignment assignment = new Assignment(kind, origin, Direction.NORTH, Math.min(5, w), 0, 0, targetY, stairs);
        StaffSelection.applyTo(bed, assignment);
        source.sendSuccess(() -> Component.literal("Order for " + bed.getGoblinName() + ": " + assignment), true);
        return 1;
    }

    private static int tunnel(CommandSourceStack source, BlockPos pos, BlockPos origin, String directionId, int width, int height, int length) {
        GoblinBedBlockEntity bed = bed(source, pos);
        if (bed == null) return 0;
        Direction direction = Direction.byName(directionId.toLowerCase());
        if (direction == null || direction.getAxis().isVertical()) {
            source.sendFailure(Component.literal("Direction must be north, south, east or west"));
            return 0;
        }
        Assignment assignment = new Assignment(Assignment.Kind.TUNNEL, origin, direction, width, height, length, origin.getY(), false);
        StaffSelection.applyTo(bed, assignment);
        source.sendSuccess(() -> Component.literal("Order for " + bed.getGoblinName() + ": " + assignment), true);
        return 1;
    }

    /** Opens or closes a chest lid for everyone watching (the block event players send), for screenshots. */
    private static int lid(CommandSourceStack source, BlockPos pos, boolean open) {
        BlockState state = source.getLevel().getBlockState(pos);
        if (!(state.getBlock() instanceof ChestBlock)) {
            source.sendFailure(Component.literal("No chest at " + pos.toShortString()));
            return 0;
        }
        source.getLevel().blockEvent(pos, state.getBlock(), 1, open ? 1 : 0);
        source.sendSuccess(() -> Component.literal("Lid at " + pos.toShortString() + (open ? " open" : " closed")), false);
        return 1;
    }

    private static int fill(CommandSourceStack source, BlockPos pos, ItemStack stack) {
        GoblinBedBlockEntity bed = bed(source, pos);
        if (bed == null) return 0;
        GoblinEntity goblin = bed.findGoblin(source.getLevel());
        if (goblin == null) {
            source.sendFailure(Component.literal("No goblin at " + pos.toShortString()));
            return 0;
        }
        for (int i = GoblinEntity.HOTBAR_SIZE; i < goblin.storageEnd(); i++) {
            ItemStack full = stack.copy();
            full.setCount(full.getMaxStackSize());
            goblin.getInventory().setItem(i, full);
        }
        source.sendSuccess(() -> Component.literal("Filled the storage of " + goblin.goblinName() + " with " + stack.getItem()), true);
        return 1;
    }

    private static int tool(CommandSourceStack source, BlockPos pos, int slot, ItemStack stack) {
        GoblinBedBlockEntity bed = bed(source, pos);
        if (bed == null) return 0;
        GoblinEntity goblin = bed.findGoblin(source.getLevel());
        if (goblin == null) {
            source.sendFailure(Component.literal("No goblin at " + pos.toShortString()));
            return 0;
        }
        goblin.getInventory().setItem(slot, stack);
        source.sendSuccess(() -> Component.literal("Slot " + slot + " of " + goblin.goblinName() + " = " + stack.getItem()), true);
        return 1;
    }
}
