package goblinlabour.menu;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** Sent to the client when the bed screen opens: where the bed is and the goblin's name. */
public record GoblinBedMenuData(BlockPos pos, String goblinName) {
    public static final StreamCodec<RegistryFriendlyByteBuf, GoblinBedMenuData> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, GoblinBedMenuData::pos,
            ByteBufCodecs.STRING_UTF8, GoblinBedMenuData::goblinName,
            GoblinBedMenuData::new
    );
}
