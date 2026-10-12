package net.questz.network.packet;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.questz.QuestzMain;

public record DeleteChapterPacket(String chapterId) implements CustomPayload {

    public static final CustomPayload.Id<DeleteChapterPacket> PACKET_ID = new CustomPayload.Id<>(QuestzMain.identifierOf("delete_chapter_packet"));

    public static final PacketCodec<PacketByteBuf, DeleteChapterPacket> PACKET_CODEC = PacketCodec.tuple(PacketCodecs.STRING, DeleteChapterPacket::chapterId, DeleteChapterPacket::new);

    @Override
    public Id<? extends CustomPayload> getId() {
        return PACKET_ID;
    }
}
