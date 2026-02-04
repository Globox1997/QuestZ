package net.questz.network.packet;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.questz.QuestzMain;

public record QuestCreationPacket(String fileName, String jsonContent) implements CustomPayload {

    public static final CustomPayload.Id<QuestCreationPacket> PACKET_ID = new CustomPayload.Id<>(QuestzMain.identifierOf( "quest_creation_packet"));

    public static final PacketCodec<RegistryByteBuf, QuestCreationPacket> PACKET_CODEC = PacketCodec.tuple(
            PacketCodecs.STRING, QuestCreationPacket::fileName,
            PacketCodecs.STRING, QuestCreationPacket::jsonContent,
            QuestCreationPacket::new
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return PACKET_ID;
    }
}
