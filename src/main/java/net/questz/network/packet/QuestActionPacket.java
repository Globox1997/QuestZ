package net.questz.network.packet;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.questz.QuestzMain;

public record QuestActionPacket(Action action, String questId, String entryId, int option) implements CustomPayload {

    public enum Action {
        CLAIM,
        SUBMIT,
        CHECKMARK,
        CHOOSE
    }

    public static final CustomPayload.Id<QuestActionPacket> PACKET_ID = new CustomPayload.Id<>(QuestzMain.identifierOf("quest_action_packet"));

    public static final PacketCodec<PacketByteBuf, QuestActionPacket> PACKET_CODEC = PacketCodec.tuple(
            PacketCodecs.indexed(index -> Action.values()[Math.floorMod(index, Action.values().length)], Action::ordinal), QuestActionPacket::action,
            PacketCodecs.STRING, QuestActionPacket::questId,
            PacketCodecs.STRING, QuestActionPacket::entryId,
            PacketCodecs.VAR_INT, QuestActionPacket::option,
            QuestActionPacket::new);

    @Override
    public Id<? extends CustomPayload> getId() {
        return PACKET_ID;
    }
}
