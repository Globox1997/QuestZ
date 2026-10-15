package net.questz.client.editor;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.List;

@Environment(EnvType.CLIENT)
public class EntryTemplates {

    private static String heldItem(String fallback) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) {
            return fallback;
        }
        ItemStack stack = client.player.getMainHandStack();
        return stack.isEmpty() ? fallback : Registries.ITEM.getId(stack.getItem()).toString();
    }

    private static JsonObject itemObject(String id) {
        JsonObject item = new JsonObject();
        item.addProperty("id", id);
        return item;
    }

    @Nullable
    public static List<FormField> taskForm(Identifier type) {
        if (!type.getNamespace().equals("questz")) {
            return null;
        }
        return switch (type.getPath()) {
            case "checkmark" -> List.of();
            case "item" -> List.of(FormField.itemList("item"), FormField.number("count"), FormField.bool("consume"));
            case "kill" -> List.of(FormField.idList("entity", RegistryKeys.ENTITY_TYPE), FormField.number("count"));
            case "mine", "place" -> List.of(FormField.idList("block", RegistryKeys.BLOCK), FormField.number("count"));
            case "craft", "use" -> List.of(FormField.idList("item", RegistryKeys.ITEM, "items"), FormField.number("count"));
            case "stat" -> List.of(FormField.id("stat", RegistryKeys.CUSTOM_STAT), FormField.number("count"));
            case "dimension" -> List.of(FormField.id("dimension", RegistryKeys.WORLD));
            case "biome" -> List.of(FormField.idList("biome", RegistryKeys.BIOME));
            case "structure" -> List.of(FormField.idList("structure", RegistryKeys.STRUCTURE));
            case "advancement" -> List.of(FormField.text("advancement"));
            default -> null;
        };
    }

    @Nullable
    public static List<FormField> rewardForm(Identifier type) {
        if (!type.getNamespace().equals("questz")) {
            return null;
        }
        return switch (type.getPath()) {
            case "item" -> List.of(FormField.item("item"), FormField.number("count"), FormField.bool("team_reward"));
            case "xp" -> List.of(FormField.number("amount"), FormField.bool("levels"), FormField.bool("team_reward"));
            case "command" -> List.of(FormField.text("command"), FormField.bool("team_reward"));
            case "loot_table" -> List.of(FormField.text("loot_table"), FormField.bool("team_reward"));
            default -> null;
        };
    }

    public static JsonObject task(Identifier type) {
        MinecraftClient client = MinecraftClient.getInstance();
        JsonObject json = new JsonObject();
        switch (type.getNamespace().equals("questz") ? type.getPath() : "") {
            case "item" -> {
                json.add("item", itemObject(heldItem("minecraft:oak_log")));
                json.addProperty("count", 1);
                json.addProperty("consume", false);
            }
            case "kill" -> {
                json.addProperty("entity", "minecraft:zombie");
                json.addProperty("count", 1);
            }
            case "mine", "place" -> {
                json.addProperty("block", "minecraft:stone");
                json.addProperty("count", 1);
            }
            case "craft", "use" -> {
                json.addProperty("item", heldItem("minecraft:torch"));
                json.addProperty("count", 1);
            }
            case "stat" -> {
                json.addProperty("stat", "minecraft:jump");
                json.addProperty("count", 10);
            }
            case "location" -> {
                BlockPos pos = client.player != null ? client.player.getBlockPos() : BlockPos.ORIGIN;
                json.addProperty("dimension", client.world != null ? client.world.getRegistryKey().getValue().toString() : "minecraft:overworld");
                JsonArray array = new JsonArray();
                array.add(pos.getX());
                array.add(pos.getY());
                array.add(pos.getZ());
                json.add("pos", array);
                json.addProperty("radius", 8);
            }
            case "dimension" -> json.addProperty("dimension", "minecraft:the_nether");
            case "biome" -> json.addProperty("biome", client.world != null && client.player != null
                    ? client.world.getBiome(client.player.getBlockPos()).getKey().map(key -> key.getValue().toString()).orElse("minecraft:plains")
                    : "minecraft:plains");
            case "structure" -> json.addProperty("structure", "#minecraft:village");
            case "advancement" -> json.addProperty("advancement", "minecraft:story/mine_stone");
            case "criterion" -> {
                JsonObject criterion = new JsonObject();
                criterion.addProperty("trigger", "minecraft:slept_in_bed");
                json.add("criterion", criterion);
                json.addProperty("count", 1);
            }
            default -> {
            }
        }
        return json;
    }

    public static JsonObject reward(Identifier type) {
        JsonObject json = new JsonObject();
        switch (type.getNamespace().equals("questz") ? type.getPath() : "") {
            case "item" -> {
                json.add("item", itemObject(heldItem("minecraft:diamond")));
                json.addProperty("count", 1);
            }
            case "xp" -> {
                json.addProperty("amount", 5);
                json.addProperty("levels", true);
            }
            case "command" -> json.addProperty("command", "say @s finished a quest");
            case "loot_table" -> json.addProperty("loot_table", "minecraft:chests/simple_dungeon");
            case "choice" -> {
                JsonArray options = new JsonArray();
                JsonObject first = itemObject(heldItem("minecraft:diamond_sword"));
                options.add(first);
                JsonObject second = itemObject("minecraft:diamond_pickaxe");
                options.add(second);
                json.add("options", options);
            }
            case "random" -> {
                JsonArray entries = new JsonArray();
                JsonObject common = new JsonObject();
                JsonObject commonItem = itemObject("minecraft:iron_ingot");
                commonItem.addProperty("count", 8);
                common.add("item", commonItem);
                common.addProperty("weight", 9);
                entries.add(common);
                JsonObject rare = new JsonObject();
                rare.add("item", itemObject("minecraft:diamond"));
                rare.addProperty("weight", 1);
                entries.add(rare);
                json.add("entries", entries);
                json.addProperty("rolls", 1);
            }
            default -> {
            }
        }
        json.addProperty("team_reward", false);
        return json;
    }

}
