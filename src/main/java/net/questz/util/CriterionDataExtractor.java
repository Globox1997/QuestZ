package net.questz.util;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import net.minecraft.advancement.AdvancementCriterion;
import net.minecraft.advancement.criterion.CriterionConditions;
import net.questz.access.CriterionAccess;

import java.util.HashMap;
import java.util.Map;

public class CriterionDataExtractor {

    public static JsonObject toJson(AdvancementCriterion<?> criterion) {
        var player = net.minecraft.client.MinecraftClient.getInstance().player;
        if (player == null) return new JsonObject();

        var registryManager = player.getRegistryManager();
        var ops = registryManager.getOps(JsonOps.INSTANCE);

        JsonObject result = new JsonObject();

        try {
            var encoded = AdvancementCriterion.CODEC.encodeStart(ops, criterion);
            if (encoded.result().isPresent() && encoded.result().get().isJsonObject()) {
                return encoded.result().get().getAsJsonObject();
            }
        } catch (Exception e) {
            System.err.println("Standard codec failed: " + e.getMessage());
        }

        try {
            CriterionAccess access = (CriterionAccess) (Object) criterion;
            result.addProperty("trigger", access.questz$getTriggerId().toString());

            JsonElement conditionsJson = encodeConditionsHelper(criterion, ops);
            result.add("conditions", conditionsJson);

        } catch (Exception e) {
            System.err.println("Manual serialization failed: " + e.getMessage());
            result.add("conditions", new JsonObject());
        }

        return result;
    }

    private static <T extends CriterionConditions> JsonElement encodeConditionsHelper(
            AdvancementCriterion<T> criterion,
            com.mojang.serialization.DynamicOps<JsonElement> ops
    ) {
        Codec<T> codec = criterion.trigger().getConditionsCodec();
        T conditions = criterion.conditions();

        return codec.encodeStart(ops, conditions)
                .getOrThrow(err -> new RuntimeException("Codec error: " + err));
    }

    public static Map<String, String> extractConditionData(JsonObject json) {
        Map<String, String> data = new HashMap<>();

        if (json == null || !json.has("conditions")) {
            return data;
        }

        JsonObject conditions = json.getAsJsonObject("conditions");

        extractStringField(conditions, data, "object");

        extractNumericField(conditions, data, "count");
        extractNumericField(conditions, data, "amount");

        if (conditions.has("items")) {
            var items = conditions.get("items");
            if (items.isJsonArray()) {
                StringBuilder sb = new StringBuilder();
                var array = items.getAsJsonArray();
                for (int i = 0; i < array.size(); i++) {
                    if (i > 0) sb.append(",");
                    var item = array.get(i).getAsJsonObject();

                    if (item.has("items")) {
                        var itemsField = item.get("items");
                        if (itemsField.isJsonArray() && itemsField.getAsJsonArray().size() > 0) {
                            sb.append(itemsField.getAsJsonArray().get(0).getAsString());
                        } else if (itemsField.isJsonPrimitive()) {
                            sb.append(itemsField.getAsString());
                        }
                    }

                    if (item.has("count")) {
                        var count = item.getAsJsonObject("count");
                        if (count.has("min")) {
                            sb.append(":").append(count.get("min").getAsInt());
                        }
                    }
                }
                if (sb.length() > 0) {
                    data.put("items", sb.toString());
                }
            }
        }

        if (conditions.has("entity")) {
            var entity = conditions.getAsJsonObject("entity");
            if (entity.has("type")) {
                data.put("entity", entity.get("type").getAsString());
            }
        }

        if (conditions.has("location")) {
            var location = conditions.getAsJsonObject("location");

            if (location.has("biome")) {
                data.put("biome", location.get("biome").getAsString());
            }

            if (location.has("dimension")) {
                data.put("dimension", location.get("dimension").getAsString());
            }

            if (location.has("block")) {
                var block = location.getAsJsonObject("block");
                if (block.has("blocks")) {
                    var blocks = block.get("blocks");
                    if (blocks.isJsonArray() && blocks.getAsJsonArray().size() > 0) {
                        data.put("location_block", blocks.getAsJsonArray().get(0).getAsString());
                    } else if (blocks.isJsonPrimitive()) {
                        data.put("location_block", blocks.getAsString());
                    }
                }
            }
        }

        if (conditions.has("item")) {
            var item = conditions.getAsJsonObject("item");
            if (item.has("items")) {
                var items = item.get("items");
                if (items.isJsonArray() && items.getAsJsonArray().size() > 0) {
                    data.put("item", items.getAsJsonArray().get(0).getAsString());
                } else if (items.isJsonPrimitive()) {
                    data.put("item", items.getAsString());
                }
            }
        }

        if (conditions.has("block")) {
            var block = conditions.get("block");
            if (block.isJsonPrimitive()) {
                data.put("block", block.getAsString());
            } else if (block.isJsonObject()) {
                var blockObj = block.getAsJsonObject();
                if (blockObj.has("blocks")) {
                    var blocks = blockObj.get("blocks");
                    if (blocks.isJsonArray() && blocks.getAsJsonArray().size() > 0) {
                        data.put("block", blocks.getAsJsonArray().get(0).getAsString());
                    } else if (blocks.isJsonPrimitive()) {
                        data.put("block", blocks.getAsString());
                    }
                }
            }
        }

        if (conditions.has("from")) {
            data.put("from", conditions.get("from").getAsString());
        }
        if (conditions.has("to")) {
            data.put("to", conditions.get("to").getAsString());
        }

        if (!data.containsKey("distance")) {
            extractNumericField(conditions, data, "distance");
        }
        extractNumericField(conditions, data, "level");
        extractNumericField(conditions, data, "signal_strength");
        extractNumericField(conditions, data, "levels");
        extractNumericField(conditions, data, "delta");
        extractNumericField(conditions, data, "unique_entity_types");
        extractNumericField(conditions, data, "victims");

        extractEntityType(conditions, data, "child", "child");
        extractEntityType(conditions, data, "parent", "parent");
        extractEntityType(conditions, data, "villager", "villager");

        extractStringField(conditions, data, "recipe");
        extractStringField(conditions, data, "loot_table");
        extractStringField(conditions, data, "effect");
        extractStringField(conditions, data, "potion");

        if (conditions.has("damage")) {
            var damage = conditions.getAsJsonObject("damage");
            if (damage.has("source_entity")) {
                var sourceEntity = damage.getAsJsonObject("source_entity");
                if (sourceEntity.has("type")) {
                    data.put("source_entity", sourceEntity.get("type").getAsString());
                }
            }
        }

        extractEntityType(conditions, data, "lightning", "lightning");

        return data;
    }

    private static void extractNumericField(JsonObject json, Map<String, String> data, String field) {
        if (json.has(field)) {
            data.put(field, String.valueOf(json.get(field).getAsNumber()));
        }
    }

    private static void extractStringField(JsonObject json, Map<String, String> data, String field) {
        if (json.has(field) && !data.containsKey(field)) {
            data.put(field, json.get(field).getAsString());
        }
    }

    private static void extractEntityType(JsonObject json, Map<String, String> data, String field, String outputKey) {
        if (json.has(field)) {
            var entity = json.get(field);
            if (entity.isJsonObject()) {
                var entityObj = entity.getAsJsonObject();
                if (entityObj.has("type")) {
                    data.put(outputKey, entityObj.get("type").getAsString());
                }
            }
        }
    }
}