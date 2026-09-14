package goblinlabour.menu;

import goblinlabour.job.Assignment;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** What the staff screen needs to know: where the player clicked, which order that means, and the world's limits. */
public record StaffMenuData(BlockPos pos, Direction face, Assignment.Kind kind, int selectedGoblins,
                            int minY, int surfaceY, int maxY) {
    public static final StreamCodec<RegistryFriendlyByteBuf, StaffMenuData> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, StaffMenuData::pos,
            Direction.STREAM_CODEC, StaffMenuData::face,
            ByteBufCodecs.VAR_INT.map(i -> Assignment.Kind.values()[i], Assignment.Kind::ordinal), StaffMenuData::kind,
            ByteBufCodecs.VAR_INT, StaffMenuData::selectedGoblins,
            ByteBufCodecs.VAR_INT, StaffMenuData::minY,
            ByteBufCodecs.VAR_INT, StaffMenuData::surfaceY,
            ByteBufCodecs.VAR_INT, StaffMenuData::maxY,
            StaffMenuData::new
    );
}
