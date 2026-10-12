package net.questz;

import net.fabricmc.api.ModInitializer;
import net.minecraft.util.Identifier;
import net.questz.api.QuestzRegistries;
import net.questz.init.*;
import net.questz.network.QuestSync;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class QuestzMain implements ModInitializer {

    public static final Logger LOGGER = LoggerFactory.getLogger("questz");

    @Override
    public void onInitialize() {
        ConfigInit.init();
        QuestzRegistries.init();
        TaskTypeInit.init();
        RewardTypeInit.init();
        EventInit.init();
        CommandInit.init();
        QuestSync.init();
    }

    public static Identifier identifierOf(String name) {
        return Identifier.of("questz", name);
    }

}
