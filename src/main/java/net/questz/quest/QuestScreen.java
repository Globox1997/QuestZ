package net.questz.quest;

import com.google.common.collect.Maps;
import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.advancement.AdvancementDisplay;
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
import net.questz.access.DisplayAccess;
import net.questz.init.ConfigInit;
import net.questz.init.KeyInit;
import net.questz.network.packet.QuestPositionPacket;
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

    private QuestWidget draggedWidget = null;
    private float dragWidgetX = 0;
    private float dragWidgetY = 0;
    private boolean isDraggingAdvancement = false;

    public QuestScreen(ClientAdvancementManager advancementHandler, boolean creationMode) {
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
        this.client.setScreen(this.parent);
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
        int i = (this.width - 256) / 2;
        int j = (this.height - 206) / 2;

        if (button == 0) {
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

            if (this.creationMode && this.selectedTab != null) {
                QuestWidget clickedWidget = getWidgetAtMouse(mouseX, mouseY);
                if (clickedWidget != null) {
                    this.draggedWidget = clickedWidget;

                    if (clickedWidget.getAdvancement().getAdvancement().display().isPresent()) {
                        AdvancementDisplay display = clickedWidget.getAdvancement().getAdvancement().display().get();
                        this.dragWidgetX = display.getX();
                        this.dragWidgetY = display.getY();
                    }

                    this.isDraggingAdvancement = true;
                    this.movingTab = false;

                    this.client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK, 0.5F));
                    return true;
                }
            }
        }

        if (isPointWithinBounds(i + 9, j + 18, 238, 179, mouseX, mouseY) && button == 1 && this.creationMode) {
            this.client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK, 1.0F));

            int screenX = (this.width - 256) / 2;
            int screenY = (this.height - 206) / 2;

            int clickX = 0;
            int clickY = 0;

            if (this.selectedTab != null) {
                double unitFactor = 28.0;

                float scale = this.selectedTab.getTabScale();
                double tabMouseX = mouseX - (screenX + 9);
                double tabMouseY = mouseY - (screenY + 18);

                clickX = (int) ((tabMouseX / scale - this.selectedTab.getOriginX()) / unitFactor);
                clickY = (int) ((tabMouseY / scale - this.selectedTab.getOriginY()) / unitFactor);
            }

            this.client.setScreen(new QuestEditorScreen(this.selectedTab != null ? this.selectedTab.getHoveredWidget() : null, clickX, clickY));
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && this.isDraggingAdvancement) {
            this.isDraggingAdvancement = false;

            if (this.draggedWidget != null) {
                this.client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.BLOCK_STONE_PLACE, 0.3F));

                if (this.selectedTab != null) {
                    this.selectedTab.updateWidgetPosition(this.draggedWidget);
                }

                ClientPlayNetworking.send(new QuestPositionPacket(this.draggedWidget.getAdvancement().getAdvancementEntry().id(), (int) this.draggedWidget.getAdvancement().getAdvancement().display().get().getX(), (int) this.draggedWidget.getAdvancement().getAdvancement().display().get().getY()));
            }

            this.draggedWidget = null;
            return true;
        }

        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (button != 0) {
            this.movingTab = false;
            this.isDraggingAdvancement = false;
            return false;
        }

        if (this.isDraggingAdvancement && this.draggedWidget != null && this.creationMode) {
            if (this.selectedTab != null) {
                float scale = this.selectedTab.getTabScale();

                double unitFactor = 28.0;

                double correctedDeltaX = (deltaX / scale) / unitFactor;
                double correctedDeltaY = (deltaY / scale) / unitFactor;

                this.dragWidgetX += (float) correctedDeltaX;
                this.dragWidgetY += (float) correctedDeltaY;

                int gridX = Math.round(this.dragWidgetX);
                int gridY = Math.round(this.dragWidgetY);

                if (this.draggedWidget.getAdvancement().getAdvancement().display().isPresent()) {
                    AdvancementDisplay display = this.draggedWidget.getAdvancement().getAdvancement().display().get();

                    if (display instanceof DisplayAccess access) {
                        access.setManualPosition(gridX, gridY);
                    } else {
                        display.setPos(gridX, gridY);
                    }
                    this.draggedWidget.updatePosition(gridX, gridY);
                }
            }
            return true;
        }

        if (!this.movingTab) {
            this.movingTab = true;
        } else if (this.selectedTab != null && !this.isDraggingAdvancement) {
            float scale = this.selectedTab.getTabScale();
            double correctedDeltaX = deltaX / scale;
            double correctedDeltaY = deltaY / scale;
            this.selectedTab.move(correctedDeltaX, correctedDeltaY);
        }

        return true;
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

    private static boolean isPointWithinBounds(int x, int y, int width, int height, double pointX, double pointY) {
        return pointX >= (double) (x - 1) && pointX < (double) (x + width + 1) && pointY >= (double) (y - 1) && pointY < (double) (y + height + 1);
    }

    private QuestWidget getWidgetAtMouse(double mouseX, double mouseY) {
        if (this.selectedTab == null) {
            return null;
        }
        return this.selectedTab.getHoveredWidget();
    }

    private void renderDragFeedback(DrawContext context, int mouseX, int mouseY) {
        if (this.selectedTab != null && this.draggedWidget != null) {
            int actualX = 0;
            int actualY = 0;

            if (this.draggedWidget.getAdvancement().getAdvancement().display().isPresent()) {
                AdvancementDisplay display = this.draggedWidget.getAdvancement().getAdvancement().display().get();
                actualX = (int) display.getX();
                actualY = (int) display.getY();
            }

            String coordText = String.format("X: %d, Y: %d", actualX, actualY);

            context.getMatrices().push();
            context.getMatrices().translate(0f, 0f, 500f);
            context.drawTextWithShadow(this.textRenderer, Text.literal(coordText), mouseX + 10, mouseY - 20, 0xFFFFFF);

            int crossSize = 5;
            context.fill(mouseX - crossSize, mouseY, mouseX + crossSize, mouseY + 1, 0x88FFFFFF);
            context.fill(mouseX, mouseY - crossSize, mouseX + 1, mouseY + crossSize, 0x88FFFFFF);
            context.getMatrices().pop();
        }
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

        int i = (this.width - 256) / 2;
        int j = (this.height - 206) / 2;
        this.drawAdvancementTree(context, mouseX, mouseY, i, j);
        this.drawWindow(context, i, j);
        if (this.draggedWidget == null) {
            this.drawWidgetTooltip(context, mouseX, mouseY, i, j);
        }
        if (this.client != null && this.client.player != null && this.client.player.isCreativeLevelTwoOp()) {
            context.drawTexture(CREATION_MODE_TEXTURE, i + 237, j + 2, 16, 16, this.creationMode ? 0 : 16, 0, 16, 16, 32, 16);
        }

        if (this.isDraggingAdvancement && this.draggedWidget != null && this.creationMode) {
            renderDragFeedback(context, mouseX, mouseY);
        }
    }

    private void drawAdvancementTree(DrawContext context, int mouseX, int mouseY, int x, int y) {
        QuestTab advancementTab = this.selectedTab;
        if (advancementTab != null) {
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
            if (ConfigInit.CONFIG.questAdvancementNamespaceIds.contains(root.getAdvancementEntry().id().getNamespace())) {
                this.tabs.put(root.getAdvancementEntry(), advancementTab);

                if (this.selectedTab == null && this.tabs.size() == 1) {
                    this.advancementHandler.selectTab(root.getAdvancementEntry(), true);
                }
            }
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