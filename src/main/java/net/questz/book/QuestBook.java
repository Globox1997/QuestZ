package net.questz.book;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import net.questz.reward.UnresolvedReward;
import net.questz.task.UnresolvedTask;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;
import java.util.regex.Pattern;

public class QuestBook {

    private static final Pattern ID_PATTERN = Pattern.compile("[a-z0-9_\\-]+");

    public static final Codec<String> ID_CODEC = Codec.STRING.validate(id -> ID_PATTERN.matcher(id).matches() ? DataResult.success(id) : DataResult.error(() -> "Invalid id: " + id));

    public static final QuestBook EMPTY = new QuestBook(List.of());

    private final List<Chapter> chapters;
    private final Map<String, Chapter> chapterById = new LinkedHashMap<>();
    private final Map<String, Quest> questById = new LinkedHashMap<>();
    private final Map<String, Chapter> chapterByQuestId = new HashMap<>();
    private final Map<String, List<Quest>> dependentsByQuestId = new HashMap<>();
    private final List<String> problems = new ArrayList<>();

    public QuestBook(Collection<Chapter> chapters) {
        List<Chapter> sorted = new ArrayList<>(chapters);
        sorted.sort(Comparator.comparingInt(Chapter::order));
        this.chapters = List.copyOf(sorted);

        for (Chapter chapter : this.chapters) {
            if (this.chapterById.putIfAbsent(chapter.id(), chapter) != null) {
                this.problems.add("Duplicate chapter id " + chapter.id());
                continue;
            }
            for (Quest quest : chapter.quests()) {
                if (this.questById.putIfAbsent(quest.id(), quest) != null) {
                    this.problems.add("Duplicate quest id " + quest.id() + " in chapter " + chapter.id());
                    continue;
                }
                this.chapterByQuestId.put(quest.id(), chapter);
            }
        }
        for (Quest quest : this.questById.values()) {
            for (String dependency : quest.dependencies()) {
                this.dependentsByQuestId.computeIfAbsent(dependency, id -> new ArrayList<>()).add(quest);
            }
        }
        this.validate();
    }

    private void validate() {
        for (Quest quest : this.questById.values()) {
            for (String dependency : quest.dependencies()) {
                if (dependency.equals(quest.id())) {
                    this.problems.add("Quest " + quest.id() + " depends on itself");
                } else if (!this.questById.containsKey(dependency)) {
                    this.problems.add("Quest " + quest.id() + " depends on missing quest " + dependency);
                }
            }
            Set<String> taskIds = new HashSet<>();
            quest.tasks().forEach(task -> {
                if (!taskIds.add(task.getId())) {
                    this.problems.add("Duplicate task id " + task.getId() + " in quest " + quest.id());
                }
                if (task instanceof UnresolvedTask unresolved) {
                    this.problems.add("Unresolved task " + task.getId() + " (" + unresolved.getTypeId() + ") in quest " + quest.id() + ": " + unresolved.getError());
                }
            });
            Set<String> rewardIds = new HashSet<>();
            quest.rewards().forEach(reward -> {
                if (!rewardIds.add(reward.getId())) {
                    this.problems.add("Duplicate reward id " + reward.getId() + " in quest " + quest.id());
                }
                if (reward instanceof UnresolvedReward unresolved) {
                    this.problems.add("Unresolved reward " + reward.getId() + " (" + unresolved.getTypeId() + ") in quest " + quest.id() + ": " + unresolved.getError());
                }
            });
        }

        Map<String, Integer> state = new HashMap<>();
        for (String questId : this.questById.keySet()) {
            String cycle = this.findCycle(questId, state);
            if (cycle != null) {
                this.problems.add("Dependency cycle involving quest " + cycle);
            }
        }
    }

    @Nullable
    private String findCycle(String questId, Map<String, Integer> state) {
        Integer current = state.get(questId);
        if (current != null) {
            return current == 1 ? questId : null;
        }
        state.put(questId, 1);
        Quest quest = this.questById.get(questId);
        if (quest != null) {
            for (String dependency : quest.dependencies()) {
                if (!dependency.equals(questId)) {
                    String cycle = this.findCycle(dependency, state);
                    if (cycle != null) {
                        state.put(questId, 2);
                        return cycle;
                    }
                }
            }
        }
        state.put(questId, 2);
        return null;
    }

    public List<Chapter> getChapters() {
        return this.chapters;
    }

    public Collection<Quest> getQuests() {
        return Collections.unmodifiableCollection(this.questById.values());
    }

    @Nullable
    public Chapter getChapter(String chapterId) {
        return this.chapterById.get(chapterId);
    }

    @Nullable
    public Quest getQuest(String questId) {
        return this.questById.get(questId);
    }

    @Nullable
    public Chapter getChapterOf(String questId) {
        return this.chapterByQuestId.get(questId);
    }

    public List<Quest> getDependents(String questId) {
        return Collections.unmodifiableList(this.dependentsByQuestId.getOrDefault(questId, List.of()));
    }

    public List<String> getProblems() {
        return Collections.unmodifiableList(this.problems);
    }

    public QuestBook withChapter(Chapter chapter) {
        List<Chapter> updated = new ArrayList<>();
        boolean replaced = false;
        for (Chapter existing : this.chapters) {
            if (existing.id().equals(chapter.id())) {
                updated.add(chapter);
                replaced = true;
            } else {
                updated.add(existing);
            }
        }
        if (!replaced) {
            updated.add(chapter);
        }
        return new QuestBook(updated);
    }

    public QuestBook withoutChapter(String chapterId) {
        return new QuestBook(this.chapters.stream().filter(chapter -> !chapter.id().equals(chapterId)).toList());
    }

    public String generateId() {
        return generateId(id -> this.chapterById.containsKey(id) || this.questById.containsKey(id));
    }

    public static String generateId(Predicate<String> taken) {
        String id;
        do {
            id = String.format("%08x", ThreadLocalRandom.current().nextInt());
        } while (taken.test(id));
        return id;
    }

}
