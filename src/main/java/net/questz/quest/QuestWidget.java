package net.questz.quest;

import com.google.common.collect.Lists;
import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.advancement.AdvancementDisplay;
import net.minecraft.advancement.AdvancementProgress;
import net.minecraft.advancement.AdvancementRewards;
import net.minecraft.advancement.PlacedAdvancement;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextHandler;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.advancement.AdvancementObtainedStatus;
import net.minecraft.client.gui.screen.advancement.AdvancementWidget;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.text.*;
import net.minecraft.util.Colors;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.Language;
import net.minecraft.util.math.MathHelper;
import net.questz.access.RewardAccess;
import net.questz.init.ConfigInit;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

@Environment(EnvType.CLIENT)
public class QuestWidget extends AdvancementWidget {
    private static final Identifier TITLE_BOX_TEXTURE = Identifier.ofVanilla("advancements/title_box");

    private static final int[] SPLIT_OFFSET_CANDIDATES = new int[]{0, 10, -10, 25, -25};
    private final QuestTab tab;
    private final PlacedAdvancement advancement;
    private final AdvancementDisplay display;
    private final OrderedText title;
    private int width;
    private List<OrderedText> description;
    private final List<Object> itemRenderList = new ArrayList<>();
    private final List<Object> itemRewardRenderList = new ArrayList<>();
    private final MinecraftClient client;
    @Nullable
    private QuestWidget parent;
    private final List<QuestWidget> children = Lists.newArrayList();
    @Nullable
    private AdvancementProgress progress;
    private final int x;
    private final int y;

    public QuestWidget(QuestTab tab, MinecraftClient client, PlacedAdvancement advancement, AdvancementDisplay display) {
        super(tab, client, advancement, display);
        this.tab = tab;
        this.advancement = advancement;
        this.display = display;
        this.client = client;
        this.title = Language.getInstance().reorder(client.textRenderer.trimToWidth(display.getTitle(), 163));

        this.x = MathHelper.floor(display.getX() * 28.0F);
        this.y = MathHelper.floor(display.getY() * 27.0F);
        int i = this.getProgressWidth();
        int j = 29 + client.textRenderer.getWidth(this.title) + i;
//        this.description = Language.getInstance()
//                .reorder(this.wrapDescription(Texts.setStyleIfAbsent(display.getDescription().copy(), Style.EMPTY.withColor(display.getFrame().getTitleFormat())), j));
//
//        for (OrderedText orderedText : this.description) {
//            j = Math.max(j, client.textRenderer.getWidth(orderedText));
//        }

        this.width = j + 3 + 5;

        if (!display.getDescription().copy().getString().contains("QK:")) {
            setWidthAndDescription(display.getDescription());
        }
    }

    private int getProgressWidth() {
        int i = this.advancement.getAdvancement().requirements().getLength();
        if (i <= 1) {
            return 0;
        } else {
            int j = 8;
            Text text = Text.translatable("advancements.progress", i, i);
            return this.client.textRenderer.getWidth(text) + 8;
        }
    }

    private static float getMaxWidth(TextHandler textHandler, List<StringVisitable> lines) {
        return (float) lines.stream().mapToDouble(textHandler::getWidth).max().orElse(0.0);
    }

    private List<StringVisitable> wrapDescription(Text text, int width) {
        TextHandler textHandler = this.client.textRenderer.getTextHandler();
        List<StringVisitable> list = null;
        float f = Float.MAX_VALUE;

        for (int i : SPLIT_OFFSET_CANDIDATES) {
            List<StringVisitable> list2 = textHandler.wrapLines(text, width - i, Style.EMPTY);
            float g = Math.abs(getMaxWidth(textHandler, list2) - width);
            if (g <= 10.0F) {
                return list2;
            }

            if (g < f) {
                f = g;
                list = list2;
            }
        }

        return list;
    }

    @Nullable
    private QuestWidget getParent(PlacedAdvancement advancement) {
        do {
            advancement = advancement.getParent();
        } while (advancement != null && advancement.getAdvancement().display().isEmpty());

        return advancement != null && !advancement.getAdvancement().display().isEmpty() ? this.tab.getWidget(advancement.getAdvancementEntry()) : null;
    }

