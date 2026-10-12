package net.questz.client.screen;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.advancement.AdvancementObtainedStatus;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.OrderedText;
import net.minecraft.text.StringVisitable;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Language;
import net.minecraft.util.math.MathHelper;
import net.questz.book.Quest;
import net.questz.book.QuestBook;
import net.questz.client.ClientQuestData;
import net.questz.client.ClientTeam;
import net.questz.client.render.RewardRenderers;
import net.questz.client.render.TaskRenderers;
import net.questz.init.KeyInit;
import net.questz.network.packet.QuestActionPacket;
import net.questz.progress.ProgressView;
import net.questz.progress.QuestEngine;
import net.questz.reward.ChoiceReward;
import net.questz.reward.ItemReward;
import net.questz.reward.Reward;
import net.questz.task.CheckmarkTask;
import net.questz.task.ItemTask;
import net.questz.task.Task;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

@Environment(EnvType.CLIENT)
public class QuestDetailScreen extends Screen {

    private static final int PANEL_WIDTH = 256;
    private static final int PANEL_HEIGHT = 206;
    private static final int HEADER_HEIGHT = 36;
    private static final int FOOTER_HEIGHT = 29;
    private static final int TEXT_HEIGHT = 10;
    private static final int ENTRY_HEIGHT = 20;
    private static final int INLINE_BUTTON_HEIGHT = 14;
    private static final int COLOR_TEXT = 0xFFFFFFFF;
    private static final int COLOR_DESCRIPTION = 0xFFE0E0E0;
    private static final int COLOR_MUTED = 0xFFA0A0A0;
    private static final int COLOR_DONE = 0xFF55FF55;
    private static final int COLOR_HEADER = 0xFFFFAA00;
    private static final int COLOR_HOVER = 0x20FFFFFF;
    private static final int TRACK_WIDTH = QuestzGui.SCROLLBAR_WIDTH;
    private static final int INSET_BORDER = QuestzGui.INSET_BORDER;

    private record Row(int height, @Nullable OrderedText text, int color, @Nullable Task task, @Nullable Reward reward, int option) {

        Row(int height, @Nullable OrderedText text, int color, @Nullable Task task, @Nullable Reward reward) {
            this(height, text, color, task, reward, -1);
        }
    }

    private record InlineButton(Text label, boolean active, Runnable action) {
    }

    private record RowHit(Row row, int y) {
    }

    private final QuestBookScreen parent;
    private final String questId;
    private int revision = -1;
    @Nullable
    private Quest quest;
    private final List<Row> rows = new ArrayList<>();
    private int contentHeight;
    private double scroll;
    private boolean draggingScrollbar;
    private double dragOffset;
    private int panelX;
    private int panelY;
    private ButtonWidget claimButton;
    private ButtonWidget submitButton;

    public QuestDetailScreen(QuestBookScreen parent, String questId) {
        super(Text.translatable("gui.questz"));
        this.parent = parent;
        this.questId = questId;
    }

    @Override
    protected void init() {
        if (this.parent.width != this.width || this.parent.height != this.height) {
            this.parent.init(this.client, this.width, this.height);
        }
        this.panelX = (this.width - PANEL_WIDTH) / 2;
        this.panelY = Math.max(0, (this.height - PANEL_HEIGHT) / 2);
        int buttonY = this.panelY + PANEL_HEIGHT - 25;
        this.submitButton = this.addDrawableChild(ButtonWidget.builder(Text.translatable("gui.questz.submit"), button -> this.sendAction(QuestActionPacket.Action.SUBMIT, "", 0))
                .dimensions(this.panelX + 7, buttonY, 74, 20).build());
        this.claimButton = this.addDrawableChild(ButtonWidget.builder(Text.translatable("gui.questz.claim"), button -> this.sendAction(QuestActionPacket.Action.CLAIM, "", 0))
                .dimensions(this.panelX + 83, buttonY, 74, 20).build());
        this.addDrawableChild(ButtonWidget.builder(ScreenTexts.BACK, button -> this.close())
                .dimensions(this.panelX + PANEL_WIDTH - 81, buttonY, 74, 20).build());
        this.revision = -1;
        this.refresh();
    }

