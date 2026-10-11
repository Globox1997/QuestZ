package net.questz.init;

import com.mojang.serialization.MapCodec;
import net.minecraft.registry.Registry;
import net.questz.QuestzMain;
import net.questz.api.QuestzRegistries;
import net.questz.task.*;

public class TaskTypeInit {

    public static final TaskType<CheckmarkTask> CHECKMARK = register("checkmark", CheckmarkTask.CODEC);
    public static final TaskType<ItemTask> ITEM = register("item", ItemTask.CODEC);
    public static final TaskType<KillTask> KILL = register("kill", KillTask.CODEC);
    public static final TaskType<MineTask> MINE = register("mine", MineTask.CODEC);
    public static final TaskType<PlaceTask> PLACE = register("place", PlaceTask.CODEC);
    public static final TaskType<CraftTask> CRAFT = register("craft", CraftTask.CODEC);
    public static final TaskType<UseTask> USE = register("use", UseTask.CODEC);
    public static final TaskType<CustomStatTask> STAT = register("stat", CustomStatTask.CODEC);
    public static final TaskType<LocationTask> LOCATION = register("location", LocationTask.CODEC);
    public static final TaskType<DimensionTask> DIMENSION = register("dimension", DimensionTask.CODEC);
    public static final TaskType<BiomeTask> BIOME = register("biome", BiomeTask.CODEC);
    public static final TaskType<StructureTask> STRUCTURE = register("structure", StructureTask.CODEC);
    public static final TaskType<AdvancementTask> ADVANCEMENT = register("advancement", AdvancementTask.CODEC);
    public static final TaskType<CriterionTask> CRITERION = register("criterion", CriterionTask.CODEC);
    public static final TaskType<UnresolvedTask> UNRESOLVED = register("unresolved", UnresolvedTask.CODEC);

    private static <T extends Task> TaskType<T> register(String name, MapCodec<T> codec) {
        return Registry.register(QuestzRegistries.TASK_TYPE, QuestzMain.identifierOf(name), new TaskType<>(codec));
    }

    public static void init() {
    }

}
