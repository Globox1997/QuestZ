package net.questz.task;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.World;
import net.questz.init.TaskTypeInit;

public class DimensionTask extends Task implements PollingTask {

    public static final MapCodec<DimensionTask> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Task.<DimensionTask>idField(),
            World.CODEC.fieldOf("dimension").forGetter(DimensionTask::getDimension)
    ).apply(instance, DimensionTask::new));

    private final RegistryKey<World> dimension;

    public DimensionTask(String id, RegistryKey<World> dimension) {
        super(id);
        this.dimension = dimension;
    }

    public RegistryKey<World> getDimension() {
        return this.dimension;
    }

    @Override
    public long poll(ServerPlayerEntity player) {
        return player.getWorld().getRegistryKey().equals(this.dimension) ? 1L : 0L;
    }

    @Override
    public TaskType<?> getType() {
        return TaskTypeInit.DIMENSION;
    }

}
