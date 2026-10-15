package net.questz.client.editor;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.command.argument.ItemStringReader;
import net.minecraft.component.ComponentType;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtOps;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryOps;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Environment(EnvType.CLIENT)
public class ItemStrings {

    public static RegistryWrapper.WrapperLookup registries() {
        MinecraftClient client = MinecraftClient.getInstance();
        return client.getNetworkHandler() != null ? client.getNetworkHandler().getRegistryManager() : DynamicRegistryManager.of(Registries.REGISTRIES);
    }

    public static DataResult<ItemStack> parse(String input) {
        StringReader reader = new StringReader(input.trim());
        try {
            ItemStringReader.ItemResult result = new ItemStringReader(registries()).consume(reader);
            if (reader.canRead()) {
                return DataResult.error(() -> "Unexpected text after the item: " + reader.getRemaining());
            }
            ItemStack stack = new ItemStack(result.item(), 1, result.components());
            return stack.isEmpty() ? DataResult.error(() -> "The item must not be air") : DataResult.success(stack);
        } catch (CommandSyntaxException exception) {
            return DataResult.error(exception::getMessage);
        }
    }

    public static DataResult<List<ItemStack>> parseList(String input) {
        List<ItemStack> stacks = new ArrayList<>();
        for (String part : split(input)) {
            DataResult<ItemStack> result = parse(part);
            if (result.isError()) {
                return DataResult.error(() -> part + ": " + result.error().map(DataResult.Error::message).orElse(""));
            }
            stacks.add(result.getOrThrow());
        }
        return DataResult.success(stacks);
    }

    public static String format(ItemStack stack) {
        StringBuilder builder = new StringBuilder(Registries.ITEM.getId(stack.getItem()).toString());
        RegistryOps<NbtElement> ops = registries().getOps(NbtOps.INSTANCE);
        List<String> parts = new ArrayList<>();
        for (Map.Entry<ComponentType<?>, Optional<?>> entry : stack.getComponentChanges().entrySet()) {
            Identifier id = Registries.DATA_COMPONENT_TYPE.getId(entry.getKey());
            if (id == null) {
                continue;
            }
            if (entry.getValue().isEmpty()) {
                parts.add("!" + id);
            } else {
                encode(entry.getKey(), entry.getValue().get(), ops).ifPresent(nbt -> parts.add(id + "=" + nbt.asString()));
            }
        }
        if (!parts.isEmpty()) {
            builder.append('[').append(String.join(",", parts)).append(']');
        }
        return builder.toString();
    }

    public static String formatList(List<ItemStack> stacks) {
        List<String> parts = new ArrayList<>();
        stacks.forEach(stack -> parts.add(format(stack)));
        return String.join(", ", parts);
    }

    @SuppressWarnings("unchecked")
    private static <T> Optional<NbtElement> encode(ComponentType<T> type, Object value, RegistryOps<NbtElement> ops) {
        Codec<T> codec = type.getCodec();
        return codec == null ? Optional.empty() : codec.encodeStart(ops, (T) value).result();
    }

    public static List<String> split(String text) {
        List<String> parts = new ArrayList<>();
        int depth = 0;
        char quote = 0;
        int start = 0;
        for (int i = 0; i < text.length(); i++) {
            char character = text.charAt(i);
            if (quote != 0) {
                if (character == '\\') {
                    i++;
                } else if (character == quote) {
                    quote = 0;
                }
            } else if (character == '"' || character == '\'') {
                quote = character;
            } else if (character == '[' || character == '{' || character == '(') {
                depth++;
            } else if (character == ']' || character == '}' || character == ')') {
                depth--;
            } else if (character == ',' && depth <= 0) {
                addPart(parts, text.substring(start, i));
                start = i + 1;
            }
        }
        addPart(parts, text.substring(start));
        return parts;
    }

    private static void addPart(List<String> parts, String part) {
        String trimmed = part.trim();
        if (!trimmed.isEmpty()) {
            parts.add(trimmed);
        }
    }

    public static int tokenStart(String text, int cursor, boolean multiple) {
        int depth = 0;
        char quote = 0;
        int start = 0;
        for (int i = 0; i < cursor && i < text.length(); i++) {
            char character = text.charAt(i);
            if (quote != 0) {
                if (character == '\\') {
                    i++;
                } else if (character == quote) {
                    quote = 0;
                }
            } else if (character == '"' || character == '\'') {
                quote = character;
            } else if (character == '[' || character == '{' || character == '(') {
                depth++;
            } else if (character == ']' || character == '}' || character == ')') {
                depth--;
            } else if (character == ',' && depth <= 0 && multiple) {
                start = i + 1;
            }
        }
        if (quote != 0 || depth > 0) {
            return -1;
        }
        while (start < cursor && Character.isWhitespace(text.charAt(start))) {
            start++;
        }
        return start;
    }

}
