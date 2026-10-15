package net.questz.client.screen;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.item.ItemStack;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.texture.MissingSprite;
import net.minecraft.client.texture.Sprite;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.questz.book.Chapter;
import net.questz.book.DependencyMode;
import net.questz.book.Quest;
import net.questz.book.QuestBook;
import net.questz.client.ClientQuestData;
import net.questz.client.ClientTeam;
import net.questz.client.render.DisplayNames;
import net.questz.client.render.TaskRenderers;
import net.questz.progress.ProgressView;
import net.questz.progress.QuestEngine;

@Environment(EnvType.CLIENT)
public class QuestTexts {

    public static final ProgressView NO_PROGRESS = new ProgressView() {
        @Override
        public boolean isCompleted(String questId) {
            return false;
        }

        @Override
        public long getProgress(String questId, String taskId) {
            return 0L;
        }

        @Override
        public int getCompletions(String questId) {
            return 0;
        }
    };

    public static ProgressView progress() {
        ClientTeam team = ClientQuestData.getTeam();
        return team != null ? team : NO_PROGRESS;
    }

    public static Text title(Quest quest) {
        return quest.title().getString().isEmpty() ? Text.literal(quest.id()) : quest.title();
    }

    public static ItemStack icon(Quest quest) {
        if (!quest.icons().isEmpty()) {
            return DisplayNames.cycle(quest.icons());
        }
        return quest.tasks().isEmpty() ? ItemStack.EMPTY : TaskRenderers.getIcon(quest.tasks().getFirst());
    }

    public static void drawIcon(DrawContext context, ItemStack icon, int x, int y) {
        if (icon.isEmpty()) {
            Sprite sprite = MinecraftClient.getInstance().getSpriteAtlas(PlayerScreenHandler.BLOCK_ATLAS_TEXTURE).apply(MissingSprite.getMissingSpriteId());
            context.drawSprite(x, y, 0, 16, 16, sprite);
        } else {
            context.drawItemWithoutEntity(icon, x, y);
        }
    }

    public static Text chapterTitle(Chapter chapter) {
        return chapter.title().getString().isEmpty() ? Text.literal(chapter.id()) : chapter.title();
    }

    public static boolean isUnlocked(QuestBook book, ProgressView progress, Quest quest) {
        return progress.isCompleted(quest.id()) || QuestEngine.areDependenciesMet(book, progress, quest);
    }

    public static boolean hasClaimableRewards(Quest quest) {
        ClientTeam team = ClientQuestData.getTeam();
        return team != null && team.isCompleted(quest.id())
                && quest.rewards().stream().anyMatch(reward -> !reward.requiresChoice() && !team.isRewardClaimed(quest.id(), reward.getId()));
    }

    public static boolean isClaimable(Quest quest) {
        ClientTeam team = ClientQuestData.getTeam();
        return team != null && team.isCompleted(quest.id()) && quest.rewards().stream().anyMatch(reward -> !team.isRewardClaimed(quest.id(), reward.getId()));
    }

    public static Text status(QuestBook book, ProgressView progress, Quest quest) {
        if (progress.isCompleted(quest.id())) {
            return Text.translatable("gui.questz.status.completed").formatted(Formatting.GREEN);
        }
        if (QuestEngine.areDependenciesMet(book, progress, quest)) {
            return Text.translatable("gui.questz.status.available").formatted(Formatting.YELLOW);
        }
        return Text.translatable("gui.questz.status.locked").formatted(Formatting.GRAY);
    }

    public static Text requirements(QuestBook book, ProgressView progress, Quest quest) {
        MutableText names = Text.empty();
        boolean first = true;
        for (String dependencyId : quest.dependencies()) {
            Quest dependency = book.getQuest(dependencyId);
            if (dependency == null) {
                continue;
            }
            if (!first) {
                names.append(Text.literal(", ").formatted(Formatting.GRAY));
            }
            names.append(title(dependency).copy().formatted(progress.hasEverCompleted(dependencyId) ? Formatting.GREEN : Formatting.RED));
            first = false;
        }
        String key = quest.dependencyMode() == DependencyMode.ALL ? "gui.questz.requires.all" : "gui.questz.requires.any";
        return Text.translatable(key, names).formatted(Formatting.GRAY);
    }

}
