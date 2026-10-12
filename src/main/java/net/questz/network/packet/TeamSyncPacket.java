package net.questz.network.packet;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.questz.QuestzMain;

import java.util.*;

public record TeamSyncPacket(UUID teamId, String name, UUID owner, boolean party, Map<UUID, String> members, Map<String, Long> progress, Map<String, Long> completed,
                             Set<String> claimed, Map<String, Integer> completions, Map<UUID, String> invites) implements CustomPayload {

    public static final CustomPayload.Id<TeamSyncPacket> PACKET_ID = new CustomPayload.Id<>(QuestzMain.identifierOf("team_sync_packet"));

    public static final PacketCodec<PacketByteBuf, TeamSyncPacket> PACKET_CODEC = CustomPayload.codecOf(TeamSyncPacket::write, TeamSyncPacket::read);

    private void write(PacketByteBuf buf) {
        buf.writeUuid(this.teamId);
        buf.writeString(this.name);
        buf.writeUuid(this.owner);
        buf.writeBoolean(this.party);
        buf.writeMap(this.members, (b, uuid) -> b.writeUuid(uuid), (b, name) -> b.writeString(name));
        buf.writeMap(this.progress, (b, key) -> b.writeString(key), (b, value) -> b.writeVarLong(value));
        buf.writeMap(this.completed, (b, key) -> b.writeString(key), (b, value) -> b.writeVarLong(value));
        buf.writeCollection(this.claimed, (b, key) -> b.writeString(key));
        buf.writeMap(this.completions, (b, key) -> b.writeString(key), (b, value) -> b.writeVarInt(value));
        buf.writeMap(this.invites, (b, uuid) -> b.writeUuid(uuid), (b, name) -> b.writeString(name));
    }

    private static TeamSyncPacket read(PacketByteBuf buf) {
        UUID teamId = buf.readUuid();
        String name = buf.readString();
        UUID owner = buf.readUuid();
        boolean party = buf.readBoolean();
        Map<UUID, String> members = buf.readMap(LinkedHashMap::new, b -> b.readUuid(), b -> b.readString());
        Map<String, Long> progress = buf.readMap(HashMap::new, b -> b.readString(), b -> b.readVarLong());
        Map<String, Long> completed = buf.readMap(HashMap::new, b -> b.readString(), b -> b.readVarLong());
        Set<String> claimed = buf.readCollection(HashSet::new, b -> b.readString());
        Map<String, Integer> completions = buf.readMap(HashMap::new, b -> b.readString(), b -> b.readVarInt());
        Map<UUID, String> invites = buf.readMap(LinkedHashMap::new, b -> b.readUuid(), b -> b.readString());
        return new TeamSyncPacket(teamId, name, owner, party, members, progress, completed, claimed, completions, invites);
    }

    @Override
    public Id<? extends CustomPayload> getId() {
        return PACKET_ID;
    }
}
