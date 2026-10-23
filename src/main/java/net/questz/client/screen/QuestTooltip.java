package net.questz.client.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.advancement.AdvancementFrame;
import net.minecraft.client.font.TextHandler;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.advancement.AdvancementObtainedStatus;
import net.minecraft.text.*;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.Language;
import net.minecraft.util.math.MathHelper;
import net.questz.book.Quest;
import net.questz.book.QuestBook;
import net.questz.progress.ProgressView;
import net.questz.progress.QuestEngine;
import net.questz.task.Task;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

@Environment(EnvType.CLIENT)
public class QuestTooltip {

    private static final Identifier TITLE_BOX_TEXTURE = Identifier.ofVanilla("advancements/title_box");
    private static final int[] SPLIT_OFFSET_CANDIDATES = new int[]{0, 10, -10, 25, -25};
    private static final int DESCRIPTION_COLOR = -5592406;
    private static final int BAR_TEXTURE_WIDTH = 200;
    private static final int MAX_CONTENT_WIDTH = BAR_TEXTURE_WIDTH - 8;
    private static final int MIN_WRAP_WIDTH = 150;
    private static final int MAX_WRAP_WIDTH = 160;

    public static void draw(DrawContext context, TextRenderer textRenderer, QuestBook book, ProgressView progress, Quest quest, boolean editing, boolean hidden,
                            int centerX, int centerY, int rightLimit, int bottomLimit) {
        int total = quest.tasks().size();
        int done = 0;
        float fraction = 0.0F;
        for (Task task : quest.tasks()) {
            long value = progress.getProgress(quest.id(), task.getId());
            if (value >= task.getTarget()) {
                done++;
            }
            fraction += Math.min(1.0F, (float) value / Math.max(1L, task.getTarget()));
        }
        boolean completed = progress.isCompleted(quest.id());
        float percent = completed ? 1.0F : total == 0 ? 0.0F : fraction / total;
        @Nullable Text progressText = total > 1 ? Text.translatable("advancements.progress", done, total) : null;

        int digits = String.valueOf(total).length();
        int progressWidth = total > 1 ? textRenderer.getWidth("  ") + textRenderer.getWidth("0") * digits * 2 + textRenderer.getWidth("/") : 0;
        OrderedText title = trim(textRenderer, QuestTexts.title(quest), MAX_CONTENT_WIDTH - 29 - progressWidth);
        int width = 29 + textRenderer.getWidth(title) + progressWidth;
        int wrapWidth = MathHelper.clamp(width, MIN_WRAP_WIDTH, MAX_WRAP_WIDTH);
        AdvancementFrame frame = quest.shape().getFrame();

        List<OrderedText> lines = new ArrayList<>();
        if (!QuestTexts.isUnlocked(book, progress, quest) && !quest.dependencies().isEmpty()) {
            lines.addAll(Language.getInstance().reorder(wrapDescription(textRenderer, QuestTexts.requirements(book, progress, quest), wrapWidth)));
        }
        if (!quest.description().isEmpty()) {
            MutableText description = Text.empty();
            for (int i = 0; i < quest.description().size(); i++) {
                if (i > 0) {
                    description.append("\n");
                }
                description.append(quest.description().get(i));
            }
            Text styled = Texts.setStyleIfAbsent(description, Style.EMPTY.withColor(frame.getTitleFormat()));
            lines.addAll(Language.getInstance().reorder(wrapDescription(textRenderer, styled, wrapWidth)));
        }
        if (quest.repeatable()) {
            lines.add(Text.translatable("gui.questz.repeatable", progress.getCompletions(quest.id())).formatted(Formatting.AQUA).asOrderedText());
        }
        if (QuestTexts.isClaimable(quest)) {
            lines.add(Text.translatable("gui.questz.rewards_ready").formatted(Formatting.GOLD).asOrderedText());
        }
        if (editing) {
            if (hidden) {
                lines.add(Text.translatable("gui.questz.editor.hidden").formatted(Formatting.BLUE).asOrderedText());
            }
            lines.add(Text.literal(quest.id()).formatted(Formatting.DARK_GRAY).asOrderedText());
        }
        if (!lines.isEmpty()) {
            width = Math.max(width, wrapWidth);
        }
        for (OrderedText line : lines) {
            width = Math.max(width, textRenderer.getWidth(line));
        }
        width = Math.min(width, MAX_CONTENT_WIDTH) + 3 + 5;

        int x = centerX - 16;
        int y = centerY - 13;
        boolean flipLeft = x + width + 26 >= rightLimit;
        boolean flipUp = bottomLimit - y - 26 <= 6 + lines.size() * 9;
        int filled = MathHelper.floor(percent * width);
        AdvancementObtainedStatus leftStatus;
        AdvancementObtainedStatus rightStatus;
        AdvancementObtainedStatus frameStatus;
        if (percent >= 1.0F) {
            filled = width / 2;
            leftStatus = AdvancementObtainedStatus.OBTAINED;
            rightStatus = AdvancementObtainedStatus.OBTAINED;
            frameStatus = AdvancementObtainedStatus.OBTAINED;
        } else if (filled < 2) {
            filled = width / 2;
            leftStatus = AdvancementObtainedStatus.UNOBTAINED;
            rightStatus = AdvancementObtainedStatus.UNOBTAINED;
            frameStatus = AdvancementObtainedStatus.UNOBTAINED;
        } else if (filled > width - 2) {
            filled = width / 2;
            leftStatus = AdvancementObtainedStatus.OBTAINED;
            rightStatus = AdvancementObtainedStatus.OBTAINED;
            frameStatus = AdvancementObtainedStatus.UNOBTAINED;
        } else {
            leftStatus = AdvancementObtainedStatus.OBTAINED;
            rightStatus = AdvancementObtainedStatus.UNOBTAINED;
            frameStatus = AdvancementObtainedStatus.UNOBTAINED;
        }
        int remaining = width - filled;

        context.getMatrices().push();
        context.getMatrices().translate(0.0F, 0.0F, 400.0F);
        RenderSystem.enableBlend();
        int boxX = flipLeft ? x - width + 26 + 6 : x;
        int boxHeight = 32 + lines.size() * 9;
        if (!lines.isEmpty()) {
            context.drawGuiTexture(TITLE_BOX_TEXTURE, boxX, flipUp ? y + 26 - boxHeight : y, width, boxHeight);
        }
        context.drawGuiTexture(leftStatus.getBoxTexture(), 200, 26, 0, 0, boxX, y, filled, 26);
        context.drawGuiTexture(rightStatus.getBoxTexture(), 200, 26, 200 - remaining, 0, boxX + filled, y, remaining, 26);
        float[] tint = QuestTexts.frameTint(quest, editing, completed);
        RenderSystem.setShaderColor(tint[0], tint[1], tint[2], 1.0F);
        context.drawGuiTexture(QuestTexts.frameStatus(quest, frameStatus).getFrameTexture(frame), x + 3, y, 26, 26);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);

