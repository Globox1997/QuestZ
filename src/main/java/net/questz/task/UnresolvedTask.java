package net.questz.task;

import com.google.gson.JsonElement;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import net.minecraft.util.Identifier;
import net.questz.book.QuestBook;
import net.questz.init.TaskTypeInit;

public class UnresolvedTask extends Task {

    public static final MapCodec<UnresolvedTask> CODEC = QuestBook.ID_CODEC.fieldOf("id").flatXmap(
            id -> DataResult.error(() -> "Unresolved tasks cannot be decoded directly"),
            task -> DataResult.success(task.getId()));

    private final Identifier typeId;
    private final JsonElement raw;
    private final String error;

    public UnresolvedTask(String id, Identifier typeId, JsonElement raw, String error) {
        super(id);
        this.typeId = typeId;
        this.raw = raw;
        this.error = error;
    }

    public JsonElement getRaw() {
        return this.raw;
    }

    public String getError() {
        return this.error;
    }

    @Override
    public Identifier getTypeId() {
        return this.typeId;
    }

    @Override
    public TaskType<?> getType() {
        return TaskTypeInit.UNRESOLVED;
    }

}
