package net.questz.util;

import net.minecraft.advancement.AdvancementRewards;

import java.util.List;
import java.util.WeakHashMap;

public class RewardCommandStorage {

    private static final WeakHashMap<AdvancementRewards, List<String>> REWARD_COMMANDS = new WeakHashMap<>();

    public static void put(AdvancementRewards rewards, List<String> commands) {
        if (!commands.isEmpty()) {
            REWARD_COMMANDS.put(rewards, commands);
        }
    }

    public static void remove(AdvancementRewards rewards, String command) {
        if (REWARD_COMMANDS.containsKey(rewards)) {
            REWARD_COMMANDS.get(rewards).remove(command);
        }
    }

    public static List<String> get(AdvancementRewards rewards) {
        return REWARD_COMMANDS.get(rewards);
    }

}
