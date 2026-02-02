package net.questz;

import net.fabricmc.api.ModInitializer;
import net.minecraft.util.Identifier;
import net.questz.init.*;

public class QuestzMain implements ModInitializer {

    // add this to fabric.mod.json "minecraft": "${minecraft_version}",

    @Override
    public void onInitialize() {
        ConfigInit.init();
        CriteriaInit.init();
    }

    public static Identifier identifierOf(String name) {
        return Identifier.of("questz", name);
    }

}
