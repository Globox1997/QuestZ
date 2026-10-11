package net.questz.task;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.stat.Stat;

public abstract class StatTask extends Task {

    private final long count;

    protected StatTask(String id, long count) {
        super(id);
        this.count = Math.max(1L, count);
    }

    @Override
    public long getTarget() {
        return this.count;
    }

    public abstract boolean matches(Stat<?> stat);

    protected static <T extends StatTask> RecordCodecBuilder<T, Long> countField() {
        return Codec.LONG.optionalFieldOf("count", 1L).forGetter(StatTask::getTarget);
    }

}
