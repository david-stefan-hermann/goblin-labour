package goblinlabour;

import goblinlabour.entity.GoblinEntity;
import goblinlabour.job.Job;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import org.jetbrains.annotations.Nullable;

/** What goblins say in chat. Server-wide, always English, one line per event; some events pick a random line. */
public final class GoblinSpeech {
    public static final String SPAWNED = "Reporting for duty, boss!";
    public static final String RESPAWNED = "Ugh... back at the bed. What hit me?";
    public static final String INVENTORY_FULL = "No room left for my loot. Empty the chests!";
    public static final String NO_EXIT = "There's no way out of our home. Open a door for me!";
    public static final String NO_TOOL = "I can't dig through this with my bare hands. Give me a pickaxe!";
    public static final String TOOL_BROKEN = "My tool broke. Got a spare?";
    public static final String BED_GONE = "My bed! Where's my bed?!";
    public static final String STAY = "Staying here.";
    public static final String FETCH_ITEMS = "Going back for my stuff.";

    /** Picked with the staff. */
    public static final String[] FOLLOW = {
            "Right behind you, boss!", "Coming, master!", "Lead the way, master!", "Yes, master?",
    };
    /** Given an order. */
    public static final String[] YES = {
            "Yes, master!", "As you wish, master!", "Right away, master!", "Me do it, master!", "Diggy diggy hole!",
    };
    /** Finished a job. */
    public static final String[] DONE = {
            "Job's done, boss. What's next?", "All done, master!", "Finished! Me clever goblin.",
    };
    /** Now and then while working. */
    public static final String[] WORKING = {
            "Working, working, working!", "I love my job!", "Work work work!", "Me strong! Me useful!",
            "Best job ever!", "Another block, another day.", "Busy busy busy!",
    };
    public static final String[] WORKING_MINE = {
            "Diggy diggy hole!", "Shiny rocks for the boss!", "Deeper! Always deeper!", "Rocks go crunch!",
    };
    public static final String[] WORKING_CHOP = {
            "Chop chop chop!", "Timber!", "Trees fear me.", "Wood for the boss!",
    };
    public static final String[] WORKING_FARM = {
            "Crops grow, goblin harvest.", "Fresh veggies for master!", "Me like farming. Quiet.", "Snip snip, pluck pluck!",
    };

    private GoblinSpeech() {
    }

    public static String random(RandomSource random, String[] lines) {
        return lines[random.nextInt(lines.length)];
    }

    /** A random working line, half the time specific to the job. */
    public static String workingLine(RandomSource random, @Nullable Job job) {
        if (job != null && random.nextBoolean()) {
            switch (job) {
                case MINE_DOWN, MINE_UP, MINE_AHEAD -> {
                    return random(random, WORKING_MINE);
                }
                case CHOP -> {
                    return random(random, WORKING_CHOP);
                }
                case FARM -> {
                    return random(random, WORKING_FARM);
                }
                default -> {
                }
            }
        }
        return random(random, WORKING);
    }

    public static void say(ServerLevel level, String goblinName, String line) {
        Component text = Component.literal("<" + goblinName + "> ").withStyle(ChatFormatting.GREEN)
                .append(Component.literal(line).withStyle(ChatFormatting.WHITE));
        level.getServer().getPlayerList().broadcastSystemMessage(text, false);
    }

    /** Chat line plus a voice sound at the goblin. */
    public static void say(ServerLevel level, GoblinEntity goblin, String line, @Nullable SoundEvent sound) {
        say(level, goblin.goblinName(), line);
        if (sound != null) {
            level.playSound(null, goblin.getX(), goblin.getY(), goblin.getZ(), sound, SoundSource.NEUTRAL, 1.0f,
                    0.95f + goblin.getRandom().nextFloat() * 0.1f);
        }
    }
}
