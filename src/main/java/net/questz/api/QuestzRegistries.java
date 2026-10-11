package net.questz.api;

import net.fabricmc.fabric.api.event.registry.FabricRegistryBuilder;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.questz.QuestzMain;
import net.questz.reward.RewardType;
import net.questz.task.TaskType;

public class QuestzRegistries {

    public static final RegistryKey<Registry<TaskType<?>>> TASK_TYPE_KEY = RegistryKey.ofRegistry(QuestzMain.identifierOf("task_type"));
    public static final RegistryKey<Registry<RewardType<?>>> REWARD_TYPE_KEY = RegistryKey.ofRegistry(QuestzMain.identifierOf("reward_type"));

    public static final Registry<TaskType<?>> TASK_TYPE = FabricRegistryBuilder.createSimple(TASK_TYPE_KEY).buildAndRegister();
    public static final Registry<RewardType<?>> REWARD_TYPE = FabricRegistryBuilder.createSimple(REWARD_TYPE_KEY).buildAndRegister();

    public static void init() {
    }

}
