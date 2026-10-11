package net.questz.network.packet;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.questz.QuestzMain;

public record TaskProgressPacket(String questId, String taskId, long progress) implements CustomPayload {

    public static final CustomPayload.Id<TaskProgressPacket> PACKET_ID = new CustomPayload.Id<>(QuestzMain.identifierOf("task_progress_packet"));

    public static final PacketCodec<PacketByteBuf, TaskProgressPacket> PACKET_CODEC = PacketCodec.tuple(
            PacketCodecs.STRING, TaskProgressPacket::questId,
            PacketCodecs.STRING, TaskProgressPacket::taskId,
            PacketCodecs.VAR_LONG, TaskProgressPacket::progress,
            TaskProgressPacket::new);

    @Override
    public Id<? extends CustomPayload> getId() {
        return PACKET_ID;
    }
}
