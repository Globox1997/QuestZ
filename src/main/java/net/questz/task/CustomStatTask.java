package net.questz.task;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.stat.Stat;
import net.minecraft.stat.Stats;
import net.minecraft.util.Identifier;
import net.questz.init.TaskTypeInit;

public class CustomStatTask extends StatTask {

    public static final MapCodec<CustomStatTask> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Task.<CustomStatTask>idField(),
            StatTask.<CustomStatTask>countField(),
            Identifier.CODEC.fieldOf("stat").forGetter(CustomStatTask::getStat)
    ).apply(instance, CustomStatTask::new));

    private final Identifier stat;

    public CustomStatTask(String id, long count, Identifier stat) {
        super(id, count);
        this.stat = stat;
    }

    public Identifier getStat() {
        return this.stat;
    }

    @Override
    public boolean matches(Stat<?> stat) {
        return stat.getType().equals(Stats.CUSTOM) && this.stat.equals(stat.getValue());
    }

    @Override
    public TaskType<?> getType() {
        return TaskTypeInit.STAT;
    }

}
