package net.questz.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.questz.network.packet.QuestCreationPacket;
import net.questz.quest.QuestData;

public class QuestHelper {

    private void sendSavePacket(QuestData.AdvancementData data, String name) {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        String jsonString = gson.toJson(data);

        ClientPlayNetworking.send(new QuestCreationPacket(name, jsonString));
    }

}
