package net.questz.util;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.component.Component;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

public record ItemReward(Identifier item, int count, @Nullable NbtCompound nbt) {
    public static final Codec<ItemReward> CODEC =
            RecordCodecBuilder.create(instance -> instance.group(
                    Identifier.CODEC.fieldOf("item").forGetter(ItemReward::item),
                    Codec.INT.optionalFieldOf("count", 1).forGetter(ItemReward::count),
//                    Component
                    NbtCompound.CODEC.optionalFieldOf("nbt").forGetter(r -> Optional.ofNullable(r.nbt))
            ).apply(instance, (item, count, nbt) ->
                    new ItemReward(item, count, nbt.orElse(null))
            ));
}
