package net.questz.client.render;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.entry.RegistryEntryList;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Function;

@Environment(EnvType.CLIENT)
public class DisplayNames {

    private static final long CYCLE_MILLIS = 1000L;

    public static String humanize(String path) {
        String name = path.substring(path.lastIndexOf('/') + 1).replace('_', ' ');
        return name.isEmpty() ? name : name.substring(0, 1).toUpperCase(Locale.ROOT) + name.substring(1);
    }

    public static Text ofId(Identifier id, String translationPrefix) {
        return Text.translatableWithFallback(id.toTranslationKey(translationPrefix), humanize(id.getPath()));
    }

    public static int cycleIndex(int size) {
        return size <= 0 ? 0 : (int) (Util.getMeasuringTimeMs() / CYCLE_MILLIS % size);
    }

    public static <T> T cycle(List<T> values) {
        return values.get(cycleIndex(values.size()));
    }

    public static Text ofTag(TagKey<?> tag) {
        Identifier registry = tag.registry().getValue();
        Identifier id = tag.id();
        return Text.translatableWithFallback("tag." + registry.getPath().replace('/', '.') + "." + id.getNamespace() + "." + id.getPath().replace('/', '.'), "#" + id);
    }

    public static Text ofAlternatives(List<Text> names) {
        if (names.isEmpty()) {
            return Text.literal("?");
        }
        return names.size() == 1 ? names.getFirst() : Text.translatable("task.questz.or_more", names.getFirst(), names.size() - 1);
    }

    public static <T> Text ofList(RegistryEntryList<T> list, Function<RegistryEntry<T>, Text> namer) {
        Optional<TagKey<T>> tag = list.getTagKey();
        if (tag.isPresent()) {
            return ofTag(tag.get());
        }
        if (list.size() == 0) {
            return Text.literal("?");
        }
        Text first = namer.apply(list.get(0));
        return list.size() == 1 ? first : Text.translatable("task.questz.or_more", first, list.size() - 1);
    }

    public static <T> Text ofOptionalList(Optional<RegistryEntryList<T>> list, Function<RegistryEntry<T>, Text> namer, Text any) {
        return list.map(entries -> ofList(entries, namer)).orElse(any);
    }

    public static <T> ItemStack iconOf(Optional<RegistryEntryList<T>> list, Function<T, ItemStack> iconFactory, ItemStack fallback) {
        if (list.isPresent() && list.get().size() > 0) {
            ItemStack icon = iconFactory.apply(list.get().get(cycleIndex(list.get().size())).value());
            if (!icon.isEmpty()) {
                return icon;
            }
        }
        return fallback;
    }

    public static Text keyName(RegistryEntry<?> entry, String translationPrefix) {
        return entry.getKey().map(key -> ofId(key.getValue(), translationPrefix)).orElse(Text.literal("?"));
    }

}
