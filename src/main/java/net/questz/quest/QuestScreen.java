package net.questz.quest;

import com.google.common.collect.Maps;
import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.advancement.AdvancementEntry;
import net.minecraft.advancement.AdvancementProgress;
import net.minecraft.advancement.PlacedAdvancement;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.advancement.AdvancementsScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.ThreePartsLayoutWidget;
import net.minecraft.client.network.ClientAdvancementManager;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.network.packet.c2s.play.AdvancementTabC2SPacket;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.questz.QuestzMain;
import net.questz.init.KeyInit;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

@Environment(EnvType.CLIENT)
public class QuestScreen extends AdvancementsScreen implements ClientAdvancementManager.Listener {
    private static final Identifier WINDOW_TEXTURE = QuestzMain.identifierOf("textures/gui/window.png");
    private static final Identifier CREATION_MODE_TEXTURE = QuestzMain.identifierOf("textures/gui/creation_mode.png");
    private static final Text QUESTS_TEXT = Text.translatable("gui.questz");
    private final ThreePartsLayoutWidget layout = new ThreePartsLayoutWidget(this);
    @Nullable
    private final Screen parent;
    private final ClientAdvancementManager advancementHandler;
    private final Map<AdvancementEntry, QuestTab> tabs = Maps.newLinkedHashMap();
    @Nullable
    private QuestTab selectedTab;
    private boolean movingTab;

    private boolean creationMode = false;

    public QuestScreen(ClientAdvancementManager advancementHandler,boolean creationMode) {
        this(advancementHandler, null);
        this.creationMode = creationMode;
    }

    public QuestScreen(ClientAdvancementManager advancementHandler, @Nullable Screen parent) {
        super(advancementHandler);
        this.advancementHandler = advancementHandler;
        this.parent = parent;
    }

    @Override
    protected void init() {
        this.layout.addHeader(QUESTS_TEXT, this.textRenderer);
        this.tabs.clear();
        this.selectedTab = null;
        this.advancementHandler.setListener(this);
        if (this.selectedTab == null && !this.tabs.isEmpty()) {
            QuestTab advancementTab = this.tabs.values().iterator().next();
            this.advancementHandler.selectTab(advancementTab.getRoot().getAdvancementEntry(), true);
        } else {
            this.advancementHandler.selectTab(this.selectedTab == null ? null : this.selectedTab.getRoot().getAdvancementEntry(), true);
        }

        this.layout.addFooter(ButtonWidget.builder(ScreenTexts.DONE, buttonWidget -> this.close()).width(200).build());
        this.layout.forEachChild(element -> {
            ClickableWidget clickableWidget = this.addDrawableChild(element);
        });
        this.initTabNavigation();
    }

