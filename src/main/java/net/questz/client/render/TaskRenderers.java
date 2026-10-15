package net.questz.client.render;

import com.google.gson.JsonElement;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.advancement.AdvancementEntry;
import net.minecraft.block.Block;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.EntityType;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.SpawnEggItem;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import net.questz.api.QuestzRegistries;
import net.questz.init.TaskTypeInit;
import net.questz.task.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Environment(EnvType.CLIENT)
public class TaskRenderers {

    private static final Map<TaskType<?>, TaskRenderer<?>> RENDERERS = new HashMap<>();

    public static <T extends Task> void register(TaskType<T> type, TaskRenderer<T> renderer) {
        RENDERERS.put(type, renderer);
    }

    @SuppressWarnings("unchecked")
    private static <T extends Task> Optional<TaskRenderer<T>> get(T task) {
        return Optional.ofNullable((TaskRenderer<T>) RENDERERS.get(task.getType()));
    }

    public static Text getTitle(Task task) {
        return get(task).map(renderer -> renderer.getTitle(task)).orElseGet(() -> Text.literal(String.valueOf(task.getTypeId())));
    }

    public static ItemStack getIcon(Task task) {
        return get(task).map(renderer -> renderer.getIcon(task)).orElseGet(() -> new ItemStack(Items.PAPER));
    }

    public static List<ItemStack> itemTaskIcons(ItemTask task) {
        List<ItemStack> icons = new ArrayList<>(task.getItems());
        for (TagKey<Item> tag : task.getTags()) {
            Registries.ITEM.iterateEntries(tag).forEach(entry -> icons.add(new ItemStack(entry)));
        }
        return icons;
    }

    private static ItemStack blockIcon(Block block) {
        return new ItemStack(block.asItem());
    }

    private static ItemStack entityIcon(EntityType<?> type) {
        SpawnEggItem egg = SpawnEggItem.forEntity(type);
        return egg != null ? new ItemStack(egg) : ItemStack.EMPTY;
    }

    private static ItemStack dimensionIcon(Identifier dimension) {
        if (dimension.equals(World.NETHER.getValue())) {
            return new ItemStack(Items.NETHERRACK);
        }
        if (dimension.equals(World.END.getValue())) {
            return new ItemStack(Items.END_STONE);
        }
        if (dimension.equals(World.OVERWORLD.getValue())) {
            return new ItemStack(Items.GRASS_BLOCK);
        }
        return new ItemStack(Items.ENDER_EYE);
    }

