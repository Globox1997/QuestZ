package net.questz.network;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.advancement.Advancement;
import net.minecraft.advancement.AdvancementDisplay;
import net.minecraft.advancement.AdvancementEntry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerAdvancementLoader;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.questz.access.DisplayAccess;
import net.questz.network.packet.QuestCreationPacket;
import net.questz.network.packet.QuestDeletionPacket;
import net.questz.network.packet.QuestPositionPacket;
import net.questz.quest.QuestHandler;

public class QuestServerPacket {

    public static void init() {
        PayloadTypeRegistry.playC2S().register(QuestCreationPacket.PACKET_ID, QuestCreationPacket.PACKET_CODEC);
        PayloadTypeRegistry.playC2S().register(QuestPositionPacket.PACKET_ID, QuestPositionPacket.PACKET_CODEC);
        PayloadTypeRegistry.playC2S().register(QuestDeletionPacket.PACKET_ID, QuestDeletionPacket.PACKET_CODEC);

        ServerPlayNetworking.registerGlobalReceiver(QuestCreationPacket.PACKET_ID, (payload, context) -> {
            String fileName = payload.fileName();
            String json = payload.jsonContent();
            String existingAdvancementId = payload.existingAdvancementId();

            context.server().execute(() -> {
                if (context.player().isCreativeLevelTwoOp()) {
                    QuestHandler.createAdvancement(context.server(), json, fileName, existingAdvancementId);
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(QuestPositionPacket.PACKET_ID, (payload, context) -> {
            context.server().execute(() -> {
                if (context.player().isCreativeLevelTwoOp()) {
                    ServerAdvancementLoader loader = context.server().getAdvancementLoader();

                    applyPositionUpdate(loader, context.server(), payload.id(), payload.x(), payload.y());

                    for (QuestPositionPacket.ChildMove child : payload.childMoves()) {
                        applyPositionUpdate(loader, context.server(), child.id(), child.x(), child.y());
                    }

                    for (ServerPlayerEntity player : context.server().getPlayerManager().getPlayerList()) {
                        player.getAdvancementTracker().reload(loader);
                    }
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(QuestDeletionPacket.PACKET_ID, (payload, context) -> {
            Identifier advancementId = payload.advancementId();

            context.server().execute(() -> {
                if (context.player().isCreativeLevelTwoOp()) {
                    QuestHandler.deleteAdvancement(context.server(), advancementId);

                    ServerAdvancementLoader loader = context.server().getAdvancementLoader();
                    for (ServerPlayerEntity onlinePlayer : context.server().getPlayerManager().getPlayerList()) {
                        onlinePlayer.getAdvancementTracker().reload(loader);
                    }
                }
            });
        });
    }

    private static void applyPositionUpdate(ServerAdvancementLoader loader, MinecraftServer server, Identifier id, int x, int y) {
        AdvancementEntry entry = loader.get(id);
        if (entry == null || !id.getNamespace().equals("questz")) {
            return;
        }
        Advancement advancement = entry.value();
        if (advancement.display().isEmpty()) {
            return;
        }
        AdvancementDisplay display = advancement.display().get();
        if (display instanceof DisplayAccess access) {
            access.setManualPosition(x, y);
            QuestHandler.updateAdvancementPosition(server, id, x, y);
        }
    }

}
