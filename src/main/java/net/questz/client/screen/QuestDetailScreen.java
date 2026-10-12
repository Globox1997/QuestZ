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
import net.minecraft.util.Identifier;
import net.minecraft.util.Language;
import net.minecraft.util.math.MathHelper;
import net.questz.QuestzMain;
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

    private static final Identifier PANEL_TEXTURE = QuestzMain.identifierOf("textures/gui/task.png");
    private static final int PANEL_TEXTURE_SIZE = 15;
    private static final int PANEL_BORDER = 4;
    private static final int COLOR_TEXT = 0xFF404040;
    private static final int COLOR_TEXT_LIGHT = 0xFF555555;
    private static final int COLOR_TEXT_MUTED = 0xFF808080;
    private static final int COLOR_TEXT_DONE = 0xFF00AA00;
    private static final int COLOR_HEADER = 0xFF8A5A00;
    private static final int COLOR_LINE = 0xFF8B8B8B;
    private static final int COLOR_HOVER = 0x40FFFFFF;
    private static final int PANEL_WIDTH = 256;
    private static final int PANEL_HEIGHT = 206;
    private static final int BODY_TOP = 40;
    private static final int BODY_BOTTOM = 30;
    private static final int TEXT_HEIGHT = 10;
    private static final int ENTRY_HEIGHT = 18;

    private record Row(int height, @Nullable OrderedText text, int color, @Nullable Task task, @Nullable Reward reward, int option) {

        Row(int height, @Nullable OrderedText text, int color, @Nullable Task task, @Nullable Reward reward) {
            this(height, text, color, task, reward, -1);
        }
    }

    private final QuestBookScreen parent;
    private final String questId;
    private int revision = -1;
    @Nullable
    private Quest quest;
    private final List<Row> rows = new ArrayList<>();
    private int contentHeight;
    private double scroll;
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
        this.claimButton = this.addDrawableChild(ButtonWidget.builder(Text.translatable("gui.questz.claim"), button -> this.sendAction(QuestActionPacket.Action.CLAIM, "", 0))
                .dimensions(this.panelX + 7, buttonY, 74, 20).build());
        this.submitButton = this.addDrawableChild(ButtonWidget.builder(Text.translatable("gui.questz.submit"), button -> this.sendAction(QuestActionPacket.Action.SUBMIT, "", 0))
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
    }

    private void buildRows() {
        this.rows.clear();
        int width = PANEL_WIDTH - 24;
        for (Text description : this.quest.description()) {
            for (OrderedText line : this.textRenderer.wrapLines(description, width)) {
                this.rows.add(new Row(TEXT_HEIGHT, line, COLOR_TEXT_LIGHT, null, null));
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

    private int getBodyHeight() {
        return PANEL_HEIGHT - BODY_TOP - BODY_BOTTOM;
    }

    private double getMaxScroll() {
        return Math.max(0, this.contentHeight - this.getBodyHeight());
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
        this.drawPanel(context, this.panelX, this.panelY, PANEL_WIDTH, PANEL_HEIGHT);
        this.drawHeader(context);
        Row hovered = this.drawBody(context, mouseX, mouseY);
        super.render(context, mouseX, mouseY, delta);
        this.drawRowTooltip(context, hovered, mouseX, mouseY);
        matrices.pop();
    }

    private void drawPanel(DrawContext context, int x, int y, int width, int height) {
        int border = PANEL_BORDER;
        int center = PANEL_TEXTURE_SIZE - border * 2;
        int innerWidth = width - border * 2;
        int innerHeight = height - border * 2;
        int far = PANEL_TEXTURE_SIZE - border;
        panelSlice(context, x, y, border, border, 0, 0, border, border);
        panelSlice(context, x + border, y, innerWidth, border, border, 0, center, border);
        panelSlice(context, x + width - border, y, border, border, far, 0, border, border);
        panelSlice(context, x, y + border, border, innerHeight, 0, border, border, center);
        panelSlice(context, x + border, y + border, innerWidth, innerHeight, border, border, center, center);
        panelSlice(context, x + width - border, y + border, border, innerHeight, far, border, border, center);
        panelSlice(context, x, y + height - border, border, border, 0, far, border, border);
        panelSlice(context, x + border, y + height - border, innerWidth, border, border, far, center, border);
        panelSlice(context, x + width - border, y + height - border, border, border, far, far, border, border);
    }

    private static void panelSlice(DrawContext context, int x, int y, int width, int height, int u, int v, int uWidth, int vHeight) {
        if (width > 0 && height > 0) {
            context.drawTexture(PANEL_TEXTURE, x, y, width, height, u, v, uWidth, vHeight, PANEL_TEXTURE_SIZE, PANEL_TEXTURE_SIZE);
        }
    }

    private void drawHeader(DrawContext context) {
        QuestBook book = ClientQuestData.getBook();
        ProgressView progress = QuestTexts.progress();
        boolean completed = progress.isCompleted(this.quest.id());
        AdvancementObtainedStatus status = completed ? AdvancementObtainedStatus.OBTAINED : AdvancementObtainedStatus.UNOBTAINED;
        context.drawGuiTexture(status.getFrameTexture(this.quest.shape().getFrame()), this.panelX + 7, this.panelY + 7, 26, 26);
        context.drawItemWithoutEntity(QuestTexts.icon(this.quest), this.panelX + 12, this.panelY + 12);
        context.drawText(this.textRenderer, this.trim(QuestTexts.title(this.quest), PANEL_WIDTH - 50), this.panelX + 40, this.panelY + 10, COLOR_TEXT, false);
        Text statusText = QuestTexts.isUnlocked(book, progress, this.quest) || this.quest.dependencies().isEmpty()
                ? QuestTexts.status(book, progress, this.quest, true)
                : QuestTexts.requirements(book, progress, this.quest, true);
        if (this.quest.repeatable()) {
            statusText = statusText.copy().append(Text.translatable("gui.questz.repeatable", progress.getCompletions(this.quest.id())).formatted(Formatting.DARK_AQUA));
        }
        context.drawText(this.textRenderer, this.trim(statusText, PANEL_WIDTH - 50), this.panelX + 40, this.panelY + 22, COLOR_TEXT, false);
        context.fill(this.panelX + 6, this.panelY + BODY_TOP - 3, this.panelX + PANEL_WIDTH - 6, this.panelY + BODY_TOP - 2, COLOR_LINE);
    }

    @Nullable
    private Row drawBody(DrawContext context, int mouseX, int mouseY) {
        int left = this.panelX + 8;
        int right = this.panelX + PANEL_WIDTH - 8;
        int top = this.panelY + BODY_TOP;
        int bottom = top + this.getBodyHeight();
        boolean mouseInBody = mouseX >= left && mouseX < right && mouseY >= top && mouseY < bottom;
        ProgressView progress = QuestTexts.progress();
        ClientTeam team = ClientQuestData.getTeam();
        boolean canProgress = QuestEngine.canProgress(ClientQuestData.getBook(), progress, this.quest);
        Row hovered = null;

        context.enableScissor(left, top, right, bottom);
        int y = top - (int) this.scroll;
        for (Row row : this.rows) {
            if (y + row.height() > top && y < bottom) {
                boolean rowHovered = mouseInBody && mouseY >= y && mouseY < y + row.height();
                if (rowHovered) {
                    hovered = row;
                }
                if (row.text() != null) {
                    context.drawText(this.textRenderer, row.text(), left + 2, y + 1, row.color(), false);
                } else if (row.task() != null) {
                    Task task = row.task();
                    boolean done = QuestEngine.isTaskComplete(progress, this.quest, task);
                    boolean clickable = task instanceof CheckmarkTask && !done && canProgress;
                    if (clickable && rowHovered) {
                        context.fill(left, y, right, y + row.height(), COLOR_HOVER);
                    }
                    context.drawItemWithoutEntity(TaskRenderers.getIcon(task), left + 2, y + 1);
                    Text status = this.taskStatus(task, done, clickable, progress);
                    int statusWidth = this.textRenderer.getWidth(status);
                    context.drawText(this.textRenderer, this.trim(TaskRenderers.getTitle(task), right - left - 30 - statusWidth), left + 22, y + 5, done ? COLOR_TEXT_DONE : COLOR_TEXT, false);
                    context.drawText(this.textRenderer, status, right - 2 - statusWidth, y + 5, COLOR_TEXT, false);
                } else if (row.reward() instanceof ChoiceReward choice && row.option() >= 0) {
                    ItemStack option = choice.getOptions().get(row.option());
                    boolean claimed = team != null && team.isRewardClaimed(this.quest.id(), choice.getId());
                    boolean choosable = !claimed && team != null && team.isCompleted(this.quest.id());
                    if (choosable && rowHovered) {
                        context.fill(left + 12, y, right, y + row.height(), COLOR_HOVER);
                    }
                    context.drawItemWithoutEntity(option, left + 14, y + 1);
                    context.drawItemInSlot(this.textRenderer, option, left + 14, y + 1);
                    Text status = choosable ? Text.translatable("gui.questz.choose").formatted(Formatting.DARK_BLUE) : Text.empty();
                    int statusWidth = this.textRenderer.getWidth(status);
                    context.drawText(this.textRenderer, this.trim(option.getName(), right - left - 42 - statusWidth), left + 34, y + 5, claimed ? COLOR_TEXT_MUTED : COLOR_TEXT_LIGHT, false);
                    context.drawText(this.textRenderer, status, right - 2 - statusWidth, y + 5, COLOR_TEXT, false);
                } else if (row.reward() != null) {
                    Reward reward = row.reward();
                    ItemStack icon = RewardRenderers.getIcon(reward);
                    boolean claimed = team != null && team.isRewardClaimed(this.quest.id(), reward.getId());
                    context.drawItemWithoutEntity(icon, left + 2, y + 1);
                    context.drawItemInSlot(this.textRenderer, icon, left + 2, y + 1);
                    Text status = claimed ? Text.translatable("gui.questz.claimed").formatted(Formatting.DARK_GRAY)
                            : reward.isTeamReward() ? Text.translatable("gui.questz.team_reward").formatted(Formatting.DARK_AQUA) : Text.empty();
                    int statusWidth = this.textRenderer.getWidth(status);
                    context.drawText(this.textRenderer, this.trim(RewardRenderers.getTitle(reward), right - left - 30 - statusWidth), left + 22, y + 5, claimed ? COLOR_TEXT_MUTED : COLOR_TEXT, false);
                    context.drawText(this.textRenderer, status, right - 2 - statusWidth, y + 5, COLOR_TEXT, false);
                }
            }
            y += row.height();
        }
        context.disableScissor();

        if (this.getMaxScroll() > 0) {
            int barHeight = Math.max(10, this.getBodyHeight() * this.getBodyHeight() / this.contentHeight);
            int barY = top + (int) ((this.getBodyHeight() - barHeight) * (this.scroll / this.getMaxScroll()));
            context.fill(right + 2, barY, right + 4, barY + barHeight, COLOR_LINE);
        }
        return hovered;
    }

    private Text taskStatus(Task task, boolean done, boolean clickable, ProgressView progress) {
        if (done) {
            return Text.literal("✔").formatted(Formatting.DARK_GREEN);
        }
        if (clickable) {
            return Text.translatable("gui.questz.click_to_complete").formatted(Formatting.DARK_BLUE);
        }
        if (task.getTarget() <= 1L) {
            return Text.empty();
        }
        return Text.literal(progress.getProgress(this.quest.id(), task.getId()) + "/" + task.getTarget()).formatted(Formatting.DARK_GRAY);
    }

    private void drawRowTooltip(DrawContext context, @Nullable Row row, int mouseX, int mouseY) {
        int iconLeft = this.panelX + 10;
        if (row == null || mouseX < iconLeft || mouseX >= iconLeft + 16) {
            return;
        }
        if (row.reward() instanceof ChoiceReward choice && row.option() >= 0) {
            if (mouseX >= iconLeft + 12 && mouseX < iconLeft + 28) {
                context.drawItemTooltip(this.textRenderer, choice.getOptions().get(row.option()), mouseX, mouseY);
            }
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

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (button != 0 || this.quest == null) {
            return false;
        }
        Row row = this.getRowAt(mouseX, mouseY);
        if (row != null && row.reward() instanceof ChoiceReward choice && row.option() >= 0) {
            ClientTeam team = ClientQuestData.getTeam();
            if (team != null && team.isCompleted(this.quest.id()) && !team.isRewardClaimed(this.quest.id(), choice.getId())) {
                this.sendAction(QuestActionPacket.Action.CHOOSE, choice.getId(), row.option());
                return true;
            }
        }
        if (row != null && row.task() instanceof CheckmarkTask task) {
            ProgressView progress = QuestTexts.progress();
            if (!QuestEngine.isTaskComplete(progress, this.quest, task) && QuestEngine.canProgress(ClientQuestData.getBook(), progress, this.quest)) {
                this.sendAction(QuestActionPacket.Action.CHECKMARK, task.getId(), 0);
                return true;
            }
        }
        return false;
    }

    @Nullable
    private Row getRowAt(double mouseX, double mouseY) {
        int left = this.panelX + 8;
        int right = this.panelX + PANEL_WIDTH - 8;
        int top = this.panelY + BODY_TOP;
        if (mouseX < left || mouseX >= right || mouseY < top || mouseY >= top + this.getBodyHeight()) {
            return null;
        }
        int y = top - (int) this.scroll;
        for (Row row : this.rows) {
            if (mouseY >= y && mouseY < y + row.height()) {
                return row;
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
