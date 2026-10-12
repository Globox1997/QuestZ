package net.questz.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.client.MinecraftClient;
import net.questz.book.Chapter;
import net.questz.book.Quest;
import net.questz.book.QuestBook;
import net.questz.client.screen.QuestTexts;
import net.questz.network.packet.QuestStatePacket;
import net.questz.network.packet.TeamSyncPacket;
import org.jetbrains.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class ClientQuestData {

    public static final Event<Changed> CHANGED = EventFactory.createArrayBacked(Changed.class, listeners -> () -> {
        for (Changed listener : listeners) {
            listener.onChanged();
        }
    });

    private static QuestBook book = QuestBook.EMPTY;
    private static int revision;
    @Nullable
    private static ClientTeam team;

    public static int getRevision() {
        return revision;
    }

    private static void changed() {
        revision++;
        CHANGED.invoker().onChanged();
    }

    public static QuestBook getBook() {
        return book;
    }

    @Nullable
    public static ClientTeam getTeam() {
        return team;
    }

    public static void resetBook() {
        book = QuestBook.EMPTY;
        changed();
    }

    public static void putChapter(Chapter chapter) {
        book = book.withChapter(chapter);
        changed();
    }

    public static void removeChapter(String chapterId) {
        book = book.withoutChapter(chapterId);
        changed();
    }

    public static void setTeam(TeamSyncPacket packet) {
        team = new ClientTeam(packet);
        changed();
    }

    public static void setTaskProgress(String questId, String taskId, long progress) {
        if (team != null) {
            team.setProgress(questId, taskId, progress);
            changed();
        }
    }

    public static void applyQuestState(QuestStatePacket packet) {
        if (team != null) {
            int previousCompletions = team.getCompletions(packet.questId());
            team.applyQuestState(packet);
            changed();
            Quest quest = book.getQuest(packet.questId());
            MinecraftClient client = MinecraftClient.getInstance();
            if (quest != null && packet.completions() > previousCompletions) {
                client.getToastManager().add(new QuestToast(quest, QuestTexts.isClaimable(quest)));
            }
        }
    }

    public static void clear() {
        book = QuestBook.EMPTY;
        team = null;
        changed();
    }

    @FunctionalInterface
    public interface Changed {
        void onChanged();
    }

}
