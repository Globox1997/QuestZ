package net.questz.task;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.block.Block;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryCodecs;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntryList;
import net.minecraft.stat.Stat;
import net.minecraft.stat.Stats;
import net.questz.init.TaskTypeInit;

import java.util.Optional;

public class MineTask extends StatTask {

    public static final MapCodec<MineTask> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Task.<MineTask>idField(),
            StatTask.<MineTask>countField(),
            RegistryCodecs.entryList(RegistryKeys.BLOCK).optionalFieldOf("block").forGetter(MineTask::getBlocks)
    ).apply(instance, MineTask::new));

    private final Optional<RegistryEntryList<Block>> blocks;

    public MineTask(String id, long count, Optional<RegistryEntryList<Block>> blocks) {
        super(id, count);
        this.blocks = blocks;
    }

    public Optional<RegistryEntryList<Block>> getBlocks() {
        return this.blocks;
    }

    @Override
    public boolean matches(Stat<?> stat) {
        return stat.getType().equals(Stats.MINED) && stat.getValue() instanceof Block block
                && this.blocks.map(list -> list.contains(Registries.BLOCK.getEntry(block))).orElse(true);
    }

    @Override
    public TaskType<?> getType() {
        return TaskTypeInit.MINE;
    }

}
