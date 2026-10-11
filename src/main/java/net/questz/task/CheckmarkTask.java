package net.questz.task;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.questz.init.TaskTypeInit;

public class CheckmarkTask extends Task {

    public static final MapCodec<CheckmarkTask> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Task.<CheckmarkTask>idField()
    ).apply(instance, CheckmarkTask::new));

    public CheckmarkTask(String id) {
        super(id);
    }

    @Override
    public TaskType<?> getType() {
        return TaskTypeInit.CHECKMARK;
    }

}
