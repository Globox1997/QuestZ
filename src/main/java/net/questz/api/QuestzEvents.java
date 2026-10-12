package net.questz.api;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.questz.book.Chapter;
import net.questz.book.Quest;
import net.questz.book.QuestBook;
import net.questz.progress.TeamData;
import net.questz.reward.Reward;
import net.questz.task.Task;
import org.jetbrains.annotations.Nullable;

public class QuestzEvents {

    public static final Event<BookChanged> BOOK_CHANGED = EventFactory.createArrayBacked(BookChanged.class, listeners -> (server, book) -> {
        for (BookChanged listener : listeners) {
            listener.onBookChanged(server, book);
        }
    });

    public static final Event<ChapterChanged> CHAPTER_CHANGED = EventFactory.createArrayBacked(ChapterChanged.class, listeners -> (server, chapterId, chapter) -> {
        for (ChapterChanged listener : listeners) {
            listener.onChapterChanged(server, chapterId, chapter);
        }
    });

    public static final Event<TaskProgress> TASK_PROGRESS = EventFactory.createArrayBacked(TaskProgress.class, listeners -> (server, team, quest, task, progress) -> {
        for (TaskProgress listener : listeners) {
            listener.onTaskProgress(server, team, quest, task, progress);
        }
    });

    public static final Event<QuestCompleted> QUEST_COMPLETED = EventFactory.createArrayBacked(QuestCompleted.class, listeners -> (server, team, quest) -> {
        for (QuestCompleted listener : listeners) {
            listener.onQuestCompleted(server, team, quest);
        }
    });

    public static final Event<QuestReset> QUEST_RESET = EventFactory.createArrayBacked(QuestReset.class, listeners -> (server, team, quest) -> {
        for (QuestReset listener : listeners) {
            listener.onQuestReset(server, team, quest);
        }
    });

    public static final Event<RewardClaimed> REWARD_CLAIMED = EventFactory.createArrayBacked(RewardClaimed.class, listeners -> (player, team, quest, reward) -> {
        for (RewardClaimed listener : listeners) {
            listener.onRewardClaimed(player, team, quest, reward);
        }
    });

    public static final Event<TeamUpdated> TEAM_UPDATED = EventFactory.createArrayBacked(TeamUpdated.class, listeners -> (server, team) -> {
        for (TeamUpdated listener : listeners) {
            listener.onTeamUpdated(server, team);
        }
    });

    public static final Event<TeamInvited> TEAM_INVITED = EventFactory.createArrayBacked(TeamInvited.class, listeners -> (server, player, party) -> {
        for (TeamInvited listener : listeners) {
            listener.onTeamInvited(server, player, party);
        }
    });

    public static final Event<TeamChanged> TEAM_CHANGED = EventFactory.createArrayBacked(TeamChanged.class, listeners -> (server, player, previous, current) -> {
        for (TeamChanged listener : listeners) {
            listener.onTeamChanged(server, player, previous, current);
        }
    });

    @FunctionalInterface
    public interface BookChanged {
        void onBookChanged(MinecraftServer server, QuestBook book);
    }

    @FunctionalInterface
    public interface ChapterChanged {
        void onChapterChanged(MinecraftServer server, String chapterId, @Nullable Chapter chapter);
    }

    @FunctionalInterface
    public interface TaskProgress {
        void onTaskProgress(MinecraftServer server, TeamData team, Quest quest, Task task, long progress);
    }

    @FunctionalInterface
    public interface QuestCompleted {
        void onQuestCompleted(MinecraftServer server, TeamData team, Quest quest);
    }

    @FunctionalInterface
    public interface QuestReset {
        void onQuestReset(MinecraftServer server, TeamData team, Quest quest);
    }

    @FunctionalInterface
    public interface RewardClaimed {
        void onRewardClaimed(ServerPlayerEntity player, TeamData team, Quest quest, Reward reward);
    }

    @FunctionalInterface
    public interface TeamUpdated {
        void onTeamUpdated(MinecraftServer server, TeamData team);
    }

    @FunctionalInterface
    public interface TeamInvited {
        void onTeamInvited(MinecraftServer server, ServerPlayerEntity player, TeamData party);
    }

    @FunctionalInterface
    public interface TeamChanged {
        void onTeamChanged(MinecraftServer server, ServerPlayerEntity player, TeamData previous, TeamData current);
    }

}
