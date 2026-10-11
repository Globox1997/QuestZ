package net.questz.network.packet;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.questz.QuestzMain;
import net.questz.book.Chapter;

public record ChapterSyncPacket(Chapter chapter) implements CustomPayload {

    public static final CustomPayload.Id<ChapterSyncPacket> PACKET_ID = new CustomPayload.Id<>(QuestzMain.identifierOf("chapter_sync_packet"));

    public static final PacketCodec<RegistryByteBuf, ChapterSyncPacket> PACKET_CODEC =
            PacketCodec.tuple(PacketCodecs.registryCodec(Chapter.CODEC), ChapterSyncPacket::chapter, ChapterSyncPacket::new);

    @Override
    public Id<? extends CustomPayload> getId() {
        return PACKET_ID;
    }
}
