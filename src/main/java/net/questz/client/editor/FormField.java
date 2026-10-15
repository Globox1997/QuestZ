package net.questz.client.editor;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import org.jetbrains.annotations.Nullable;

@Environment(EnvType.CLIENT)
public record FormField(String key, Kind kind, @Nullable RegistryKey<? extends Registry<?>> registry, String label) {

    public enum Kind {
        TEXT,
        NUMBER,
        BOOL,
        ITEM,
        ITEM_LIST,
        ID,
        ID_LIST
    }

    private static String labelOf(String key) {
        return "gui.questz.editor.field." + key;
    }

    public static FormField text(String key) {
        return new FormField(key, Kind.TEXT, null, labelOf(key));
    }

    public static FormField number(String key) {
        return new FormField(key, Kind.NUMBER, null, labelOf(key));
    }

    public static FormField bool(String key) {
        return new FormField(key, Kind.BOOL, null, labelOf(key));
    }

    public static FormField item(String key) {
        return new FormField(key, Kind.ITEM, RegistryKeys.ITEM, labelOf(key));
    }

    public static FormField itemList(String key) {
        return new FormField(key, Kind.ITEM_LIST, RegistryKeys.ITEM, labelOf("items"));
    }

    public static FormField id(String key, RegistryKey<? extends Registry<?>> registry) {
        return new FormField(key, Kind.ID, registry, labelOf(key));
    }

    public static FormField idList(String key, RegistryKey<? extends Registry<?>> registry) {
        return new FormField(key, Kind.ID_LIST, registry, labelOf(key));
    }

    public static FormField idList(String key, RegistryKey<? extends Registry<?>> registry, String label) {
        return new FormField(key, Kind.ID_LIST, registry, labelOf(label));
    }

    public boolean isList() {
        return this.kind == Kind.ITEM_LIST || this.kind == Kind.ID_LIST;
    }

    public boolean allowsTags() {
        return this.isList();
    }

}