    @Override
    protected void initTabNavigation() {
        this.layout.refreshPositions();
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void close() {
//        this.client.setScreen(this.parent);
        this.client.setScreen(null);
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
                if (advancementTab.isClickOnTab(i, j, mouseX, mouseY)) {
                    this.advancementHandler.selectTab(advancementTab.getRoot().getAdvancementEntry(), true);
                    this.client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                    return true;
                }
            }
            if (isPointWithinBounds(i + 237, j + 2, 15, 12, mouseX, mouseY) && this.client != null && this.client.player != null && this.client.player.isCreativeLevelTwoOp()) {
                this.creationMode = !this.creationMode;
                this.client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                return true;
            }
        }
        if (button == 1 && this.creationMode) {
            this.client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            this.client.setScreen(new QuestEditorScreen(this.selectedTab != null ? this.selectedTab.getHoveredWidget() : null));
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    public static boolean isPointWithinBounds(int x, int y, int width, int height, double pointX, double pointY) {
        return pointX >= (double) (x - 1) && pointX < (double) (x + width + 1) && pointY >= (double) (y - 1) && pointY < (double) (y + height + 1);
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
        this.renderBackground(context, mouseX, mouseY, delta);

//        for (Drawable drawable : this.drawables) {
//            drawable.render(context, mouseX, mouseY, delta);
//        }

        int i = (this.width - 256) / 2;
        int j = (this.height - 206) / 2;
        this.drawAdvancementTree(context, mouseX, mouseY, i, j);
        this.drawWindow(context, i, j);
        this.drawWidgetTooltip(context, mouseX, mouseY, i, j);

        if (this.client != null && this.client.player != null && this.client.player.isCreativeLevelTwoOp()) {
            context.drawTexture(CREATION_MODE_TEXTURE, i + 237, j + 2, 16, 16, this.creationMode ? 0 : 16, 0, 16, 16, 32, 16);
        }
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (button != 0) {
            this.movingTab = false;
            return false;
        } else {
            if (!this.movingTab) {
                this.movingTab = true;
            } else if (this.selectedTab != null) {
                float scale = this.selectedTab.getTabScale();

                double correctedDeltaX = deltaX / scale;
                double correctedDeltaY = deltaY / scale;
                this.selectedTab.move(correctedDeltaX, correctedDeltaY);
            }

            return true;
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (this.selectedTab == null) return false;

        float oldScale = this.selectedTab.getTabScale();
        float newScale = MathHelper.clamp(oldScale + (verticalAmount > 0 ? 0.1f : -0.1f), 0.3f, 1.3f);

        if (oldScale != newScale) {
            double tabMouseX = mouseX - ((this.width - 256) / 2 + 9);
            double tabMouseY = mouseY - ((this.height - 206) / 2 + 18);

            double worldMouseX = tabMouseX / oldScale - this.selectedTab.getOriginX();
            double worldMouseY = tabMouseY / oldScale - this.selectedTab.getOriginY();

            this.selectedTab.setTabScale(newScale, false);

            double newOriginX = (tabMouseX / newScale) - worldMouseX;
            double newOriginY = (tabMouseY / newScale) - worldMouseY;

            this.selectedTab.setOrigin(newOriginX, newOriginY);
        }

        return true;
    }

    private void drawAdvancementTree(DrawContext context, int mouseX, int mouseY, int x, int y) {
        QuestTab advancementTab = this.selectedTab;
//        System.out.println("TT");
        if (advancementTab == null) {
//            context.fill(x + 9, y + 18, x + 9 + 234, y + 18 + 113, Colors.BLACK);
//            int i = x + 9 + 117;
//            context.drawCenteredTextWithShadow(this.textRenderer, EMPTY_TEXT, i, y + 18 + 56 - 9 / 2, Colors.WHITE);
//            context.drawCenteredTextWithShadow(this.textRenderer, SAD_LABEL_TEXT, i, y + 18 + 113 - 9, Colors.WHITE);
        } else {
            advancementTab.render(context, x + 9, y + 18);
        }
    }

    @Override
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
            context.getMatrices().translate((float) (x + 9), (float) (y + 18), 400.0F);
            RenderSystem.enableDepthTest();
            this.selectedTab.drawWidgetTooltip(context, mouseX - x - 9, mouseY - y - 18, x, y);
            RenderSystem.disableDepthTest();
            context.getMatrices().pop();
        }

        if (this.tabs.size() > 1) {
            for (QuestTab advancementTab : this.tabs.values()) {
                if (advancementTab.isClickOnTab(x, y, mouseX, mouseY)) {
                    context.drawTooltip(this.textRenderer, advancementTab.getTitle(), mouseX, mouseY);
                }
            }
        }
    }

    @Override
    public void onRootAdded(PlacedAdvancement root) {
        QuestTab advancementTab = QuestTab.create(this.client, this, this.tabs.size(), root);
        if (advancementTab != null) {
            this.tabs.put(root.getAdvancementEntry(), advancementTab);
        }
    }

    @Override
    public void onRootRemoved(PlacedAdvancement root) {
    }

    @Override
    public void onDependentAdded(PlacedAdvancement dependent) {
        QuestTab advancementTab = this.getTab(dependent);
        if (advancementTab != null) {
            advancementTab.addAdvancement(dependent);
        }
    }

    @Override
    public void onDependentRemoved(PlacedAdvancement dependent) {
    }

    @Override
    public void setProgress(PlacedAdvancement advancement, AdvancementProgress progress) {
        QuestWidget advancementWidget = this.getAdvancementWidget(advancement);
        if (advancementWidget != null) {
            advancementWidget.setProgress(progress);
        }
    }

    @Override
    public void selectTab(@Nullable AdvancementEntry advancement) {
        this.selectedTab = this.tabs.get(advancement);
    }

    @Override
    public void onClear() {
        this.tabs.clear();
        this.selectedTab = null;
    }

    @Nullable
    public QuestWidget getAdvancementWidget(PlacedAdvancement advancement) {
        QuestTab advancementTab = this.getTab(advancement);
        return advancementTab == null ? null : advancementTab.getWidget(advancement.getAdvancementEntry());
    }

    @Nullable
    private QuestTab getTab(PlacedAdvancement advancement) {
        PlacedAdvancement placedAdvancement = advancement.getRoot();
        return this.tabs.get(placedAdvancement.getAdvancementEntry());
    }
}

