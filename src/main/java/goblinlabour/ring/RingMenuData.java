package goblinlabour.ring;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

import java.util.UUID;

/** Sent to the client when a ring screen opens: which ring it shows. */
public record RingMenuData(UUID ringId) {
    public static final StreamCodec<RegistryFriendlyByteBuf, RingMenuData> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, RingMenuData::ringId,
            RingMenuData::new
    );
}
