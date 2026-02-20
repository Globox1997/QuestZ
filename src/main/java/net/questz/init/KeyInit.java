package net.questz.init;

import org.lwjgl.glfw.GLFW;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.questz.quest.QuestScreen;

@Environment(EnvType.CLIENT)
public class KeyInit {
    public static final KeyBinding screenKey = new KeyBinding("key.questz.openquestscreen", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_L, "category.questz.keybind");

    public static void init() {
        // Registering
        KeyBindingHelper.registerKeyBinding(screenKey);
        // Callback
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (screenKey.wasPressed()) {
                if (client.player != null && client.player.networkHandler != null) {
                    client.setScreen(new QuestScreen(client.player.networkHandler.getAdvancementHandler(), null));
                    return;
                }
            }
        });
    }

}
