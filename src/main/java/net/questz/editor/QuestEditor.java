package net.questz.editor;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.MathHelper;
import net.questz.QuestzMain;
import net.questz.book.Chapter;
import net.questz.book.Quest;
import net.questz.book.QuestBook;
import net.questz.book.QuestBookManager;
import net.questz.network.packet.MoveQuestsPacket;
import net.questz.reward.Reward;
import net.questz.reward.UnresolvedReward;
import net.questz.task.Task;
import net.questz.task.UnresolvedTask;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class QuestEditor {

    public static final int PERMISSION_LEVEL = 2;
    private static final double MAX_COORDINATE = 10000.0D;

    public static boolean canEdit(PlayerEntity player) {
        return player.hasPermissionLevel(PERMISSION_LEVEL);
    }

    private static void fail(ServerPlayerEntity player, String key, Object... args) {
        player.sendMessage(Text.translatable(key, args).formatted(Formatting.RED), false);
    }

    public static void editQuest(ServerPlayerEntity player, String chapterId, Quest quest) {
        MinecraftServer server = player.getServer();
        QuestBook book = QuestBookManager.getBook();
        Chapter target = book.getChapter(chapterId);
        if (server == null) {
            return;
        }
        if (target == null) {
            fail(player, "editor.questz.error.unknown_chapter", chapterId);
            return;
        }
        String error = validateQuest(book.getQuest(quest.id()), quest);
        if (error != null) {
            fail(player, "editor.questz.error.invalid_quest", error);
            return;
        }
        Quest clamped = quest.withPosition(clamp(quest.x()), clamp(quest.y()));
        Map<String, Chapter> changed = new LinkedHashMap<>();
        Chapter previous = book.getChapterOf(quest.id());
        if (previous != null && !previous.id().equals(chapterId)) {
            changed.put(previous.id(), previous.withoutQuest(quest.id()));
        }
        changed.put(chapterId, target.withQuest(clamped));
        if (apply(player, server, book, changed)) {
            QuestzMain.LOGGER.info("{} edited quest {} in chapter {}", player.getGameProfile().getName(), quest.id(), chapterId);
        }
    }

    public static void deleteQuest(ServerPlayerEntity player, String questId) {
        MinecraftServer server = player.getServer();
        QuestBook book = QuestBookManager.getBook();
        Chapter chapter = book.getChapterOf(questId);
        if (server == null || chapter == null) {
            return;
        }
        Map<String, Chapter> changed = removeDependencyReferences(book, Set.of(questId));
        changed.put(chapter.id(), changed.getOrDefault(chapter.id(), chapter).withoutQuest(questId));
        if (apply(player, server, book, changed)) {
            QuestzMain.LOGGER.info("{} deleted quest {}", player.getGameProfile().getName(), questId);
        }
    }

    public static void moveQuests(ServerPlayerEntity player, List<MoveQuestsPacket.Move> moves) {
        MinecraftServer server = player.getServer();
        QuestBook book = QuestBookManager.getBook();
        if (server == null) {
            return;
        }
        Map<String, Chapter> changed = new LinkedHashMap<>();
        for (MoveQuestsPacket.Move move : moves) {
            Quest quest = book.getQuest(move.questId());
            Chapter chapter = book.getChapterOf(move.questId());
            if (quest == null || chapter == null || !Double.isFinite(move.x()) || !Double.isFinite(move.y())) {
                continue;
            }
            Chapter current = changed.getOrDefault(chapter.id(), chapter);
            changed.put(chapter.id(), current.withQuest(quest.withPosition(clamp(move.x()), clamp(move.y()))));
        }
        apply(player, server, book, changed);
    }

    public static void editChapter(ServerPlayerEntity player, Chapter header) {
        MinecraftServer server = player.getServer();
        QuestBook book = QuestBookManager.getBook();
        if (server == null) {
            return;
        }
        if (book.getQuest(header.id()) != null) {
            fail(player, "editor.questz.error.id_taken", header.id());
            return;
        }
        Chapter existing = book.getChapter(header.id());
        Chapter chapter = header.withQuests(existing != null ? existing.quests() : List.of());
        if (apply(player, server, book, Map.of(chapter.id(), chapter))) {
            QuestzMain.LOGGER.info("{} edited chapter {}", player.getGameProfile().getName(), chapter.id());
        }
    }

    public static void deleteChapter(ServerPlayerEntity player, String chapterId) {
        MinecraftServer server = player.getServer();
        QuestBook book = QuestBookManager.getBook();
        Chapter chapter = book.getChapter(chapterId);
        if (server == null || chapter == null) {
            return;
        }
        Set<String> removed = new HashSet<>();
        chapter.quests().forEach(quest -> removed.add(quest.id()));
        Map<String, Chapter> changed = removeDependencyReferences(book, removed);
        changed.remove(chapterId);
        if (!apply(player, server, book, changed)) {
            return;
        }
        if (QuestBookManager.removeChapter(server, chapterId)) {
            QuestzMain.LOGGER.info("{} deleted chapter {}", player.getGameProfile().getName(), chapterId);
        } else {
            fail(player, "editor.questz.error.save");
        }
    }

    private static Map<String, Chapter> removeDependencyReferences(QuestBook book, Set<String> removedQuests) {
        Map<String, Chapter> changed = new LinkedHashMap<>();
        for (Chapter chapter : book.getChapters()) {
            Chapter current = chapter;
            for (Quest quest : chapter.quests()) {
                if (quest.dependencies().stream().anyMatch(removedQuests::contains)) {
                    current = current.withQuest(quest.withDependencies(quest.dependencies().stream().filter(id -> !removedQuests.contains(id)).toList()));
                }
            }
            if (current != chapter) {
                changed.put(chapter.id(), current);
            }
        }
        return changed;
    }

    private static boolean apply(ServerPlayerEntity player, MinecraftServer server, QuestBook book, Map<String, Chapter> changed) {
        if (changed.isEmpty()) {
            return true;
        }
        QuestBook updated = book;
        for (Chapter chapter : changed.values()) {
            updated = updated.withChapter(chapter);
        }
        if (countStructuralProblems(updated) > countStructuralProblems(book)) {
            fail(player, "editor.questz.error.structure");
            return false;
        }
        for (Chapter chapter : changed.values()) {
            if (!QuestBookManager.putChapter(server, chapter)) {
                fail(player, "editor.questz.error.save");
                return false;
            }
        }
        return true;
    }

    private static long countStructuralProblems(QuestBook book) {
        return book.getProblems().stream()
                .filter(problem -> problem.startsWith("Dependency cycle") || problem.contains("depends on itself") || problem.startsWith("Duplicate quest id") || problem.startsWith("Duplicate chapter id"))
                .count();
    }

    @Nullable
    private static String validateQuest(@Nullable Quest previous, Quest quest) {
        Set<String> taskIds = new HashSet<>();
        for (Task task : quest.tasks()) {
            if (!taskIds.add(task.getId())) {
                return "duplicate task id " + task.getId();
            }
            if (task instanceof UnresolvedTask unresolved && !wasUnresolved(previous, task.getId(), unresolved)) {
                return task.getId() + ": " + unresolved.getError();
            }
        }
        Set<String> rewardIds = new HashSet<>();
        for (Reward reward : quest.rewards()) {
            if (!rewardIds.add(reward.getId())) {
                return "duplicate reward id " + reward.getId();
            }
            if (reward instanceof UnresolvedReward unresolved && (previous == null || previous.getReward(reward.getId())
                    .filter(old -> old instanceof UnresolvedReward oldUnresolved && oldUnresolved.getRaw().equals(unresolved.getRaw())).isEmpty())) {
                return reward.getId() + ": " + unresolved.getError();
            }
        }
        if (!Float.isFinite(quest.size()) || quest.size() < 0.25F || quest.size() > 4.0F) {
            return "size must be between 0.25 and 4";
        }
        return null;
    }

    private static boolean wasUnresolved(@Nullable Quest previous, String taskId, UnresolvedTask unresolved) {
        return previous != null && previous.getTask(taskId).filter(old -> old instanceof UnresolvedTask oldUnresolved && oldUnresolved.getRaw().equals(unresolved.getRaw())).isPresent();
    }

    private static double clamp(double value) {
        return MathHelper.clamp(value, -MAX_COORDINATE, MAX_COORDINATE);
    }

}
