package net.questz.network.packet;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.questz.QuestzMain;

import java.util.List;

public record QuestPositionPacket(Identifier id, int x, int y, List<ChildMove> childMoves) implements CustomPayload {

    public record ChildMove(Identifier id, int x, int y) {
        public static final PacketCodec<RegistryByteBuf, ChildMove> CODEC = PacketCodec.tuple(
                Identifier.PACKET_CODEC, ChildMove::id,
                PacketCodecs.INTEGER, ChildMove::x,
                PacketCodecs.INTEGER, ChildMove::y,
                ChildMove::new
        );
    }

    public static final CustomPayload.Id<QuestPositionPacket> PACKET_ID = new CustomPayload.Id<>(QuestzMain.identifierOf("quest_position_packet"));

    public static final PacketCodec<RegistryByteBuf, QuestPositionPacket> PACKET_CODEC = PacketCodec.tuple(
            Identifier.PACKET_CODEC, QuestPositionPacket::id,
            PacketCodecs.INTEGER, QuestPositionPacket::x,
            PacketCodecs.INTEGER, QuestPositionPacket::y,
            ChildMove.CODEC.collect(PacketCodecs.toList()), QuestPositionPacket::childMoves,
            QuestPositionPacket::new
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return PACKET_ID;
    }
}
