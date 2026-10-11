package net.questz.init;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.questz.command.QuestzCommand;

public class CommandInit {

    public static void init() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> QuestzCommand.register(dispatcher));
    }

}
