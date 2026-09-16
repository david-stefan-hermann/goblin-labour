package goblinlabour.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import goblinlabour.GoblinLabour;
import goblinlabour.ring.RingContainer;
import goblinlabour.ring.RingCrew;
import goblinlabour.ring.RingInventory;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.world.phys.Vec3;
import java.util.Locale;
import java.util.UUID;
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
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * /goblinlabour spawn <bed> [name]                          spawns a fresh goblin on the bed (no blank needed)
 * /goblinlabour status <bed>                                bed status and the goblin's inventory
 * /goblinlabour job <bed> <rest|chop|farm|collect> [radius] sets a bed job
 * /goblinlabour replant <bed> <true|false>                  lumberjack replanting on or off
 * /goblinlabour dig <bed> <down|up> <origin> <width> <targetY> <stairs>   staff order without a staff
 * /goblinlabour tunnel <bed> <origin> <north|south|east|west> <width> <height> <length>
 * /goblinlabour tool <bed> <slot> <item>                    puts an item into the goblin's slot 0-17
 * /goblinlabour fillstorage <bed> <item>                    fills the storage row(s) with full stacks
 * /goblinlabour lid <chest> <true|false>                    opens or closes a chest lid (screenshots)
 * /goblinlabour ring start|move <pos> <yaw>                 a stand-in player with a Goblin Ring at pos (start calls its crew)
 * /goblinlabour ring toggle|status                          right-click of the stand-in's ring, crew and ring state
 * /goblinlabour ring give <item> <count>                    puts items into the stand-in's ring
 * /goblinlabour ring expire <seconds>                       sets the crew's time left
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
                .then(Commands.literal("columns")
                        .then(Commands.argument("bed", BlockPosArgument.blockPos())
                                .then(Commands.argument("target", BlockPosArgument.blockPos())
                                        .executes(ctx -> columns(ctx.getSource(), BlockPosArgument.getLoadedBlockPos(ctx, "bed"),
                                                BlockPosArgument.getLoadedBlockPos(ctx, "target"))))))
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
                .then(Commands.literal("replant")
                        .then(Commands.argument("bed", BlockPosArgument.blockPos())
                                .then(Commands.argument("on", BoolArgumentType.bool())
                                        .executes(ctx -> replant(ctx.getSource(), BlockPosArgument.getLoadedBlockPos(ctx, "bed"),
                                                BoolArgumentType.getBool(ctx, "on"))))))
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
                                                        .then(Commands.argument("height", IntegerArgumentType.integer(1, 5))
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
                .then(Commands.literal("ring")
                        .then(Commands.literal("start")
                                .then(Commands.argument("pos", Vec3Argument.vec3())
                                        .then(Commands.argument("yaw", FloatArgumentType.floatArg(-360.0f, 360.0f))
                                                .executes(ctx -> ringPlace(ctx.getSource(), Vec3Argument.getVec3(ctx, "pos"),
                                                        FloatArgumentType.getFloat(ctx, "yaw"), true)))))
                        .then(Commands.literal("move")
                                .then(Commands.argument("pos", Vec3Argument.vec3())
                                        .then(Commands.argument("yaw", FloatArgumentType.floatArg(-360.0f, 360.0f))
                                                .executes(ctx -> ringPlace(ctx.getSource(), Vec3Argument.getVec3(ctx, "pos"),
                                                        FloatArgumentType.getFloat(ctx, "yaw"), false)))))
                        .then(Commands.literal("toggle").executes(ctx -> ringToggle(ctx.getSource())))
                        .then(Commands.literal("status").executes(ctx -> ringStatus(ctx.getSource())))
                        .then(Commands.literal("select").executes(ctx -> ringSelect(ctx.getSource())))
                        .then(Commands.literal("deselect").executes(ctx -> ringDeselect(ctx.getSource())))
                        .then(Commands.literal("assist")
                                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                        .executes(ctx -> ringAssist(ctx.getSource(), BlockPosArgument.getLoadedBlockPos(ctx, "pos")))))
                        .then(Commands.literal("tunnel")
                                .then(Commands.argument("origin", BlockPosArgument.blockPos())
                                        .then(Commands.argument("direction", StringArgumentType.word())
                                                .then(Commands.argument("width", IntegerArgumentType.integer(1, 5))
                                                        .then(Commands.argument("height", IntegerArgumentType.integer(1, 5))
                                                                .then(Commands.argument("length", IntegerArgumentType.integer(4, 96))
                                                                        .executes(ctx -> ringTunnel(ctx.getSource(),
                                                                                BlockPosArgument.getLoadedBlockPos(ctx, "origin"),
                                                                                StringArgumentType.getString(ctx, "direction"),
                                                                                IntegerArgumentType.getInteger(ctx, "width"),
                                                                                IntegerArgumentType.getInteger(ctx, "height"),
                                                                                IntegerArgumentType.getInteger(ctx, "length")))))))))
                        .then(Commands.literal("give")
                                .then(Commands.argument("item", ItemArgument.item(context))
                                        .then(Commands.argument("count", IntegerArgumentType.integer(1, RingInventory.SIZE * 64))
                                                .executes(ctx -> ringGive(ctx.getSource(), ItemArgument.getItem(ctx, "item").createItemStack(1),
                                                        IntegerArgumentType.getInteger(ctx, "count"))))))
                        .then(Commands.literal("drop")
                                .then(Commands.argument("item", ItemArgument.item(context))
                                        .executes(ctx -> ringDrop(ctx.getSource(), ItemArgument.getItem(ctx, "item").createItemStack(1)))))
                        .then(Commands.literal("expire")
                                .then(Commands.argument("seconds", IntegerArgumentType.integer(0, 3600))
                                        .executes(ctx -> ringExpire(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "seconds"))))))
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

    /** Dev: why the bed's goblin can or cannot put a scaffold column next to a high block. */
    private static int columns(CommandSourceStack source, BlockPos bedPos, BlockPos target) {
        GoblinBedBlockEntity bed = bed(source, bedPos);
        if (bed == null) return 0;
        GoblinEntity goblin = bed.findGoblin(source.getLevel());
        if (goblin == null) {
            source.sendFailure(Component.literal("No goblin"));
            return 0;
        }
        String report = goblin.runner().columnReport(source.getLevel(), target);
        source.sendSuccess(() -> Component.literal(report), false);
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
        bed.forgetJobBefore();
        bed.setJob(config);
        JobConfig set = config;
        source.sendSuccess(() -> Component.literal("Job at " + pos.toShortString() + ": " + set), true);
        return 1;
    }

    private static int replant(CommandSourceStack source, BlockPos pos, boolean on) {
        GoblinBedBlockEntity bed = bed(source, pos);
        if (bed == null) return 0;
        bed.setJob(bed.getJob().withReplant(on));
        source.sendSuccess(() -> Component.literal("Replant at " + pos.toShortString() + ": " + on), true);
        return 1;
    }

    private static int dig(CommandSourceStack source, BlockPos pos, String kindId, BlockPos origin, int width, int targetY, boolean stairs) {
        GoblinBedBlockEntity bed = bed(source, pos);
        if (bed == null) return 0;
        Assignment.Kind kind = kindId.equalsIgnoreCase("up") ? Assignment.Kind.DIG_UP : Assignment.Kind.DIG_DOWN;
        int w = width % 2 == 0 ? width + 1 : width;
        Assignment assignment = new Assignment(kind, origin, Direction.NORTH, Math.min(5, w), 0, 0, targetY, stairs)
                .withFloorFrom(source.getLevel());
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

    // ---- ring tests ----------------------------------------------------------------------------------------------

    private static final UUID RING_TESTER_ID = new UUID(0L, 0xBEEFL);

    /** The stand-in player of the ring tests, carrying a ring in slot 0. It never joins the world; only its crew sees it. */
    private static FakePlayer ringTester(ServerLevel level) {
        FakePlayer tester = FakePlayer.get(level, new GameProfile(RING_TESTER_ID, "RingTester"));
        if (!tester.getInventory().getItem(0).is(GoblinLabour.GOBLIN_RING)) {
            tester.getInventory().setItem(0, new ItemStack(GoblinLabour.GOBLIN_RING));
        }
        RingInventory.ensureId(tester.getInventory().getItem(0));
        return tester;
    }

    private static UUID ringId(FakePlayer tester) {
        return RingInventory.ensureId(tester.getInventory().getItem(0));
    }

    private static int ringPlace(CommandSourceStack source, Vec3 pos, float yaw, boolean call) {
        ServerLevel level = source.getLevel();
        FakePlayer tester = ringTester(level);
        tester.snapTo(pos.x, pos.y, pos.z, yaw, 0.0f);
        tester.setYHeadRot(yaw);
        if (call && RingCrew.of(ringId(tester)) == null) RingCrew.toggle(level, tester, tester.getInventory().getItem(0));
        RingCrew.Session session = RingCrew.of(ringId(tester));
        int size = session == null ? 0 : session.goblins().size();
        source.sendSuccess(() -> Component.literal("RingTester at " + tester.blockPosition().toShortString() + ", crew of " + size), false);
        return 1;
    }

    private static int ringToggle(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        FakePlayer tester = ringTester(level);
        RingCrew.toggle(level, tester, tester.getInventory().getItem(0));
        RingCrew.Session session = RingCrew.of(ringId(tester));
        int size = session == null ? 0 : session.goblins().size();
        source.sendSuccess(() -> Component.literal("Ring toggled, crew of " + size), false);
        return 1;
    }

    private static int ringStatus(CommandSourceStack source) {
        FakePlayer tester = ringTester(source.getLevel());
        UUID id = ringId(tester);
        StringBuilder sb = new StringBuilder("ring:");
        RingContainer ring = RingInventory.open(tester, id);
        if (ring != null) {
            for (int i = 0; i < ring.getContainerSize(); i++) {
                ItemStack stack = ring.getItem(i);
                if (!stack.isEmpty()) sb.append(' ').append(stack.getCount()).append('x').append(stack.getItem());
            }
        }
        sb.append(" | tester=").append(tester.blockPosition().toShortString().replace(" ", ""));
        RingCrew.Session session = RingCrew.of(id);
        if (session == null) {
            sb.append(" | crew=-");
        } else {
            sb.append(" | crew=").append(session.goblins().size()).append(" left=").append(session.ticksLeft() / 20)
                    .append("s ores=").append(session.ores().size()).append(" vein=").append(session.vein().size())
                    .append(" assist=").append(session.assistFocus() == null ? "-" : session.assistFocus().toShortString().replace(" ", ""));
            for (GoblinEntity goblin : session.goblins()) {
                sb.append(" | ").append(goblin.goblinName())
                        .append(String.format(Locale.ROOT, " pos=%.1f,%.1f,%.1f ", goblin.getX(), goblin.getY(), goblin.getZ()))
                        .append(goblin.crewDebug(tester));
            }
        }
        source.sendSuccess(() -> Component.literal(sb.toString()), false);
        return 1;
    }

    /** The stand-in picks every goblin of its crew with the staff (as its left-clicks would). */
    /** Stands in for the ring's player breaking a natural block (setblock does not fire the break event). */
    private static int ringAssist(CommandSourceStack source, BlockPos pos) {
        RingCrew.Session session = RingCrew.of(ringId(ringTester(source.getLevel())));
        if (session == null) {
            source.sendFailure(Component.literal("No ring crew"));
            return 0;
        }
        session.assist(pos);
        source.sendSuccess(() -> Component.literal("Ring crew helps dig at " + pos.toShortString()), false);
        return 1;
    }

    /** Lets go of every goblin the ring's stand-in has picked with the staff, as a second click on each would. */
    private static int ringDeselect(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        FakePlayer tester = ringTester(level);
        StaffSelection.clear(level, tester);
        source.sendSuccess(() -> Component.literal("Selected " + StaffSelection.count(level, tester)), false);
        return 1;
    }

    private static int ringSelect(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        FakePlayer tester = ringTester(level);
        RingCrew.Session session = RingCrew.of(ringId(tester));
        if (session == null) {
            source.sendFailure(Component.literal("No ring crew"));
            return 0;
        }
        for (GoblinEntity goblin : session.goblins()) {
            if (goblin.following() == null) StaffSelection.toggle(level, tester, goblin);
        }
        int selected = StaffSelection.count(level, tester);
        source.sendSuccess(() -> Component.literal("Selected " + selected), false);
        return 1;
    }

    /** A tunnel order from the stand-in's staff to its picked goblins. */
    private static int ringTunnel(CommandSourceStack source, BlockPos origin, String directionId, int width, int height, int length) {
        Direction direction = Direction.byName(directionId.toLowerCase(Locale.ROOT));
        if (direction == null || direction.getAxis().isVertical()) {
            source.sendFailure(Component.literal("Direction must be north, south, east or west"));
            return 0;
        }
        ServerLevel level = source.getLevel();
        Assignment assignment = new Assignment(Assignment.Kind.TUNNEL, origin, direction, width, height, length, origin.getY(), false);
        Component result = StaffSelection.assign(level, ringTester(level), assignment);
        source.sendSuccess(() -> Component.literal("Order: " + result.getString()), false);
        return 1;
    }

    private static int ringGive(CommandSourceStack source, ItemStack stack, int count) {
        FakePlayer tester = ringTester(source.getLevel());
        RingContainer ring = RingInventory.open(tester, ringId(tester));
        if (ring == null) return 0;
        int left = count;
        while (left > 0) {
            ItemStack part = stack.copyWithCount(Math.min(left, stack.getMaxStackSize()));
            int before = part.getCount();
            ItemStack rest = ring.addItem(part);
            left -= before - rest.getCount();
            if (!rest.isEmpty()) break;
        }
        ring.setChanged();
        int given = count - left;
        source.sendSuccess(() -> Component.literal("Ring got " + given + "x" + stack.getItem()), false);
        return 1;
    }

    /** Throws an item the way a player does, with the ring's stand-in as its thrower, to test that a crew fetches it. */
    private static int ringDrop(CommandSourceStack source, ItemStack stack) {
        ServerLevel level = source.getLevel();
        FakePlayer tester = ringTester(level);
        ItemEntity item = new ItemEntity(level, tester.getX(), tester.getY() + 0.5, tester.getZ(), stack);
        item.setThrower(tester);
        item.setDefaultPickUpDelay();
        level.addFreshEntity(item);
        source.sendSuccess(() -> Component.literal("RingTester threw " + stack.getItem()
                + " at " + item.blockPosition().toShortString()), false);
        return 1;
    }

    private static int ringExpire(CommandSourceStack source, int seconds) {
        RingCrew.Session session = RingCrew.of(ringId(ringTester(source.getLevel())));
        if (session == null) {
            source.sendFailure(Component.literal("No ring crew"));
            return 0;
        }
        session.setTicksLeft(seconds * 20);
        source.sendSuccess(() -> Component.literal("Ring crew leaves in " + seconds + " s"), false);
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
