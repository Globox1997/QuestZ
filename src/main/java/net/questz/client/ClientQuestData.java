package net.questz.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.questz.book.Chapter;
import net.questz.book.QuestBook;
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
    @Nullable
    private static ClientTeam team;

    public static QuestBook getBook() {
        return book;
    }

    @Nullable
    public static ClientTeam getTeam() {
        return team;
    }

    public static void resetBook() {
        book = QuestBook.EMPTY;
        CHANGED.invoker().onChanged();
    }

    public static void putChapter(Chapter chapter) {
        book = book.withChapter(chapter);
        CHANGED.invoker().onChanged();
    }

    public static void setTeam(TeamSyncPacket packet) {
        team = new ClientTeam(packet);
        CHANGED.invoker().onChanged();
    }

    public static void setTaskProgress(String questId, String taskId, long progress) {
        if (team != null) {
            team.setProgress(questId, taskId, progress);
            CHANGED.invoker().onChanged();
        }
    }

    public static void applyQuestState(QuestStatePacket packet) {
        if (team != null) {
            team.applyQuestState(packet);
            CHANGED.invoker().onChanged();
        }
    }

    public static void clear() {
        book = QuestBook.EMPTY;
        team = null;
        CHANGED.invoker().onChanged();
    }

    @FunctionalInterface
    public interface Changed {
        void onChanged();
    }

}