    @Override
    public void resize(MinecraftClient client, int width, int height) {
        this.parent.resize(client, width, height);
        super.resize(client, width, height);
    }

    private int insetLeft() {
        return this.panelX + 7;
    }

    private int insetTop() {
        return this.panelY + HEADER_HEIGHT;
    }

    private int insetRight() {
        return this.panelX + PANEL_WIDTH - 7 - TRACK_WIDTH - 1;
    }

    private int insetBottom() {
        return this.panelY + PANEL_HEIGHT - FOOTER_HEIGHT;
    }

    private int trackX() {
        return this.insetRight() + 1;
    }

    private int contentLeft() {
        return this.insetLeft() + INSET_BORDER + 3;
    }

    private int contentRight() {
        return this.insetRight() - INSET_BORDER - 3;
    }

    private int contentTop() {
        return this.insetTop() + INSET_BORDER + 2;
    }

    private int getBodyHeight() {
        return this.insetBottom() - INSET_BORDER - 2 - this.contentTop();
    }

    private double getMaxScroll() {
        return Math.max(0, this.contentHeight - this.getBodyHeight());
    }

    private int getHandleHeight() {
        return QuestzGui.handleHeight(this.insetBottom() - this.insetTop());
    }

    private int getHandleY() {
        return QuestzGui.handleY(this.insetTop(), this.insetBottom() - this.insetTop(), this.scroll, this.getMaxScroll());
    }

    private void refresh() {
        if (this.revision != ClientQuestData.getRevision()) {
            this.revision = ClientQuestData.getRevision();
            this.quest = ClientQuestData.getBook().getQuest(this.questId);
            if (this.quest == null) {
                this.close();
                return;
            }
            this.buildRows();
        }
        ProgressView progress = QuestTexts.progress();
        QuestBook book = ClientQuestData.getBook();
        boolean hasSubmitTasks = this.quest.tasks().stream().anyMatch(task -> task instanceof ItemTask itemTask && itemTask.isConsume());
        this.claimButton.active = QuestTexts.hasClaimableRewards(this.quest);
        this.claimButton.visible = !this.quest.rewards().isEmpty();
        this.submitButton.visible = hasSubmitTasks;
        this.submitButton.active = hasSubmitTasks && QuestEngine.canProgress(book, progress, this.quest);
        this.claimButton.setX(this.panelX + (hasSubmitTasks ? 83 : 7));
    }

    private void buildRows() {
        this.rows.clear();
        int width = this.contentRight() - this.contentLeft();
        for (Text description : this.quest.description()) {
            for (OrderedText line : this.textRenderer.wrapLines(description, width)) {
                this.rows.add(new Row(TEXT_HEIGHT, line, COLOR_DESCRIPTION, null, null));
            }
        }
        if (!this.quest.description().isEmpty()) {
            this.rows.add(new Row(6, null, 0, null, null));
        }
        if (!this.quest.tasks().isEmpty()) {
            this.rows.add(new Row(12, Text.translatable("gui.questz.tasks").asOrderedText(), COLOR_HEADER, null, null));
            for (Task task : this.quest.tasks()) {
                this.rows.add(new Row(ENTRY_HEIGHT, null, 0, task, null));
            }
            this.rows.add(new Row(6, null, 0, null, null));
        }
        if (!this.quest.rewards().isEmpty()) {
            this.rows.add(new Row(12, Text.translatable("gui.questz.rewards").asOrderedText(), COLOR_HEADER, null, null));
            for (Reward reward : this.quest.rewards()) {
                this.rows.add(new Row(ENTRY_HEIGHT, null, 0, null, reward));
                if (reward instanceof ChoiceReward choice) {
                    for (int i = 0; i < choice.getOptions().size(); i++) {
                        this.rows.add(new Row(ENTRY_HEIGHT, null, 0, null, reward, i));
                    }
                }
            }
        }
        this.contentHeight = this.rows.stream().mapToInt(Row::height).sum();
        this.scroll = MathHelper.clamp(this.scroll, 0.0D, this.getMaxScroll());
    }

