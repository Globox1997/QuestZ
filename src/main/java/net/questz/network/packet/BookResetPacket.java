package net.questz.network.packet;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.questz.QuestzMain;

public record BookResetPacket() implements CustomPayload {

    public static final BookResetPacket INSTANCE = new BookResetPacket();

    public static final CustomPayload.Id<BookResetPacket> PACKET_ID = new CustomPayload.Id<>(QuestzMain.identifierOf("book_reset_packet"));

    public static final PacketCodec<PacketByteBuf, BookResetPacket> PACKET_CODEC = PacketCodec.unit(INSTANCE);

    @Override
    public Id<? extends CustomPayload> getId() {
        return PACKET_ID;
    }
}
