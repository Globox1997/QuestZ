package net.questz.task;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.registry.RegistryCodecs;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntryList;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.biome.Biome;
import net.questz.init.TaskTypeInit;

public class BiomeTask extends Task implements PollingTask {

    public static final MapCodec<BiomeTask> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Task.<BiomeTask>idField(),
            RegistryCodecs.entryList(RegistryKeys.BIOME).fieldOf("biome").forGetter(BiomeTask::getBiomes)
    ).apply(instance, BiomeTask::new));

    private final RegistryEntryList<Biome> biomes;

    public BiomeTask(String id, RegistryEntryList<Biome> biomes) {
        super(id);
        this.biomes = biomes;
    }

    public RegistryEntryList<Biome> getBiomes() {
        return this.biomes;
    }

    @Override
    public long poll(ServerPlayerEntity player) {
        return this.biomes.contains(player.getWorld().getBiome(player.getBlockPos())) ? 1L : 0L;
    }

    @Override
    public TaskType<?> getType() {
        return TaskTypeInit.BIOME;
    }

}
