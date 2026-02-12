package net.questz.quest;

import com.google.common.collect.Maps;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.advancement.AdvancementDisplay;
import net.minecraft.advancement.AdvancementEntry;
import net.minecraft.advancement.PlacedAdvancement;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.advancement.AdvancementTab;
import net.minecraft.client.gui.screen.advancement.AdvancementTabType;
import net.minecraft.client.gui.screen.advancement.AdvancementWidget;
import net.minecraft.client.gui.screen.advancement.AdvancementsScreen;
import net.minecraft.client.texture.TextureManager;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Optional;

@Environment(EnvType.CLIENT)
public class QuestTab extends AdvancementTab {
    private final MinecraftClient client;
    private final AdvancementsScreen screen;
    private final AdvancementTabType type;
    private final int index;
    private final PlacedAdvancement root;
    private final AdvancementDisplay display;
    private final ItemStack icon;
    private final Text title;
    private final QuestWidget rootWidget;
    private final Map<AdvancementEntry, QuestWidget> widgets = Maps.newLinkedHashMap();
    private double originX;
    private double originY;
    private int minPanX = Integer.MAX_VALUE;
    private int minPanY = Integer.MAX_VALUE;
    private int maxPanX = Integer.MIN_VALUE;
    private int maxPanY = Integer.MIN_VALUE;

    private QuestWidget hoveredWidget = null;

    private int oldMaxPanX = 0;
    private int oldMaxPanY = 0;
    private float tabScale = 1.0f;

    private float alpha;
    private boolean initialized;

    public QuestTab(MinecraftClient client, AdvancementsScreen screen, AdvancementTabType type, int index, PlacedAdvancement root, AdvancementDisplay display) {

        super(client, screen, type, index, root, display);
        this.client = client;
        this.screen = screen;
        this.type = type;
        this.index = index;
        this.root = root;
        this.display = display;
        this.icon = display.getIcon();
        this.title = display.getTitle();
        this.rootWidget = new QuestWidget(this, client, root, display);
        this.addWidget(this.rootWidget, root.getAdvancementEntry());
    }

    @Override
    public AdvancementTabType getType() {
        return this.type;
    }

    @Override
    public int getIndex() {
        return this.index;
    }

    @Override
    public PlacedAdvancement getRoot() {
        return this.root;
    }

    @Override
    public Text getTitle() {
        return this.title;
    }

    @Override
    public AdvancementDisplay getDisplay() {
        return this.display;
    }

    @Override
    public void drawBackground(DrawContext context, int x, int y, boolean selected) {
        this.type.drawBackground(context, x, y, selected, this.index);
    }

    @Override
    public void drawIcon(DrawContext context, int x, int y) {
        this.type.drawIcon(context, x, y, this.index, this.icon);
    }

    @Override
    public void render(DrawContext context, int x, int y) {
        if (!this.initialized) {
            float contentCenterX = (this.minPanX + this.maxPanX) / 2.0f;
            float contentCenterY = (this.minPanY + this.maxPanY) / 2.0f;

            this.originX = (119.0f / this.tabScale) - contentCenterX;
            this.originY = (89.5f / this.tabScale) - contentCenterY;

            this.initialized = true;
        }

        context.enableScissor(x, y, x + 238, y + 179);
        context.getMatrices().push();
        context.getMatrices().translate((float) x, (float) y, 0.0F);
        Identifier identifier = this.display.getBackground().orElse(TextureManager.MISSING_IDENTIFIER);
        int i = MathHelper.floor(this.originX);
        int j = MathHelper.floor(this.originY);
        int k = i % 16;
        int l = j % 16;

        context.getMatrices().scale(this.tabScale, this.tabScale, 1.0f);

        float multiplier = MathHelper.square(1.0f + (1.0f - this.tabScale));
        if (this.tabScale > 1.0f) {
            multiplier = 1.0f;
        }
        for (int m = -1; m <= (16 * (multiplier + (this.tabScale < 0.5f ? 0.2f : 0.0f))); ++m) {
            for (int n = -1; n <= (13 * multiplier); ++n) {
                context.drawTexture(identifier, k + 16 * m, l + 16 * n, 0.0f, 0.0f, 16, 16, 16, 16);
            }
        }

        this.rootWidget.renderLines(context, i, j, true);
        this.rootWidget.renderLines(context, i, j, false);
        this.rootWidget.renderWidgets(context, i, j);
        context.getMatrices().pop();
        context.disableScissor();
    }

