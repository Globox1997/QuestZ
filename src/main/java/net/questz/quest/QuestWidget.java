package net.questz.quest;

import com.google.common.collect.Lists;
import com.mojang.blaze3d.systems.RenderSystem;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.advancement.Advancement;
import net.minecraft.advancement.AdvancementDisplay;
import net.minecraft.advancement.AdvancementProgress;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextHandler;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.advancement.AdvancementObtainedStatus;
import net.minecraft.client.gui.screen.advancement.AdvancementWidget;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.text.MutableText;
import net.minecraft.text.OrderedText;
import net.minecraft.text.StringVisitable;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.text.Texts;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.Language;
import net.minecraft.util.math.MathHelper;
import net.questz.init.ConfigInit;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class QuestWidget extends AdvancementWidget {
    private static final Identifier WIDGETS_TEXTURE = new Identifier("textures/gui/advancements/widgets.png");
    private static final int[] SPLIT_OFFSET_CANDIDATES = new int[] { 0, 10, -10, 25, -25 };
    private final QuestTab tab;
    private final Advancement advancement;
    private final AdvancementDisplay display;
    private final OrderedText title;
    private final MinecraftClient client;
    private final List<QuestWidget> children = Lists.newArrayList();

    private List<Object> itemRenderList = new ArrayList<Object>();
    private int width;
    private List<OrderedText> description;
    @Nullable
    private QuestWidget parent;
    @Nullable
    private AdvancementProgress progress;
    private final int x;
    private final int y;

    public QuestWidget(QuestTab tab, MinecraftClient client, Advancement advancement, AdvancementDisplay display) {
        super(tab, client, advancement, display);
        this.tab = tab;
        this.advancement = advancement;
        this.display = display;
        this.client = client;
        this.title = Language.getInstance().reorder(client.textRenderer.trimToWidth(display.getTitle(), 163));
        this.x = MathHelper.floor(display.getX() * 28.0f);
        this.y = MathHelper.floor(display.getY() * 27.0f);

        if (!display.getDescription().copy().getString().contains("QK:")) {
            setWidthAndDescription(display.getDescription());
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
            float g = Math.abs(QuestWidget.getMaxWidth(textHandler, list2) - (float) width);
            if (g <= 10.0f) {
                return list2;
            }
            if (!(g < f))
                continue;
            f = g;
            list = list2;
        }
        return list;
    }

    @Nullable
    private QuestWidget getParent(Advancement advancement) {
        while ((advancement = advancement.getParent()) != null && advancement.getDisplay() == null) {
        }
        if (advancement == null || advancement.getDisplay() == null) {
            return null;
        }
        return this.tab.getWidget(advancement);
    }

    public void renderLines(DrawContext context, int x, int y, boolean border) {
        if (this.parent != null) {

            int i = x + this.parent.x + 13;
            int j = x + this.parent.x + 26 + 4;
            int k = y + this.parent.y + 13;
            int l = x + this.x + 13;
            int m = y + this.y + 13;
            int n = border ? -16777216 : -1;
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

    public void renderWidgets(DrawContext context, int x, int y) {
        if (!this.display.isHidden() || this.progress != null && this.progress.isDone()) {
            float f = this.progress == null ? 0.0f : this.progress.getProgressBarPercentage();
            AdvancementObtainedStatus advancementObtainedStatus = f >= 1.0f ? AdvancementObtainedStatus.OBTAINED : AdvancementObtainedStatus.UNOBTAINED;
            context.drawTexture(WIDGETS_TEXTURE, x + this.x + 3, y + this.y, this.display.getFrame().getTextureV(), 128 + advancementObtainedStatus.getSpriteIndex() * 26, 26, 26);
            context.drawItemWithoutEntity(this.display.getIcon(), x + this.x + 8, y + this.y + 5);
        }
        for (QuestWidget advancementWidget : this.children) {
            advancementWidget.renderWidgets(context, x, y);
        }
    }

    public int getWidth() {
        return this.width;
    }

    public void setProgress(AdvancementProgress progress) {
        this.progress = progress;
    }

    public void addChild(QuestWidget widget) {
        this.children.add(widget);
    }

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

                            Item item = Registries.ITEM.get(new Identifier(subString));
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
        AdvancementObtainedStatus advancementObtainedStatus3;
        AdvancementObtainedStatus advancementObtainedStatus2;
        AdvancementObtainedStatus advancementObtainedStatus;
        boolean bl = x + originX + this.x + this.width + 26 >= this.tab.getScreen().width;
        String string = this.progress == null ? null : this.progress.getProgressBarFraction();
        int i = string == null ? 0 : this.client.textRenderer.getWidth(string);
        boolean bl2 = 179 - originY - this.y - 26 <= 6 + this.description.size() * this.client.textRenderer.fontHeight;
        float f = this.progress == null ? 0.0f : this.progress.getProgressBarPercentage();
        int j = MathHelper.floor(f * (float) this.width);
        if (f >= 1.0f) {
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
        int m = bl ? originX + this.x - this.width + 26 + 6 : originX + this.x;
        int n = 32 + this.description.size() * this.client.textRenderer.fontHeight;
        if (!this.description.isEmpty()) {
            if (bl2) {
                context.drawNineSlicedTexture(WIDGETS_TEXTURE, m, l + 26 - n, this.width, n, 10, 200, 26, 0, 52);
            } else {
                context.drawNineSlicedTexture(WIDGETS_TEXTURE, m, l, this.width, n, 10, 200, 26, 0, 52);
            }
        }
        context.drawTexture(WIDGETS_TEXTURE, m, l, 0, advancementObtainedStatus.getSpriteIndex() * 26, j, 26);
        context.drawTexture(WIDGETS_TEXTURE, m + j, l, 200 - k, advancementObtainedStatus2.getSpriteIndex() * 26, k, 26);
        context.drawTexture(WIDGETS_TEXTURE, originX + this.x + 3, originY + this.y, this.display.getFrame().getTextureV(), 128 + advancementObtainedStatus3.getSpriteIndex() * 26, 26, 26);
        if (bl) {
            context.drawTextWithShadow(this.client.textRenderer, this.title, m + 5, originY + this.y + 9, -1);
            if (string != null) {
                context.drawTextWithShadow(this.client.textRenderer, string, originX + this.x - i, originY + this.y + 9, -1);
            }
        } else {
            context.drawTextWithShadow(this.client.textRenderer, this.title, originX + this.x + 32, originY + this.y + 9, -1);
            if (string != null) {
                context.drawTextWithShadow(this.client.textRenderer, string, originX + this.x + this.width - i - 5, originY + this.y + 9, -1);
            }
        }
        if (bl2) {
            for (int o = 0; o < this.description.size(); ++o) {
                int posX = m + 5;
                int posY = l + 26 - n + 7 + o * this.client.textRenderer.fontHeight;
                drawItem(context, o, posX, posY);
                context.drawText(this.client.textRenderer, this.description.get(o), posX, posY, -5592406, false);
            }
        } else {
            for (int o = 0; o < this.description.size(); ++o) {
                int posX = m + 5;
                int posY = originY + this.y + 9 + 17 + o * this.client.textRenderer.fontHeight;
                drawItem(context, o, posX, posY);
                context.drawText(this.client.textRenderer, this.description.get(o), posX, posY, -5592406, false);
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

    public boolean shouldRender(DrawContext context, int originX, int originY, int mouseX, int mouseY) {
        if (this.display.isHidden() && (this.progress == null || !this.progress.isDone())) {
            return false;
        }
        float scale = this.tab.getTabScale();

        int i = Math.round(scale * (originX + this.x));
        int j = i + Math.round(scale * 26);
        int k = Math.round(scale * (originY + this.y));
        int l = k + Math.round(scale * 26);
        return mouseX >= i && mouseX <= j && mouseY >= k && mouseY <= l;
    }

    public void addToTree() {
        if (this.parent == null && this.advancement.getParent() != null) {
            this.parent = this.getParent(this.advancement);
            if (this.parent != null) {
                this.parent.addChild(this);
            }
        }
    }

    public int getY() {
        return this.y;
    }

    public int getX() {
        return this.x;
    }

    private void setWidthAndDescription(Text description) {
        int i = advancement.getRequirementCount();
        int j = String.valueOf(i).length();
        int k = i > 1 ? client.textRenderer.getWidth("  ") + client.textRenderer.getWidth("0") * j * 2 + client.textRenderer.getWidth("/") : 0;

        int l = 29 + client.textRenderer.getWidth(this.title) + k;

        if (l > ConfigInit.CONFIG.maxTextWidth) {
        } else if (client.textRenderer.getWidth(description) > ConfigInit.CONFIG.maxTextWidth) {
            l = ConfigInit.CONFIG.maxTextWidth;
        } else if (client.textRenderer.getWidth(description) > l) {
            l = client.textRenderer.getWidth(description);
        }
        if (l < ConfigInit.CONFIG.minTextWidth) {
            l = ConfigInit.CONFIG.minTextWidth;
        }

        this.description = Language.getInstance().reorder(this.wrapDescription(Texts.setStyleIfAbsent(description.copy(), Style.EMPTY.withColor(display.getFrame().getTitleFormat())), l));

        if (!this.itemRenderList.isEmpty()) {
            if ((description.getSiblings().size() + 1) < this.description.size()) {
                int difference = this.description.size() - (description.getSiblings().size() + 1);
                for (int o = 0; o < this.itemRenderList.size() / 3; o++) {
                    this.itemRenderList.set(o * 3, (int) this.itemRenderList.get(o * 3) + difference);
                }
            }
        }
        for (OrderedText orderedText : this.description) {
            l = Math.max(l, client.textRenderer.getWidth(orderedText));
        }
        this.width = l + 8;
    }
}
