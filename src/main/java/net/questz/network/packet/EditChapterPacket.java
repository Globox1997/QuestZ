package net.questz.network.packet;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.questz.QuestzMain;
import net.questz.book.Chapter;

public record EditChapterPacket(Chapter chapter) implements CustomPayload {

    public static final CustomPayload.Id<EditChapterPacket> PACKET_ID = new CustomPayload.Id<>(QuestzMain.identifierOf("edit_chapter_packet"));

    public static final PacketCodec<RegistryByteBuf, EditChapterPacket> PACKET_CODEC = PacketCodec.tuple(
            PacketCodecs.registryCodec(Chapter.CODEC), EditChapterPacket::chapter,
            EditChapterPacket::new);

    @Override
    public Id<? extends CustomPayload> getId() {
        return PACKET_ID;
    }
}
