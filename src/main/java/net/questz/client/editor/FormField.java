package net.questz.client.editor;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public record FormField(String key, Kind kind) {

    public enum Kind {
        TEXT,
        NUMBER,
        BOOL,
        ITEM
    }

    public static FormField text(String key) {
        return new FormField(key, Kind.TEXT);
    }

    public static FormField number(String key) {
        return new FormField(key, Kind.NUMBER);
    }

    public static FormField bool(String key) {
        return new FormField(key, Kind.BOOL);
    }

    public static FormField item(String key) {
        return new FormField(key, Kind.ITEM);
    }

}