    private OrderedText trim(Text text, int width) {
        if (this.textRenderer.getWidth(text) <= width) {
            return text.asOrderedText();
        }
        StringVisitable trimmed = this.textRenderer.trimToWidth(text, width - this.textRenderer.getWidth("..."));
        return Language.getInstance().reorder(StringVisitable.concat(trimmed, StringVisitable.plain("...")));
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.refresh();
        if (this.quest == null) {
            return;
        }
        this.parent.render(context, -1, -1, delta);

        MatrixStack matrices = context.getMatrices();
        matrices.push();
        matrices.translate(0.0F, 0.0F, 400.0F);
        context.fill(0, 0, this.width, this.height, 0x99000000);
        QuestzGui.drawPanel(context, this.panelX, this.panelY, PANEL_WIDTH, PANEL_HEIGHT);
        this.drawHeader(context);
        QuestzGui.drawInset(context, this.insetLeft(), this.insetTop(), this.insetRight() - this.insetLeft(), this.insetBottom() - this.insetTop());
        QuestzGui.drawScrollbar(context, this.trackX(), this.insetTop(), this.insetBottom() - this.insetTop(), this.scroll, this.getMaxScroll());
        RowHit hovered = this.drawBody(context, mouseX, mouseY);
        super.render(context, mouseX, mouseY, delta);
        this.drawRowTooltip(context, hovered, mouseX, mouseY);
        matrices.pop();
    }

    private void drawHeader(DrawContext context) {
        QuestBook book = ClientQuestData.getBook();
        ProgressView progress = QuestTexts.progress();
        boolean completed = progress.isCompleted(this.quest.id());
        AdvancementObtainedStatus status = completed ? AdvancementObtainedStatus.OBTAINED : AdvancementObtainedStatus.UNOBTAINED;
        context.drawGuiTexture(status.getFrameTexture(this.quest.shape().getFrame()), this.panelX + 6, this.panelY + 6, 26, 26);
        context.drawItemWithoutEntity(QuestTexts.icon(this.quest), this.panelX + 11, this.panelY + 11);
        int textWidth = PANEL_WIDTH - 48 - (this.quest.repeatable() ? 18 : 0);
        context.drawTextWithShadow(this.textRenderer, this.trim(QuestTexts.title(this.quest), textWidth), this.panelX + 37, this.panelY + 9, COLOR_TEXT);
        if (this.quest.repeatable()) {
            QuestzGui.drawIcon(context, QuestzGui.REFRESH, this.getRepeatIconX(), this.panelY + 9, 16, true);
        }
        Text statusText = QuestTexts.isUnlocked(book, progress, this.quest) || this.quest.dependencies().isEmpty()
                ? QuestTexts.status(book, progress, this.quest)
                : QuestTexts.requirements(book, progress, this.quest);
        context.drawTextWithShadow(this.textRenderer, this.trim(statusText, textWidth), this.panelX + 37, this.panelY + 20, COLOR_TEXT);
    }

