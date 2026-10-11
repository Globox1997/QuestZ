package net.questz.book;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringIdentifiable;

import java.util.Locale;

public enum DependencyMode implements StringIdentifiable {
    ALL,
    ANY;

    public static final Codec<DependencyMode> CODEC = StringIdentifiable.createCodec(DependencyMode::values);

    @Override
    public String asString() {
        return this.name().toLowerCase(Locale.ROOT);
    }
}
