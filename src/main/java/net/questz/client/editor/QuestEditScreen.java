package net.questz.client.editor;

import com.mojang.serialization.DataResult;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.CyclingButtonWidget;
import net.minecraft.client.gui.widget.EditBoxWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.StringVisitable;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Language;
import net.minecraft.util.math.MathHelper;
import net.questz.api.QuestzRegistries;
import net.questz.book.DependencyMode;
import net.questz.book.Quest;
import net.questz.book.QuestBook;
import net.questz.book.QuestShape;
import net.questz.book.QuestVisibility;
import net.questz.client.render.DisplayNames;
import net.questz.client.render.RewardRenderers;
import net.questz.client.render.TaskRenderers;
import net.questz.client.screen.QuestTexts;
import net.questz.client.screen.QuestzGui;
import net.questz.network.packet.EditQuestPacket;
import net.questz.reward.Reward;
import net.questz.task.Task;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Environment(EnvType.CLIENT)
public class QuestEditScreen extends Screen {

    private static final int ROW_HEIGHT = 16;

    private enum RowKind {
        HEADER_TASKS,
        TASK,
        ADD_TASK,
        HEADER_REWARDS,
        REWARD,
        ADD_REWARD
    }

    private enum RowButton {
        NONE,
        UP,
        DOWN,
        DELETE
    }

    private record Row(RowKind kind, int index) {
    }

    private final Screen parent;
    private final String chapterId;
    private final Quest original;
    private String titleValue;
    private String iconValue;
    private String descriptionValue;
    private String sizeValue;
    private QuestShape shape;
    private QuestVisibility visibility;
    private DependencyMode dependencyMode;
    private DependencyMode taskMode;
    private boolean autoClaim;
    private boolean repeatable;
    private final List<Task> tasks;
    private final List<Reward> rewards;
    private double listScroll;
    private boolean draggingScrollbar;
    private double dragOffset;
    private TextFieldWidget iconField;
    @Nullable
    private IdSuggester iconSuggester;
    private TextFieldWidget sizeField;
    private ButtonWidget saveButton;

    public QuestEditScreen(Screen parent, String chapterId, Quest quest) {
        super(Text.translatable("gui.questz.editor.edit_quest"));
        this.parent = parent;
        this.chapterId = chapterId;
        this.original = quest;
        this.titleValue = ClientEditor.toEditString(quest.title());
        this.iconValue = ItemStrings.formatList(quest.icons());
        this.descriptionValue = ClientEditor.toEditString(quest.description());
        this.sizeValue = String.valueOf(quest.size());
        this.shape = quest.shape();
        this.visibility = quest.visibility();
        this.dependencyMode = quest.dependencyMode();
        this.taskMode = quest.taskMode();
        this.autoClaim = quest.autoClaim();
        this.repeatable = quest.repeatable();
        this.tasks = new ArrayList<>(quest.tasks());
        this.rewards = new ArrayList<>(quest.rewards());
    }

    private int getTotalWidth() {
        return Math.min(this.width - 20, 440);
    }

    private int getLeft() {
        return (this.width - this.getTotalWidth()) / 2;
    }

    private int getColumnWidth() {
        return (this.getTotalWidth() - 10) / 2;
    }

    private int getListX() {
        return this.getLeft() + this.getColumnWidth() + 10;
    }

    private int getListWidth() {
        return this.getColumnWidth() - QuestzGui.SCROLLBAR_WIDTH - 1;
    }

    private int getScrollbarX() {
        return this.getListX() + this.getListWidth() + 1;
    }

    private int getListTop() {
        return 22;
    }

    private int getListBottom() {
        return this.height - 32;
    }

