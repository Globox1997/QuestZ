package net.questz.task;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryCodecs;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntryList;
import net.minecraft.stat.Stat;
import net.minecraft.stat.Stats;
import net.questz.init.TaskTypeInit;

import java.util.Optional;

public class UseTask extends StatTask {

    public static final MapCodec<UseTask> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Task.<UseTask>idField(),
            StatTask.<UseTask>countField(),
            RegistryCodecs.entryList(RegistryKeys.ITEM).optionalFieldOf("item").forGetter(UseTask::getItems)
    ).apply(instance, UseTask::new));

    private final Optional<RegistryEntryList<Item>> items;

    public UseTask(String id, long count, Optional<RegistryEntryList<Item>> items) {
        super(id, count);
        this.items = items;
    }

    public Optional<RegistryEntryList<Item>> getItems() {
        return this.items;
    }

    @Override
    public boolean matches(Stat<?> stat) {
        return stat.getType().equals(Stats.USED) && stat.getValue() instanceof Item item
                && this.items.map(list -> list.contains(Registries.ITEM.getEntry(item))).orElse(true);
    }

    @Override
    public TaskType<?> getType() {
        return TaskTypeInit.USE;
    }

}
