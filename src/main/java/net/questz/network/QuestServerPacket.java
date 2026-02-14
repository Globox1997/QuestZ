package net.questz.network;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.advancement.Advancement;
import net.minecraft.advancement.AdvancementDisplay;
import net.minecraft.advancement.AdvancementEntry;
import net.minecraft.server.ServerAdvancementLoader;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.questz.QuestzMain;
import net.questz.access.DisplayAccess;
import net.questz.network.packet.QuestCreationPacket;
import net.questz.network.packet.QuestPositionPacket;
import net.questz.quest.QuestHandler;

public class QuestServerPacket {

    public static void init() {
        PayloadTypeRegistry.playC2S().register(QuestCreationPacket.PACKET_ID, QuestCreationPacket.PACKET_CODEC);
        PayloadTypeRegistry.playC2S().register(QuestPositionPacket.PACKET_ID, QuestPositionPacket.PACKET_CODEC);

        ServerPlayNetworking.registerGlobalReceiver(QuestCreationPacket.PACKET_ID, (payload, context) -> {
            String fileName = payload.fileName();
            String json = payload.jsonContent();

            context.server().execute(() -> {
                if (context.player().isCreativeLevelTwoOp()) {
                    QuestHandler.createAdvancement(context.server(), json, fileName);
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(QuestPositionPacket.PACKET_ID, (payload, context) -> {
            Identifier id = payload.id();
            int x = payload.x();
            int y = payload.y();

            context.server().execute(() -> {
                if (context.player().isCreativeLevelTwoOp()) {
                    ServerAdvancementLoader loader = context.server().getAdvancementLoader();
                    AdvancementEntry entry = loader.get(id);

                    if (entry == null) {
                        QuestzMain.LOGGER.warn("Advancement {} not found", id);
                        return;
                    }

                    if (!id.getNamespace().equals("questz")) {
                        QuestzMain.LOGGER.warn("Cannot modify non-quest advancement {}", id);
                        return;
                    }

                    Advancement advancement = entry.value();

                    if (advancement.display().isEmpty()) {
                        QuestzMain.LOGGER.warn("Advancement {} has no display", id);
                        return;
                    }

                    AdvancementDisplay display = advancement.display().get();

                    if (display instanceof DisplayAccess access) {
                        access.setManualPosition(x, y);

                        QuestzMain.LOGGER.info("Updated quest {} position to ({}, {})", id, x, y);

                        QuestHandler.updateAdvancementPosition(context.server(), id, x, y);

                        for (ServerPlayerEntity onlinePlayer : context.server().getPlayerManager().getPlayerList()) {
                            onlinePlayer.getAdvancementTracker().reload(loader);
                        }
                    }
                }
            });
        });
    }

}
