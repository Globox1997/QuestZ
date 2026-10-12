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
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.CyclingButtonWidget;
import net.minecraft.client.gui.widget.EditBoxWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
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
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

@Environment(EnvType.CLIENT)
public class EntryEditScreen<T> extends Screen {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final int FORM_ROW_HEIGHT = 24;
    private static final int LABEL_WIDTH = 100;

    private final Screen parent;
    private final Codec<T> strictCodec;
    private final Codec<T> lenientCodec;
    private final Function<T, Text> titler;
    private final Function<Identifier, JsonObject> templates;
    private final Function<Identifier, List<FormField>> forms;
    private final Consumer<T> onSave;
    private final String entryId;
    private final List<Identifier> types = new ArrayList<>();
    private Identifier type;
    private String json;
    private boolean formMode = true;
    @Nullable
    private EditBoxWidget editBox;
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
        this.addDrawableChild(CyclingButtonWidget.<Identifier>builder(id -> Text.literal(id.toString()))
                .values(this.types)
                .initially(this.type)
                .build(left, 22, typeWidth, 20, Text.translatable("gui.questz.editor.type"), (button, value) -> {
                    this.type = value;
                    this.json = GSON.toJson(this.templates.apply(value));
                    this.clearAndInit();
                }));
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
            JsonElement value = object.get(field.key());
            if (field.kind() == FormField.Kind.BOOL) {
                boolean current = value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean() && value.getAsBoolean();
                this.addDrawableChild(CyclingButtonWidget.onOffBuilder(current).omitKeyText()
                        .build(x, y - 1, fieldWidth, 20, this.label(field), (button, enabled) -> this.updateField(field, String.valueOf(enabled))));
            } else {
                TextFieldWidget textField = new TextFieldWidget(this.textRenderer, x, y, fieldWidth, 18, this.label(field));
                textField.setMaxLength(1024);
                textField.setText(this.readField(field, value));
                textField.setChangedListener(text -> this.updateField(field, text));
                this.addDrawableChild(textField);
            }
            y += FORM_ROW_HEIGHT;
        }
    }

    private Text label(FormField field) {
        return Text.translatableWithFallback("gui.questz.editor.field." + field.key(), DisplayNames.humanize(field.key()));
    }

    private String readField(FormField field, @Nullable JsonElement value) {
        if (value == null || value.isJsonNull()) {
            return "";
        }
        if (field.kind() == FormField.Kind.ITEM && value.isJsonObject()) {
            JsonElement id = value.getAsJsonObject().get("id");
            return id != null && id.isJsonPrimitive() ? id.getAsString() : "";
        }
        return value.isJsonPrimitive() ? value.getAsString() : value.toString();
    }

    private void updateField(FormField field, String text) {
        JsonObject object = this.parseObject();
        if (object == null) {
            return;
        }
        String trimmed = text.trim();
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
            case ITEM -> {
                JsonElement existing = object.get(field.key());
                JsonObject item = existing != null && existing.isJsonObject() ? existing.getAsJsonObject() : new JsonObject();
                item.addProperty("id", trimmed);
                object.add(field.key(), item);
            }
            case TEXT -> {
                if (trimmed.isEmpty()) {
                    object.remove(field.key());
                } else {
                    object.addProperty(field.key(), trimmed);
                }
            }
        }
        this.json = GSON.toJson(object);
        this.validate();
    }

    private void validate() {
        if (this.saveButton == null) {
            return;
        }
        this.parsed = null;
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
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        super.renderBackground(context, mouseX, mouseY, delta);
        QuestzGui.drawPanel(context, this.getContentLeft() - 8, 2, this.getContentWidth() + 16, this.height - 4);
        QuestzGui.drawInset(context, this.getContentLeft(), this.height - 54, this.getContentWidth(), 24);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 8, 0xFFFFFFFF);
        int width = this.getContentWidth();
        int left = this.getContentLeft();
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
    }

    @Override
    public void close() {
        if (this.client != null) {
            this.client.setScreen(this.parent);
        }
    }

}