    @Nullable
    private RowHit drawBody(DrawContext context, int mouseX, int mouseY) {
        int left = this.contentLeft();
        int right = this.contentRight();
        int top = this.contentTop();
        int bottom = top + this.getBodyHeight();
        boolean mouseInBody = mouseX >= left && mouseX < right && mouseY >= top && mouseY < bottom;
        ProgressView progress = QuestTexts.progress();
        ClientTeam team = ClientQuestData.getTeam();
        RowHit hovered = null;

        context.enableScissor(this.insetLeft() + INSET_BORDER, this.insetTop() + INSET_BORDER, this.insetRight() - INSET_BORDER, this.insetBottom() - INSET_BORDER);
        int y = top - (int) this.scroll;
        for (Row row : this.rows) {
            if (y + row.height() > top && y < bottom) {
                boolean rowHovered = mouseInBody && mouseY >= y && mouseY < y + row.height();
                if (rowHovered) {
                    hovered = new RowHit(row, y);
                }
                InlineButton inlineButton = this.getInlineButton(row);
                int buttonWidth = inlineButton != null ? this.getInlineButtonWidth(inlineButton) : 0;
                if (row.text() != null) {
                    context.drawText(this.textRenderer, row.text(), left, y + 1, row.color(), true);
                } else if (row.task() != null) {
                    Task task = row.task();
                    boolean done = QuestEngine.isTaskComplete(progress, this.quest, task);
                    if (rowHovered) {
                        context.fill(left - 2, y, right + 2, y + row.height(), COLOR_HOVER);
                    }
                    QuestzGui.drawSlot(context, left, y + 1);
                    context.drawItemWithoutEntity(TaskRenderers.getIcon(task), left + 1, y + 2);
                    Text status = inlineButton != null ? Text.empty() : this.taskStatus(task, done, progress);
                    int statusWidth = inlineButton != null ? buttonWidth + 2 : done ? 18 : this.textRenderer.getWidth(status);
                    if (done && inlineButton == null) {
                        QuestzGui.drawIcon(context, QuestzGui.CHECK, right - 16, y + 2, 16, true);
                    }
                    context.drawText(this.textRenderer, this.trim(TaskRenderers.getTitle(task), right - left - 24 - statusWidth), left + 22, y + 6, done ? COLOR_DONE : COLOR_TEXT, true);
                    context.drawText(this.textRenderer, status, right - this.textRenderer.getWidth(status), y + 6, COLOR_TEXT, true);
                } else if (row.reward() instanceof ChoiceReward choice && row.option() >= 0) {
                    ItemStack option = choice.getOptions().get(row.option());
                    boolean claimed = team != null && team.isRewardClaimed(this.quest.id(), choice.getId());
                    if (rowHovered) {
                        context.fill(left + 10, y, right + 2, y + row.height(), COLOR_HOVER);
                    }
                    QuestzGui.drawSlot(context, left + 12, y + 1);
                    context.drawItemWithoutEntity(option, left + 13, y + 2);
                    context.drawItemInSlot(this.textRenderer, option, left + 13, y + 2);
                    int statusWidth = inlineButton != null ? buttonWidth + 2 : 0;
                    context.drawText(this.textRenderer, this.trim(option.getName(), right - left - 36 - statusWidth), left + 34, y + 6, claimed ? COLOR_MUTED : COLOR_DESCRIPTION, true);
                } else if (row.reward() != null) {
                    Reward reward = row.reward();
                    ItemStack icon = RewardRenderers.getIcon(reward);
                    boolean claimed = team != null && team.isRewardClaimed(this.quest.id(), reward.getId());
                    if (rowHovered) {
                        context.fill(left - 2, y, right + 2, y + row.height(), COLOR_HOVER);
                    }
                    QuestzGui.drawSlot(context, left, y + 1);
                    context.drawItemWithoutEntity(icon, left + 1, y + 2);
                    context.drawItemInSlot(this.textRenderer, icon, left + 1, y + 2);
                    Text status = claimed ? Text.translatable("gui.questz.claimed").formatted(Formatting.GRAY)
                            : reward.isTeamReward() ? Text.translatable("gui.questz.team_reward").formatted(Formatting.AQUA) : Text.empty();
                    int statusWidth = this.textRenderer.getWidth(status);
                    Text title = reward instanceof ItemReward itemReward ? itemReward.getItem().getName() : RewardRenderers.getTitle(reward);
                    context.drawText(this.textRenderer, this.trim(title, right - left - 24 - statusWidth), left + 22, y + 6, claimed ? COLOR_MUTED : COLOR_TEXT, true);
                    context.drawText(this.textRenderer, status, right - statusWidth, y + 6, COLOR_TEXT, true);
                }
                if (inlineButton != null) {
                    this.drawInlineButton(context, inlineButton, right - buttonWidth, y + (row.height() - INLINE_BUTTON_HEIGHT) / 2, buttonWidth, mouseX, mouseY, mouseInBody);
                }
            }
            y += row.height();
        }
        context.disableScissor();
        return hovered;
    }

