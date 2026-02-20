package net.questz.network.packet;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.questz.QuestzMain;

public record QuestDeletionPacket(Identifier advancementId) implements CustomPayload {

    public static final CustomPayload.Id<QuestDeletionPacket> PACKET_ID = new CustomPayload.Id<>(QuestzMain.identifierOf("quest_deletion_packet"));

    public static final PacketCodec<PacketByteBuf, QuestDeletionPacket> PACKET_CODEC =
            PacketCodec.tuple(Identifier.PACKET_CODEC, QuestDeletionPacket::advancementId, QuestDeletionPacket::new);


    @Override
    public Id<? extends CustomPayload> getId() {
        return PACKET_ID;
    }
}
