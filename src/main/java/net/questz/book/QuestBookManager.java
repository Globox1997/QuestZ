package net.questz.book;

import net.minecraft.server.MinecraftServer;
import net.questz.QuestzMain;
import net.questz.api.QuestzEvents;
import net.questz.storage.QuestStorage;
import net.questz.tracking.ActiveTasks;

public class QuestBookManager {

    private static QuestBook book = QuestBook.EMPTY;

    public static QuestBook getBook() {
        return book;
    }

    public static void load(MinecraftServer server) {
        book = QuestStorage.load(server);
        ActiveTasks.invalidateAll();
        QuestzEvents.BOOK_CHANGED.invoker().onBookChanged(server, book);
        QuestzMain.LOGGER.info("Loaded {} quest chapters with {} quests", book.getChapters().size(), book.getQuests().size());
    }

    public static void unload() {
        book = QuestBook.EMPTY;
        ActiveTasks.invalidateAll();
    }

    public static boolean putChapter(MinecraftServer server, Chapter chapter) {
        if (!QuestStorage.saveChapter(server, chapter)) {
            return false;
        }
        book = book.withChapter(chapter);
        ActiveTasks.invalidateAll();
        QuestzEvents.CHAPTER_CHANGED.invoker().onChapterChanged(server, chapter.id(), book.getChapter(chapter.id()));
        return true;
    }

    public static boolean removeChapter(MinecraftServer server, String chapterId) {
        if (!QuestStorage.deleteChapter(server, chapterId)) {
            return false;
        }
        book = book.withoutChapter(chapterId);
        ActiveTasks.invalidateAll();
        QuestzEvents.CHAPTER_CHANGED.invoker().onChapterChanged(server, chapterId, null);
        return true;
    }

}
