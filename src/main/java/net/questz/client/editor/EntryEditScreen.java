package net.questz.client.editor;

import com.google.gson.*;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.CyclingButtonWidget;
import net.minecraft.client.gui.widget.EditBoxWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryOps;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.questz.client.render.DisplayNames;
import net.questz.client.screen.QuestzGui;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

@Environment(EnvType.CLIENT)
public class EntryEditScreen<T> extends Screen {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final int FORM_ROW_HEIGHT = 24;
    private static final int LABEL_WIDTH = 100;
    private static final int COLOR_VALID = 0xE0E0E0;
    private static final int COLOR_INVALID = 0xFF5555;

    private final Screen parent;
    private final Codec<T> strictCodec;
    private final Codec<T> lenientCodec;
    private final Function<T, Text> titler;
    private final Function<Identifier, JsonObject> templates;
    private final Function<Identifier, List<FormField>> forms;
    private final Consumer<T> onSave;
    private final String entryId;
    private final List<Identifier> types = new ArrayList<>();
    private final Map<String, Text> formErrors = new LinkedHashMap<>();
    private final List<IdSuggester> suggesters = new ArrayList<>();
    private Identifier type;
    private String json;
    private boolean formMode = true;
    @Nullable
    private EditBoxWidget editBox;
    private ButtonWidget typeButton;
    private PopupList typePopup;
    private ButtonWidget saveButton;
    private Text status = Text.empty();
    @Nullable
    private T parsed;

    public EntryEditScreen(Screen parent, Text title, Registry<?> registry, Codec<T> strictCodec, Codec<T> lenientCodec, Function<T, Text> titler, Function<T, Identifier> typeGetter,
                           Function<Identifier, JsonObject> templates, Function<Identifier, List<FormField>> forms, String entryId, @Nullable T existing, Consumer<T> onSave) {
        super(title);
        this.parent = parent;
        this.strictCodec = strictCodec;
        this.lenientCodec = lenientCodec;
        this.titler = titler;
        this.templates = templates;
        this.forms = forms;
        this.onSave = onSave;
        this.entryId = entryId;
        registry.getIds().stream().filter(id -> !id.getPath().equals("unresolved")).sorted().forEach(this.types::add);
        if (existing != null) {
            this.type = typeGetter.apply(existing);
            if (!this.types.contains(this.type)) {
                this.types.add(this.type);
            }
            this.json = this.encode(existing);
        } else {
            this.type = this.types.isEmpty() ? Identifier.of("questz", "unknown") : this.types.getFirst();
            this.json = GSON.toJson(this.templates.apply(this.type));
        }
    }

    private static RegistryOps<JsonElement> ops() {
        MinecraftClient client = MinecraftClient.getInstance();
        DynamicRegistryManager registries = client.getNetworkHandler() != null ? client.getNetworkHandler().getRegistryManager() : DynamicRegistryManager.EMPTY;
        return RegistryOps.of(JsonOps.INSTANCE, registries);
    }

    private static Text typeName(Identifier type) {
        Text name = DisplayNames.ofId(type, "gui.questz.type");
        return type.getNamespace().equals("questz") ? name : Text.empty().append(name).append(Text.literal(" (" + type.getNamespace() + ")").formatted(Formatting.GRAY));
    }

    private String encode(T entry) {
        JsonElement element = this.lenientCodec.encodeStart(ops(), entry).result().orElse(new JsonObject());
        if (element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject().deepCopy();
            object.remove("id");
            object.remove("type");
            return GSON.toJson(object);
        }
        return "{}";
    }

    @Nullable
    private JsonObject parseObject() {
        try {
            JsonElement element = JsonParser.parseString(this.json.isBlank() ? "{}" : this.json);
            return element.isJsonObject() ? element.getAsJsonObject() : null;
        } catch (JsonParseException exception) {
            return null;
        }
    }

    @Nullable
    private List<FormField> getForm() {
        return this.forms.apply(this.type);
    }

    private boolean isShowingForm() {
        return this.formMode && this.getForm() != null && this.parseObject() != null;
    }

    private int getContentWidth() {
        return Math.min(this.width - 20, 360);
    }

    private int getContentLeft() {
        return (this.width - this.getContentWidth()) / 2;
    }

