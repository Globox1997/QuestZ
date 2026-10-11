package net.questz.task;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.entity.EntityType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryCodecs;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntryList;
import net.minecraft.stat.Stat;
import net.minecraft.stat.Stats;
import net.questz.init.TaskTypeInit;

import java.util.Optional;

public class KillTask extends StatTask {

    public static final MapCodec<KillTask> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Task.<KillTask>idField(),
            StatTask.<KillTask>countField(),
            RegistryCodecs.entryList(RegistryKeys.ENTITY_TYPE).optionalFieldOf("entity").forGetter(KillTask::getEntities)
    ).apply(instance, KillTask::new));

    private final Optional<RegistryEntryList<EntityType<?>>> entities;

    public KillTask(String id, long count, Optional<RegistryEntryList<EntityType<?>>> entities) {
        super(id, count);
        this.entities = entities;
    }

    public Optional<RegistryEntryList<EntityType<?>>> getEntities() {
        return this.entities;
    }

    @Override
    public boolean matches(Stat<?> stat) {
        return stat.getType().equals(Stats.KILLED) && stat.getValue() instanceof EntityType<?> type
                && this.entities.map(list -> list.contains(Registries.ENTITY_TYPE.getEntry(type))).orElse(true);
    }

    @Override
    public TaskType<?> getType() {
        return TaskTypeInit.KILL;
    }

}
