package net.questz.book;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringIdentifiable;

import java.util.Locale;

public enum LineStyle implements StringIdentifiable {
    DIRECT,
    ORTHOGONAL;

    public static final Codec<LineStyle> CODEC = StringIdentifiable.createCodec(LineStyle::values);

    public LineStyle next() {
        return this == DIRECT ? ORTHOGONAL : DIRECT;
    }

    @Override
    public String asString() {
        return this.name().toLowerCase(Locale.ROOT);
    }
}
