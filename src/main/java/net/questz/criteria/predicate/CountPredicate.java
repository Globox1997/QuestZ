package net.questz.criteria.predicate;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.util.JsonHelper;

public class CountPredicate {
    private final int count;

    public CountPredicate(int count) {
        this.count = count;
    }

    public boolean test(int count) {
        if (this.count == count)
            return true;
        else
            return false;
    }

    public static CountPredicate fromJson(JsonElement json) {
        int count = JsonHelper.asInt(json, "count");
        return new CountPredicate(count);
    }

    public JsonElement toJson() {
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("count", (Number) this.count);
        return jsonObject;
    }

}