    @Override
    public void renderLines(DrawContext context, int x, int y, boolean border) {
        if (this.parent != null) {
            int i = x + this.parent.x + 13;
            int j = x + this.parent.x + 26 + 4;
            int k = y + this.parent.y + 13;
            int l = x + this.x + 13;
            int m = y + this.y + 13;
            int n = border ? Colors.BLACK : Colors.WHITE;
            if (border) {
                context.drawHorizontalLine(j, i, k - 1, n);
                context.drawHorizontalLine(j + 1, i, k, n);
                context.drawHorizontalLine(j, i, k + 1, n);
                context.drawHorizontalLine(l, j - 1, m - 1, n);
                context.drawHorizontalLine(l, j - 1, m, n);
                context.drawHorizontalLine(l, j - 1, m + 1, n);
                context.drawVerticalLine(j - 1, m, k, n);
                context.drawVerticalLine(j + 1, m, k, n);
            } else {
                context.drawHorizontalLine(j, i, k, n);
                context.drawHorizontalLine(l, j, m, n);
                context.drawVerticalLine(j, m, k, n);
            }
        }

        for (QuestWidget advancementWidget : this.children) {
            advancementWidget.renderLines(context, x, y, border);
        }
    }

    @Override
    public void renderWidgets(DrawContext context, int x, int y) {
        if (!this.display.isHidden() || this.progress != null && this.progress.isDone()) {
            float f = this.progress == null ? 0.0F : this.progress.getProgressBarPercentage();
            AdvancementObtainedStatus advancementObtainedStatus;
            if (f >= 1.0F) {
                advancementObtainedStatus = AdvancementObtainedStatus.OBTAINED;
            } else {
                advancementObtainedStatus = AdvancementObtainedStatus.UNOBTAINED;
            }

            context.drawGuiTexture(advancementObtainedStatus.getFrameTexture(this.display.getFrame()), x + this.x + 3, y + this.y, 26, 26);
            context.drawItemWithoutEntity(this.display.getIcon(), x + this.x + 8, y + this.y + 5);
        }

        for (QuestWidget advancementWidget : this.children) {
            advancementWidget.renderWidgets(context, x, y);
        }
    }

    @Override
    public int getWidth() {
        return this.width;
    }

    @Override
    public void setProgress(AdvancementProgress progress) {
        this.progress = progress;
    }

    public void addChild(QuestWidget widget) {
        this.children.add(widget);
    }