    @Override
    public void drawWidgetTooltip(DrawContext context, int mouseX, int mouseY, int x, int y) {
        context.getMatrices().push();
        context.getMatrices().translate(0.0F, 0.0F, 400.0F);
        context.fill(0, 0, 238, 179, MathHelper.floor(this.alpha * 255.0F) << 24);
        boolean bl = false;
        int i = MathHelper.floor(this.originX);
        int j = MathHelper.floor(this.originY);

        float scale = this.tabScale;

        int virtualMouseX = MathHelper.floor(mouseX / scale);
        int virtualMouseY = MathHelper.floor(mouseY / scale);

        if (mouseX > 0 && mouseX < 238 && mouseY > 0 && mouseY < 179) {
            for (QuestWidget advancementWidget : this.widgets.values()) {
                if (advancementWidget.shouldRender(i, j, virtualMouseX, virtualMouseY)) {
                    bl = true;

                    int screenWidgetX = MathHelper.floor((this.originX + advancementWidget.getX()) * this.tabScale);
                    int screenWidgetY = MathHelper.floor((this.originY + advancementWidget.getY()) * this.tabScale);

                    context.getMatrices().translate((float)screenWidgetX, (float)screenWidgetY, 0.0F);
                    context.getMatrices().scale(scale,scale,1.0f);

                    int fakeOriginX = -advancementWidget.getX();
                    int fakeOriginY = -advancementWidget.getY();

                    advancementWidget.drawTooltip(context, fakeOriginX, fakeOriginY, this.alpha, x, y);
                    this.hoveredWidget = advancementWidget;
                    break;
                }
            }
        }

        context.getMatrices().pop();
        if (bl) {
            this.alpha = MathHelper.clamp(this.alpha + 0.02F, 0.0F, 0.3F);
        } else {
            this.alpha = MathHelper.clamp(this.alpha - 0.04F, 0.0F, 1.0F);
            this.hoveredWidget = null;
        }
    }

    @Override
    public boolean isClickOnTab(int screenX, int screenY, double mouseX, double mouseY) {
        return this.type.isClickOnTab(screenX, screenY, this.index, mouseX, mouseY);
    }

    @Nullable
    public static QuestTab create(MinecraftClient client, AdvancementsScreen screen, int index, PlacedAdvancement root) {
        Optional<AdvancementDisplay> optional = root.getAdvancement().display();
        if (optional.isEmpty()) {
            return null;
        } else {
            for (AdvancementTabType advancementTabType : AdvancementTabType.values()) {
                if (index < advancementTabType.getTabCount()) {
                    return new QuestTab(client, screen, advancementTabType, index, root, optional.get());
                }

                index -= advancementTabType.getTabCount();
            }

            return null;
        }
    }

    @Override
    public void move(double offsetX, double offsetY) {
        double contentWidth = (this.maxPanX - this.minPanX) * this.tabScale;
        double contentHeight = (this.maxPanY - this.minPanY) * this.tabScale;

        if (contentWidth > 238) {
            this.originX = MathHelper.clamp(this.originX + offsetX, (double) (238f - contentWidth), 0.0f);
        } else {
            this.originX += offsetX;
        }

        if (contentHeight > 179) {
            this.originY = MathHelper.clamp(this.originY + offsetY, (double) (179f - contentHeight), 0.0f);
        } else {
            this.originY += offsetY;
        }
    }

    @Override
    public void addAdvancement(PlacedAdvancement advancement) {
        Optional<AdvancementDisplay> optional = advancement.getAdvancement().display();
        if (!optional.isEmpty()) {
            QuestWidget advancementWidget = new QuestWidget(this, this.client, advancement, optional.get());
            this.addWidget(advancementWidget, advancement.getAdvancementEntry());
        }
    }

    private void addWidget(QuestWidget widget, AdvancementEntry advancement) {
        this.widgets.put(advancement, widget);
        int i = widget.getX();
        int j = i + 28;
        int k = widget.getY();
        int l = k + 27;
        this.minPanX = Math.min(this.minPanX, i);
        this.maxPanX = Math.max(this.maxPanX, j);
        this.minPanY = Math.min(this.minPanY, k);
        this.maxPanY = Math.max(this.maxPanY, l);

        for (AdvancementWidget advancementWidget : this.widgets.values()) {
            advancementWidget.addToTree();
        }
    }

    @Override
    @Nullable
    public QuestWidget getWidget(AdvancementEntry advancement) {
        return this.widgets.get(advancement);
    }

    @Override
    public AdvancementsScreen getScreen() {
        return this.screen;
    }

    public void setTabScale(float tabScale, boolean init) {
        tabScale = Math.round(tabScale * 100.0f) / 100.0f;
        if (init) {
            tabScale = Math.round(tabScale * 10.0f) / 10.0f;
        }
        if (tabScale > 1.3f) {
            this.tabScale = 1.3f;
        } else if (tabScale < 0.3f) {
            this.tabScale = 0.3f;
        } else {
            this.tabScale = tabScale;
        }
    }

    public float getTabScale() {
        return this.tabScale;
    }

    public void setOrigin(double x, double y) {
        this.originX = x;
        this.originY = y;
    }

    public double getOriginX() {
        return this.originX;
    }

    public double getOriginY() {
        return this.originY;
    }

    public QuestWidget getHoveredWidget() {
        return hoveredWidget;
    }

}
