package net.questz.book;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.text.TextCodecs;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public record Chapter(String id, Text title, ItemStack icon, int order, Optional<Identifier> background, List<Quest> quests) {

    public static final Codec<Chapter> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            QuestBook.ID_CODEC.fieldOf("id").forGetter(Chapter::id),
            TextCodecs.CODEC.optionalFieldOf("title", Text.empty()).forGetter(Chapter::title),
            ItemStack.UNCOUNTED_CODEC.optionalFieldOf("icon", new ItemStack(Items.BOOK)).forGetter(Chapter::icon),
            Codec.INT.optionalFieldOf("order", 0).forGetter(Chapter::order),
            Identifier.CODEC.optionalFieldOf("background").forGetter(Chapter::background),
            Quest.CODEC.listOf().optionalFieldOf("quests", List.of()).forGetter(Chapter::quests)
    ).apply(instance, Chapter::new));

    public Chapter withQuests(List<Quest> quests) {
        return new Chapter(this.id, this.title, this.icon, this.order, this.background, List.copyOf(quests));
    }

    public Chapter withQuest(Quest quest) {
        List<Quest> updated = new ArrayList<>(this.quests);
        int index = -1;
        for (int i = 0; i < updated.size(); i++) {
            if (updated.get(i).id().equals(quest.id())) {
                index = i;
                break;
            }
        }
        if (index >= 0) {
            updated.set(index, quest);
        } else {
            updated.add(quest);
        }
        return this.withQuests(updated);
    }

    public Chapter withoutQuest(String questId) {
        return this.withQuests(this.quests.stream().filter(quest -> !quest.id().equals(questId)).toList());
    }

}
