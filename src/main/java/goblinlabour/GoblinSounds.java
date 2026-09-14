package goblinlabour;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

/**
 * Goblin voices. The sound events are defined in {@code assets/goblinlabour/sounds.json} and reuse vanilla villager
 * and witch recordings at a higher pitch, so a resource pack can swap them for real goblin voices.
 */
public final class GoblinSounds {
    public static final SoundEvent AMBIENT = register("entity.goblin.ambient");
    public static final SoundEvent YES = register("entity.goblin.yes");
    public static final SoundEvent CHATTER = register("entity.goblin.chatter");
    public static final SoundEvent NO = register("entity.goblin.no");
    public static final SoundEvent HURT = register("entity.goblin.hurt");
    public static final SoundEvent DEATH = register("entity.goblin.death");

    private GoblinSounds() {
    }

    public static void init() {
        // registration happens in the static initialiser
    }

    private static SoundEvent register(String name) {
        Identifier id = GoblinLabour.id(name);
        return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
    }
}
