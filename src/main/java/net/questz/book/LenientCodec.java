package net.questz.book;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import net.minecraft.util.Identifier;

import java.math.BigDecimal;
import java.util.function.Function;

public class LenientCodec<A> implements Codec<A> {

    public interface Fallback<A> {
        A create(String id, Identifier typeId, JsonObject raw, String error);
    }

    private final Codec<A> codec;
    private final Fallback<A> fallback;
    private final Function<A, JsonElement> rawGetter;

    public LenientCodec(Codec<A> codec, Fallback<A> fallback, Function<A, JsonElement> rawGetter) {
        this.codec = codec;
        this.fallback = fallback;
        this.rawGetter = rawGetter;
    }

    @Override
    public <T> DataResult<Pair<A, T>> decode(DynamicOps<T> ops, T input) {
        DataResult<Pair<A, T>> result = this.codec.decode(ops, input);
        if (result.isSuccess()) {
            return result;
        }
        JsonElement raw = ops.convertTo(JsonOps.INSTANCE, input);
        if (!raw.isJsonObject()) {
            return result;
        }
        JsonObject object = raw.getAsJsonObject();
        String id = getString(object, "id", "unknown");
        Identifier typeId = Identifier.tryParse(getString(object, "type", "questz:unknown"));
        String error = result.error().map(DataResult.Error::message).orElse("");
        return DataResult.success(Pair.of(this.fallback.create(id, typeId != null ? typeId : Identifier.of("questz", "unknown"), object, error), ops.empty()));
    }

    @Override
    public <T> DataResult<T> encode(A input, DynamicOps<T> ops, T prefix) {
        JsonElement raw = this.rawGetter.apply(input);
        if (raw != null) {
            return DataResult.success(toOps(ops, raw));
        }
        return this.codec.encode(input, ops, prefix);
    }

    private static <T> T toOps(DynamicOps<T> ops, JsonElement json) {
        if (json == null || json.isJsonNull()) {
            return ops.empty();
        }
        if (json.isJsonObject()) {
            return ops.createMap(json.getAsJsonObject().entrySet().stream().map(entry -> Pair.of(ops.createString(entry.getKey()), toOps(ops, entry.getValue()))));
        }
        if (json.isJsonArray()) {
            return ops.createList(json.getAsJsonArray().asList().stream().map(element -> toOps(ops, element)));
        }
        JsonPrimitive primitive = json.getAsJsonPrimitive();
        if (primitive.isBoolean()) {
            return ops.createBoolean(primitive.getAsBoolean());
        }
        if (primitive.isNumber()) {
            BigDecimal number = primitive.getAsBigDecimal();
            try {
                long value = number.longValueExact();
                return value == (int) value ? ops.createInt((int) value) : ops.createLong(value);
            } catch (ArithmeticException exception) {
                return ops.createDouble(number.doubleValue());
            }
        }
        return ops.createString(primitive.getAsString());
    }

    private static String getString(JsonObject object, String key, String fallback) {
        JsonElement element = object.get(key);
        return element instanceof JsonPrimitive primitive && primitive.isString() ? primitive.getAsString() : fallback;
    }

}
