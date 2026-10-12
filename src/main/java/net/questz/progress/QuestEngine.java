package net.questz.progress;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.questz.api.QuestzEvents;
import net.questz.book.DependencyMode;
import net.questz.book.Quest;
import net.questz.book.QuestBook;
import net.questz.book.QuestBookManager;
import net.questz.reward.ChoiceReward;
import net.questz.reward.Reward;
import net.questz.task.Task;
import net.questz.tracking.ActiveTasks;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class QuestEngine {

    public static boolean areDependenciesMet(QuestBook book, ProgressView team, Quest quest) {
        List<String> dependencies = quest.dependencies().stream().filter(id -> book.getQuest(id) != null).toList();
        if (dependencies.isEmpty()) {
            return true;
        }
        if (quest.dependencyMode() == DependencyMode.ALL) {
            return dependencies.stream().allMatch(team::hasEverCompleted);
        }
        return dependencies.stream().anyMatch(team::hasEverCompleted);
    }

    public static boolean isVisible(QuestBook book, ProgressView team, Quest quest) {
        return isVisible(book, team, quest, new HashSet<>());
    }

    private static boolean isVisible(QuestBook book, ProgressView team, Quest quest, Set<String> visiting) {
        if (team.hasEverCompleted(quest.id())) {
            return true;
        }
        return switch (quest.visibility()) {
            case ALWAYS -> true;
            case DEPENDENCIES_DONE -> areDependenciesMet(book, team, quest);
            case SECRET -> false;
            case DEPENDENCIES_VISIBLE -> {
                if (!visiting.add(quest.id())) {
                    yield false;
                }
                for (String dependencyId : quest.dependencies()) {
                    Quest dependency = book.getQuest(dependencyId);
                    if (dependency != null && !isVisible(book, team, dependency, visiting)) {
                        yield false;
                    }
                }
                yield true;
            }
        };
    }

    public static boolean canProgress(QuestBook book, ProgressView team, Quest quest) {
        return !team.isCompleted(quest.id()) && areDependenciesMet(book, team, quest);
    }

    public static boolean isTaskComplete(ProgressView team, Quest quest, Task task) {
        return team.getProgress(quest.id(), task.getId()) >= task.getTarget();
    }

    public static boolean areTasksComplete(ProgressView team, Quest quest) {
        return !quest.tasks().isEmpty() && quest.tasks().stream().allMatch(task -> isTaskComplete(team, quest, task));
    }

    public static void addProgress(MinecraftServer server, TeamData team, Quest quest, Task task, long amount) {
        if (amount <= 0L) {
            return;
        }
        long sum = team.getProgress(quest.id(), task.getId()) + amount;
        setProgress(server, team, quest, task, sum < 0L ? Long.MAX_VALUE : sum);
    }

    public static void setProgress(MinecraftServer server, TeamData team, Quest quest, Task task, long value) {
        QuestBook book = QuestBookManager.getBook();
        if (!canProgress(book, team, quest)) {
            return;
        }
        long clamped = Math.max(0L, Math.min(value, task.getTarget()));
        if (clamped == team.getProgress(quest.id(), task.getId())) {
            return;
        }
        team.setProgress(quest.id(), task.getId(), clamped);
        QuestzState.get(server).markDirty();
        QuestzEvents.TASK_PROGRESS.invoker().onTaskProgress(server, team, quest, task, clamped);

        if (clamped >= task.getTarget()) {
            tryComplete(server, book, team, quest);
        }
    }

    public static void forceComplete(MinecraftServer server, TeamData team, Quest quest) {
        if (team.isCompleted(quest.id())) {
            return;
        }
        for (Task task : quest.tasks()) {
            team.setProgress(quest.id(), task.getId(), task.getTarget());
        }
        markCompleted(server, QuestBookManager.getBook(), team, quest);
    }

    public static void reset(MinecraftServer server, TeamData team, Quest quest) {
        team.resetQuest(quest.id(), false);
        QuestzState.get(server).markDirty();
        ActiveTasks.invalidate(team.getId());
        QuestzEvents.QUEST_RESET.invoker().onQuestReset(server, team, quest);
    }

    public static boolean hasUnclaimedRewards(ServerPlayerEntity player, TeamData team, Quest quest) {
        return team.isCompleted(quest.id()) && quest.rewards().stream().anyMatch(reward -> !team.isRewardClaimed(quest.id(), reward.getId(), player.getUuid(), reward.isTeamReward()));
    }

    public static boolean isAutoClaim(Quest quest) {
        return quest.autoClaim() && !quest.repeatable();
    }

    public static int claimRewards(ServerPlayerEntity player, TeamData team, Quest quest) {
        if (!team.isCompleted(quest.id())) {
            return 0;
        }
        int claimed = 0;
        for (Reward reward : quest.rewards()) {
            if (reward.requiresChoice() || team.isRewardClaimed(quest.id(), reward.getId(), player.getUuid(), reward.isTeamReward())) {
                continue;
            }
            team.setRewardClaimed(quest.id(), reward.getId(), player.getUuid(), reward.isTeamReward());
            reward.claim(player);
            QuestzEvents.REWARD_CLAIMED.invoker().onRewardClaimed(player, team, quest, reward);
            claimed++;
        }
        if (claimed > 0) {
            QuestzState.get(player.getServer()).markDirty();
            repeatIfDone(player, team, quest);
        }
        return claimed;
    }

    public static boolean claimChoice(ServerPlayerEntity player, TeamData team, Quest quest, String rewardId, int option) {
        if (!team.isCompleted(quest.id())) {
            return false;
        }
        Reward reward = quest.getReward(rewardId).orElse(null);
        if (!(reward instanceof ChoiceReward choice) || option < 0 || option >= choice.getOptions().size()
                || team.isRewardClaimed(quest.id(), reward.getId(), player.getUuid(), reward.isTeamReward())) {
            return false;
        }
        team.setRewardClaimed(quest.id(), reward.getId(), player.getUuid(), reward.isTeamReward());
        choice.claim(player, option);
        QuestzEvents.REWARD_CLAIMED.invoker().onRewardClaimed(player, team, quest, reward);
        QuestzState.get(player.getServer()).markDirty();
        repeatIfDone(player, team, quest);
        return true;
    }

    private static void repeatIfDone(ServerPlayerEntity player, TeamData team, Quest quest) {
        MinecraftServer server = player.getServer();
        if (server == null || !quest.repeatable() || quest.rewards().isEmpty() || hasUnclaimedRewards(player, team, quest)) {
            return;
        }
        team.resetQuest(quest.id(), true);
        QuestzState.get(server).markDirty();
        ActiveTasks.invalidate(team.getId());
        QuestzEvents.QUEST_RESET.invoker().onQuestReset(server, team, quest);
    }

    public static void claimAutoRewards(ServerPlayerEntity player, TeamData team) {
        for (Quest quest : QuestBookManager.getBook().getQuests()) {
            if (isAutoClaim(quest)) {
                claimRewards(player, team, quest);
            }
        }
    }

    public static void checkOnlineTeams(MinecraftServer server) {
        if (server.getPlayerManager() == null) {
            return;
        }
        Set<TeamData> teams = new LinkedHashSet<>();
        server.getPlayerManager().getPlayerList().forEach(player -> teams.add(TeamManager.getTeam(player)));
        teams.forEach(team -> checkTeam(server, team));
    }

    public static void checkTeam(MinecraftServer server, TeamData team) {
        QuestBook book = QuestBookManager.getBook();
        for (Quest quest : book.getQuests()) {
            tryComplete(server, book, team, quest);
        }
    }

    private static void tryComplete(MinecraftServer server, QuestBook book, TeamData team, Quest quest) {
        if (canProgress(book, team, quest) && areTasksComplete(team, quest)) {
            markCompleted(server, book, team, quest);
        }
    }

    private static void markCompleted(MinecraftServer server, QuestBook book, TeamData team, Quest quest) {
        team.setCompleted(quest.id(), System.currentTimeMillis());
        QuestzState.get(server).markDirty();
        ActiveTasks.invalidate(team.getId());
        QuestzEvents.QUEST_COMPLETED.invoker().onQuestCompleted(server, team, quest);

        if (isAutoClaim(quest)) {
            for (ServerPlayerEntity member : team.getOnlineMembers(server)) {
                claimRewards(member, team, quest);
            }
        }

        for (Quest dependent : book.getDependents(quest.id())) {
            tryComplete(server, book, team, dependent);
        }
    }

}
