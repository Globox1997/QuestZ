package net.questz.task;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.registry.RegistryCodecs;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntryList;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.gen.structure.Structure;
import net.questz.init.TaskTypeInit;

public class StructureTask extends Task implements PollingTask {

    public static final MapCodec<StructureTask> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Task.<StructureTask>idField(),
            RegistryCodecs.entryList(RegistryKeys.STRUCTURE).fieldOf("structure").forGetter(StructureTask::getStructures)
    ).apply(instance, StructureTask::new));

    private final RegistryEntryList<Structure> structures;

    public StructureTask(String id, RegistryEntryList<Structure> structures) {
        super(id);
        this.structures = structures;
    }

    public RegistryEntryList<Structure> getStructures() {
        return this.structures;
    }

    @Override
    public long poll(ServerPlayerEntity player) {
        return player.getServerWorld().getStructureAccessor().getStructureContaining(player.getBlockPos(), this.structures).hasChildren() ? 1L : 0L;
    }

    @Override
    public TaskType<?> getType() {
        return TaskTypeInit.STRUCTURE;
    }

}
