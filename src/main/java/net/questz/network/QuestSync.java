package net.questz.network;

import com.mojang.authlib.GameProfile;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.UserCache;
import net.questz.api.QuestzEvents;
import net.questz.book.Chapter;
import net.questz.book.Quest;
import net.questz.book.QuestBookManager;
import net.questz.network.packet.*;
import net.questz.progress.TeamData;
import net.questz.progress.TeamManager;
import net.questz.reward.Reward;
import net.questz.task.Task;

import java.util.*;

public class QuestSync {

    public static void init() {
        PayloadTypeRegistry.playS2C().register(BookResetPacket.PACKET_ID, BookResetPacket.PACKET_CODEC);
        PayloadTypeRegistry.playS2C().register(ChapterSyncPacket.PACKET_ID, ChapterSyncPacket.PACKET_CODEC);
        PayloadTypeRegistry.playS2C().register(TeamSyncPacket.PACKET_ID, TeamSyncPacket.PACKET_CODEC);
        PayloadTypeRegistry.playS2C().register(TaskProgressPacket.PACKET_ID, TaskProgressPacket.PACKET_CODEC);
        PayloadTypeRegistry.playS2C().register(QuestStatePacket.PACKET_ID, QuestStatePacket.PACKET_CODEC);

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            sendBook(handler.getPlayer());
            sendTeam(handler.getPlayer());
        });
        QuestzEvents.BOOK_CHANGED.register((server, book) -> {
            if (server.getPlayerManager() != null) {
                server.getPlayerManager().getPlayerList().forEach(QuestSync::sendBook);
            }
        });
        QuestzEvents.TEAM_CHANGED.register((server, player, previous, current) -> sendTeam(player));
        QuestzEvents.TEAM_UPDATED.register((server, team) -> team.getOnlineMembers(server).forEach(QuestSync::sendTeam));
        QuestzEvents.TASK_PROGRESS.register((server, team, quest, task, progress) -> {
            TaskProgressPacket packet = new TaskProgressPacket(quest.id(), task.getId(), progress);
            team.getOnlineMembers(server).forEach(member -> send(member, packet));
        });
        QuestzEvents.QUEST_COMPLETED.register(QuestSync::sendQuestState);
        QuestzEvents.QUEST_RESET.register(QuestSync::sendQuestState);
        QuestzEvents.REWARD_CLAIMED.register((player, team, quest, reward) -> {
            if (reward.isTeamReward()) {
                sendQuestState(player.getServer(), team, quest);
            } else {
                send(player, questState(team, quest, player.getUuid()));
            }
        });
    }

    private static void send(ServerPlayerEntity player, CustomPayload payload) {
        if (ServerPlayNetworking.canSend(player, payload.getId())) {
            ServerPlayNetworking.send(player, payload);
        }
    }

    public static void sendBook(ServerPlayerEntity player) {
        send(player, BookResetPacket.INSTANCE);
        for (Chapter chapter : QuestBookManager.getBook().getChapters()) {
            send(player, new ChapterSyncPacket(chapter));
        }
    }

    public static void sendTeam(ServerPlayerEntity player) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        TeamData team = TeamManager.getTeam(player);
        Map<UUID, String> members = new LinkedHashMap<>();
        team.getMembers().forEach(member -> members.put(member, playerName(server, member)));
        send(player, new TeamSyncPacket(team.getId(), team.getName(), team.getOwner(), team.isParty(), members,
                Map.copyOf(team.getProgressMap()), Map.copyOf(team.getCompletedMap()), team.getClaimedRewardKeys(player.getUuid())));
    }

    private static void sendQuestState(MinecraftServer server, TeamData team, Quest quest) {
        if (server == null) {
            return;
        }
        for (ServerPlayerEntity member : team.getOnlineMembers(server)) {
            send(member, questState(team, quest, member.getUuid()));
        }
    }

    private static QuestStatePacket questState(TeamData team, Quest quest, UUID viewer) {
        Map<String, Long> progress = new HashMap<>();
        for (Task task : quest.tasks()) {
            long value = team.getProgress(quest.id(), task.getId());
            if (value > 0L) {
                progress.put(task.getId(), value);
            }
        }
        Set<String> claimed = new HashSet<>();
        for (Reward reward : quest.rewards()) {
            if (team.isRewardClaimed(quest.id(), reward.getId(), viewer, reward.isTeamReward())) {
                claimed.add(reward.getId());
            }
        }
        return new QuestStatePacket(quest.id(), progress, team.getCompletedAt(quest.id()), claimed);
    }

    private static String playerName(MinecraftServer server, UUID uuid) {
        ServerPlayerEntity online = server.getPlayerManager().getPlayer(uuid);
        if (online != null) {
            return online.getGameProfile().getName();
        }
        UserCache cache = server.getUserCache();
        return cache == null ? uuid.toString() : cache.getByUuid(uuid).map(GameProfile::getName).orElse(uuid.toString());
    }

}