    @Nullable
    private InlineButton getInlineButton(Row row) {
        ProgressView progress = QuestTexts.progress();
        if (row.task() instanceof CheckmarkTask task && !QuestEngine.isTaskComplete(progress, this.quest, task)) {
            boolean active = QuestEngine.canProgress(ClientQuestData.getBook(), progress, this.quest);
            return new InlineButton(Text.translatable("gui.questz.complete"), active, () -> this.sendAction(QuestActionPacket.Action.CHECKMARK, task.getId(), 0));
        }
        if (row.reward() instanceof ChoiceReward choice && row.option() >= 0) {
            ClientTeam team = ClientQuestData.getTeam();
            if (team != null && team.isRewardClaimed(this.quest.id(), choice.getId())) {
                return null;
            }
            boolean active = team != null && team.isCompleted(this.quest.id());
            int option = row.option();
            return new InlineButton(Text.translatable("gui.questz.choose"), active, () -> this.sendAction(QuestActionPacket.Action.CHOOSE, choice.getId(), option));
        }
        return null;
    }

    private int getInlineButtonWidth(InlineButton button) {
        return Math.max(40, this.textRenderer.getWidth(button.label()) + 10);
    }

    private boolean isOverInlineButton(InlineButton button, int rowY, int rowHeight, double mouseX, double mouseY) {
        int width = this.getInlineButtonWidth(button);
        int x = this.contentRight() - width;
        int y = rowY + (rowHeight - INLINE_BUTTON_HEIGHT) / 2;
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + INLINE_BUTTON_HEIGHT;
    }

    private void drawInlineButton(DrawContext context, InlineButton button, int x, int y, int width, int mouseX, int mouseY, boolean mouseInBody) {
        boolean hovered = mouseInBody && mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + INLINE_BUTTON_HEIGHT;
        QuestzGui.drawButton(context, x, y, width, INLINE_BUTTON_HEIGHT, button.active(), hovered);
        context.drawCenteredTextWithShadow(this.textRenderer, button.label(), x + width / 2, y + (INLINE_BUTTON_HEIGHT - 8) / 2, button.active() ? 0xFFFFFF : 0xA0A0A0);
    }

    private Text taskStatus(Task task, boolean done, ProgressView progress) {
        if (done) {
            return Text.empty();
        }
        if (task.getTarget() <= 1L) {
            return Text.empty();
        }
        return Text.literal(progress.getProgress(this.quest.id(), task.getId()) + "/" + task.getTarget()).formatted(Formatting.GRAY);
    }

    private int getRepeatIconX() {
        return this.panelX + PANEL_WIDTH - 24;
    }

