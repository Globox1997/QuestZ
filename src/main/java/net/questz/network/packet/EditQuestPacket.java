package net.questz.network.packet;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.questz.QuestzMain;
import net.questz.book.Quest;

public record EditQuestPacket(String chapterId, Quest quest) implements CustomPayload {

    public static final CustomPayload.Id<EditQuestPacket> PACKET_ID = new CustomPayload.Id<>(QuestzMain.identifierOf("edit_quest_packet"));

    public static final PacketCodec<RegistryByteBuf, EditQuestPacket> PACKET_CODEC = PacketCodec.tuple(
            PacketCodecs.STRING, EditQuestPacket::chapterId,
            PacketCodecs.registryCodec(Quest.CODEC), EditQuestPacket::quest,
            EditQuestPacket::new);

    @Override
    public Id<? extends CustomPayload> getId() {
        return PACKET_ID;
    }
}
