package net.questz.network.packet;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.questz.QuestzMain;

import java.util.ArrayList;
import java.util.List;

public record MoveQuestsPacket(List<Move> moves) implements CustomPayload {

    public record Move(String questId, double x, double y) {

        public static final PacketCodec<PacketByteBuf, Move> PACKET_CODEC = PacketCodec.tuple(
                PacketCodecs.STRING, Move::questId,
                PacketCodecs.DOUBLE, Move::x,
                PacketCodecs.DOUBLE, Move::y,
                Move::new);
    }

    public static final CustomPayload.Id<MoveQuestsPacket> PACKET_ID = new CustomPayload.Id<>(QuestzMain.identifierOf("move_quests_packet"));

    public static final PacketCodec<PacketByteBuf, MoveQuestsPacket> PACKET_CODEC = PacketCodec.tuple(
            PacketCodecs.collection(ArrayList::new, Move.PACKET_CODEC, 1024), MoveQuestsPacket::moves,
            MoveQuestsPacket::new);

    @Override
    public Id<? extends CustomPayload> getId() {
        return PACKET_ID;
    }
}
