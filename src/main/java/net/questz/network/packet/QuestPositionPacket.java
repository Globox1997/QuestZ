package net.questz.network.packet;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.questz.QuestzMain;

public record QuestPositionPacket(Identifier id, int x, int y) implements CustomPayload {

    public static final CustomPayload.Id<QuestPositionPacket> PACKET_ID = new CustomPayload.Id<>(QuestzMain.identifierOf( "quest_position_packet"));

    public static final PacketCodec<RegistryByteBuf, QuestPositionPacket> PACKET_CODEC = PacketCodec.tuple(
            Identifier.PACKET_CODEC, QuestPositionPacket::id,
            PacketCodecs.INTEGER, QuestPositionPacket::x,
            PacketCodecs.INTEGER, QuestPositionPacket::y,
            QuestPositionPacket::new
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return PACKET_ID;
    }
}
