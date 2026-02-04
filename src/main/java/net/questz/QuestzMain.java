package net.questz;

import net.fabricmc.api.ModInitializer;
import net.minecraft.util.Identifier;
import net.questz.init.*;
import net.questz.network.QuestServerPacket;

public class QuestzMain implements ModInitializer {

    @Override
    public void onInitialize() {
        ConfigInit.init();
        CriteriaInit.init();
        QuestServerPacket.init();
    }

    public static Identifier identifierOf(String name) {
        return Identifier.of("questz", name);
    }

}
