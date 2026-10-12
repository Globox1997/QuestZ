package net.questz.book;

import com.mojang.serialization.Codec;
import net.minecraft.advancement.AdvancementFrame;
import net.minecraft.util.StringIdentifiable;

import java.util.Locale;

public enum QuestShape implements StringIdentifiable {
    TASK(AdvancementFrame.TASK),
    GOAL(AdvancementFrame.GOAL),
    CHALLENGE(AdvancementFrame.CHALLENGE);

    public static final Codec<QuestShape> CODEC = StringIdentifiable.createCodec(QuestShape::values);

    private final AdvancementFrame frame;

    QuestShape(AdvancementFrame frame) {
        this.frame = frame;
    }

    public AdvancementFrame getFrame() {
        return this.frame;
    }

    @Override
    public String asString() {
        return this.name().toLowerCase(Locale.ROOT);
    }
}