    @Override
    public void drawTooltip(DrawContext context, int originX, int originY, float alpha, int x, int y) {
        if (this.description == null) {
            if (this.progress != null) {

                MutableText text = display.getDescription().copy();
                if (text.getString().contains("QK:")) {

                    List<Text> siblings = text.getSiblings();
                    for (int i = 0; i < siblings.size(); i++) {

                        String subString = StringUtils.substringBetween(siblings.get(i).getString(), "QK:", "*");
                        Iterator<String> iterator = this.progress.getUnobtainedCriteria().iterator();

                        TextColor textColor = siblings.get(i).copy().getStyle().getColor();
                        String newText = siblings.get(i).copyContentOnly().getString().replace("QK:" + subString + "*", "");
                        if (subString != null && newText.contains("%I%")) {

                            if (!subString.contains(":")) {
                                subString = "minecraft:" + subString;
                            }
                            Item item = Registries.ITEM.get(Identifier.of(subString));
                            if (item != Items.AIR) {

                                String widthString = newText.replace(newText.substring(newText.indexOf("§"), newText.indexOf("§") + 2), "");
                                while (widthString.contains("§")) {
                                    widthString = widthString.replace(newText.substring(newText.indexOf("§"), newText.indexOf("§") + 2), "");
                                }
                                int index = widthString.indexOf("%I%");
                                itemRenderList.add(i + 1);
                                itemRenderList.add(this.client.textRenderer.getWidth(widthString.substring(0, index)) - 11);
                                itemRenderList.add(new ItemStack(item));

                                newText = newText.replace("%I%", " ");
                            }
                        }
                        newText = newText.replace("%I%", "");

                        while (iterator.hasNext()) {
                            if (iterator.next().equals(subString)) {
                                // Could get tweaked here
                                while (newText.contains("§")) {
                                    newText = newText.replace(newText.substring(newText.indexOf("§"), newText.indexOf("§") + 2), "");
                                }
                                textColor = TextColor.fromFormatting(Formatting.RED);
                                break;
                            }
                        }
                        Text siblingReplacement = Text.of(newText).copy().setStyle(siblings.get(i).getStyle().withColor(textColor));
                        siblings.set(i, siblingReplacement);
                    }
                    setWidthAndDescription(text);
                }
            }
            return;
        }

        boolean bl = x + originX + this.x + this.width + 26 >= this.tab.getScreen().width;
        Text text = this.progress == null ? null : this.progress.getProgressBarFraction();
        int i = text == null ? 0 : this.client.textRenderer.getWidth(text);
        boolean bl2 = 179 - originY - this.y - 26 <= 6 + this.description.size() * 9;
        float f = this.progress == null ? 0.0F : this.progress.getProgressBarPercentage();
        int j = MathHelper.floor(f * this.width);
        AdvancementObtainedStatus advancementObtainedStatus;
        AdvancementObtainedStatus advancementObtainedStatus2;
        AdvancementObtainedStatus advancementObtainedStatus3;
        if (f >= 1.0F) {
            j = this.width / 2;
            advancementObtainedStatus = AdvancementObtainedStatus.OBTAINED;
            advancementObtainedStatus2 = AdvancementObtainedStatus.OBTAINED;
            advancementObtainedStatus3 = AdvancementObtainedStatus.OBTAINED;
        } else if (j < 2) {
            j = this.width / 2;
            advancementObtainedStatus = AdvancementObtainedStatus.UNOBTAINED;
            advancementObtainedStatus2 = AdvancementObtainedStatus.UNOBTAINED;
            advancementObtainedStatus3 = AdvancementObtainedStatus.UNOBTAINED;
        } else if (j > this.width - 2) {
            j = this.width / 2;
            advancementObtainedStatus = AdvancementObtainedStatus.OBTAINED;
            advancementObtainedStatus2 = AdvancementObtainedStatus.OBTAINED;
            advancementObtainedStatus3 = AdvancementObtainedStatus.UNOBTAINED;
        } else {
            advancementObtainedStatus = AdvancementObtainedStatus.OBTAINED;
            advancementObtainedStatus2 = AdvancementObtainedStatus.UNOBTAINED;
            advancementObtainedStatus3 = AdvancementObtainedStatus.UNOBTAINED;
        }

        int k = this.width - j;
        RenderSystem.enableBlend();
        int l = originY + this.y;
        int m;
        if (bl) {
            m = originX + this.x - this.width + 26 + 6;
        } else {
            m = originX + this.x;
        }

        int n = 32 + this.description.size() * 9;
        if (!this.description.isEmpty()) {
            if (bl2) {
                context.drawGuiTexture(TITLE_BOX_TEXTURE, m, l + 26 - n, this.width, n);
            } else {
                context.drawGuiTexture(TITLE_BOX_TEXTURE, m, l, this.width, n);
            }
        }

        context.drawGuiTexture(advancementObtainedStatus.getBoxTexture(), 200, 26, 0, 0, m, l, j, 26);
        context.drawGuiTexture(advancementObtainedStatus2.getBoxTexture(), 200, 26, 200 - k, 0, m + j, l, k, 26);
        context.drawGuiTexture(advancementObtainedStatus3.getFrameTexture(this.display.getFrame()), originX + this.x + 3, originY + this.y, 26, 26);
        if (bl) {
            context.drawTextWithShadow(this.client.textRenderer, this.title, m + 5, originY + this.y + 9, -1);
            if (text != null) {
                context.drawTextWithShadow(this.client.textRenderer, text, originX + this.x - i, originY + this.y + 9, Colors.WHITE);
            }
        } else {
            context.drawTextWithShadow(this.client.textRenderer, this.title, originX + this.x + 32, originY + this.y + 9, -1);
            if (text != null) {
                context.drawTextWithShadow(this.client.textRenderer, text, originX + this.x + this.width - i - 5, originY + this.y + 9, Colors.WHITE);
            }
        }

        if (bl2) {
            for (int o = 0; o < this.description.size(); o++) {
                int posX = m + 5;
                int posY = l + 26 - n + 7 + o * this.client.textRenderer.fontHeight;
                drawItem(context, o, posX, posY);
                drawRewardItem(context, o, posX, posY);
                context.drawText(this.client.textRenderer, this.description.get(o), m + 5, l + 26 - n + 7 + o * 9, -5592406, false);
            }
        } else {
            for (int o = 0; o < this.description.size(); o++) {
                int posX = m + 5;
                int posY = originY + this.y + 9 + 17 + o * this.client.textRenderer.fontHeight;
                drawItem(context, o, posX, posY);
                drawRewardItem(context, o, posX, posY);
                context.drawText(this.client.textRenderer, this.description.get(o), m + 5, originY + this.y + 9 + 17 + o * 9, -5592406, false);
            }
        }

        context.drawItemWithoutEntity(this.display.getIcon(), originX + this.x + 8, originY + this.y + 5);
    }

    private void drawItem(DrawContext context, int itemIndex, int posX, int posY) {
        if (!this.itemRenderList.isEmpty()) {
            for (int h = 0; h < this.itemRenderList.size() / 3; h++) {
                if (itemIndex == (int) this.itemRenderList.get(h * 3)) {
                    context.getMatrices().push();
                    context.getMatrices().translate(posX + (int) this.itemRenderList.get(h * 3 + 1), posY, 0.0D);
                    context.getMatrices().scale(0.5f, 0.5f, 1.0f);
                    context.getMatrices().translate(ConfigInit.CONFIG.test, ConfigInit.CONFIG.test, 0.0);
                    context.drawItem((ItemStack) this.itemRenderList.get(h * 3 + 2), 0, 0);
                    context.getMatrices().pop();
                    break;
                }
            }
        }
    }

