package net.questz.network.packet;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.questz.QuestzMain;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

public record QuestCreationPacket(String fileName, String jsonContent, @Nullable String existingAdvancementId) implements CustomPayload {

    public static final CustomPayload.Id<QuestCreationPacket> PACKET_ID = new CustomPayload.Id<>(QuestzMain.identifierOf("quest_creation_packet"));

    public static final PacketCodec<RegistryByteBuf, QuestCreationPacket> PACKET_CODEC = PacketCodec.tuple(
            PacketCodecs.STRING, QuestCreationPacket::fileName,
            PacketCodecs.STRING, QuestCreationPacket::jsonContent,
            PacketCodecs.STRING.collect(PacketCodecs::optional),
            packet -> Optional.ofNullable(packet.existingAdvancementId()),
            (fileName, jsonContent, optionalId) -> new QuestCreationPacket(fileName, jsonContent, optionalId.orElse(null))
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return PACKET_ID;
    }
}
