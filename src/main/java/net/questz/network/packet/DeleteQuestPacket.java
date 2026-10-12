package net.questz.network.packet;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.questz.QuestzMain;

public record DeleteQuestPacket(String questId) implements CustomPayload {

    public static final CustomPayload.Id<DeleteQuestPacket> PACKET_ID = new CustomPayload.Id<>(QuestzMain.identifierOf("delete_quest_packet"));

    public static final PacketCodec<PacketByteBuf, DeleteQuestPacket> PACKET_CODEC = PacketCodec.tuple(PacketCodecs.STRING, DeleteQuestPacket::questId, DeleteQuestPacket::new);

    @Override
    public Id<? extends CustomPayload> getId() {
        return PACKET_ID;
    }
}
