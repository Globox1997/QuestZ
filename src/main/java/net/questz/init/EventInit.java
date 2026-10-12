package net.questz.init;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.questz.api.QuestzEvents;
import net.questz.book.QuestBookManager;
import net.questz.command.QuestzCommand;
import net.questz.network.packet.QuestStatePacket;
import net.questz.progress.QuestEngine;
import net.questz.progress.TeamData;
import net.questz.progress.TeamManager;
import net.questz.task.CriterionTask;
import net.questz.tracking.ActiveTasks;
import net.questz.tracking.CriterionBridge;
import net.questz.tracking.TaskTracker;

public class EventInit {

    public static void init() {
        ServerLifecycleEvents.SERVER_STARTING.register(QuestBookManager::load);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            QuestBookManager.unload();
            TeamManager.clearInvites();
            CriterionBridge.clear();
        });
        ServerTickEvents.END_SERVER_TICK.register(TaskTracker::tick);

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayerEntity player = handler.getPlayer();
            TeamManager.onJoin(player);
            TeamData team = TeamManager.getTeam(player);
            QuestEngine.checkTeam(server, team);
            QuestEngine.claimAutoRewards(player, team);
            CriterionBridge.sync(player);
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> CriterionBridge.onDisconnect(handler.getPlayer()));

        QuestzEvents.BOOK_CHANGED.register((server, book) -> CriterionBridge.syncAll(server));
        QuestzEvents.CHAPTER_CHANGED.register((server, chapterId, chapter) -> {
            QuestEngine.checkOnlineTeams(server);
            CriterionBridge.syncAll(server);
        });
        QuestzEvents.TEAM_CHANGED.register((server, player, previous, current) -> {
            ActiveTasks.invalidate(previous.getId());
            CriterionBridge.sync(player);
        });
        QuestzEvents.TASK_PROGRESS.register((server, team, quest, task, progress) -> {
            if (task instanceof CriterionTask && progress >= task.getTarget()) {
                CriterionBridge.syncTeam(server, team);
            }
        });
        QuestzEvents.QUEST_RESET.register((server, team, quest) -> CriterionBridge.syncTeam(server, team));
        QuestzEvents.QUEST_COMPLETED.register((server, team, quest) -> {
            CriterionBridge.syncTeam(server, team);
            Text name = QuestzCommand.questName(quest);
            for (ServerPlayerEntity member : team.getOnlineMembers(server)) {
                if (ServerPlayNetworking.canSend(member, QuestStatePacket.PACKET_ID)) {
                    continue;
                }
                member.sendMessage(Text.translatable("quest.questz.completed", name).formatted(Formatting.GREEN), false);
                if (!QuestEngine.isAutoClaim(quest) && QuestEngine.hasUnclaimedRewards(member, team, quest)) {
                    Text claim = Text.translatable("quest.questz.claim").styled(style -> style.withColor(Formatting.GOLD)
                            .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/questz claim " + quest.id())));
                    member.sendMessage(Text.translatable("quest.questz.rewards_available", claim), false);
                }
            }
        });
    }

}
