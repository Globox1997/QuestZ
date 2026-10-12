package net.questz.client.editor;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableTextContent;
import net.minecraft.util.Language;
import net.questz.editor.QuestEditor;
import net.questz.network.packet.EditQuestPacket;

import java.util.ArrayList;
import java.util.List;

@Environment(EnvType.CLIENT)
public class ClientEditor {

    public static boolean canEdit() {
        MinecraftClient client = MinecraftClient.getInstance();
        return client.player != null && client.player.hasPermissionLevel(QuestEditor.PERMISSION_LEVEL) && ClientPlayNetworking.canSend(EditQuestPacket.PACKET_ID);
    }

    public static void send(CustomPayload payload) {
        if (ClientPlayNetworking.canSend(payload.getId())) {
            ClientPlayNetworking.send(payload);
        }
    }

    public static String toEditString(Text text) {
        if (text.getContent() instanceof TranslatableTextContent translatable && text.getSiblings().isEmpty()) {
            return translatable.getKey();
        }
        return text.getString();
    }

    public static Text fromEditString(String value) {
        if (!value.isEmpty() && !value.contains(" ") && Language.getInstance().hasTranslation(value)) {
            return Text.translatable(value);
        }
        return Text.literal(value);
    }

    public static String toEditString(List<Text> lines) {
        List<String> parts = new ArrayList<>();
        lines.forEach(line -> parts.add(toEditString(line)));
        return String.join("\n", parts);
    }

    public static List<Text> fromEditLines(String value) {
        List<Text> lines = new ArrayList<>();
        for (String line : value.split("\n", -1)) {
            lines.add(fromEditString(line));
        }
        while (!lines.isEmpty() && lines.getLast().getString().isEmpty()) {
            lines.removeLast();
        }
        return lines;
    }

}
