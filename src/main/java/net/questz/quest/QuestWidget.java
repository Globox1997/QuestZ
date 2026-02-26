package net.questz.quest;

import com.google.common.collect.Lists;
import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.advancement.*;
import net.minecraft.advancement.criterion.InventoryChangedCriterion;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextHandler;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.advancement.AdvancementObtainedStatus;
import net.minecraft.client.gui.screen.advancement.AdvancementWidget;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.predicate.item.ItemPredicate;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntryList;
import net.minecraft.text.*;
import net.minecraft.util.Colors;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.Language;
import net.minecraft.util.math.MathHelper;
import net.questz.access.RewardAccess;
import net.questz.criteria.QuestCriterion;
import net.questz.init.ConfigInit;
import org.jetbrains.annotations.Nullable;

import java.util.*;

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
    private final List<ItemRenderInfo> itemRenderList = new ArrayList<>();
    private final List<ItemRenderInfo> itemRewardRenderList = new ArrayList<>();
    private final MinecraftClient client;
    @Nullable
    private QuestWidget parent;
    private final List<QuestWidget> children = Lists.newArrayList();
    @Nullable
    private AdvancementProgress progress;
    private int x;
    private int y;

    public QuestWidget(QuestTab tab, MinecraftClient client, PlacedAdvancement advancement, AdvancementDisplay display) {
        super(tab, client, advancement, display);
        this.tab = tab;
        this.advancement = advancement;
        this.display = display;
        this.client = client;
        this.title = Language.getInstance().reorder(client.textRenderer.trimToWidth(display.getTitle(), 163));

        this.x = MathHelper.floor(display.getX() * 28.0F);
        this.y = MathHelper.floor(display.getY() * 28.0F);
        int i = this.getProgressWidth();
        int j = 29 + client.textRenderer.getWidth(this.title) + i;

        this.width = j + 3 + 5;

        buildDescription();
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

        return advancement != null && !advancement.getAdvancement().display().isEmpty()
                ? this.tab.getWidget(advancement.getAdvancementEntry()) : null;
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

            context.drawGuiTexture(advancementObtainedStatus.getFrameTexture(this.display.getFrame()),
                    x + this.x + 3, y + this.y, 26, 26);
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
        buildDescription();
    }

    public void addChild(QuestWidget widget) {
        this.children.add(widget);
    }

    @Override
    public void drawTooltip(DrawContext context, int originX, int originY, float alpha, int x, int y) {
        if (this.description == null) {
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
        context.drawGuiTexture(advancementObtainedStatus3.getFrameTexture(this.display.getFrame()),
                originX + this.x + 3, originY + this.y, 26, 26);

        if (bl) {
            context.drawTextWithShadow(this.client.textRenderer, this.title, m + 5, originY + this.y + 9, -1);
            if (text != null) {
                context.drawTextWithShadow(this.client.textRenderer, text, originX + this.x - i, originY + this.y + 9, Colors.WHITE);
            }
        } else {
            context.drawTextWithShadow(this.client.textRenderer, this.title,
                    originX + this.x + 32, originY + this.y + 9, -1);
            if (text != null) {
                context.drawTextWithShadow(this.client.textRenderer, text, originX + this.x + this.width - i - 5, originY + this.y + 9, Colors.WHITE);
            }
        }

        if (bl2) {
            for (int o = 0; o < this.description.size(); o++) {
                int posX = m + 5;
                int posY = l + 26 - n + 7 + o * this.client.textRenderer.fontHeight;
                drawItems(context, o, posX, posY);
                context.drawText(this.client.textRenderer, this.description.get(o), m + 5, l + 26 - n + 7 + o * 9, -5592406, false);
            }
        } else {
            for (int o = 0; o < this.description.size(); o++) {
                int posX = m + 5;
                int posY = originY + this.y + 9 + 17 + o * this.client.textRenderer.fontHeight;
                drawItems(context, o, posX, posY);
                context.drawText(this.client.textRenderer, this.description.get(o), m + 5, originY + this.y + 9 + 17 + o * 9, -5592406, false);
            }
        }

        context.drawItemWithoutEntity(this.display.getIcon(), originX + this.x + 8, originY + this.y + 5);
    }

    private void drawItems(DrawContext context, int lineIndex, int posX, int posY) {
        for (ItemRenderInfo info : this.itemRenderList) {
            if (info.lineIndex == lineIndex) {
                context.getMatrices().push();
                context.getMatrices().translate(posX + info.xOffset, posY, 0.0D);
                context.getMatrices().scale(0.5f, 0.5f, 1.0f);
                context.drawItem(info.itemStack, 0, 0);
                context.drawItemInSlot(this.client.textRenderer, info.itemStack, 0, 0);
                context.getMatrices().pop();
            }
        }

        for (ItemRenderInfo info : this.itemRewardRenderList) {
            if (info.lineIndex == lineIndex) {
                context.getMatrices().push();
                context.getMatrices().translate(posX + info.xOffset, posY, 0.0D);
                context.getMatrices().scale(0.5f, 0.5f, 1.0f);
                context.drawItem(info.itemStack, 0, 0);
                context.drawItemInSlot(this.client.textRenderer, info.itemStack, 0, 0);
                context.getMatrices().pop();
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

    public void updatePosition(float x, float y) {
        if (this.getAdvancement().getAdvancement().display().isPresent()) {
            AdvancementDisplay display = this.getAdvancement().getAdvancement().display().get();

            display.setPos(x, y);

            this.x = (int) (display.getX() * 28);
            this.y = (int) (display.getY() * 28);
        }
    }

    private void buildDescription() {
        this.itemRenderList.clear();

        List<Text> descriptionParts = new ArrayList<>();
        List<ItemRenderInfo> pendingItems = new ArrayList<>();

        descriptionParts.add(display.getDescription().copy());

        Map<String, AdvancementCriterion<?>> criteria = this.advancement.getAdvancement().criteria();

        if (criteria != null && !criteria.isEmpty() && !criteria.containsKey("quest_start") && (ConfigInit.CONFIG.showRootRequirements && advancement.getParent() == null)) {
            descriptionParts.add(Text.literal(""));
            descriptionParts.add(Text.translatable("gui.questz.requirements").formatted(Formatting.YELLOW));

            criteria.forEach((criterionName, criterion) -> {
                boolean isCompleted = this.progress != null;
                if (isCompleted) {
                    for (String unobtainedCriteria : this.progress.getUnobtainedCriteria()) {
                        if (unobtainedCriteria.equals(criterionName)) {
                            isCompleted = false;
                            break;
                        }
                    }
                }
                Formatting statusColor = isCompleted ? Formatting.GREEN : Formatting.RED;

                if (criterion.trigger() instanceof InventoryChangedCriterion) {
                    InventoryChangedCriterion.Conditions conditions = (InventoryChangedCriterion.Conditions) criterion.conditions();

                    List<ItemPredicate> itemPredicates = conditions.items();

                    if (!itemPredicates.isEmpty()) {
                        for (ItemPredicate predicate : itemPredicates) {
                            Optional<RegistryEntryList<Item>> itemEntry = predicate.items();
                            if (itemEntry.isPresent()) {
                                Item item = itemEntry.get().get(0).value();
                                int count = 1;
                                if (predicate.count().min().isPresent()) {
                                    count = predicate.count().min().get();
                                }

                                String itemName = item.getName().getString();
                                descriptionParts.add(Text.literal("    " + itemName).formatted(statusColor));

                                ItemStack stack = new ItemStack(item, count);
                                pendingItems.add(new ItemRenderInfo(-1, 2, stack, itemName));
                            }
                        }
                    } else {
                        descriptionParts.add(Text.literal("  • " + criterionName).formatted(statusColor));
                    }
                } else if (criterion.trigger() instanceof QuestCriterion) {
                    QuestCriterion.Conditions conditions = (QuestCriterion.Conditions) criterion.conditions();

                    if (Registries.ITEM.get(conditions.objectPredicate().objectId()) != Items.AIR) {
                        Item item = Registries.ITEM.get(conditions.objectPredicate().objectId());
                        int count = conditions.countPredicate().count();

                        String itemName = item.getName().getString();
                        descriptionParts.add(Text.literal("    " + itemName).formatted(statusColor));

                        ItemStack stack = new ItemStack(item, count);
                        pendingItems.add(new ItemRenderInfo(-1, 2, stack, itemName));
                    } else {
                        descriptionParts.add(Text.literal("  • " + criterionName).formatted(statusColor));
                    }
                } else {
                    descriptionParts.add(Text.literal("  • " + criterionName).formatted(statusColor));
                }
            });
        }

        MutableText finalDescription = Text.literal("");
        for (int i = 0; i < descriptionParts.size(); i++) {
            if (i > 0) {
                finalDescription.append("\n");
            }
            finalDescription.append(descriptionParts.get(i));
        }

        setWidthAndDescription(finalDescription, pendingItems);
    }

    private void setWidthAndDescription(Text description, List<ItemRenderInfo> pendingItems) {
        int i = advancement.getAdvancement().requirements().getLength();
        int j = String.valueOf(i).length();
        int k = i > 1 ? client.textRenderer.getWidth("  ") +
                client.textRenderer.getWidth("0") * j * 2 + client.textRenderer.getWidth("/") : 0;
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

        for (ItemRenderInfo pendingItem : pendingItems) {
            String searchText = pendingItem.itemName;

            for (int lineIndex = 0; lineIndex < this.description.size(); lineIndex++) {
                OrderedText line = this.description.get(lineIndex);
                StringBuilder lineText = new StringBuilder();
                line.accept((index, style, codePoint) -> {
                    lineText.appendCodePoint(codePoint);
                    return true;
                });

                if (lineText.toString().contains(searchText) && lineText.toString().split(" ").length - 4 == searchText.split(" ").length) {
                    this.itemRenderList.add(new ItemRenderInfo(
                            lineIndex,
                            pendingItem.xOffset,
                            pendingItem.itemStack,
                            pendingItem.itemName
                    ));
                    break;
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
                    String space = "   " + item.getName().getString();
                    this.description.add(Language.getInstance().reorder(Text.literal(space).formatted(Formatting.GRAY)));

                    ItemStack stack = new ItemStack(item, count);

                    itemRewardRenderList.add(new ItemRenderInfo(
                            this.description.size() - 1,
                            0,
                            stack
                    ));
                }
            });
        }
    }

    private static class ItemRenderInfo {
        final int lineIndex;
        final int xOffset;
        final ItemStack itemStack;
        final String itemName;

        ItemRenderInfo(int lineIndex, int xOffset, ItemStack itemStack, String itemName) {
            this.lineIndex = lineIndex;
            this.xOffset = xOffset;
            this.itemStack = itemStack;
            this.itemName = itemName;
        }

        ItemRenderInfo(int lineIndex, int xOffset, ItemStack itemStack) {
            this(lineIndex, xOffset, itemStack, "");
        }
    }
}