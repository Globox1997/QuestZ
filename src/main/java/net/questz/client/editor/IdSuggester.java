package net.questz.client.editor;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Environment(EnvType.CLIENT)
public class IdSuggester {

    private static final int MAX_ROWS = 8;
    private static final int MAX_MATCHES = 200;

    private final TextFieldWidget field;
    private final List<String> candidates;
    private final boolean multiple;
    private final PopupList popup;
    private List<String> matches = List.of();
    private int tokenStart;
    private int tokenEnd;
    @Nullable
    private String lastText;
    private int lastCursor = -1;
    private boolean lastFocused;
    private int screenHeight = Integer.MAX_VALUE;

    public IdSuggester(TextRenderer textRenderer, TextFieldWidget field, List<String> candidates, boolean multiple) {
        this.field = field;
        this.candidates = candidates;
        this.multiple = multiple;
        this.popup = new PopupList(textRenderer, this::pick);
    }

    public static List<String> registryIds(@Nullable RegistryKey<? extends Registry<?>> key, boolean tags) {
        List<String> ids = new ArrayList<>();
        MinecraftClient client = MinecraftClient.getInstance();
        if (key == null || client.getNetworkHandler() == null) {
            return ids;
        }
        if (key.equals(RegistryKeys.WORLD)) {
            client.getNetworkHandler().getWorldKeys().forEach(world -> ids.add(world.getValue().toString()));
            ids.sort(String::compareTo);
            return ids;
        }
        Optional<? extends Registry<?>> registry = client.getNetworkHandler().getRegistryManager().getOptional(key);
        if (registry.isEmpty()) {
            return ids;
        }
        registry.get().getIds().stream().map(Identifier::toString).filter(id -> !id.equals("minecraft:air")).sorted().forEach(ids::add);
        if (tags) {
            registry.get().streamTags().map(tag -> "#" + tag.id()).sorted().forEach(ids::add);
        }
        return ids;
    }

    private static boolean isIdChar(char character) {
        return Identifier.isCharValid(Character.toLowerCase(character)) || character == '#';
    }

    private static String path(String id) {
        return id.substring(id.indexOf(':') + 1);
    }

    private static int rank(String candidate, String token) {
        String key = candidate.startsWith("#") ? candidate.substring(1) : candidate;
        String search = token.startsWith("#") ? token.substring(1) : token;
        if (token.startsWith("#") && !candidate.startsWith("#")) {
            return -1;
        }
        if (key.startsWith(search)) {
            return 0;
        }
        if (path(key).startsWith(search)) {
            return 1;
        }
        if (key.contains(search)) {
            return 2;
        }
        return matchesParts(path(key), search) ? 3 : -1;
    }

    private static boolean matchesParts(String path, String search) {
        int index = 0;
        for (String part : search.split("_")) {
            if (part.isEmpty()) {
                continue;
            }
            int found = path.indexOf(part, index);
            if (found < 0) {
                return false;
            }
            index = found + part.length();
        }
        return true;
    }

    private void update() {
        boolean focused = this.field.isFocused() && this.field.isVisible();
        String text = this.field.getText();
        int cursor = this.field.getCursor();
        if (focused == this.lastFocused && text.equals(this.lastText) && cursor == this.lastCursor) {
            return;
        }
        boolean textChanged = !text.equals(this.lastText);
        this.lastFocused = focused;
        this.lastText = text;
        this.lastCursor = cursor;
        if (!focused || !textChanged && !this.popup.isVisible()) {
            this.hide();
            return;
        }
        this.recompute(text, cursor);
    }

    private void recompute(String text, int cursor) {
        int start = ItemStrings.tokenStart(text, cursor, this.multiple);
        if (start < 0) {
            this.hide();
            return;
        }
        int end = cursor;
        while (end < text.length() && isIdChar(text.charAt(end))) {
            end++;
        }
        String token = text.substring(start, cursor).toLowerCase(Locale.ROOT);
        if (token.isEmpty() || token.chars().anyMatch(character -> !isIdChar((char) character))) {
            this.hide();
            return;
        }
        List<String> found = new ArrayList<>();
        List<Integer> ranks = new ArrayList<>();
        for (String candidate : this.candidates) {
            int rank = rank(candidate, token);
            if (rank >= 0) {
                found.add(candidate);
                ranks.add(rank);
            }
        }
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < found.size(); i++) {
            order.add(i);
        }
        order.sort(Comparator.<Integer>comparingInt(ranks::get).thenComparing(found::get));
        List<String> sorted = new ArrayList<>();
        for (int i = 0; i < Math.min(MAX_MATCHES, order.size()); i++) {
            sorted.add(found.get(order.get(i)));
        }
        String current = text.substring(start, end);
        if (sorted.isEmpty() || (sorted.size() == 1 && sorted.getFirst().equals(current))) {
            this.hide();
            return;
        }
        this.matches = sorted;
        this.tokenStart = start;
        this.tokenEnd = end;
        List<Text> entries = new ArrayList<>();
        for (String match : sorted) {
            entries.add(match.startsWith("#") ? Text.literal(match).formatted(Formatting.AQUA) : Text.literal(match));
        }
        int rows = Math.min(MAX_ROWS, entries.size());
        int height = rows * PopupList.ROW_HEIGHT + 2;
        int below = this.field.getY() + this.field.getHeight() + 1;
        int above = this.field.getY() - height - 1;
        this.popup.show(this.field.getX(), below + height > this.screenHeight && above >= 0 ? above : below, this.field.getWidth(), MAX_ROWS, entries, 0);
    }

    private void hide() {
        this.popup.hide();
        this.field.setSuggestion(null);
    }

    private void pick(int index) {
        if (index < 0 || index >= this.matches.size()) {
            return;
        }
        String text = this.field.getText();
        int start = Math.min(this.tokenStart, text.length());
        int end = Math.min(this.tokenEnd, text.length());
        String replaced = text.substring(0, start) + this.matches.get(index) + text.substring(end);
        this.field.setText(replaced);
        this.field.setCursor(start + this.matches.get(index).length(), false);
        this.lastText = this.field.getText();
        this.lastCursor = this.field.getCursor();
        this.hide();
    }

    private void updateInlineSuggestion() {
        int selected = this.popup.getSelected();
        String text = this.field.getText();
        if (!this.popup.isVisible() || selected < 0 || this.field.getCursor() != text.length() || this.tokenEnd != text.length()) {
            this.field.setSuggestion(null);
            return;
        }
        String token = text.substring(Math.min(this.tokenStart, text.length())).toLowerCase(Locale.ROOT);
        String match = this.matches.get(selected);
        this.field.setSuggestion(match.startsWith(token) ? match.substring(token.length()) : null);
    }

    public void hidePopup() {
        this.hide();
    }

    public void render(DrawContext context, int mouseX, int mouseY, int screenHeight) {
        this.screenHeight = screenHeight;
        this.update();
        this.updateInlineSuggestion();
        this.popup.render(context, mouseX, mouseY);
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return this.popup.mouseClicked(mouseX, mouseY, button);
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double verticalAmount) {
        return this.popup.mouseScrolled(mouseX, mouseY, verticalAmount);
    }

    public boolean keyPressed(int keyCode) {
        if (!this.field.isFocused()) {
            return false;
        }
        boolean handled = this.popup.keyPressed(keyCode);
        if (handled && !this.popup.isVisible()) {
            this.field.setSuggestion(null);
        }
        return handled;
    }

}