    @Override
    public boolean shouldRender(int originX, int originY, int mouseX, int mouseY) {
        if (!this.display.isHidden() || this.progress != null && this.progress.isDone()) {
            int i = originX + this.x;
            int j = i + 26;
            int k = originY + this.y;
            int l = k + 26;
            return mouseX >= i && mouseX <= j && mouseY >= k && mouseY <= l;
        } else {
            return false;
        }
    }

    @Override
    public void addToTree() {
        if (this.parent == null && this.advancement.getParent() != null) {
            this.parent = this.getParent(this.advancement);
            if (this.parent != null) {
                this.parent.addChild(this);
            }
        }
    }

    @Override
    public int getY() {
        return this.y;
    }

    @Override
    public int getX() {
        return this.x;
    }

    public PlacedAdvancement getAdvancement() {
        return this.advancement;
    }

    private void setWidthAndDescription(Text description) {
        int i = advancement.getAdvancement().requirements().getLength();
        int j = String.valueOf(i).length();
        int k = i > 1 ? client.textRenderer.getWidth("  ") + client.textRenderer.getWidth("0") * j * 2 + client.textRenderer.getWidth("/") : 0;
        int l = 29 + client.textRenderer.getWidth(this.title) + k;

        if (client.textRenderer.getWidth(description) > l) {
            l = Math.min(ConfigInit.CONFIG.maxTextWidth, client.textRenderer.getWidth(description));
        }
        l = MathHelper.clamp(l, ConfigInit.CONFIG.minTextWidth, ConfigInit.CONFIG.maxTextWidth);

        if (i > 1) {
            Text progressText = Text.translatable("advancements.progress", i, i);
            l += this.client.textRenderer.getWidth(progressText);
        }

        this.description = new ArrayList<>(Language.getInstance().reorder(
                this.wrapDescription(Texts.setStyleIfAbsent(description.copy(),
                        Style.EMPTY.withColor(display.getFrame().getTitleFormat())), l)
        ));

        if (!this.itemRenderList.isEmpty()) {
            int originalSiblingCount = description.getSiblings().size() + 1;
            if (originalSiblingCount < this.description.size()) {
                int difference = this.description.size() - originalSiblingCount;
                for (int o = 0; o < this.itemRenderList.size() / 3; o++) {
                    int oldIndex = (int) this.itemRenderList.get(o * 3);
                    this.itemRenderList.set(o * 3, oldIndex + difference);
                }
            }
        }

        appendCustomRewards();

        for (OrderedText orderedText : this.description) {
            l = Math.max(l, client.textRenderer.getWidth(orderedText));
        }
        this.width = l + 8;
    }

    private void appendCustomRewards() {
        AdvancementRewards rewards = this.advancement.getAdvancement().rewards();
        RewardAccess rewardAccess = (RewardAccess) (Object) rewards;

        String rewardText = rewardAccess.questz$getText();
        boolean emptyLine = false;

        if (rewardText != null && !rewardText.isEmpty()) {
            this.description.add(OrderedText.EMPTY);
            emptyLine = true;
            Text formattedText = Text.literal(rewardText).formatted(Formatting.GOLD);
            this.description.addAll(Language.getInstance().reorder(this.wrapDescription(formattedText, 160)));
        }

        Map<Identifier, Integer> items = rewardAccess.questz$getItems();
        if (items != null && !items.isEmpty()) {
            if (!emptyLine) {
                this.description.add(OrderedText.EMPTY);
                this.description.add(Language.getInstance().reorder(Text.translatable("gui.questz.reward").formatted(Formatting.GOLD)));
            }

            items.forEach((id, count) -> {
                Item item = Registries.ITEM.get(id);
                if (item != Items.AIR) {
                    String space = "   x" + count + " " + item.getName().getString();
                    this.description.add(Language.getInstance().reorder(Text.literal(space).formatted(Formatting.GRAY)));

                    this.itemRewardRenderList.add(this.description.size() - 1);
                    this.itemRewardRenderList.add(0);
                    this.itemRewardRenderList.add(new ItemStack(item));
                }
            });
        }
    }

    private void drawRewardItem(DrawContext context, int itemIndex, int posX, int posY) {
        if (!this.itemRewardRenderList.isEmpty()) {
            for (int h = 0; h < this.itemRewardRenderList.size() / 3; h++) {
                if (itemIndex == (int) this.itemRewardRenderList.get(h * 3)) {
                    context.getMatrices().push();
                    context.getMatrices().translate(posX + (int) this.itemRewardRenderList.get(h * 3 + 1), posY, 0.0D);
                    context.getMatrices().scale(0.5f, 0.5f, 1.0f);
                    context.drawItem((ItemStack) this.itemRewardRenderList.get(h * 3 + 2), 0, 0);
                    context.getMatrices().pop();
                    break;
                }
            }
        }
    }
}
