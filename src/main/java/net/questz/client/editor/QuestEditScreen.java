package net.questz.client.editor;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.CyclingButtonWidget;
import net.minecraft.client.gui.widget.EditBoxWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.StringVisitable;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.Language;
import net.minecraft.util.math.MathHelper;
import net.questz.api.QuestzRegistries;
import net.questz.book.DependencyMode;
import net.questz.book.Quest;
import net.questz.book.QuestBook;
import net.questz.book.QuestShape;
import net.questz.book.QuestVisibility;
import net.questz.client.render.RewardRenderers;
import net.questz.client.render.TaskRenderers;
import net.questz.network.packet.EditQuestPacket;
import net.questz.reward.Reward;
import net.questz.task.Task;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

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
    private boolean autoClaim;
    private boolean repeatable;
    private final List<Task> tasks;
    private final List<Reward> rewards;
    private double listScroll;
    private TextFieldWidget iconField;
    private TextFieldWidget sizeField;
    private ButtonWidget saveButton;

    public QuestEditScreen(Screen parent, String chapterId, Quest quest) {
        super(Text.translatable("gui.questz.editor.edit_quest"));
        this.parent = parent;
        this.chapterId = chapterId;
        this.original = quest;
        this.titleValue = ClientEditor.toEditString(quest.title());
        this.iconValue = quest.icon().map(stack -> Registries.ITEM.getId(stack.getItem()).toString()).orElse("");
        this.descriptionValue = ClientEditor.toEditString(quest.description());
        this.sizeValue = String.valueOf(quest.size());
        this.shape = quest.shape();
        this.visibility = quest.visibility();
        this.dependencyMode = quest.dependencyMode();
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
        this.iconField.setMaxLength(256);
        this.iconField.setText(this.iconValue);
        this.iconField.setPlaceholder(Text.translatable("gui.questz.editor.icon_auto").formatted(Formatting.DARK_GRAY));
        this.iconField.setTooltip(Tooltip.of(Text.translatable("gui.questz.editor.icon.tooltip")));
        this.iconField.setChangedListener(value -> {
            this.iconValue = value;
            this.updateValidity();
        });
        this.addDrawableChild(this.iconField);

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

        this.saveButton = this.addDrawableChild(ButtonWidget.builder(Text.translatable("gui.questz.editor.save"), button -> this.save())
                .dimensions(this.width / 2 - 102, this.height - 26, 100, 20).build());
        this.addDrawableChild(ButtonWidget.builder(ScreenTexts.CANCEL, button -> this.close())
                .dimensions(this.width / 2 + 2, this.height - 26, 100, 20).build());
        this.updateValidity();
    }

    @Nullable
    private Item parseIcon() {
        Identifier id = Identifier.tryParse(this.iconValue.trim().toLowerCase(Locale.ROOT));
        if (id == null || !Registries.ITEM.containsId(id)) {
            return null;
        }
        Item item = Registries.ITEM.get(id);
        return item == Items.AIR ? null : item;
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
        boolean iconValid = this.iconValue.isBlank() || this.parseIcon() != null;
        boolean sizeValid = this.parseSize() != null;
        this.iconField.setEditableColor(iconValid ? 0xE0E0E0 : 0xFF5555);
        this.sizeField.setEditableColor(sizeValid ? 0xE0E0E0 : 0xFF5555);
        this.saveButton.active = iconValid && sizeValid;
    }

    private void save() {
        Item item = this.parseIcon();
        Float size = this.parseSize();
        if ((item == null && !this.iconValue.isBlank()) || size == null) {
            return;
        }
        Optional<ItemStack> icon = Optional.empty();
        if (item != null) {
            icon = Optional.of(this.original.icon().filter(stack -> stack.isOf(item)).orElseGet(() -> new ItemStack(item)));
        }
        Quest quest = new Quest(this.original.id(), ClientEditor.fromEditString(this.titleValue), ClientEditor.fromEditLines(this.descriptionValue), icon, this.original.x(),
                this.original.y(), size, this.shape, this.original.dependencies(), this.dependencyMode, this.visibility, List.copyOf(this.tasks), List.copyOf(this.rewards),
                this.repeatable, this.autoClaim);
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
        return Math.max(0, this.getRows().size() * ROW_HEIGHT - (this.getListBottom() - this.getListTop()));
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 8, 0xFFFFFFFF);
        Item item = this.parseIcon();
        ItemStack preview = item != null ? new ItemStack(item) : this.iconValue.isBlank() && !this.tasks.isEmpty() ? TaskRenderers.getIcon(this.tasks.getFirst()) : ItemStack.EMPTY;
        context.drawItem(preview, this.getLeft() + this.getColumnWidth() - 17, 45);
        this.renderList(context, mouseX, mouseY);
    }

    private void renderList(DrawContext context, int mouseX, int mouseY) {
        int x = this.getListX();
        int width = this.getColumnWidth();
        int top = this.getListTop();
        int bottom = this.getListBottom();
        context.fill(x, top, x + width, bottom, 0x80000000);
        context.enableScissor(x, top, x + width, bottom);
        int y = top - (int) this.listScroll;
        for (Row row : this.getRows()) {
            boolean hovered = mouseX >= x && mouseX < x + width && mouseY >= Math.max(y, top) && mouseY < Math.min(y + ROW_HEIGHT, bottom);
            switch (row.kind()) {
                case HEADER_TASKS -> context.drawText(this.textRenderer, Text.translatable("gui.questz.tasks"), x + 3, y + 4, 0xFFFFAA00, false);
                case HEADER_REWARDS -> context.drawText(this.textRenderer, Text.translatable("gui.questz.rewards"), x + 3, y + 4, 0xFFFFAA00, false);
                case ADD_TASK, ADD_REWARD -> {
                    if (hovered) {
                        context.fill(x, y, x + width, y + ROW_HEIGHT, 0x30FFFFFF);
                    }
                    Text label = Text.translatable(row.kind() == RowKind.ADD_TASK ? "gui.questz.editor.add_task" : "gui.questz.editor.add_reward");
                    context.drawText(this.textRenderer, label, x + 5, y + 4, 0xFF55FF55, false);
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
                    StringVisitable trimmed = this.textRenderer.trimToWidth(title, width - 32);
                    context.drawText(this.textRenderer, Language.getInstance().reorder(trimmed), x + 18, y + 4, 0xFFFFFFFF, false);
                    boolean deleteHovered = hovered && mouseX >= x + width - 12;
                    context.drawText(this.textRenderer, Text.literal("✕"), x + width - 10, y + 4, deleteHovered ? 0xFFFF5555 : 0xFF888888, false);
                }
            }
            y += ROW_HEIGHT;
        }
        context.disableScissor();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        int x = this.getListX();
        int width = this.getColumnWidth();
        if (button != 0 || mouseX < x || mouseX >= x + width || mouseY < this.getListTop() || mouseY >= this.getListBottom()) {
            return false;
        }
        int index = (int) ((mouseY - this.getListTop() + this.listScroll) / ROW_HEIGHT);
        List<Row> rows = this.getRows();
        if (index < 0 || index >= rows.size()) {
            return false;
        }
        Row row = rows.get(index);
        boolean delete = mouseX >= x + width - 12;
        switch (row.kind()) {
            case TASK -> {
                if (delete) {
                    this.tasks.remove(row.index());
                } else {
                    this.openTaskEditor(row.index());
                }
            }
            case REWARD -> {
                if (delete) {
                    this.rewards.remove(row.index());
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
        if (mouseX >= this.getListX() && mouseX < this.getListX() + this.getColumnWidth() && mouseY >= this.getListTop() && mouseY < this.getListBottom()) {
            this.listScroll = MathHelper.clamp(this.listScroll - verticalAmount * ROW_HEIGHT, 0.0D, this.getMaxScroll());
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public void close() {
        if (this.client != null) {
            this.client.setScreen(this.parent);
        }
    }

}