    private void drawRowTooltip(DrawContext context, @Nullable RowHit hit, int mouseX, int mouseY) {
        if (this.quest != null && this.quest.repeatable() && mouseX >= this.getRepeatIconX() && mouseX < this.getRepeatIconX() + 16 && mouseY >= this.panelY + 9
                && mouseY < this.panelY + 25) {
            context.drawTooltip(this.textRenderer, Text.translatable("gui.questz.repeatable", QuestTexts.progress().getCompletions(this.quest.id())), mouseX, mouseY);
            return;
        }
        int iconLeft = this.contentLeft();
        if (hit == null || this.draggingScrollbar) {
            return;
        }
        Row row = hit.row();
        if (row.reward() instanceof ChoiceReward choice && row.option() >= 0) {
            if (mouseX >= iconLeft + 12 && mouseX < iconLeft + 30) {
                context.drawItemTooltip(this.textRenderer, choice.getOptions().get(row.option()), mouseX, mouseY);
            }
            return;
        }
        if (mouseX < iconLeft || mouseX >= iconLeft + QuestzGui.SLOT_SIZE) {
            return;
        }
        if (row.task() instanceof ItemTask itemTask) {
            context.drawItemTooltip(this.textRenderer, itemTask.getItem(), mouseX, mouseY);
        } else if (row.task() != null) {
            context.drawTooltip(this.textRenderer, TaskRenderers.getTitle(row.task()), mouseX, mouseY);
        } else if (row.reward() instanceof ItemReward itemReward) {
            context.drawItemTooltip(this.textRenderer, itemReward.getItem(), mouseX, mouseY);
        } else if (row.reward() != null) {
            context.drawTooltip(this.textRenderer, RewardRenderers.getTitle(row.reward()), mouseX, mouseY);
        }
    }

    private boolean isOverTrack(double mouseX, double mouseY) {
        return mouseX >= this.trackX() && mouseX < this.trackX() + TRACK_WIDTH && mouseY >= this.insetTop() && mouseY < this.insetBottom();
    }

    private void scrollToHandle(double handleTop) {
        this.scroll = QuestzGui.scrollForHandle(this.insetTop(), this.insetBottom() - this.insetTop(), handleTop, this.getMaxScroll());
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (button != 0 || this.quest == null) {
            return false;
        }
        if (this.isOverTrack(mouseX, mouseY) && this.getMaxScroll() > 0) {
            int handleY = this.getHandleY();
            if (mouseY >= handleY && mouseY < handleY + this.getHandleHeight()) {
                this.dragOffset = mouseY - handleY;
            } else {
                this.dragOffset = this.getHandleHeight() / 2.0D;
                this.scrollToHandle(mouseY - this.dragOffset);
            }
            this.draggingScrollbar = true;
            return true;
        }
        RowHit hit = this.getRowAt(mouseX, mouseY);
        if (hit != null) {
            InlineButton inlineButton = this.getInlineButton(hit.row());
            if (inlineButton != null && inlineButton.active() && this.isOverInlineButton(inlineButton, hit.y(), hit.row().height(), mouseX, mouseY)) {
                inlineButton.action().run();
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (this.draggingScrollbar && button == 0) {
            this.scrollToHandle(mouseY - this.dragOffset);
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

    @Nullable
    private RowHit getRowAt(double mouseX, double mouseY) {
        int top = this.contentTop();
        if (mouseX < this.contentLeft() - 2 || mouseX >= this.contentRight() + 2 || mouseY < top || mouseY >= top + this.getBodyHeight()) {
            return null;
        }
        int y = top - (int) this.scroll;
        for (Row row : this.rows) {
            if (mouseY >= y && mouseY < y + row.height()) {
                return new RowHit(row, y);
            }
            y += row.height();
        }
        return null;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        this.scroll = MathHelper.clamp(this.scroll - verticalAmount * 12.0D, 0.0D, this.getMaxScroll());
        return true;
    }

    private void sendAction(QuestActionPacket.Action action, String entryId, int option) {
        if (this.quest != null && ClientPlayNetworking.canSend(QuestActionPacket.PACKET_ID)) {
            ClientPlayNetworking.send(new QuestActionPacket(action, this.quest.id(), entryId, option));
            if (this.client != null) {
                this.client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            }
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.client != null && (KeyInit.screenKey.matchesKey(keyCode, scanCode) || this.client.options.inventoryKey.matchesKey(keyCode, scanCode))) {
            this.client.setScreen(null);
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

    @Override
    public boolean shouldPause() {
        return false;
    }

}
