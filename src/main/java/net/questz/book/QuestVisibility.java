package net.questz.book;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringIdentifiable;

import java.util.Locale;

public enum QuestVisibility implements StringIdentifiable {
    ALWAYS,
    DEPENDENCIES_VISIBLE,
    DEPENDENCIES_DONE,
    SECRET;

    public static final Codec<QuestVisibility> CODEC = StringIdentifiable.createCodec(QuestVisibility::values);

    @Override
    public String asString() {
        return this.name().toLowerCase(Locale.ROOT);
    }
}
