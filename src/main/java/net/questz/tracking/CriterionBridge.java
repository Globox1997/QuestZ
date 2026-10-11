package net.questz.tracking;

import net.minecraft.advancement.*;
import net.minecraft.advancement.criterion.Criterion;
import net.minecraft.advancement.criterion.CriterionConditions;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.questz.QuestzMain;
import net.questz.book.Quest;
import net.questz.book.QuestBookManager;
import net.questz.progress.QuestEngine;
import net.questz.progress.TeamData;
import net.questz.progress.TeamManager;
import net.questz.task.CriterionTask;
import net.questz.task.Task;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class CriterionBridge {

    private static final String PREFIX = "bridge/";

    private static final Map<UUID, Map<String, Registration<?>>> REGISTRATIONS = new HashMap<>();
    private static final Map<String, AdvancementEntry> ENTRIES = new HashMap<>();

    private record Registration<T extends CriterionConditions>(AdvancementCriterion<T> criterion, Criterion.ConditionsContainer<T> container) {

        static <T extends CriterionConditions> Registration<T> register(PlayerAdvancementTracker tracker, AdvancementEntry entry, String name, AdvancementCriterion<T> criterion) {
            Criterion.ConditionsContainer<T> container = new Criterion.ConditionsContainer<>(criterion.conditions(), entry, name);
            criterion.trigger().beginTrackingCondition(tracker, container);
            return new Registration<>(criterion, container);
        }

        void unregister(PlayerAdvancementTracker tracker) {
            this.criterion.trigger().endTrackingCondition(tracker, this.container);
        }
    }

    private static AdvancementEntry entry(String key) {
        return ENTRIES.computeIfAbsent(key, id -> new AdvancementEntry(QuestzMain.identifierOf(PREFIX + id),
                new Advancement(Optional.empty(), Optional.empty(), AdvancementRewards.NONE, Map.of(), AdvancementRequirements.EMPTY, false)));
    }

    public static boolean handleGrant(@Nullable ServerPlayerEntity player, AdvancementEntry advancement) {
        Identifier id = advancement.id();
        if (!id.getNamespace().equals("questz") || !id.getPath().startsWith(PREFIX)) {
            return false;
        }
        MinecraftServer server = player != null ? player.getServer() : null;
        if (server == null) {
            return true;
        }
        String[] parts = id.getPath().substring(PREFIX.length()).split("/", 2);
        Quest quest = parts.length == 2 ? QuestBookManager.getBook().getQuest(parts[0]) : null;
        Task task = quest != null ? quest.getTask(parts[1]).orElse(null) : null;
        if (task instanceof CriterionTask) {
            QuestEngine.addProgress(server, TeamManager.getTeam(player), quest, task, 1L);
        }
        return true;
    }

    public static void sync(ServerPlayerEntity player) {
        PlayerAdvancementTracker tracker = player.getAdvancementTracker();
        TeamData team = TeamManager.getTeam(player);

        Map<String, CriterionTask> desired = new HashMap<>();
        for (ActiveTasks.Entry entry : ActiveTasks.get(team)) {
            if (entry.task() instanceof CriterionTask criterionTask && !QuestEngine.isTaskComplete(team, entry.quest(), criterionTask)) {
                desired.put(TeamData.key(entry.quest().id(), criterionTask.getId()), criterionTask);
            }
        }

        Map<String, Registration<?>> current = REGISTRATIONS.computeIfAbsent(player.getUuid(), uuid -> new HashMap<>());
        Iterator<Map.Entry<String, Registration<?>>> iterator = current.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, Registration<?>> registered = iterator.next();
            CriterionTask task = desired.get(registered.getKey());
            if (task == null || task.getCriterion() != registered.getValue().criterion()) {
                registered.getValue().unregister(tracker);
                iterator.remove();
            }
        }
        desired.forEach((key, task) -> {
            if (!current.containsKey(key)) {
                current.put(key, Registration.register(tracker, entry(key), task.getId(), task.getCriterion()));
            }
        });
    }

    public static void syncTeam(MinecraftServer server, TeamData team) {
        team.getOnlineMembers(server).forEach(CriterionBridge::sync);
    }

    public static void syncAll(MinecraftServer server) {
        if (server.getPlayerManager() == null) {
            return;
        }
        server.getPlayerManager().getPlayerList().forEach(CriterionBridge::sync);
    }

    public static void onTrackerCleared(@Nullable ServerPlayerEntity player) {
        if (player != null) {
            REGISTRATIONS.remove(player.getUuid());
        }
    }

    public static void onDisconnect(ServerPlayerEntity player) {
        Map<String, Registration<?>> registrations = REGISTRATIONS.remove(player.getUuid());
        if (registrations != null) {
            PlayerAdvancementTracker tracker = player.getAdvancementTracker();
            registrations.values().forEach(registration -> registration.unregister(tracker));
        }
    }

    public static void clear() {
        REGISTRATIONS.clear();
        ENTRIES.clear();
    }

}