    @Override
    protected void init() {
        int width = this.getContentWidth();
        int left = this.getContentLeft();
        boolean hasForm = this.getForm() != null;
        int typeWidth = hasForm ? width - 64 : width;
        this.suggesters.clear();
        this.formErrors.clear();
        this.typePopup = new PopupList(this.textRenderer, this::selectType);
        this.typeButton = this.addDrawableChild(ButtonWidget.builder(Text.translatable("gui.questz.editor.type_value", typeName(this.type)), button -> this.toggleTypePopup())
                .dimensions(left, 22, typeWidth, 20).build());
        if (hasForm) {
            this.addDrawableChild(ButtonWidget.builder(Text.translatable(this.formMode ? "gui.questz.editor.mode.json" : "gui.questz.editor.mode.form"), button -> {
                this.formMode = !this.formMode;
                this.clearAndInit();
            }).dimensions(left + width - 60, 22, 60, 20).build());
        }

        this.editBox = null;
        if (this.isShowingForm()) {
            this.initForm(left, width);
        } else {
            this.editBox = new EditBoxWidget(this.textRenderer, left, 46, width, Math.max(40, this.height - 46 - 58), Text.translatable("gui.questz.editor.json"), Text.empty());
            this.editBox.setMaxLength(16000);
            this.editBox.setText(this.json);
            this.editBox.setChangeListener(text -> {
                this.json = text;
                this.validate();
            });
            this.addDrawableChild(this.editBox);
        }
        this.saveButton = this.addDrawableChild(ButtonWidget.builder(ScreenTexts.DONE, button -> this.save()).dimensions(this.width / 2 - 102, this.height - 26, 100, 20).build());
        this.addDrawableChild(ButtonWidget.builder(ScreenTexts.CANCEL, button -> this.close()).dimensions(this.width / 2 + 2, this.height - 26, 100, 20).build());
        this.validate();
    }

    private void toggleTypePopup() {
        if (this.typePopup.isVisible()) {
            this.typePopup.hide();
            return;
        }
        this.suggesters.forEach(IdSuggester::hidePopup);
        List<Text> entries = new ArrayList<>();
        this.types.forEach(id -> entries.add(typeName(id)));
        int y = this.typeButton.getY() + this.typeButton.getHeight() + 1;
        int maxRows = Math.max(3, (this.height - y - 32) / PopupList.ROW_HEIGHT);
        this.typePopup.show(this.typeButton.getX(), y, this.typeButton.getWidth(), maxRows, entries, this.types.indexOf(this.type));
    }

    private void selectType(int index) {
        Identifier value = this.types.get(index);
        if (!value.equals(this.type)) {
            this.type = value;
            this.json = GSON.toJson(this.templates.apply(value));
        }
        this.clearAndInit();
    }

    private void initForm(int left, int width) {
        JsonObject object = this.parseObject();
        List<FormField> form = this.getForm();
        if (object == null || form == null) {
            return;
        }
        int y = 50;
        for (FormField field : form) {
            int x = left + LABEL_WIDTH;
            int fieldWidth = width - LABEL_WIDTH;
            if (field.kind() == FormField.Kind.BOOL) {
                JsonElement value = object.get(field.key());
                boolean current = value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean() && value.getAsBoolean();
                this.addDrawableChild(CyclingButtonWidget.onOffBuilder(current).omitKeyText()
                        .build(x, y - 1, fieldWidth, 20, this.label(field), (button, enabled) -> this.updateField(field, null, String.valueOf(enabled))));
            } else {
                TextFieldWidget textField = new TextFieldWidget(this.textRenderer, x, y, fieldWidth, 18, this.label(field));
                textField.setMaxLength(2048);
                textField.setText(this.readField(field, object));
                textField.setChangedListener(text -> this.updateField(field, textField, text));
                Text tooltip = this.fieldTooltip(field);
                if (tooltip != null) {
                    textField.setTooltip(Tooltip.of(tooltip));
                }
                this.addDrawableChild(textField);
                if (field.registry() != null) {
                    this.suggesters.add(new IdSuggester(this.textRenderer, textField, IdSuggester.registryIds(field.registry(), field.allowsTags()), field.isList()));
                }
            }
            y += FORM_ROW_HEIGHT;
        }
    }

    private Text label(FormField field) {
        return Text.translatableWithFallback(field.label(), DisplayNames.humanize(field.key()));
    }

    @Nullable
    private Text fieldTooltip(FormField field) {
        return switch (field.kind()) {
            case ITEM -> Text.translatable("gui.questz.editor.field.item.tooltip");
            case ITEM_LIST -> Text.translatable("gui.questz.editor.field.items.tooltip");
            case ID_LIST -> Text.translatable("gui.questz.editor.field.list.tooltip");
            default -> null;
        };
    }