    @Override
    protected void init() {
        int left = this.getLeft();
        int column = this.getColumnWidth();
        int half = column / 2 - 1;

        TextFieldWidget titleField = new TextFieldWidget(this.textRenderer, left, 22, column, 18, Text.translatable("gui.questz.editor.title"));
        titleField.setMaxLength(256);
        titleField.setText(this.titleValue);
        titleField.setPlaceholder(Text.translatable("gui.questz.editor.title").formatted(Formatting.DARK_GRAY));
        titleField.setTooltip(Tooltip.of(Text.translatable("gui.questz.editor.title.tooltip")));
        titleField.setChangedListener(value -> this.titleValue = value);
        this.addDrawableChild(titleField);

        this.iconField = new TextFieldWidget(this.textRenderer, left, 44, column - 20, 18, Text.translatable("gui.questz.editor.icon"));
        this.iconField.setMaxLength(4096);
        this.iconField.setText(this.iconValue);
        this.iconField.setPlaceholder(Text.translatable("gui.questz.editor.icon_auto").formatted(Formatting.DARK_GRAY));
        this.iconField.setTooltip(Tooltip.of(Text.translatable("gui.questz.editor.icon.tooltip")));
        this.iconField.setChangedListener(value -> {
            this.iconValue = value;
            this.updateValidity();
        });
        this.addDrawableChild(this.iconField);
        this.iconSuggester = new IdSuggester(this.textRenderer, this.iconField, IdSuggester.registryIds(RegistryKeys.ITEM, false), true);

        EditBoxWidget descriptionBox = new EditBoxWidget(this.textRenderer, left, 66, column, 52, Text.translatable("gui.questz.editor.description").formatted(Formatting.DARK_GRAY), Text.empty());
        descriptionBox.setText(this.descriptionValue);
        descriptionBox.setChangeListener(value -> this.descriptionValue = value);
        this.addDrawableChild(descriptionBox);

        this.addDrawableChild(CyclingButtonWidget.<QuestShape>builder(value -> Text.translatable("gui.questz.editor.shape." + value.asString()))
                .values(QuestShape.values()).initially(this.shape)
                .build(left, 122, half, 20, Text.translatable("gui.questz.editor.shape"), (button, value) -> this.shape = value));
        this.addDrawableChild(CyclingButtonWidget.<QuestVisibility>builder(value -> Text.translatable("gui.questz.editor.visibility." + value.asString()))
                .values(QuestVisibility.values()).initially(this.visibility).omitKeyText()
                .tooltip(value -> Tooltip.of(Text.translatable("gui.questz.editor.visibility.tooltip")))
                .build(left + half + 2, 122, half, 20, Text.translatable("gui.questz.editor.visibility"), (button, value) -> this.visibility = value));
        this.addDrawableChild(CyclingButtonWidget.<DependencyMode>builder(value -> Text.translatable("gui.questz.editor.dependency_mode." + value.asString()))
                .values(DependencyMode.values()).initially(this.dependencyMode)
                .build(left, 144, half, 20, Text.translatable("gui.questz.editor.dependency_mode"), (button, value) -> this.dependencyMode = value));
        this.addDrawableChild(CyclingButtonWidget.onOffBuilder(this.autoClaim)
                .build(left + half + 2, 144, half, 20, Text.translatable("gui.questz.editor.auto_claim"), (button, value) -> this.autoClaim = value));

        this.sizeField = new TextFieldWidget(this.textRenderer, left, 168, half, 18, Text.translatable("gui.questz.editor.size"));
        this.sizeField.setPlaceholder(Text.translatable("gui.questz.editor.size").formatted(Formatting.DARK_GRAY));
        this.sizeField.setText(this.sizeValue);
        this.sizeField.setTooltip(Tooltip.of(Text.translatable("gui.questz.editor.size.tooltip")));
        this.sizeField.setChangedListener(value -> {
            this.sizeValue = value;
            this.updateValidity();
        });
        this.addDrawableChild(this.sizeField);
        this.addDrawableChild(CyclingButtonWidget.onOffBuilder(this.repeatable)
                .tooltip(value -> Tooltip.of(Text.translatable("gui.questz.editor.repeatable.tooltip")))
                .build(left + half + 2, 167, half, 20, Text.translatable("gui.questz.editor.repeatable"), (button, value) -> this.repeatable = value));
        this.addDrawableChild(CyclingButtonWidget.<DependencyMode>builder(value -> Text.translatable("gui.questz.editor.task_mode." + value.asString()))
                .values(DependencyMode.values()).initially(this.taskMode)
                .tooltip(value -> Tooltip.of(Text.translatable("gui.questz.editor.task_mode.tooltip")))
                .build(left, 190, half, 20, Text.translatable("gui.questz.editor.task_mode"), (button, value) -> this.taskMode = value));

        this.saveButton = this.addDrawableChild(ButtonWidget.builder(Text.translatable("gui.questz.editor.save"), button -> this.save())
                .dimensions(this.width / 2 - 102, this.height - 26, 100, 20).build());
        this.addDrawableChild(ButtonWidget.builder(ScreenTexts.CANCEL, button -> this.close())
                .dimensions(this.width / 2 + 2, this.height - 26, 100, 20).build());
        this.updateValidity();
    }

