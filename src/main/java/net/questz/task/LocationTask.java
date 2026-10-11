package net.questz.task;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.questz.init.TaskTypeInit;

public class LocationTask extends Task implements PollingTask {

    public static final MapCodec<LocationTask> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Task.<LocationTask>idField(),
            World.CODEC.optionalFieldOf("dimension", World.OVERWORLD).forGetter(LocationTask::getDimension),
            BlockPos.CODEC.fieldOf("pos").forGetter(LocationTask::getPos),
            Codec.INT.optionalFieldOf("radius", 8).forGetter(LocationTask::getRadius)
    ).apply(instance, LocationTask::new));

    private final RegistryKey<World> dimension;
    private final BlockPos pos;
    private final int radius;

    public LocationTask(String id, RegistryKey<World> dimension, BlockPos pos, int radius) {
        super(id);
        this.dimension = dimension;
        this.pos = pos;
        this.radius = Math.max(0, radius);
    }

    public RegistryKey<World> getDimension() {
        return this.dimension;
    }

    public BlockPos getPos() {
        return this.pos;
    }

    public int getRadius() {
        return this.radius;
    }

    @Override
    public long poll(ServerPlayerEntity player) {
        return player.getWorld().getRegistryKey().equals(this.dimension) && player.getBlockPos().isWithinDistance(this.pos, this.radius + 0.5D) ? 1L : 0L;
    }

    @Override
    public TaskType<?> getType() {
        return TaskTypeInit.LOCATION;
    }

}
