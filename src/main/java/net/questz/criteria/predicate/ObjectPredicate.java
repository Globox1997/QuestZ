package net.questz.criteria.predicate;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import net.minecraft.block.Block;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.JsonHelper;

public class ObjectPredicate {
    private final Identifier object;

    public ObjectPredicate(Identifier object) {
        this.object = object;
    }

    // 0: item, 1: block, 2: entity
    public boolean test(Object object, int code) {
        if (code == 0) {
            return Registries.ITEM.getId((Item) object).equals(this.object);
        } else if (code == 1) {
            return Registries.BLOCK.getId((Block) object).equals(this.object);
        } else if (code == 2) {
            return Registries.ENTITY_TYPE.getId(((LivingEntity) object).getType()).equals(this.object);
        }
        return false;
    }

    public static ObjectPredicate fromJson(JsonElement json) {
        Identifier object = new Identifier(JsonHelper.asString(json, "object"));
        return new ObjectPredicate(object);
    }

    public JsonElement toJson() {
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("object", (String) this.object.toString());
        return jsonObject;
    }

}
