package net.questz.book;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.text.TextCodecs;

import java.util.List;

public record Chapter(String id, Text title, ItemStack icon, int order, List<Quest> quests) {

    public static final Codec<Chapter> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            QuestBook.ID_CODEC.fieldOf("id").forGetter(Chapter::id),
            TextCodecs.CODEC.optionalFieldOf("title", Text.empty()).forGetter(Chapter::title),
            ItemStack.UNCOUNTED_CODEC.optionalFieldOf("icon", new ItemStack(Items.BOOK)).forGetter(Chapter::icon),
            Codec.INT.optionalFieldOf("order", 0).forGetter(Chapter::order),
            Quest.CODEC.listOf().optionalFieldOf("quests", List.of()).forGetter(Chapter::quests)
    ).apply(instance, Chapter::new));

}
