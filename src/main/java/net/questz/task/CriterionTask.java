package net.questz.task;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.advancement.AdvancementCriterion;
import net.questz.init.TaskTypeInit;

public class CriterionTask extends Task {

    public static final MapCodec<CriterionTask> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Task.<CriterionTask>idField(),
            AdvancementCriterion.CODEC.fieldOf("criterion").forGetter(CriterionTask::getCriterion),
            Codec.LONG.optionalFieldOf("count", 1L).forGetter(CriterionTask::getTarget)
    ).apply(instance, CriterionTask::new));

    private final AdvancementCriterion<?> criterion;
    private final long count;

    public CriterionTask(String id, AdvancementCriterion<?> criterion, long count) {
        super(id);
        this.criterion = criterion;
        this.count = Math.max(1L, count);
    }

    public AdvancementCriterion<?> getCriterion() {
        return this.criterion;
    }

    @Override
    public long getTarget() {
        return this.count;
    }

    @Override
    public TaskType<?> getType() {
        return TaskTypeInit.CRITERION;
    }

}