    private static List<JsonElement> elements(@Nullable JsonElement value) {
        List<JsonElement> elements = new ArrayList<>();
        if (value == null || value.isJsonNull()) {
            return elements;
        }
        if (value.isJsonArray()) {
            value.getAsJsonArray().forEach(elements::add);
        } else {
            elements.add(value);
        }
        return elements;
    }

    private static String plain(JsonElement element) {
        return element.isJsonPrimitive() ? element.getAsString() : element.toString();
    }

    private String readStack(JsonElement element) {
        DataResult<ItemStack> result = ItemStack.UNCOUNTED_CODEC.parse(ops(), element);
        if (result.isSuccess()) {
            return ItemStrings.format(result.getOrThrow());
        }
        if (element.isJsonObject() && element.getAsJsonObject().has("id")) {
            return plain(element.getAsJsonObject().get("id"));
        }
        return plain(element);
    }

    private String readField(FormField field, JsonObject object) {
        JsonElement value = object.get(field.key());
        switch (field.kind()) {
            case ITEM -> {
                return value == null || value.isJsonNull() ? "" : this.readStack(value);
            }
            case ITEM_LIST -> {
                List<String> parts = new ArrayList<>();
                elements(value).forEach(element -> parts.add(this.readStack(element)));
                elements(object.get("tag")).forEach(element -> parts.add(plain(element)));
                return String.join(", ", parts);
            }
            case ID_LIST -> {
                List<String> parts = new ArrayList<>();
                elements(value).forEach(element -> parts.add(plain(element)));
                return String.join(", ", parts);
            }
            default -> {
                return value == null || value.isJsonNull() ? "" : plain(value);
            }
        }
    }

    private static void setList(JsonObject object, String key, JsonArray values) {
        if (values.isEmpty()) {
            object.remove(key);
        } else if (values.size() == 1) {
            object.add(key, values.get(0));
        } else {
            object.add(key, values);
        }
    }

    @Nullable
    private Text writeStacks(JsonObject object, String key, String text, boolean list) {
        JsonArray items = new JsonArray();
        JsonArray tags = new JsonArray();
        for (String part : list ? ItemStrings.split(text) : List.of(text)) {
            if (list && part.startsWith("#")) {
                tags.add(part);
                continue;
            }
            DataResult<ItemStack> result = ItemStrings.parse(part);
            if (result.isError()) {
                return Text.translatable("gui.questz.editor.invalid_item", result.error().map(DataResult.Error::message).orElse(part));
            }
            items.add(ItemStack.UNCOUNTED_CODEC.encodeStart(ops(), result.getOrThrow()).result().orElse(new JsonObject()));
        }
        if (list) {
            setList(object, key, items);
            setList(object, "tag", tags);
        } else {
            object.add(key, items.get(0));
        }
        return null;
    }

    private void updateField(FormField field, @Nullable TextFieldWidget widget, String text) {
        JsonObject object = this.parseObject();
        if (object == null) {
            return;
        }
        String trimmed = text.trim();
        Text error = null;
        switch (field.kind()) {
            case BOOL -> object.addProperty(field.key(), Boolean.parseBoolean(trimmed));
            case NUMBER -> {
                if (trimmed.isEmpty()) {
                    object.remove(field.key());
                } else {
                    try {
                        object.addProperty(field.key(), Long.parseLong(trimmed));
                    } catch (NumberFormatException exception) {
                        object.addProperty(field.key(), trimmed);
                    }
                }
            }
            case ITEM, ITEM_LIST -> {
                if (trimmed.isEmpty()) {
                    object.remove(field.key());
                    if (field.kind() == FormField.Kind.ITEM_LIST) {
                        object.remove("tag");
                    }
                } else {
                    error = this.writeStacks(object, field.key(), trimmed, field.kind() == FormField.Kind.ITEM_LIST);
                }
            }
            case ID_LIST -> {
                JsonArray values = new JsonArray();
                ItemStrings.split(trimmed).forEach(values::add);
                setList(object, field.key(), values);
            }
            case TEXT, ID -> {
                if (trimmed.isEmpty()) {
                    object.remove(field.key());
                } else {
                    object.addProperty(field.key(), trimmed);
                }
            }
        }
        if (widget != null) {
            widget.setEditableColor(error == null ? COLOR_VALID : COLOR_INVALID);
        }
        if (error != null) {
            this.formErrors.put(field.key(), error);
        } else {
            this.formErrors.remove(field.key());
            this.json = GSON.toJson(object);
        }
        this.validate();
    }

