package net.questz.book;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringIdentifiable;

import java.util.Locale;

public enum QuestShape implements StringIdentifiable {
    CIRCLE,
    SQUARE,
    DIAMOND,
    HEXAGON;

    public static final Codec<QuestShape> CODEC = StringIdentifiable.createCodec(QuestShape::values);

    @Override
    public String asString() {
        return this.name().toLowerCase(Locale.ROOT);
    }
}
