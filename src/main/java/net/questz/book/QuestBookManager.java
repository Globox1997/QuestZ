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
        setBook(server, QuestStorage.load(server));
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
        setBook(server, book.withChapter(chapter));
        return true;
    }

    public static boolean removeChapter(MinecraftServer server, String chapterId) {
        if (!QuestStorage.deleteChapter(server, chapterId)) {
            return false;
        }
        setBook(server, book.withoutChapter(chapterId));
        return true;
    }

    private static void setBook(MinecraftServer server, QuestBook newBook) {
        book = newBook;
        ActiveTasks.invalidateAll();
        QuestzEvents.BOOK_CHANGED.invoker().onBookChanged(server, newBook);
    }

}
