package net.questz.tracking;

import net.questz.book.Quest;
import net.questz.book.QuestBook;
import net.questz.book.QuestBookManager;
import net.questz.progress.QuestEngine;
import net.questz.progress.TeamData;
import net.questz.task.Task;

import java.util.*;

public class ActiveTasks {

    public record Entry(Quest quest, Task task) {
    }

    private static final Map<UUID, List<Entry>> CACHE = new HashMap<>();

    public static List<Entry> get(TeamData team) {
        return CACHE.computeIfAbsent(team.getId(), id -> compute(team));
    }

    private static List<Entry> compute(TeamData team) {
        QuestBook book = QuestBookManager.getBook();
        List<Entry> entries = new ArrayList<>();
        for (Quest quest : book.getQuests()) {
            if (QuestEngine.canProgress(book, team, quest)) {
                for (Task task : quest.tasks()) {
                    entries.add(new Entry(quest, task));
                }
            }
        }
        return List.copyOf(entries);
    }

    public static void invalidate(UUID teamId) {
        CACHE.remove(teamId);
    }

    public static void invalidateAll() {
        CACHE.clear();
    }

}
