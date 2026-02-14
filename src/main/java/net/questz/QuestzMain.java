package net.questz;

import net.fabricmc.api.ModInitializer;
import net.minecraft.util.Identifier;
import net.questz.init.*;
import net.questz.network.QuestServerPacket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class QuestzMain implements ModInitializer {

    public static final Logger LOGGER = LoggerFactory.getLogger("questz");

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
