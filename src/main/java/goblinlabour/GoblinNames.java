package goblinlabour;

import net.minecraft.util.RandomSource;

import java.util.List;

/** 100 goblin names; picked at random when a fresh blank is placed on a bed. */
public final class GoblinNames {
    public static final List<String> NAMES = List.of(
            "Grubnak", "Snikkit", "Bogrot", "Wizzle", "Murkfang", "Skabb", "Nogg", "Fizzgrit", "Dreg", "Krunkle",
            "Zoggit", "Grimble", "Snotch", "Rattle", "Mudge", "Gribbly", "Skreek", "Blister", "Nibbs", "Gnash",
            "Toadwart", "Sneevil", "Grizzik", "Pox", "Wormtongue", "Skitch", "Rukk", "Gobber", "Fleck", "Mizzle",
            "Crag", "Snagg", "Vexx", "Boggle", "Thrum", "Gizzard", "Nackle", "Splurt", "Krik", "Dobble",
            "Ratchet", "Smudge", "Glim", "Scrag", "Twitch", "Hobnob", "Yikk", "Mange", "Gutz", "Sprocket",
            "Blaggard", "Nurgle", "Skulk", "Grot", "Wheeze", "Zibbit", "Gnarl", "Fester", "Muck", "Snib",
            "Cackle", "Drool", "Grubb", "Slinker", "Tinkle", "Warp", "Bristle", "Knurl", "Scuttle", "Gimble",
            "Rotgut", "Stink", "Nettle", "Quibble", "Grak", "Zug", "Ickabod", "Snarl", "Mossback", "Pustule",
            "Hex", "Wart", "Frizzle", "Klonk", "Bilge", "Skrat", "Gloom", "Nubbin", "Scab", "Tusk",
            "Vermin", "Grizzle", "Lurk", "Oozle", "Snaggle", "Crank", "Jibber", "Fang", "Rasp", "Squelch");

    private GoblinNames() {
    }

    public static String random(RandomSource random) {
        return NAMES.get(random.nextInt(NAMES.size()));
    }
}
