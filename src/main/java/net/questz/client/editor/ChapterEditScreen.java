package net.questz.client.editor;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ConfirmScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.questz.book.Chapter;
import net.questz.client.screen.QuestBookScreen;
import net.questz.network.packet.DeleteChapterPacket;
import net.questz.network.packet.EditChapterPacket;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Environment(EnvType.CLIENT)
public class ChapterEditScreen extends Screen {

    private final QuestBookScreen parent;
    private final Chapter original;
    private final boolean isNew;
    private String titleValue;
    private String iconValue;
    private String orderValue;
    private String backgroundValue;
    private TextFieldWidget iconField;
    private TextFieldWidget orderField;
    private TextFieldWidget backgroundField;
    private ButtonWidget saveButton;

    public ChapterEditScreen(QuestBookScreen parent, Chapter chapter, boolean isNew) {
        super(Text.translatable(isNew ? "gui.questz.editor.new_chapter" : "gui.questz.editor.edit_chapter"));
        this.parent = parent;
        this.original = chapter;
        this.isNew = isNew;
        this.titleValue = ClientEditor.toEditString(chapter.title());
        this.iconValue = Registries.ITEM.getId(chapter.icon().getItem()).toString();
        this.orderValue = String.valueOf(chapter.order());
        this.backgroundValue = chapter.background().map(Identifier::toString).orElse("");
    }

    @Override
    protected void init() {
        int width = Math.min(this.width - 20, 220);
        int left = (this.width - width) / 2;

        TextFieldWidget titleField = this.field(left, 30, width, "gui.questz.editor.title", this.titleValue);
        titleField.setTooltip(Tooltip.of(Text.translatable("gui.questz.editor.title.tooltip")));
        titleField.setChangedListener(value -> this.titleValue = value);
        this.iconField = this.field(left, 54, width - 20, "gui.questz.editor.icon", this.iconValue);
        this.iconField.setChangedListener(value -> {
            this.iconValue = value;
            this.updateValidity();
        });
        this.orderField = this.field(left, 78, width, "gui.questz.editor.order", this.orderValue);
        this.orderField.setTooltip(Tooltip.of(Text.translatable("gui.questz.editor.order.tooltip")));
        this.orderField.setChangedListener(value -> {
            this.orderValue = value;
            this.updateValidity();
        });
        this.backgroundField = this.field(left, 102, width, "gui.questz.editor.background", this.backgroundValue);
        this.backgroundField.setTooltip(Tooltip.of(Text.translatable("gui.questz.editor.background.tooltip")));
        this.backgroundField.setChangedListener(value -> {
            this.backgroundValue = value;
            this.updateValidity();
        });

        int buttons = this.isNew ? 2 : 3;
        int buttonWidth = 70;
        int buttonsLeft = this.width / 2 - (buttons * buttonWidth + (buttons - 1) * 4) / 2;
        this.saveButton = this.addDrawableChild(ButtonWidget.builder(Text.translatable("gui.questz.editor.save"), button -> this.save())
                .dimensions(buttonsLeft, this.height - 26, buttonWidth, 20).build());
        this.addDrawableChild(ButtonWidget.builder(ScreenTexts.CANCEL, button -> this.close())
                .dimensions(buttonsLeft + buttonWidth + 4, this.height - 26, buttonWidth, 20).build());
        if (!this.isNew) {
            this.addDrawableChild(ButtonWidget.builder(Text.translatable("gui.questz.editor.delete").formatted(Formatting.RED), button -> this.confirmDelete())
                    .dimensions(buttonsLeft + (buttonWidth + 4) * 2, this.height - 26, buttonWidth, 20).build());
        }
        this.updateValidity();
    }

    private TextFieldWidget field(int x, int y, int width, String key, String value) {
        TextFieldWidget field = new TextFieldWidget(this.textRenderer, x, y, width, 18, Text.translatable(key));
        field.setMaxLength(256);
        field.setText(value);
        field.setPlaceholder(Text.translatable(key).formatted(Formatting.DARK_GRAY));
        return this.addDrawableChild(field);
    }

    @Nullable
    private Item parseIcon() {
        Identifier id = Identifier.tryParse(this.iconValue.trim().toLowerCase(Locale.ROOT));
        return id != null && Registries.ITEM.containsId(id) && Registries.ITEM.get(id) != Items.AIR ? Registries.ITEM.get(id) : null;
    }

    @Nullable
    private Integer parseOrder() {
        try {
            return Integer.parseInt(this.orderValue.trim());
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private boolean isBackgroundValid() {
        return this.backgroundValue.isBlank() || Identifier.tryParse(this.backgroundValue.trim()) != null;
    }

    private void updateValidity() {
        boolean iconValid = this.parseIcon() != null;
        boolean orderValid = this.parseOrder() != null;
        boolean backgroundValid = this.isBackgroundValid();
        this.iconField.setEditableColor(iconValid ? 0xE0E0E0 : 0xFF5555);
        this.orderField.setEditableColor(orderValid ? 0xE0E0E0 : 0xFF5555);
        this.backgroundField.setEditableColor(backgroundValid ? 0xE0E0E0 : 0xFF5555);
        this.saveButton.active = iconValid && orderValid && backgroundValid;
    }

    private void save() {
        Item item = this.parseIcon();
        Integer order = this.parseOrder();
        if (item == null || order == null || !this.isBackgroundValid()) {
            return;
        }
        ItemStack icon = this.original.icon().isOf(item) ? this.original.icon() : new ItemStack(item);
        Optional<Identifier> background = this.backgroundValue.isBlank() ? Optional.empty() : Optional.ofNullable(Identifier.tryParse(this.backgroundValue.trim()));
        Chapter chapter = new Chapter(this.original.id(), ClientEditor.fromEditString(this.titleValue), icon, order, background, List.of());
        ClientEditor.send(new EditChapterPacket(chapter));
        QuestBookScreen.selectChapter(chapter.id());
        this.close();
    }

    private void confirmDelete() {
        if (this.client == null) {
            return;
        }
        this.client.setScreen(new ConfirmScreen(confirmed -> {
            if (confirmed) {
                ClientEditor.send(new DeleteChapterPacket(this.original.id()));
            }
            this.close();
        }, Text.translatable("gui.questz.editor.delete_chapter.title"), Text.translatable("gui.questz.editor.delete_chapter.message", this.original.quests().size())));
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 12, 0xFFFFFFFF);
        Item item = this.parseIcon();
        if (item != null) {
            int width = Math.min(this.width - 20, 220);
            int left = (this.width - width) / 2;
            context.drawItem(new ItemStack(item), left + width - 17, 55);
        }
    }

    @Override
    public void close() {
        if (this.client != null) {
            this.client.setScreen(this.parent);
        }
    }

}
