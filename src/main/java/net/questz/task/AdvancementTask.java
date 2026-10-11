package net.questz.task;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.advancement.AdvancementEntry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.questz.init.TaskTypeInit;

public class AdvancementTask extends Task implements PollingTask {

    public static final MapCodec<AdvancementTask> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Task.<AdvancementTask>idField(),
            Identifier.CODEC.fieldOf("advancement").forGetter(AdvancementTask::getAdvancement)
    ).apply(instance, AdvancementTask::new));

    private final Identifier advancement;

    public AdvancementTask(String id, Identifier advancement) {
        super(id);
        this.advancement = advancement;
    }

    public Identifier getAdvancement() {
        return this.advancement;
    }

    @Override
    public long poll(ServerPlayerEntity player) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return 0L;
        }
        AdvancementEntry entry = server.getAdvancementLoader().get(this.advancement);
        return entry != null && player.getAdvancementTracker().getProgress(entry).isDone() ? 1L : 0L;
    }

    @Override
    public TaskType<?> getType() {
        return TaskTypeInit.ADVANCEMENT;
    }

}
