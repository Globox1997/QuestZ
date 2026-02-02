package net.questz.quest;

import com.google.common.collect.Maps;

import java.util.Map;
import java.util.Objects;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.advancement.Advancement;
import net.minecraft.advancement.AdvancementDisplay;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.advancement.AdvancementTab;
import net.minecraft.client.gui.screen.advancement.AdvancementTabType;
import net.minecraft.client.texture.TextureManager;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import org.jetbrains.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class QuestTab extends AdvancementTab {
    private final MinecraftClient client;
    private final QuestScreen screen;
    private final AdvancementTabType type;
    private final int index;
    private final Advancement root;
    private final AdvancementDisplay display;
    private final ItemStack icon;
    private final Text title;
    private final QuestWidget rootWidget;
    private final Map<Advancement, QuestWidget> widgets = Maps.newLinkedHashMap();
    private double originX;
    private double originY;
    private int minPanX = Integer.MAX_VALUE;
    private int minPanY = Integer.MAX_VALUE;
    private int maxPanX = Integer.MIN_VALUE;
    private int maxPanY = Integer.MIN_VALUE;
    private int oldMaxPanX = 0;
    private int oldMaxPanY = 0;
    private float alpha;
    private boolean initialized;
    private float tabScale = 1.0f;

    public QuestTab(MinecraftClient client, QuestScreen screen, AdvancementTabType type, int index, Advancement root, AdvancementDisplay display) {
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
        this.addWidget(this.rootWidget, root);
    }

    public AdvancementTabType getType() {
        return this.type;
    }

    public int getIndex() {
        return this.index;
    }

    public Advancement getRoot() {
        return this.root;
    }

    public Text getTitle() {
        return this.title;
    }

    public AdvancementDisplay getDisplay() {
        return this.display;
    }

    public void drawBackground(DrawContext context, int x, int y, boolean selected) {
        this.type.drawBackground(context, x, y, selected, this.index);
    }

    public void drawIcon(DrawContext context, int x, int y) {
        this.type.drawIcon(context, x, y, this.index, this.icon);
    }

    public void render(DrawContext context, int x, int y) {
        if (!this.initialized) {

            // System.out.println("BEFORE INIT: " + this.maxPanX + " : " + this.maxPanY);

            // if (this.maxPanX < 238) {
            // this.maxPanX = Math.max(this.maxPanX, 238);
            // this.minPanX = Math.min(this.minPanY, 0);
            // this.oldMaxPanX = this.maxPanX;
            // }
            // if (this.maxPanY < 179) {
            // this.maxPanY = Math.max(this.maxPanY, 179);
            // this.minPanY = Math.min(this.minPanX, 0);
            // this.oldMaxPanY = this.maxPanY;
            // }

            if (238.0f / (float) this.maxPanX < 1.0f) {
                setTabScale(238.0f / (float) this.maxPanX, true);
                this.maxPanX *= this.tabScale;
                this.maxPanY *= this.tabScale;
            }

            // float test = 1.0f + this.tabScale;

            this.originX = (119.0f - (this.oldMaxPanX + this.minPanX) * this.tabScale / 2f);
            this.originY = ((89.5f * (1.0f + 1.0f - this.tabScale)) - (this.oldMaxPanY + this.minPanY) * this.tabScale / 2f);
            // this.originX = (119.0f - (this.oldMaxPanX + this.minPanX) * this.tabScale / 2f);
            // this.originY = (89.5f - (this.oldMaxPanY + this.minPanY) * this.tabScale / 2f);

            // 83,13,0
            // System.out.println(this.originY + " : " + this.maxPanY + " : " + this.minPanY);
            // this.originX = (119.0f - (this.maxPanX + this.minPanX) / 2f) * this.tabScale;
            // this.originY = (89.5f - (this.maxPanY + this.minPanY) / 2f) * this.tabScale;

            // System.out.println(this.originX + " : " + this.originY);

            // context.enableScissor(x, y, x + 234, y + 113);
            // this.originX = 117 - (this.maxPanX + this.minPanX) / 2;
            // this.originY = 56 - (this.maxPanY + this.minPanY) / 2;

            // this.originX = (119.0f - (this.maxPanX + this.minPanX) / 2f) * this.tabScale;
            // this.originY = (89.5f - (this.maxPanY + this.minPanY) / 2f) * this.tabScale;

            this.initialized = true;
        }
        context.enableScissor(x, y, x + 238, y + 179);
        context.getMatrices().push();
        context.getMatrices().translate(x, y, 0.0f);
        Identifier identifier = Objects.requireNonNullElse(this.display.getBackground(), TextureManager.MISSING_IDENTIFIER);
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

    public void drawWidgetTooltip(DrawContext context, int mouseX, int mouseY, int x, int y) {
        context.getMatrices().push();
        context.getMatrices().translate(0.0f, 0.0f, -200.0f);
        context.fill(0, 0, 238, 179, MathHelper.floor(this.alpha * 255.0f) << 24);
        boolean bl = false;
        int i = MathHelper.floor(this.originX);
        int j = MathHelper.floor(this.originY);
        context.getMatrices().scale(this.tabScale, this.tabScale, 1.0f);
        if (mouseX > 0 && mouseX < 238 && mouseY > 0 && mouseY < 179) {
            for (QuestWidget advancementWidget : this.widgets.values()) {
                if (!advancementWidget.shouldRender(context, i, j, mouseX, mouseY)) {
                    continue;
                }
                bl = true;
                advancementWidget.drawTooltip(context, i, j, this.alpha, x, y);
                break;
            }
        }
        context.getMatrices().pop();
        this.alpha = bl ? MathHelper.clamp(this.alpha + 0.02f, 0.0f, 0.3f) : MathHelper.clamp(this.alpha - 0.04f, 0.0f, 1.0f);
    }

    public boolean isClickOnTab(int screenX, int screenY, double mouseX, double mouseY) {
        return this.type.isClickOnTab(screenX, screenY, this.index, mouseX, mouseY);
    }

    @Nullable
    public static QuestTab create(MinecraftClient client, QuestScreen screen, int index, Advancement root) {
        if (root.getDisplay() == null) {
            return null;
        }
        for (AdvancementTabType advancementTabType : AdvancementTabType.values()) {
            if (index >= advancementTabType.getTabCount()) {
                index -= advancementTabType.getTabCount();
                continue;
            }
            return new QuestTab(client, screen, advancementTabType, index, root, root.getDisplay());
        }
        return null;
    }

    public void move(double offsetX, double offsetY) {
        this.originX = MathHelper.clamp(this.originX + offsetX, -10000.0D, 10000.0D);
        this.originY = MathHelper.clamp(this.originY + offsetY, -10000.0D, 10000.0D);
        // if (this.oldMaxPanX - this.minPanX > 238) {
        // this.originX = MathHelper.clamp(this.originX + offsetX, -this.maxPanX + 188D, 50.0D);
        // }
        // if (this.oldMaxPanY - this.minPanY > 179) {
        // this.originY = MathHelper.clamp(this.originY + offsetY, -this.maxPanY + 129D, 50.0D);
        // }
    }

    public void addAdvancement(Advancement advancement) {
        if (advancement.getDisplay() == null) {
            return;
        }
        QuestWidget advancementWidget = new QuestWidget(this, this.client, advancement, advancement.getDisplay());
        this.addWidget(advancementWidget, advancement);
    }

    private void addWidget(QuestWidget widget, Advancement advancement) {
        this.widgets.put(advancement, widget);
        int i = widget.getX();
        int j = i + 28;
        int k = widget.getY();
        int l = k + 27;
        this.minPanX = Math.min(this.minPanX, i);
        this.maxPanX = Math.max(this.maxPanX, j);
        this.minPanY = Math.min(this.minPanY, k);
        this.maxPanY = Math.max(this.maxPanY, l);

        for (QuestWidget advancementWidget : this.widgets.values()) {
            advancementWidget.addToTree();
        }
        this.oldMaxPanX = this.maxPanX;
        this.oldMaxPanY = this.maxPanY;
    }

    @Nullable
    public QuestWidget getWidget(Advancement advancement) {
        return this.widgets.get(advancement);
    }

    public QuestScreen getScreen() {
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

    public void setMaxPan(int maxPanX, int maxPanY) {
        this.maxPanX = maxPanX;
        this.maxPanY = maxPanY;
    }

    public int getMaxPan() {
        return this.maxPanX;
    }

    public int getOldMaxPanX() {
        return this.oldMaxPanX;
    }

    public int getOldMaxPanY() {
        return this.oldMaxPanY;
    }
}
