package net.questz.task;

import net.minecraft.server.network.ServerPlayerEntity;

public interface PollingTask {

    long poll(ServerPlayerEntity player);

}
