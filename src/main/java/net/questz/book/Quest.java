package net.questz.book;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.text.TextCodecs;
import net.questz.reward.Reward;
import net.questz.task.Task;

import java.util.List;
import java.util.Optional;

public record Quest(String id, Text title, List<Text> description, List<ItemStack> icons, double x, double y, float size, QuestShape shape, List<String> dependencies,
                    DependencyMode dependencyMode, QuestVisibility visibility, List<Task> tasks, DependencyMode taskMode, List<Reward> rewards, boolean repeatable,
                    boolean autoClaim) {

    public static final Codec<Quest> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            QuestBook.ID_CODEC.fieldOf("id").forGetter(Quest::id),
            TextCodecs.CODEC.optionalFieldOf("title", Text.empty()).forGetter(Quest::title),
            TextCodecs.CODEC.listOf().optionalFieldOf("description", List.of()).forGetter(Quest::description),
            QuestCodecs.ITEM_STACKS.optionalFieldOf("icon", List.of()).forGetter(Quest::icons),
            Codec.DOUBLE.optionalFieldOf("x", 0.0D).forGetter(Quest::x),
            Codec.DOUBLE.optionalFieldOf("y", 0.0D).forGetter(Quest::y),
            Codec.FLOAT.optionalFieldOf("size", 1.0F).forGetter(Quest::size),
            QuestShape.CODEC.optionalFieldOf("shape", QuestShape.TASK).forGetter(Quest::shape),
            QuestBook.ID_CODEC.listOf().optionalFieldOf("dependencies", List.of()).forGetter(Quest::dependencies),
            DependencyMode.CODEC.optionalFieldOf("dependency_mode", DependencyMode.ALL).forGetter(Quest::dependencyMode),
            QuestVisibility.CODEC.optionalFieldOf("visibility", QuestVisibility.ALWAYS).forGetter(Quest::visibility),
            Task.LENIENT_CODEC.listOf().optionalFieldOf("tasks", List.of()).forGetter(Quest::tasks),
            DependencyMode.CODEC.optionalFieldOf("task_mode", DependencyMode.ALL).forGetter(Quest::taskMode),
            Reward.LENIENT_CODEC.listOf().optionalFieldOf("rewards", List.of()).forGetter(Quest::rewards),
            Codec.BOOL.optionalFieldOf("repeatable", false).forGetter(Quest::repeatable),
            Codec.BOOL.optionalFieldOf("auto_claim", false).forGetter(Quest::autoClaim)
    ).apply(instance, Quest::new));

    public Quest withPosition(double x, double y) {
        return new Quest(this.id, this.title, this.description, this.icons, x, y, this.size, this.shape, this.dependencies, this.dependencyMode, this.visibility, this.tasks,
                this.taskMode, this.rewards, this.repeatable, this.autoClaim);
    }

    public Quest withDependencies(List<String> dependencies) {
        return new Quest(this.id, this.title, this.description, this.icons, this.x, this.y, this.size, this.shape, List.copyOf(dependencies), this.dependencyMode, this.visibility,
                this.tasks, this.taskMode, this.rewards, this.repeatable, this.autoClaim);
    }

    public Optional<Task> getTask(String taskId) {
        return this.tasks.stream().filter(task -> task.getId().equals(taskId)).findFirst();
    }

    public Optional<Reward> getReward(String rewardId) {
        return this.rewards.stream().filter(reward -> reward.getId().equals(rewardId)).findFirst();
    }

}
