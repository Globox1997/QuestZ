package net.questz.network.packet;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.questz.QuestzMain;

public record ChapterRemovePacket(String chapterId) implements CustomPayload {

    public static final CustomPayload.Id<ChapterRemovePacket> PACKET_ID = new CustomPayload.Id<>(QuestzMain.identifierOf("chapter_remove_packet"));

    public static final PacketCodec<PacketByteBuf, ChapterRemovePacket> PACKET_CODEC = PacketCodec.tuple(PacketCodecs.STRING, ChapterRemovePacket::chapterId, ChapterRemovePacket::new);

    @Override
    public Id<? extends CustomPayload> getId() {
        return PACKET_ID;
    }
}
