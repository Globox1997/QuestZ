package net.questz.network.packet;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.questz.QuestzMain;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public record QuestStatePacket(String questId, Map<String, Long> progress, long completedAt, Set<String> claimedRewards, int completions) implements CustomPayload {

    public static final CustomPayload.Id<QuestStatePacket> PACKET_ID = new CustomPayload.Id<>(QuestzMain.identifierOf("quest_state_packet"));

    public static final PacketCodec<PacketByteBuf, QuestStatePacket> PACKET_CODEC = CustomPayload.codecOf(QuestStatePacket::write, QuestStatePacket::read);

    private void write(PacketByteBuf buf) {
        buf.writeString(this.questId);
        buf.writeMap(this.progress, (b, key) -> b.writeString(key), (b, value) -> b.writeVarLong(value));
        buf.writeVarLong(this.completedAt);
        buf.writeCollection(this.claimedRewards, (b, key) -> b.writeString(key));
        buf.writeVarInt(this.completions);
    }

    private static QuestStatePacket read(PacketByteBuf buf) {
        String questId = buf.readString();
        Map<String, Long> progress = buf.readMap(HashMap::new, b -> b.readString(), b -> b.readVarLong());
        long completedAt = buf.readVarLong();
        Set<String> claimedRewards = buf.readCollection(HashSet::new, b -> b.readString());
        int completions = buf.readVarInt();
        return new QuestStatePacket(questId, progress, completedAt, claimedRewards, completions);
    }

    @Override
    public Id<? extends CustomPayload> getId() {
        return PACKET_ID;
    }
}
