package net.questz.quest;

import com.google.common.collect.Maps;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.Map;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.advancement.Advancement;
import net.minecraft.advancement.AdvancementProgress;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.advancement.AdvancementWidget;
import net.minecraft.client.gui.screen.advancement.AdvancementsScreen;
import net.minecraft.client.network.ClientAdvancementManager;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.c2s.play.AdvancementTabC2SPacket;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.questz.init.ConfigInit;
import net.questz.init.KeyInit;

import org.jetbrains.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class QuestScreen extends AdvancementsScreen {

    private static final Identifier WINDOW_TEXTURE = new Identifier("questz", "textures/gui/window.png");
    private static final Text QUESTS_TEXT = Text.translatable("gui.questz");

    private final ClientAdvancementManager advancementHandler;
    private final Map<Advancement, QuestTab> tabs = Maps.newLinkedHashMap();
    @Nullable
    private QuestTab selectedTab;
    private boolean movingTab;

    public QuestScreen(ClientAdvancementManager advancementHandler) {
        super(advancementHandler);
        this.advancementHandler = advancementHandler;
    }

    @Override
    protected void init() {
        this.tabs.clear();
        this.selectedTab = null;
        this.advancementHandler.setListener(this);
        if (this.selectedTab == null && !this.tabs.isEmpty()) {
            this.advancementHandler.selectTab(this.tabs.values().iterator().next().getRoot(), true);
        } else {
            this.advancementHandler.selectTab(this.selectedTab == null ? null : this.selectedTab.getRoot(), true);
        }
    }

    @Override
    public void removed() {
        this.advancementHandler.setListener(null);
        ClientPlayNetworkHandler clientPlayNetworkHandler = this.client.getNetworkHandler();
        if (clientPlayNetworkHandler != null) {
            clientPlayNetworkHandler.sendPacket(AdvancementTabC2SPacket.close());
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int i = (this.width - 256) / 2;
            int j = (this.height - 206) / 2;
            for (QuestTab advancementTab : this.tabs.values()) {
                if (!advancementTab.isClickOnTab(i, j, mouseX, mouseY)) {
                    continue;
                }
                this.advancementHandler.selectTab(advancementTab.getRoot(), true);
                break;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (KeyInit.screenKey.matchesKey(keyCode, scanCode) || this.client.options.inventoryKey.matchesKey(keyCode, scanCode)) {
            this.client.setScreen(null);
            this.client.mouse.lockCursor();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        int i = (this.width - 256) / 2;
        int j = (this.height - 206) / 2;
        this.renderBackground(context);
        this.drawAdvancementTree(context, mouseX, mouseY, i, j);
        this.drawWindow(context, i, j);
        this.drawWidgetTooltip(context, mouseX, mouseY, i, j);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (button != 0) {
            this.movingTab = false;
            return false;
        }
        if (!this.movingTab) {
            this.movingTab = true;
        } else if (this.selectedTab != null) {
            this.selectedTab.move(deltaX, deltaY);
        }
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        int i = (this.width - 256) / 2;
        int j = (this.height - 206) / 2;
        float scale = this.selectedTab.getTabScale();

        if (amount < 0) {
            scale = Math.min(1.3f, scale + 0.05f);
            if (scale < 1.3f) {
                this.selectedTab.setMaxPan(Math.round(this.selectedTab.getOldMaxPanX() * scale), Math.round(this.selectedTab.getOldMaxPanY() * scale));
                this.selectedTab.move((i - mouseX) * 0.05f, (j - mouseY) * 0.05f);
            }
        } else {
            scale = Math.max(0.3f, scale - 0.05f);
            if (scale > 0.3f) {
                this.selectedTab.setMaxPan(Math.round(this.selectedTab.getOldMaxPanX() * scale), Math.round(this.selectedTab.getOldMaxPanY() * scale));
                this.selectedTab.move((i - mouseX) * -0.05f, (j - mouseY) * -0.05f);
            }
        }
        this.selectedTab.setTabScale(scale, false);

        return super.mouseScrolled(mouseX, mouseY, amount);
    }

    private void drawAdvancementTree(DrawContext context, int mouseX, int mouseY, int x, int y) {
        QuestTab advancementTab = this.selectedTab;
        if (advancementTab == null) {
            context.fill(x + 9, y + 18, x + 9 + 238, y + 18 + 179, -16777216);
            return;
        }
        advancementTab.render(context, x + 9, y + 18);
    }

    public void drawWindow(DrawContext context, int x, int y) {
        RenderSystem.enableBlend();
        context.drawTexture(WINDOW_TEXTURE, x, y, 0, 0, 256, 206);
        if (this.tabs.size() > 1) {
            for (QuestTab advancementTab : this.tabs.values()) {
                advancementTab.drawBackground(context, x, y, advancementTab == this.selectedTab);
            }
            for (QuestTab advancementTab : this.tabs.values()) {
                advancementTab.drawIcon(context, x, y);
            }
        }
        context.drawText(this.textRenderer, QUESTS_TEXT, x + 8, y + 6, 0x404040, false);
    }

    private void drawWidgetTooltip(DrawContext context, int mouseX, int mouseY, int x, int y) {
        if (this.selectedTab != null) {
            context.getMatrices().push();
            context.getMatrices().translate(x + 9, y + 18, 400.0f);
            RenderSystem.enableDepthTest();
            this.selectedTab.drawWidgetTooltip(context, mouseX - x - 9, mouseY - y - 18, x, y);
            RenderSystem.disableDepthTest();
            context.getMatrices().pop();
        }
        if (this.tabs.size() > 1) {
            for (QuestTab advancementTab : this.tabs.values()) {
                if (!advancementTab.isClickOnTab(x, y, mouseX, mouseY))
                    continue;
                context.drawTooltip(this.textRenderer, advancementTab.getTitle(), mouseX, mouseY);
            }
        }
    }

    @Override
    public void onRootAdded(Advancement root) {
        QuestTab advancementTab = QuestTab.create(this.client, this, this.tabs.size(), root);
        if (advancementTab == null) {
            return;
        }
        if (ConfigInit.CONFIG.questAdvancementNamespaceIds.contains(root.getId().getNamespace())) {
            this.tabs.put(root, advancementTab);
        }
    }

    @Override
    public void onRootRemoved(Advancement root) {
    }

    @Override
    public void onDependentAdded(Advancement dependent) {
        QuestTab advancementTab = this.getTab(dependent);

        if (advancementTab != null) {
            advancementTab.addAdvancement(dependent);
        }
    }

    @Override
    public void onDependentRemoved(Advancement dependent) {
    }

    @Override
    public void setProgress(Advancement advancement, AdvancementProgress progress) {
        AdvancementWidget advancementWidget = this.getAdvancementWidget(advancement);
        if (advancementWidget != null) {
            advancementWidget.setProgress(progress);
        }
    }

    @Override
    public void selectTab(@Nullable Advancement advancement) {
        this.selectedTab = this.tabs.get(advancement);
    }

    @Override
    public void onClear() {
        this.tabs.clear();
        this.selectedTab = null;
    }

    @Nullable
    public AdvancementWidget getAdvancementWidget(Advancement advancement) {
        QuestTab advancementTab = this.getTab(advancement);
        return advancementTab == null ? null : advancementTab.getWidget(advancement);
    }

    @Nullable
    private QuestTab getTab(Advancement advancement) {
        while (advancement.getParent() != null) {
            advancement = advancement.getParent();
        }
        return this.tabs.get(advancement);
    }

    @Nullable
    public QuestTab getSelectedTab() {
        return this.selectedTab;
    }

}