        if (flipLeft) {
            context.drawTextWithShadow(textRenderer, title, boxX + 5, y + 9, -1);
            if (progressText != null) {
                context.drawTextWithShadow(textRenderer, progressText, x - textRenderer.getWidth(progressText), y + 9, -1);
            }
        } else {
            context.drawTextWithShadow(textRenderer, title, x + 32, y + 9, -1);
            if (progressText != null) {
                context.drawTextWithShadow(textRenderer, progressText, x + width - textRenderer.getWidth(progressText) - 5, y + 9, -1);
            }
        }
        for (int i = 0; i < lines.size(); i++) {
            int lineY = flipUp ? y + 26 - boxHeight + 7 + i * 9 : y + 9 + 17 + i * 9;
            context.drawText(textRenderer, lines.get(i), boxX + 5, lineY, DESCRIPTION_COLOR, false);
        }
        QuestTexts.drawIcon(context, QuestTexts.icon(quest), x + 8, y + 5);
        context.getMatrices().pop();
    }

    private static OrderedText trim(TextRenderer textRenderer, Text text, int width) {
        if (textRenderer.getWidth(text) <= width) {
            return text.asOrderedText();
        }
        StringVisitable trimmed = textRenderer.trimToWidth(text, width - textRenderer.getWidth("..."));
        return Language.getInstance().reorder(StringVisitable.concat(trimmed, StringVisitable.plain("...")));
    }

    private static List<StringVisitable> wrapDescription(TextRenderer textRenderer, Text text, int width) {
        TextHandler handler = textRenderer.getTextHandler();
        List<StringVisitable> best = null;
        float bestDifference = Float.MAX_VALUE;
        for (int offset : SPLIT_OFFSET_CANDIDATES) {
            List<StringVisitable> lines = handler.wrapLines(text, Math.min(width - offset, MAX_CONTENT_WIDTH), Style.EMPTY);
            float difference = Math.abs(getMaxWidth(handler, lines) - width);
            if (difference <= 10.0F) {
                return lines;
            }
            if (difference < bestDifference) {
                bestDifference = difference;
                best = lines;
            }
        }
        return best != null ? best : List.of();
    }

    private static float getMaxWidth(TextHandler handler, List<StringVisitable> lines) {
        float max = 0.0F;
        for (StringVisitable line : lines) {
            max = Math.max(max, handler.getWidth(line));
        }
        return max;
    }

}
