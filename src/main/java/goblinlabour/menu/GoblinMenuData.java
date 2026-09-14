package goblinlabour.menu;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** Sent to the client when a goblin inventory opens: which entity it belongs to (for the portrait). */
public record GoblinMenuData(int entityId) {
    public static final StreamCodec<RegistryFriendlyByteBuf, GoblinMenuData> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, GoblinMenuData::entityId,
            GoblinMenuData::new
    );
}
