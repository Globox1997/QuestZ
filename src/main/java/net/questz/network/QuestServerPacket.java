package net.questz.network;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.questz.network.packet.QuestCreationPacket;
import net.questz.quest.QuestHandler;

public class QuestServerPacket {

    public static void init() {
        PayloadTypeRegistry.playC2S().register(QuestCreationPacket.PACKET_ID, QuestCreationPacket.PACKET_CODEC);

        ServerPlayNetworking.registerGlobalReceiver(QuestCreationPacket.PACKET_ID, (payload, context) -> {
            String fileName = payload.fileName();
            String json = payload.jsonContent();

            context.server().execute(() -> {
                if (context.player().isCreativeLevelTwoOp()) {
                    QuestHandler.handleAdvancementCreation(context.server(), json, fileName);
                }
            });
        });
    }

}
