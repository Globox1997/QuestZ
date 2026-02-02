package net.questz.init;

import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.serializer.GsonConfigSerializer;
import net.questz.config.QuestConfig;

public class ConfigInit {

    public static QuestConfig CONFIG = new QuestConfig();

    public static void init() {
        AutoConfig.register(QuestConfig.class, GsonConfigSerializer::new);
        CONFIG = AutoConfig.getConfigHolder(QuestConfig.class).getConfig();
    }

}