    public static void init() {
        register(TaskTypeInit.CHECKMARK, new TaskRenderer<>() {
            public Text getTitle(CheckmarkTask task) {
                return Text.translatable("task.questz.checkmark");
            }

            public ItemStack getIcon(CheckmarkTask task) {
                return new ItemStack(Items.WRITABLE_BOOK);
            }
        });
        register(TaskTypeInit.ITEM, new TaskRenderer<>() {
            public Text getTitle(ItemTask task) {
                List<Text> names = new ArrayList<>();
                task.getItems().forEach(item -> names.add(item.getName()));
                task.getTags().forEach(tag -> names.add(DisplayNames.ofTag(tag)));
                return Text.translatable(task.isConsume() ? "task.questz.item.submit" : "task.questz.item", DisplayNames.ofAlternatives(names));
            }

            public ItemStack getIcon(ItemTask task) {
                List<ItemStack> icons = itemTaskIcons(task);
                return icons.isEmpty() ? ItemStack.EMPTY : DisplayNames.cycle(icons);
            }
        });
        register(TaskTypeInit.KILL, new TaskRenderer<>() {
            public Text getTitle(KillTask task) {
                return Text.translatable("task.questz.kill", DisplayNames.ofOptionalList(task.getEntities(), entry -> entry.value().getName(), Text.translatable("task.questz.any_mob")));
            }

            public ItemStack getIcon(KillTask task) {
                return DisplayNames.iconOf(task.getEntities(), TaskRenderers::entityIcon, new ItemStack(Items.IRON_SWORD));
            }
        });
        register(TaskTypeInit.MINE, new TaskRenderer<>() {
            public Text getTitle(MineTask task) {
                return Text.translatable("task.questz.mine", DisplayNames.ofOptionalList(task.getBlocks(), entry -> entry.value().getName(), Text.translatable("task.questz.any_block")));
            }

            public ItemStack getIcon(MineTask task) {
                return DisplayNames.iconOf(task.getBlocks(), TaskRenderers::blockIcon, new ItemStack(Items.IRON_PICKAXE));
            }
        });
        register(TaskTypeInit.PLACE, new TaskRenderer<>() {
            public Text getTitle(PlaceTask task) {
                return Text.translatable("task.questz.place", DisplayNames.ofOptionalList(task.getBlocks(), entry -> entry.value().getName(), Text.translatable("task.questz.any_block")));
            }

            public ItemStack getIcon(PlaceTask task) {
                return DisplayNames.iconOf(task.getBlocks(), TaskRenderers::blockIcon, new ItemStack(Items.STONE));
            }
        });
        register(TaskTypeInit.CRAFT, new TaskRenderer<>() {
            public Text getTitle(CraftTask task) {
                return Text.translatable("task.questz.craft", DisplayNames.ofOptionalList(task.getItems(), entry -> entry.value().getName(), Text.translatable("task.questz.any_item")));
            }

            public ItemStack getIcon(CraftTask task) {
                return DisplayNames.iconOf(task.getItems(), ItemStack::new, new ItemStack(Items.CRAFTING_TABLE));
            }
        });
        register(TaskTypeInit.USE, new TaskRenderer<>() {
            public Text getTitle(UseTask task) {
                return Text.translatable("task.questz.use", DisplayNames.ofOptionalList(task.getItems(), entry -> entry.value().getName(), Text.translatable("task.questz.any_item")));
            }

            public ItemStack getIcon(UseTask task) {
                return DisplayNames.iconOf(task.getItems(), ItemStack::new, new ItemStack(Items.STICK));
            }
        });
        register(TaskTypeInit.STAT, new TaskRenderer<>() {
            public Text getTitle(CustomStatTask task) {
                return DisplayNames.ofId(task.getStat(), "stat");
            }

            public ItemStack getIcon(CustomStatTask task) {
                return new ItemStack(Items.FEATHER);
            }
        });
        register(TaskTypeInit.LOCATION, new TaskRenderer<>() {
            public Text getTitle(LocationTask task) {
                return Text.translatable("task.questz.location", task.getPos().getX(), task.getPos().getY(), task.getPos().getZ(), DisplayNames.ofId(task.getDimension().getValue(), "dimension"));
            }

            public ItemStack getIcon(LocationTask task) {
                return new ItemStack(Items.COMPASS);
            }
        });
        register(TaskTypeInit.DIMENSION, new TaskRenderer<>() {
            public Text getTitle(DimensionTask task) {
                return Text.translatable("task.questz.dimension", DisplayNames.ofId(task.getDimension().getValue(), "dimension"));
            }

            public ItemStack getIcon(DimensionTask task) {
                return dimensionIcon(task.getDimension().getValue());
            }
        });
        register(TaskTypeInit.BIOME, new TaskRenderer<>() {
            public Text getTitle(BiomeTask task) {
                return Text.translatable("task.questz.biome", DisplayNames.ofList(task.getBiomes(), entry -> DisplayNames.keyName(entry, "biome")));
            }

            public ItemStack getIcon(BiomeTask task) {
                return new ItemStack(Items.OAK_SAPLING);
            }
        });
        register(TaskTypeInit.STRUCTURE, new TaskRenderer<>() {
            public Text getTitle(StructureTask task) {
                return Text.translatable("task.questz.structure", DisplayNames.ofList(task.getStructures(), entry -> DisplayNames.keyName(entry, "structure")));
            }

            public ItemStack getIcon(StructureTask task) {
                return new ItemStack(Items.FILLED_MAP);
            }
        });
        register(TaskTypeInit.ADVANCEMENT, new TaskRenderer<>() {
            public Text getTitle(AdvancementTask task) {
                return Text.translatable("task.questz.advancement", findAdvancement(task.getAdvancement())
                        .flatMap(entry -> entry.value().display())
                        .map(display -> display.getTitle())
                        .orElseGet(() -> Text.literal(DisplayNames.humanize(task.getAdvancement().getPath()))));
            }

            public ItemStack getIcon(AdvancementTask task) {
                return findAdvancement(task.getAdvancement()).flatMap(entry -> entry.value().display()).map(display -> display.getIcon()).orElseGet(() -> new ItemStack(Items.KNOWLEDGE_BOOK));
            }
        });
        register(TaskTypeInit.CRITERION, new TaskRenderer<>() {
            public Text getTitle(CriterionTask task) {
                Identifier trigger = Registries.CRITERION.getId(task.getCriterion().trigger());
                return trigger != null ? DisplayNames.ofId(trigger, "trigger") : Text.translatable("task.questz.criterion");
            }

            public ItemStack getIcon(CriterionTask task) {
                return new ItemStack(Items.TARGET);
            }
        });
        register(TaskTypeInit.UNRESOLVED, new TaskRenderer<>() {
            public Text getTitle(UnresolvedTask task) {
                JsonElement structure = task.getRaw().isJsonObject() ? task.getRaw().getAsJsonObject().get("structure") : null;
                if (task.getTypeId().equals(QuestzRegistries.TASK_TYPE.getId(TaskTypeInit.STRUCTURE)) && structure != null && structure.isJsonPrimitive()) {
                    String value = structure.getAsString();
                    return Text.translatable("task.questz.structure", Text.literal(DisplayNames.humanize(value.startsWith("#") ? value.substring(1) : value)));
                }
                return Text.translatable("task.questz.unresolved", task.getTypeId().toString());
            }

            public ItemStack getIcon(UnresolvedTask task) {
                return task.getTypeId().equals(QuestzRegistries.TASK_TYPE.getId(TaskTypeInit.STRUCTURE)) ? new ItemStack(Items.FILLED_MAP) : new ItemStack(Items.BARRIER);
            }
        });
    }

    private static Optional<AdvancementEntry> findAdvancement(Identifier id) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.getNetworkHandler() == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(client.getNetworkHandler().getAdvancementHandler().get(id));
    }

}
