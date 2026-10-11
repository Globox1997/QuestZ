package net.questz.network;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.questz.client.ClientQuestData;
import net.questz.network.packet.*;

@Environment(EnvType.CLIENT)
public class QuestClientPacket {

    public static void init() {
        ClientPlayNetworking.registerGlobalReceiver(BookResetPacket.PACKET_ID, (payload, context) -> ClientQuestData.resetBook());
        ClientPlayNetworking.registerGlobalReceiver(ChapterSyncPacket.PACKET_ID, (payload, context) -> ClientQuestData.putChapter(payload.chapter()));
        ClientPlayNetworking.registerGlobalReceiver(TeamSyncPacket.PACKET_ID, (payload, context) -> ClientQuestData.setTeam(payload));
        ClientPlayNetworking.registerGlobalReceiver(TaskProgressPacket.PACKET_ID, (payload, context) -> ClientQuestData.setTaskProgress(payload.questId(), payload.taskId(), payload.progress()));
        ClientPlayNetworking.registerGlobalReceiver(QuestStatePacket.PACKET_ID, (payload, context) -> ClientQuestData.applyQuestState(payload));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ClientQuestData.clear());
    }

}