    private void validate() {
        if (this.saveButton == null) {
            return;
        }
        this.parsed = null;
        if (!this.formErrors.isEmpty() && this.isShowingForm()) {
            this.setInvalid(this.formErrors.values().iterator().next());
            return;
        }
        JsonElement element;
        try {
            element = JsonParser.parseString(this.json.isBlank() ? "{}" : this.json);
        } catch (JsonParseException exception) {
            this.setInvalid(Text.translatable("gui.questz.editor.invalid_json", String.valueOf(exception.getMessage())));
            return;
        }
        if (!element.isJsonObject()) {
            this.setInvalid(Text.translatable("gui.questz.editor.not_object"));
            return;
        }
        JsonObject object = element.getAsJsonObject().deepCopy();
        object.addProperty("id", this.entryId);
        object.addProperty("type", this.type.toString());

        DataResult<T> strict = this.strictCodec.parse(ops(), object);
        if (strict.isSuccess()) {
            this.parsed = strict.getOrThrow();
            this.status = Text.literal("✔ ").append(this.titler.apply(this.parsed)).formatted(Formatting.GREEN);
        } else {
            this.parsed = this.lenientCodec.parse(ops(), object).result().orElse(null);
            String error = strict.error().map(DataResult.Error::message).orElse("");
            this.status = Text.translatable("gui.questz.editor.server_check", error).formatted(Formatting.GOLD);
        }
        this.saveButton.active = this.parsed != null;
    }

    private void setInvalid(Text message) {
        this.status = message.copy().formatted(Formatting.RED);
        this.saveButton.active = false;
    }

    private void save() {
        if (this.parsed != null) {
            this.onSave.accept(this.parsed);
            this.close();
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (this.typePopup.isVisible()) {
            if (!this.typePopup.mouseClicked(mouseX, mouseY, button)) {
                this.typePopup.hide();
            }
            return true;
        }
        for (IdSuggester suggester : this.suggesters) {
            if (suggester.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (this.typePopup.mouseScrolled(mouseX, mouseY, verticalAmount)) {
            return true;
        }
        for (IdSuggester suggester : this.suggesters) {
            if (suggester.mouseScrolled(mouseX, mouseY, verticalAmount)) {
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.typePopup.keyPressed(keyCode)) {
            return true;
        }
        for (IdSuggester suggester : this.suggesters) {
            if (suggester.keyPressed(keyCode)) {
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        super.renderBackground(context, mouseX, mouseY, delta);
        QuestzGui.drawPanel(context, this.getContentLeft() - 8, 2, this.getContentWidth() + 16, this.height - 4);
        QuestzGui.drawInset(context, this.getContentLeft(), this.height - 54, this.getContentWidth(), 24);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        boolean popupOpen = this.typePopup.isVisible();
        super.render(context, popupOpen ? -1 : mouseX, popupOpen ? -1 : mouseY, delta);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 8, 0xFFFFFFFF);
        int width = this.getContentWidth();
        int left = this.getContentLeft();
        QuestzGui.drawIcon(context, popupOpen ? QuestzGui.ARROW_UP : QuestzGui.ARROW_DOWN, this.typeButton.getX() + this.typeButton.getWidth() - 16, this.typeButton.getY() + 4, 12,
                this.typeButton.isHovered() || popupOpen);
        if (this.isShowingForm()) {
            List<FormField> form = this.getForm();
            if (form != null && form.isEmpty()) {
                context.drawText(this.textRenderer, Text.translatable("gui.questz.editor.no_settings"), left, 55, QuestzGui.COLOR_LABEL, false);
            } else if (form != null) {
                for (int i = 0; i < form.size(); i++) {
                    context.drawText(this.textRenderer, this.label(form.get(i)), left, 55 + i * FORM_ROW_HEIGHT, QuestzGui.COLOR_LABEL, false);
                }
            }
        }
        List<OrderedText> lines = this.textRenderer.wrapLines(this.status, width - 8);
        for (int i = 0; i < Math.min(2, lines.size()); i++) {
            context.drawText(this.textRenderer, lines.get(i), left + 4, this.height - 51 + i * 10, 0xFFFFFFFF, true);
        }
        if (popupOpen) {
            this.typePopup.render(context, mouseX, mouseY);
        } else {
            for (IdSuggester suggester : this.suggesters) {
                suggester.render(context, mouseX, mouseY, this.height);
            }
        }
    }

    @Override
    public void close() {
        if (this.client != null) {
            this.client.setScreen(this.parent);
        }
    }

}
