package net.questz.task;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.Identifier;
import net.questz.api.QuestzRegistries;
import net.questz.book.LenientCodec;
import net.questz.book.QuestBook;

public abstract class Task {

    public static final Codec<Task> CODEC = QuestzRegistries.TASK_TYPE.getCodec().dispatch("type", Task::getType, TaskType::codec);

    public static final Codec<Task> LENIENT_CODEC = new LenientCodec<>(CODEC, UnresolvedTask::new, task -> task instanceof UnresolvedTask unresolved ? unresolved.getRaw() : null);

    private final String id;

    protected Task(String id) {
        this.id = id;
    }

    public String getId() {
        return this.id;
    }

    public long getTarget() {
        return 1L;
    }

    public abstract TaskType<?> getType();

    public Identifier getTypeId() {
        return QuestzRegistries.TASK_TYPE.getId(this.getType());
    }

    protected static <T extends Task> RecordCodecBuilder<T, String> idField() {
        return QuestBook.ID_CODEC.fieldOf("id").forGetter(Task::getId);
    }

}