    private DataResult<List<ItemStack>> parseIcons() {
        return ItemStrings.parseList(this.iconValue);
    }

    @Nullable
    private Float parseSize() {
        try {
            float size = Float.parseFloat(this.sizeValue.trim());
            return size >= 0.25F && size <= 4.0F ? size : null;
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private void updateValidity() {
        DataResult<List<ItemStack>> icons = this.parseIcons();
        boolean iconValid = icons.isSuccess();
        this.iconField.setTooltip(Tooltip.of(iconValid ? Text.translatable("gui.questz.editor.icon.tooltip")
                : Text.translatable("gui.questz.editor.invalid_item", icons.error().map(DataResult.Error::message).orElse("")).formatted(Formatting.RED)));
        boolean sizeValid = this.parseSize() != null;
        this.iconField.setEditableColor(iconValid ? 0xE0E0E0 : 0xFF5555);
        this.sizeField.setEditableColor(sizeValid ? 0xE0E0E0 : 0xFF5555);
        this.saveButton.active = iconValid && sizeValid;
    }

    private void save() {
        DataResult<List<ItemStack>> icons = this.parseIcons();
        Float size = this.parseSize();
        if (icons.isError() || size == null) {
            return;
        }
        Quest quest = new Quest(this.original.id(), ClientEditor.fromEditString(this.titleValue), ClientEditor.fromEditLines(this.descriptionValue), icons.getOrThrow(),
                this.original.x(), this.original.y(), size, this.shape, this.original.dependencies(), this.dependencyMode, this.visibility, List.copyOf(this.tasks), this.taskMode,
                List.copyOf(this.rewards), this.repeatable, this.autoClaim);
        ClientEditor.send(new EditQuestPacket(this.chapterId, quest));
        this.close();
    }

    private List<Row> getRows() {
        List<Row> rows = new ArrayList<>();
        rows.add(new Row(RowKind.HEADER_TASKS, -1));
        for (int i = 0; i < this.tasks.size(); i++) {
            rows.add(new Row(RowKind.TASK, i));
        }
        rows.add(new Row(RowKind.ADD_TASK, -1));
        rows.add(new Row(RowKind.HEADER_REWARDS, -1));
        for (int i = 0; i < this.rewards.size(); i++) {
            rows.add(new Row(RowKind.REWARD, i));
        }
        rows.add(new Row(RowKind.ADD_REWARD, -1));
        return rows;
    }

    private double getMaxScroll() {
        return Math.max(0, this.getRows().size() * ROW_HEIGHT - (this.getListBottom() - this.getListTop() - 4));
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        super.renderBackground(context, mouseX, mouseY, delta);
        QuestzGui.drawPanel(context, this.getLeft() - 8, 2, this.getTotalWidth() + 16, this.height - 4);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 8, 0xFFFFFFFF);
        List<ItemStack> icons = this.parseIcons().result().orElse(List.of());
        ItemStack preview = !icons.isEmpty() ? DisplayNames.cycle(icons) : this.tasks.isEmpty() ? ItemStack.EMPTY : TaskRenderers.getIcon(this.tasks.getFirst());
        QuestTexts.drawIcon(context, preview, this.getLeft() + this.getColumnWidth() - 17, 45);
        this.renderList(context, mouseX, mouseY);
        if (this.iconSuggester != null) {
            this.iconSuggester.render(context, mouseX, mouseY, this.height);
        }
    }

    private void renderList(DrawContext context, int mouseX, int mouseY) {
        int top = this.getListTop();
        int bottom = this.getListBottom();
        QuestzGui.drawInset(context, this.getListX(), top, this.getListWidth(), bottom - top);
        QuestzGui.drawScrollbar(context, this.getScrollbarX(), top, bottom - top, this.listScroll, this.getMaxScroll());
        int x = this.getListX() + 2;
        int width = this.getListWidth() - 4;
        context.enableScissor(this.getListX() + 1, top + 1, this.getListX() + this.getListWidth() - 1, bottom - 1);
        int y = top + 2 - (int) this.listScroll;
        for (Row row : this.getRows()) {
            boolean hovered = mouseX >= x && mouseX < x + width && mouseY >= Math.max(y, top) && mouseY < Math.min(y + ROW_HEIGHT, bottom);
            switch (row.kind()) {
                case HEADER_TASKS -> context.drawText(this.textRenderer, Text.translatable("gui.questz.tasks"), x + 3, y + 4, 0xFFFFAA00, true);
                case HEADER_REWARDS -> context.drawText(this.textRenderer, Text.translatable("gui.questz.rewards"), x + 3, y + 4, 0xFFFFAA00, true);
                case ADD_TASK, ADD_REWARD -> {
                    if (hovered) {
                        context.fill(x, y, x + width, y + ROW_HEIGHT, 0x30FFFFFF);
                    }
                    Text label = Text.translatable(row.kind() == RowKind.ADD_TASK ? "gui.questz.editor.add_task" : "gui.questz.editor.add_reward");
                    context.drawText(this.textRenderer, label, x + 5, y + 4, 0xFF55FF55, true);
                }
                case TASK, REWARD -> {
                    if (hovered) {
                        context.fill(x, y, x + width, y + ROW_HEIGHT, 0x30FFFFFF);
                    }
                    boolean task = row.kind() == RowKind.TASK;
                    ItemStack icon = task ? TaskRenderers.getIcon(this.tasks.get(row.index())) : RewardRenderers.getIcon(this.rewards.get(row.index()));
                    Text title = task ? TaskRenderers.getTitle(this.tasks.get(row.index())) : RewardRenderers.getTitle(this.rewards.get(row.index()));
                    context.getMatrices().push();
                    context.getMatrices().translate(x + 3, y + 2, 0.0F);
                    context.getMatrices().scale(0.75F, 0.75F, 1.0F);
                    context.drawItemWithoutEntity(icon, 0, 0);
                    context.getMatrices().pop();
                    int count = task ? this.tasks.size() : this.rewards.size();
                    StringVisitable trimmed = this.textRenderer.trimToWidth(title, width - (hovered && count > 1 ? 58 : 32));
                    context.drawText(this.textRenderer, Language.getInstance().reorder(trimmed), x + 18, y + 4, 0xFFFFFFFF, true);
                    RowButton rowButton = hovered ? this.getRowButton(row, mouseX, x, width) : RowButton.NONE;
                    if (hovered && row.index() > 0) {
                        QuestzGui.drawIcon(context, QuestzGui.ARROW_UP, x + width - 36, y + 2, 12, rowButton == RowButton.UP);
                    }
                    if (hovered && row.index() < count - 1) {
                        QuestzGui.drawIcon(context, QuestzGui.ARROW_DOWN, x + width - 24, y + 2, 12, rowButton == RowButton.DOWN);
                    }
                    QuestzGui.drawIcon(context, QuestzGui.CLOSE, x + width - 12, y + 2, 12, rowButton == RowButton.DELETE);
                }
            }
            y += ROW_HEIGHT;
        }
        context.disableScissor();
    }

    private RowButton getRowButton(Row row, double mouseX, int x, int width) {
        int count = row.kind() == RowKind.TASK ? this.tasks.size() : this.rewards.size();
        if (mouseX >= x + width - 12) {
            return RowButton.DELETE;
        }
        if (mouseX >= x + width - 24 && mouseX < x + width - 12 && row.index() < count - 1) {
            return RowButton.DOWN;
        }
        if (mouseX >= x + width - 36 && mouseX < x + width - 24 && row.index() > 0) {
            return RowButton.UP;
        }
        return RowButton.NONE;
    }

    private static <E> void handleRowButton(List<E> list, int index, RowButton rowButton) {
        switch (rowButton) {
            case DELETE -> list.remove(index);
            case UP -> Collections.swap(list, index, index - 1);
            case DOWN -> Collections.swap(list, index, index + 1);
            default -> {
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (this.iconSuggester != null && this.iconSuggester.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        int top = this.getListTop();
        int trackHeight = this.getListBottom() - top;
        if (button == 0 && this.getMaxScroll() > 0 && mouseX >= this.getScrollbarX() && mouseX < this.getScrollbarX() + QuestzGui.SCROLLBAR_WIDTH && mouseY >= top
                && mouseY < this.getListBottom()) {
            int handleY = QuestzGui.handleY(top, trackHeight, this.listScroll, this.getMaxScroll());
            int handleHeight = QuestzGui.handleHeight(trackHeight);
            this.dragOffset = mouseY >= handleY && mouseY < handleY + handleHeight ? mouseY - handleY : handleHeight / 2.0D;
            this.listScroll = QuestzGui.scrollForHandle(top, trackHeight, mouseY - this.dragOffset, this.getMaxScroll());
            this.draggingScrollbar = true;
            return true;
        }
        int x = this.getListX() + 2;
        int width = this.getListWidth() - 4;
        if (button != 0 || mouseX < x || mouseX >= x + width || mouseY < top + 2 || mouseY >= this.getListBottom() - 2) {
            return false;
        }
        int index = (int) ((mouseY - top - 2 + this.listScroll) / ROW_HEIGHT);
        List<Row> rows = this.getRows();
        if (index < 0 || index >= rows.size()) {
            return false;
        }
        Row row = rows.get(index);
        RowButton rowButton = row.kind() == RowKind.TASK || row.kind() == RowKind.REWARD ? this.getRowButton(row, mouseX, x, width) : RowButton.NONE;
        switch (row.kind()) {
            case TASK -> {
                if (rowButton != RowButton.NONE) {
                    handleRowButton(this.tasks, row.index(), rowButton);
                } else {
                    this.openTaskEditor(row.index());
                }
            }
            case REWARD -> {
                if (rowButton != RowButton.NONE) {
                    handleRowButton(this.rewards, row.index(), rowButton);
                } else {
                    this.openRewardEditor(row.index());
                }
            }
            case ADD_TASK -> this.openTaskEditor(-1);
            case ADD_REWARD -> this.openRewardEditor(-1);
            default -> {
                return false;
            }
        }
        this.listScroll = MathHelper.clamp(this.listScroll, 0.0D, this.getMaxScroll());
        return true;
    }

    private void openTaskEditor(int index) {
        Task existing = index >= 0 ? this.tasks.get(index) : null;
        String id = existing != null ? existing.getId() : QuestBook.generateId(candidate -> this.tasks.stream().anyMatch(task -> task.getId().equals(candidate)));
        if (this.client != null) {
            this.client.setScreen(new EntryEditScreen<>(this, Text.translatable("gui.questz.editor.edit_task"), QuestzRegistries.TASK_TYPE, Task.CODEC, Task.LENIENT_CODEC,
                    TaskRenderers::getTitle, Task::getTypeId, EntryTemplates::task, EntryTemplates::taskForm, id, existing, task -> {
                if (index >= 0 && index < this.tasks.size()) {
                    this.tasks.set(index, task);
                } else {
                    this.tasks.add(task);
                }
            }));
        }
    }

    private void openRewardEditor(int index) {
        Reward existing = index >= 0 ? this.rewards.get(index) : null;
        String id = existing != null ? existing.getId() : QuestBook.generateId(candidate -> this.rewards.stream().anyMatch(reward -> reward.getId().equals(candidate)));
        if (this.client != null) {
            this.client.setScreen(new EntryEditScreen<>(this, Text.translatable("gui.questz.editor.edit_reward"), QuestzRegistries.REWARD_TYPE, Reward.CODEC, Reward.LENIENT_CODEC,
                    RewardRenderers::getTitle, Reward::getTypeId, EntryTemplates::reward, EntryTemplates::rewardForm, id, existing, reward -> {
                if (index >= 0 && index < this.rewards.size()) {
                    this.rewards.set(index, reward);
                } else {
                    this.rewards.add(reward);
                }
            }));
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (this.iconSuggester != null && this.iconSuggester.mouseScrolled(mouseX, mouseY, verticalAmount)) {
            return true;
        }
        if (mouseX >= this.getListX() && mouseX < this.getScrollbarX() + QuestzGui.SCROLLBAR_WIDTH && mouseY >= this.getListTop() && mouseY < this.getListBottom()) {
            this.listScroll = MathHelper.clamp(this.listScroll - verticalAmount * ROW_HEIGHT, 0.0D, this.getMaxScroll());
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (this.draggingScrollbar && button == 0) {
            this.listScroll = QuestzGui.scrollForHandle(this.getListTop(), this.getListBottom() - this.getListTop(), mouseY - this.dragOffset, this.getMaxScroll());
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (this.draggingScrollbar && button == 0) {
            this.draggingScrollbar = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.iconSuggester != null && this.iconSuggester.keyPressed(keyCode)) {
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void close() {
        if (this.client != null) {
            this.client.setScreen(this.parent);
        }
    }

}
