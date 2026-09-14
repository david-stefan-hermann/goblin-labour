package goblinlabour.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Everything a goblin keeps when it is turned back into a blank: its name, the tools in its hotbar and its health.
 * Stored as the {@code goblinlabour:goblin} data component on the Goblin Blank and mirrored into the bed.
 */
public record GoblinData(String name, List<ItemStack> hotbar, float health) {
    public static final int HOTBAR_SIZE = 9;
    public static final float MAX_HEALTH = 20.0f;

    public static final Codec<GoblinData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("name").forGetter(GoblinData::name),
            ItemStack.OPTIONAL_CODEC.listOf().optionalFieldOf("hotbar", List.of()).forGetter(GoblinData::hotbar),
            Codec.FLOAT.optionalFieldOf("health", MAX_HEALTH).forGetter(GoblinData::health)
    ).apply(instance, GoblinData::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, GoblinData> STREAM_CODEC =
            ByteBufCodecs.fromCodecWithRegistries(CODEC);

    public GoblinData {
        hotbar = List.copyOf(padded(hotbar));
    }

    public static GoblinData fresh(String name) {
        return new GoblinData(name, List.of(), MAX_HEALTH);
    }

    /** A copy of the hotbar as a mutable list of exactly nine stacks. */
    public NonNullList<ItemStack> hotbarCopy() {
        NonNullList<ItemStack> list = NonNullList.withSize(HOTBAR_SIZE, ItemStack.EMPTY);
        for (int i = 0; i < HOTBAR_SIZE; i++) {
            list.set(i, hotbar.get(i).copy());
        }
        return list;
    }

    public int toolCount() {
        int n = 0;
        for (ItemStack stack : hotbar) {
            if (!stack.isEmpty()) n++;
        }
        return n;
    }

    private static List<ItemStack> padded(List<ItemStack> stacks) {
        List<ItemStack> out = new ArrayList<>(HOTBAR_SIZE);
        for (int i = 0; i < HOTBAR_SIZE; i++) {
            out.add(i < stacks.size() ? stacks.get(i).copy() : ItemStack.EMPTY);
        }
        return out;
    }
}
