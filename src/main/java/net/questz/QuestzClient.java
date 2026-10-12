package net.questz;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.questz.client.render.RewardRenderers;
import net.questz.client.render.TaskRenderers;
import net.questz.init.KeyInit;
import net.questz.network.QuestClientPacket;

@Environment(EnvType.CLIENT)
public class QuestzClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        KeyInit.init();
        QuestClientPacket.init();
        TaskRenderers.init();
        RewardRenderers.init();
    }

}
