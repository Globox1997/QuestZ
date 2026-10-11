package net.questz.task;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.block.Block;
import net.minecraft.item.BlockItem;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryCodecs;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntryList;
import net.minecraft.stat.Stat;
import net.minecraft.stat.Stats;
import net.questz.init.TaskTypeInit;

import java.util.Optional;

public class PlaceTask extends StatTask {

    public static final MapCodec<PlaceTask> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Task.<PlaceTask>idField(),
            StatTask.<PlaceTask>countField(),
            RegistryCodecs.entryList(RegistryKeys.BLOCK).optionalFieldOf("block").forGetter(PlaceTask::getBlocks)
    ).apply(instance, PlaceTask::new));

    private final Optional<RegistryEntryList<Block>> blocks;

    public PlaceTask(String id, long count, Optional<RegistryEntryList<Block>> blocks) {
        super(id, count);
        this.blocks = blocks;
    }

    public Optional<RegistryEntryList<Block>> getBlocks() {
        return this.blocks;
    }

    @Override
    public boolean matches(Stat<?> stat) {
        return stat.getType().equals(Stats.USED) && stat.getValue() instanceof BlockItem blockItem
                && this.blocks.map(list -> list.contains(Registries.BLOCK.getEntry(blockItem.getBlock()))).orElse(true);
    }

    @Override
    public TaskType<?> getType() {
        return TaskTypeInit.PLACE;
    }

}
