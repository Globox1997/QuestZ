package net.questz.client.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ConfirmScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.advancement.AdvancementObtainedStatus;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.questz.QuestzMain;
import net.questz.book.*;
import net.questz.client.ClientQuestData;
import net.questz.client.editor.ChapterEditScreen;
import net.questz.client.editor.ClientEditor;
import net.questz.client.editor.ContextMenu;
import net.questz.client.editor.QuestEditScreen;
import net.questz.init.KeyInit;
import net.questz.network.packet.DeleteQuestPacket;
import net.questz.network.packet.EditChapterPacket;
import net.questz.network.packet.EditQuestPacket;
import net.questz.network.packet.MoveQuestsPacket;
import net.questz.progress.ProgressView;
import net.questz.progress.QuestEngine;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.*;

@Environment(EnvType.CLIENT)
public class QuestBookScreen extends Screen {

    private static final Identifier FRAME_TEXTURE = QuestzMain.identifierOf("textures/gui/quest.png");
    private static final Identifier EDIT_MODE_TEXTURE = QuestzMain.identifierOf("textures/gui/creation_mode.png");
    private static final Identifier DEFAULT_BACKGROUND = Identifier.ofVanilla("textures/gui/advancements/backgrounds/stone.png");
    private static final Identifier TAB = Identifier.ofVanilla("advancements/tab_above_middle");
    private static final Identifier TAB_SELECTED = Identifier.ofVanilla("advancements/tab_above_middle_selected");
    private static final int FRAME_TEXTURE_SIZE = 32;
    private static final int FRAME_BORDER = 14;
    private static final int FRAME_INSET = 9;
    private static final int PADDING = 8;
    private static final int TAB_WIDTH = 28;
    private static final int COLOR_LINE_DONE = 0xFFFFFFFF;
    private static final int COLOR_LINE_OPEN = 0xFF707070;
    private static final int COLOR_LINE_REQUIRED = 0xFFD9B46A;
    private static final int COLOR_LINE_UNLOCKS = 0xFF7FAED6;
    private static final int TAB_HEIGHT = 32;
    private static final int TOP_BUTTON_Y = PADDING + 6;
    private static final int BUTTON_HEIGHT = 16;
    private static final int ICON_SIZE = 16;
    private static final float EDIT_TINT_RED = 0.7F;
    private static final float EDIT_TINT_GREEN = 0.8F;
    private static final float EDIT_TINT_BLUE = 1.0F;
    public static final int GRID = 28;
    private static final float MIN_SCALE = 0.5F;
    private static final float MAX_SCALE = 1.5F;

    private static final Map<String, View> VIEWS = new HashMap<>();
    @Nullable
    private static String selectedChapterId;
    private static boolean editMode;

    private static class View {
        double originX;
        double originY;
        float scale = 1.0F;
    }

    private int revision = -1;
    private QuestBook book = QuestBook.EMPTY;
    @Nullable
    private Chapter chapter;
    private List<Quest> visibleQuests = List.of();
    private final Map<String, Quest> visibleById = new HashMap<>();
    private final Set<String> hiddenIds = new HashSet<>();
    private final Map<String, double[]> positionOverrides = new HashMap<>();
    private final Map<String, double[]> dragOrigins = new LinkedHashMap<>();
    private int page;
    private int tabsPerPage = 1;
    private int editToggleX = -1;
    private int teamX;
    private int previousPageX;
    private int nextPageX;
    private boolean pagesVisible;
    private ButtonWidget lineStyleButton;
    private List<LineRouter.Route> cachedRoutes = List.of();
    private float hoverAlpha;
    private int cachedRoutesKey;
    private boolean pressedInMap;
    private boolean dragging;
    private double pressX;
    private double pressY;
    @Nullable
    private String pressedQuestId;
    @Nullable
    private String linkSourceId;
    @Nullable
    private ContextMenu menu;

    public QuestBookScreen() {
        super(Text.translatable("gui.questz"));
    }

    public static void selectChapter(String chapterId) {
        selectedChapterId = chapterId;
    }

    @Override
    protected void init() {
        int x = this.width - PADDING - ICON_SIZE;
        this.teamX = x;
        x -= 4;
        this.editToggleX = -1;
        if (ClientEditor.canEdit()) {
            x -= ICON_SIZE;
            this.editToggleX = x;
            x -= 4;
        }
        x -= BUTTON_HEIGHT;
        this.lineStyleButton = this.addDrawableChild(ButtonWidget.builder(Text.empty(), button -> this.toggleLineStyle())
                .dimensions(x, TOP_BUTTON_Y, BUTTON_HEIGHT, BUTTON_HEIGHT).build());
        x -= 4 + ICON_SIZE;
        this.nextPageX = x;
        x -= 2 + ICON_SIZE;
        this.previousPageX = x;
        x -= 4;
        this.tabsPerPage = Math.max(1, (x - this.mapLeft()) / (TAB_WIDTH + 2));
        this.revision = -1;
        this.refresh();
    }

    private int frameLeft() {
        return PADDING;
    }

    private int frameTop() {
        return PADDING + TAB_HEIGHT - 4;
    }

    private int frameRight() {
        return this.width - PADDING;
    }

    private int frameBottom() {
        return this.height - PADDING;
    }

    private int mapLeft() {
        return this.frameLeft() + FRAME_INSET;
    }

    private int mapTop() {
        return this.frameTop() + FRAME_INSET;
    }

    private int mapWidth() {
        return this.frameRight() - FRAME_INSET - this.mapLeft();
    }

    private int mapHeight() {
        return this.frameBottom() - FRAME_INSET - this.mapTop();
    }

    private boolean isEditing() {
        return editMode && ClientEditor.canEdit();
    }

    private void refresh() {
        if (this.revision == ClientQuestData.getRevision()) {
            return;
        }
        this.revision = ClientQuestData.getRevision();
        this.book = ClientQuestData.getBook();
        this.positionOverrides.clear();
        List<Chapter> chapters = this.book.getChapters();
        this.chapter = selectedChapterId != null ? this.book.getChapter(selectedChapterId) : null;
        if (this.chapter == null && !chapters.isEmpty()) {
            this.chapter = chapters.getFirst();
            selectedChapterId = this.chapter.id();
        }
        if (this.chapter != null) {
            this.page = chapters.indexOf(this.chapter) / this.tabsPerPage;
        }
        if (this.linkSourceId != null && this.book.getQuest(this.linkSourceId) == null) {
            this.linkSourceId = null;
        }
        this.rebuildVisibleQuests();
        int pages = this.getPageCount();
        this.page = MathHelper.clamp(this.page, 0, Math.max(0, pages - 1));
        this.pagesVisible = pages > 1;
    }

    private void rebuildVisibleQuests() {
        this.visibleById.clear();
        this.hiddenIds.clear();
        if (this.chapter == null) {
            this.visibleQuests = List.of();
            return;
        }
        ProgressView progress = QuestTexts.progress();
        boolean editing = this.isEditing();
        List<Quest> quests = new ArrayList<>();
        for (Quest quest : this.chapter.quests()) {
            boolean visible = QuestEngine.isVisible(this.book, progress, quest);
            if (visible || editing) {
                quests.add(quest);
                this.visibleById.put(quest.id(), quest);
                if (!visible) {
                    this.hiddenIds.add(quest.id());
                }
            }
        }
        this.visibleQuests = quests;
    }

    private int getPageCount() {
        return (this.book.getChapters().size() + this.tabsPerPage - 1) / this.tabsPerPage;
    }

    private void changePage(int delta) {
        this.page = MathHelper.clamp(this.page + delta, 0, Math.max(0, this.getPageCount() - 1));
    }

    private void selectChapter(Chapter chapter) {
        this.chapter = chapter;
        selectedChapterId = chapter.id();
        this.linkSourceId = null;
        this.rebuildVisibleQuests();
    }

    private double getX(Quest quest) {
        double[] position = this.positionOverrides.get(quest.id());
        return position != null ? position[0] : quest.x();
    }

    private double getY(Quest quest) {
        double[] position = this.positionOverrides.get(quest.id());
        return position != null ? position[1] : quest.y();
    }

    private View getView(Chapter chapter) {
        return VIEWS.computeIfAbsent(chapter.id(), id -> {
            View view = new View();
            double minX = 0.0D, maxX = 0.0D, minY = 0.0D, maxY = 0.0D;
            boolean first = true;
            for (Quest quest : chapter.quests()) {
                minX = first ? quest.x() : Math.min(minX, quest.x());
                maxX = first ? quest.x() : Math.max(maxX, quest.x());
                minY = first ? quest.y() : Math.min(minY, quest.y());
                maxY = first ? quest.y() : Math.max(maxY, quest.y());
                first = false;
            }
            double contentWidth = (maxX - minX) * GRID + GRID * 2;
            double contentHeight = (maxY - minY) * GRID + GRID * 2;
            view.scale = MathHelper.clamp((float) Math.min(this.mapWidth() / contentWidth, this.mapHeight() / contentHeight), MIN_SCALE, 1.0F);
            view.originX = this.mapWidth() / 2.0D / view.scale - (minX + maxX) / 2.0D * GRID;
            view.originY = this.mapHeight() / 2.0D / view.scale - (minY + maxY) / 2.0D * GRID;
            return view;
        });
    }

    private boolean isInMap(double mouseX, double mouseY) {
        int left = this.mapLeft();
        int top = this.mapTop();
        return mouseX >= left && mouseX < left + this.mapWidth() && mouseY >= top && mouseY < top + this.mapHeight();
    }

    private double toGridX(View view, double mouseX) {
        return ((mouseX - this.mapLeft()) / view.scale - view.originX) / GRID;
    }

    private double toGridY(View view, double mouseY) {
        return ((mouseY - this.mapTop()) / view.scale - view.originY) / GRID;
    }

    private static double snap(double value) {
        return Math.round(value * 2.0D) / 2.0D;
    }

    @Nullable
    private Quest getQuestAt(double mouseX, double mouseY) {
        if (this.chapter == null || !this.isInMap(mouseX, mouseY)) {
            return null;
        }
        View view = this.getView(this.chapter);
        double localX = (mouseX - this.mapLeft()) / view.scale - view.originX;
        double localY = (mouseY - this.mapTop()) / view.scale - view.originY;
        for (int i = this.visibleQuests.size() - 1; i >= 0; i--) {
            Quest quest = this.visibleQuests.get(i);
            double half = 13.0D * quest.size();
            if (Math.abs(localX - this.getX(quest) * GRID) <= half && Math.abs(localY - this.getY(quest) * GRID) <= half) {
                return quest;
            }
        }
        return null;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.refresh();
        this.renderBackground(context, mouseX, mouseY, delta);
        Quest hovered = this.menu == null && !this.dragging ? this.getQuestAt(mouseX, mouseY) : null;
        this.hoverAlpha = hovered != null ? MathHelper.clamp(this.hoverAlpha + 0.02F, 0.0F, 0.3F) : MathHelper.clamp(this.hoverAlpha - 0.04F, 0.0F, 1.0F);
        this.drawMap(context, hovered);
        this.drawFrame(context);
        this.drawTabs(context);
        if (this.editToggleX >= 0) {
            context.drawTexture(EDIT_MODE_TEXTURE, this.editToggleX, TOP_BUTTON_Y, 16, 16, editMode ? 0 : 16, 0, 16, 16, 32, 16);
        }
        QuestzGui.drawIcon(context, QuestzGui.TEAM, this.teamX, TOP_BUTTON_Y, ICON_SIZE, this.isOverIcon(this.teamX, mouseX, mouseY));
        if (this.pagesVisible) {
            boolean canGoBack = this.page > 0;
            boolean canGoForward = this.page < this.getPageCount() - 1;
            QuestzGui.drawIcon(context, QuestzGui.ARROW_LEFT, this.previousPageX, TOP_BUTTON_Y, ICON_SIZE, canGoBack && this.isOverIcon(this.previousPageX, mouseX, mouseY));
            QuestzGui.drawIcon(context, QuestzGui.ARROW_RIGHT, this.nextPageX, TOP_BUTTON_Y, ICON_SIZE, canGoForward && this.isOverIcon(this.nextPageX, mouseX, mouseY));
        }
        this.updateLineStyleButton();
        this.lineStyleButton.render(context, mouseX, mouseY, delta);
        if (this.menu != null) {
            this.menu.render(context, this.textRenderer, mouseX, mouseY);
        } else {
            this.drawTooltips(context, mouseX, mouseY);
        }
    }

    private void drawFrame(DrawContext context) {
        int x = this.frameLeft();
        int y = this.frameTop();
        int width = this.frameRight() - x;
        int height = this.frameBottom() - y;
        int border = FRAME_BORDER;
        int center = FRAME_TEXTURE_SIZE - border * 2;
        int innerWidth = width - border * 2;
        int innerHeight = height - border * 2;
        int far = FRAME_TEXTURE_SIZE - border;

        RenderSystem.enableBlend();
        if (this.isEditing()) {
            RenderSystem.setShaderColor(EDIT_TINT_RED, EDIT_TINT_GREEN, EDIT_TINT_BLUE, 1.0F);
        }
        frameSlice(context, x, y, border, border, 0, 0, border, border);
        frameSlice(context, x + border, y, innerWidth, border, border, 0, center, border);
        frameSlice(context, x + width - border, y, border, border, far, 0, border, border);
        frameSlice(context, x, y + border, border, innerHeight, 0, border, border, center);
        frameSlice(context, x + width - border, y + border, border, innerHeight, far, border, border, center);
        frameSlice(context, x, y + height - border, border, border, 0, far, border, border);
        frameSlice(context, x + border, y + height - border, innerWidth, border, border, far, center, border);
        frameSlice(context, x + width - border, y + height - border, border, border, far, far, border, border);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.disableBlend();
    }

    private static void frameSlice(DrawContext context, int x, int y, int width, int height, int u, int v, int uWidth, int vHeight) {
        if (width > 0 && height > 0) {
            context.drawTexture(FRAME_TEXTURE, x, y, width, height, u, v, uWidth, vHeight, FRAME_TEXTURE_SIZE, FRAME_TEXTURE_SIZE);
        }
    }

    private void drawMap(DrawContext context, @Nullable Quest hovered) {
        int left = this.mapLeft();
        int top = this.mapTop();
        int mapWidth = this.mapWidth();
        int mapHeight = this.mapHeight();
        context.enableScissor(left, top, left + mapWidth, top + mapHeight);
        if (this.chapter == null) {
            context.drawTexture(DEFAULT_BACKGROUND, left, top, 0, 0.0F, 0.0F, mapWidth, mapHeight, 16, 16);
            Text message = Text.translatable(this.isEditing() ? "gui.questz.editor.empty" : "gui.questz.empty");
            context.drawCenteredTextWithShadow(this.textRenderer, message, left + mapWidth / 2, top + mapHeight / 2 - 4, 0xFFFFFFFF);
            context.disableScissor();
            return;
        }

        View view = this.getView(this.chapter);
        MatrixStack matrices = context.getMatrices();
        matrices.push();
        matrices.translate((float) left, (float) top, 0.0F);
        matrices.scale(view.scale, view.scale, 1.0F);
        matrices.translate((float) view.originX, (float) view.originY, 0.0F);

        Identifier background = this.chapter.background().orElse(DEFAULT_BACKGROUND);
        int startX = MathHelper.floor(-view.originX / 16.0D) * 16;
        int startY = MathHelper.floor(-view.originY / 16.0D) * 16;
        int endX = MathHelper.ceil(-view.originX + mapWidth / view.scale);
        int endY = MathHelper.ceil(-view.originY + mapHeight / view.scale);
        for (int x = startX; x < endX; x += 16) {
            for (int y = startY; y < endY; y += 16) {
                context.drawTexture(background, x, y, 0.0F, 0.0F, 16, 16, 16, 16);
            }
        }

        ProgressView progress = QuestTexts.progress();
        String hoveredId = hovered != null ? hovered.id() : null;
        if (this.chapter.lineStyle() == LineStyle.ORTHOGONAL) {
            List<LineRouter.Route> routes = this.routeLines();
            this.drawRoutes(context, progress, routes, hoveredId, true, false);
            this.drawRoutes(context, progress, routes, hoveredId, false, false);
            this.drawRoutes(context, progress, routes, hoveredId, false, true);
        } else {
            this.drawLines(context, progress, hoveredId, true, false);
            this.drawLines(context, progress, hoveredId, false, false);
            this.drawLines(context, progress, hoveredId, false, true);
        }
        for (Quest quest : this.visibleQuests) {
            this.drawNode(context, progress, quest);
        }
        matrices.pop();
        if (this.hoverAlpha > 0.0F) {
            context.fill(left, top, left + mapWidth, top + mapHeight, MathHelper.floor(this.hoverAlpha * 255.0F) << 24);
        }

        if (this.isEditing()) {
            Text hint = this.linkSourceId != null ? Text.translatable("gui.questz.editor.link_mode") : Text.translatable("gui.questz.editor.hint");
            context.fill(left, top + mapHeight - 12, left + mapWidth, top + mapHeight, 0xA0000000);
            context.drawCenteredTextWithShadow(this.textRenderer, hint, left + mapWidth / 2, top + mapHeight - 10, this.linkSourceId != null ? 0xFFFFFF55 : 0xFFAAAAAA);
        }
        context.disableScissor();
    }

    private void drawLines(DrawContext context, ProgressView progress, @Nullable String hoveredId, boolean border, boolean highlighted) {
        for (Quest quest : this.visibleQuests) {
            for (String dependencyId : quest.dependencies()) {
                Quest dependency = this.visibleById.get(dependencyId);
                if (dependency == null) {
                    continue;
                }
                boolean required = quest.id().equals(hoveredId);
                boolean unlocks = dependencyId.equals(hoveredId);
                if (!border && highlighted != (required || unlocks)) {
                    continue;
                }
                int color;
                if (border) {
                    color = 0xFF000000;
                } else if (required) {
                    color = COLOR_LINE_REQUIRED;
                } else if (unlocks) {
                    color = COLOR_LINE_UNLOCKS;
                } else {
                    color = progress.hasEverCompleted(dependencyId) ? COLOR_LINE_DONE : COLOR_LINE_OPEN;
                }
                drawLine(context, (float) this.getX(dependency) * GRID, (float) this.getY(dependency) * GRID, (float) this.getX(quest) * GRID, (float) this.getY(quest) * GRID,
                        border ? 2 : 1, color);
            }
        }
    }

    private int lineColor(ProgressView progress, Quest dependency, Quest quest, @Nullable String hoveredId) {
        if (quest.id().equals(hoveredId)) {
            return COLOR_LINE_REQUIRED;
        }
        if (dependency.id().equals(hoveredId)) {
            return COLOR_LINE_UNLOCKS;
        }
        return progress.hasEverCompleted(dependency.id()) ? COLOR_LINE_DONE : COLOR_LINE_OPEN;
    }

    private List<LineRouter.Route> routeLines() {
        int overridesHash = 0;
        for (Map.Entry<String, double[]> entry : this.positionOverrides.entrySet()) {
            overridesHash += entry.getKey().hashCode() ^ Arrays.hashCode(entry.getValue());
        }
        int key = Objects.hash(this.revision, this.chapter != null ? this.chapter.id() : "", this.visibleQuests.size(), overridesHash);
        if (key == this.cachedRoutesKey && !this.cachedRoutes.isEmpty()) {
            return this.cachedRoutes;
        }
        this.cachedRoutesKey = key;
        List<Quest[]> connections = new ArrayList<>();
        for (Quest quest : this.visibleQuests) {
            for (String dependencyId : quest.dependencies()) {
                Quest dependency = this.visibleById.get(dependencyId);
                if (dependency != null && dependency != quest) {
                    connections.add(new Quest[]{dependency, quest});
                }
            }
        }
        this.cachedRoutes = LineRouter.route(this.visibleQuests, connections, new LineRouter.Positions() {
            @Override
            public float x(Quest quest) {
                return (float) QuestBookScreen.this.getX(quest) * GRID;
            }

            @Override
            public float y(Quest quest) {
                return (float) QuestBookScreen.this.getY(quest) * GRID;
            }
        });
        return this.cachedRoutes;
    }

    private void drawRoutes(DrawContext context, ProgressView progress, List<LineRouter.Route> routes, @Nullable String hoveredId, boolean border, boolean highlighted) {
        for (LineRouter.Route route : routes) {
            boolean isHighlighted = route.to().id().equals(hoveredId) || route.from().id().equals(hoveredId);
            if (!border && highlighted != isHighlighted) {
                continue;
            }
            int color = border ? 0xFF000000 : this.lineColor(progress, route.from(), route.to(), hoveredId);
            for (LineRouter.Segment segment : route.segments()) {
                int minX = Math.round(Math.min(segment.x1(), segment.x2()));
                int maxX = Math.round(Math.max(segment.x1(), segment.x2()));
                int minY = Math.round(Math.min(segment.y1(), segment.y2()));
                int maxY = Math.round(Math.max(segment.y1(), segment.y2()));
                int expand = border ? 1 : 0;
                context.fill(minX - expand, minY - expand, maxX + expand + 1, maxY + expand + 1, color);
            }
        }
    }

    private void updateLineStyleButton() {
        boolean visible = this.isEditing() && this.chapter != null;
        this.lineStyleButton.visible = visible;
        if (visible) {
            LineStyle style = this.chapter.lineStyle();
            this.lineStyleButton.setMessage(Text.literal(style == LineStyle.ORTHOGONAL ? "D" : "V"));
            this.lineStyleButton.setTooltip(Tooltip.of(Text.translatable("gui.questz.editor.line_style." + style.asString())));
        }
    }

    private void toggleLineStyle() {
        if (this.isEditing() && this.chapter != null) {
            ClientEditor.send(new EditChapterPacket(this.chapter.withLineStyle(this.chapter.lineStyle().next()).withQuests(List.of())));
            this.playClick();
        }
    }

    private static void drawLine(DrawContext context, float fromX, float fromY, float toX, float toY, int halfWidth, int color) {
        float dx = toX - fromX;
        float dy = toY - fromY;
        float length = MathHelper.sqrt(dx * dx + dy * dy);
        if (length < 1.0F) {
            return;
        }
        MatrixStack matrices = context.getMatrices();
        matrices.push();
        matrices.translate(fromX, fromY, 0.0F);
        matrices.multiply(RotationAxis.POSITIVE_Z.rotation((float) Math.atan2(dy, dx)));
        context.fill(0, -halfWidth, MathHelper.ceil(length), halfWidth, color);
        matrices.pop();
    }

    private void drawNode(DrawContext context, ProgressView progress, Quest quest) {
        boolean completed = progress.isCompleted(quest.id());
        boolean unlocked = QuestTexts.isUnlocked(this.book, progress, quest);
        boolean hidden = this.hiddenIds.contains(quest.id());
        AdvancementObtainedStatus status = completed ? AdvancementObtainedStatus.OBTAINED : AdvancementObtainedStatus.UNOBTAINED;

        MatrixStack matrices = context.getMatrices();
        matrices.push();
        matrices.translate((float) this.getX(quest) * GRID, (float) this.getY(quest) * GRID, 0.0F);
        matrices.scale(quest.size(), quest.size(), 1.0F);
        if (this.linkSourceId != null) {
            Quest source = this.book.getQuest(this.linkSourceId);
            if (quest.id().equals(this.linkSourceId)) {
                context.drawBorder(-15, -15, 30, 30, 0xFFFFFF55);
            } else if (source != null && source.dependencies().contains(quest.id())) {
                context.drawBorder(-15, -15, 30, 30, 0xFF55FF55);
            }
        }
        if (hidden) {
            RenderSystem.setShaderColor(0.6F, 0.6F, 1.0F, 0.6F);
        } else if (!unlocked) {
            RenderSystem.setShaderColor(0.45F, 0.45F, 0.45F, 1.0F);
        }
        context.drawGuiTexture(status.getFrameTexture(quest.shape().getFrame()), -13, -13, 26, 26);
        context.drawItemWithoutEntity(QuestTexts.icon(quest), -8, -8);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        if (QuestTexts.isClaimable(quest)) {
            matrices.translate(0.0F, 0.0F, 200.0F);
            QuestzGui.drawIcon(context, QuestzGui.MARKER, 5, -17, ICON_SIZE, true);
        }
        if (quest.repeatable()) {
            matrices.translate(0.0F, 0.0F, 200.0F);
            QuestzGui.drawIcon(context, QuestzGui.REFRESH, 6, 6, 8, true);
        }
        matrices.pop();
    }

    private void drawTabs(DrawContext context) {
        List<Chapter> chapters = this.book.getChapters();
        int first = this.page * this.tabsPerPage;
        for (int i = 0; i < this.tabsPerPage && first + i < chapters.size(); i++) {
            Chapter tab = chapters.get(first + i);
            int x = this.mapLeft() + i * (TAB_WIDTH + 2);
            if (this.isEditing()) {
                RenderSystem.setShaderColor(EDIT_TINT_RED, EDIT_TINT_GREEN, EDIT_TINT_BLUE, 1.0F);
            }
            context.drawGuiTexture(tab == this.chapter ? TAB_SELECTED : TAB, x, PADDING, TAB_WIDTH, TAB_HEIGHT);
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            context.drawItemWithoutEntity(tab.icon(), x + 6, PADDING + 9);
        }
    }

    @Nullable
    private Chapter getTabAt(double mouseX, double mouseY) {
        List<Chapter> chapters = this.book.getChapters();
        int first = this.page * this.tabsPerPage;
        for (int i = 0; i < this.tabsPerPage && first + i < chapters.size(); i++) {
            int x = this.mapLeft() + i * (TAB_WIDTH + 2);
            if (mouseX >= x && mouseX < x + TAB_WIDTH && mouseY >= PADDING && mouseY < PADDING + TAB_HEIGHT - 4) {
                return chapters.get(first + i);
            }
        }
        return null;
    }

    private boolean isOverIcon(int x, double mouseX, double mouseY) {
        return mouseX >= x && mouseX < x + ICON_SIZE && mouseY >= TOP_BUTTON_Y && mouseY < TOP_BUTTON_Y + ICON_SIZE;
    }

    private boolean isOverEditToggle(double mouseX, double mouseY) {
        return this.editToggleX >= 0 && ClientEditor.canEdit() && mouseX >= this.editToggleX && mouseX < this.editToggleX + 16 && mouseY >= TOP_BUTTON_Y
                && mouseY < TOP_BUTTON_Y + 16;
    }

    private void drawTooltips(DrawContext context, int mouseX, int mouseY) {
        if (this.isOverEditToggle(mouseX, mouseY)) {
            context.drawTooltip(this.textRenderer, Text.translatable(editMode ? "gui.questz.editor.disable" : "gui.questz.editor.enable"), mouseX, mouseY);
            return;
        }
        if (this.isOverIcon(this.teamX, mouseX, mouseY)) {
            context.drawTooltip(this.textRenderer, Text.translatable("gui.questz.team"), mouseX, mouseY);
            return;
        }
        if (this.pagesVisible && (this.isOverIcon(this.previousPageX, mouseX, mouseY) || this.isOverIcon(this.nextPageX, mouseX, mouseY))) {
            String key = this.isOverIcon(this.previousPageX, mouseX, mouseY) ? "gui.questz.page.previous" : "gui.questz.page.next";
            context.drawTooltip(this.textRenderer, Text.translatable(key, this.page + 1, this.getPageCount()), mouseX, mouseY);
            return;
        }
        if (this.isEditing() && this.linkSourceId == null && mouseX >= this.mapLeft() && mouseX < this.mapLeft() + this.mapWidth()
                && mouseY >= this.mapTop() + this.mapHeight() - 12 && mouseY < this.mapTop() + this.mapHeight()) {
            context.drawTooltip(this.textRenderer, Text.translatable("gui.questz.editor.hint.tooltip"), mouseX, mouseY);
            return;
        }
        Chapter tab = this.getTabAt(mouseX, mouseY);
        if (tab != null) {
            context.drawTooltip(this.textRenderer, QuestTexts.chapterTitle(tab), mouseX, mouseY);
            return;
        }
        if (this.dragging) {
            return;
        }
        Quest quest = this.getQuestAt(mouseX, mouseY);
        if (quest == null || this.chapter == null) {
            return;
        }
        View view = this.getView(this.chapter);
        int centerX = MathHelper.floor(this.mapLeft() + (this.getX(quest) * GRID + view.originX) * view.scale);
        int centerY = MathHelper.floor(this.mapTop() + (this.getY(quest) * GRID + view.originY) * view.scale);
        QuestTooltip.draw(context, this.textRenderer, this.book, QuestTexts.progress(), quest, this.isEditing(), this.hiddenIds.contains(quest.id()), centerX, centerY,
                this.width, this.mapTop() + this.mapHeight());
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (this.menu != null) {
            ContextMenu open = this.menu;
            this.menu = null;
            if (button == 0) {
                open.click(mouseX, mouseY);
            }
            return true;
        }
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (button == 0 && this.isOverIcon(this.teamX, mouseX, mouseY) && this.client != null) {
            this.playClick();
            this.client.setScreen(new TeamScreen(this));
            return true;
        }
        if (button == 0 && this.pagesVisible && (this.isOverIcon(this.previousPageX, mouseX, mouseY) || this.isOverIcon(this.nextPageX, mouseX, mouseY))) {
            int previous = this.page;
            this.changePage(this.isOverIcon(this.previousPageX, mouseX, mouseY) ? -1 : 1);
            if (this.page != previous) {
                this.playSound(SoundEvents.ITEM_BOOK_PAGE_TURN, 1.0F);
            }
            return true;
        }
        if (button == 0 && this.isOverEditToggle(mouseX, mouseY)) {
            editMode = !editMode;
            this.linkSourceId = null;
            this.rebuildVisibleQuests();
            this.playClick();
            return true;
        }
        Chapter tab = this.getTabAt(mouseX, mouseY);
        if (tab != null) {
            if (button == 0 && tab != this.chapter) {
                this.selectChapter(tab);
                this.playSound(SoundEvents.ITEM_BOOK_PAGE_TURN, 1.0F);
            } else if (button == 1 && this.isEditing()) {
                this.selectChapter(tab);
                this.openChapterMenu((int) mouseX, (int) mouseY);
            }
            return true;
        }
        if (!this.isInMap(mouseX, mouseY)) {
            return false;
        }
        Quest quest = this.getQuestAt(mouseX, mouseY);
        if (button == 1 && this.isEditing()) {
            if (this.linkSourceId != null) {
                this.linkSourceId = null;
            } else if (quest != null) {
                this.openQuestMenu(quest, (int) mouseX, (int) mouseY);
            } else {
                this.openMapMenu((int) mouseX, (int) mouseY);
            }
            return true;
        }
        if (button != 0) {
            return false;
        }
        if (this.isEditing() && this.linkSourceId != null) {
            if (quest != null && !quest.id().equals(this.linkSourceId)) {
                this.toggleDependency(quest);
            }
            return true;
        }
        this.pressedInMap = true;
        this.dragging = false;
        this.pressX = mouseX;
        this.pressY = mouseY;
        this.pressedQuestId = quest != null ? quest.id() : null;
        this.dragOrigins.clear();
        if (this.isEditing() && quest != null) {
            for (Quest moved : Screen.hasControlDown() ? this.collectWithDependents(quest) : List.of(quest)) {
                this.dragOrigins.put(moved.id(), new double[]{this.getX(moved), this.getY(moved)});
            }
        }
        return true;
    }

    private List<Quest> collectWithDependents(Quest root) {
        List<Quest> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        Deque<Quest> queue = new ArrayDeque<>();
        queue.add(root);
        while (!queue.isEmpty()) {
            Quest quest = queue.poll();
            if (!seen.add(quest.id())) {
                continue;
            }
            result.add(quest);
            for (Quest dependent : this.book.getDependents(quest.id())) {
                if (this.visibleById.containsKey(dependent.id())) {
                    queue.add(dependent);
                }
            }
        }
        return result;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (button != 0 || !this.pressedInMap || this.chapter == null) {
            return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
        }
        if (!this.dragging && Math.abs(mouseX - this.pressX) + Math.abs(mouseY - this.pressY) > 3.0D) {
            this.dragging = true;
        }
        if (!this.dragging) {
            return true;
        }
        View view = this.getView(this.chapter);
        if (!this.dragOrigins.isEmpty()) {
            double offsetX = (mouseX - this.pressX) / view.scale / GRID;
            double offsetY = (mouseY - this.pressY) / view.scale / GRID;
            this.dragOrigins.forEach((id, origin) -> this.positionOverrides.put(id, new double[]{snap(origin[0] + offsetX), snap(origin[1] + offsetY)}));
        } else {
            view.originX += deltaX / view.scale;
            view.originY += deltaY / view.scale;
        }
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button != 0 || !this.pressedInMap) {
            return super.mouseReleased(mouseX, mouseY, button);
        }
        this.pressedInMap = false;
        if (this.dragging && !this.dragOrigins.isEmpty()) {
            List<MoveQuestsPacket.Move> moves = new ArrayList<>();
            this.dragOrigins.forEach((id, origin) -> {
                double[] position = this.positionOverrides.get(id);
                if (position != null && (position[0] != origin[0] || position[1] != origin[1])) {
                    moves.add(new MoveQuestsPacket.Move(id, position[0], position[1]));
                }
            });
            if (!moves.isEmpty()) {
                ClientEditor.send(new MoveQuestsPacket(moves));
                this.playSound(SoundEvents.BLOCK_STONE_PLACE, 0.6F);
            }
        } else if (!this.dragging && this.pressedQuestId != null && this.client != null) {
            Quest quest = this.book.getQuest(this.pressedQuestId);
            if (quest != null) {
                this.playClick();
                if (this.isEditing()) {
                    this.openQuestEditor(quest);
                } else {
                    this.client.setScreen(new QuestDetailScreen(this, quest.id()));
                }
            }
        }
        this.dragOrigins.clear();
        this.dragging = false;
        this.pressedQuestId = null;
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (this.chapter == null || !this.isInMap(mouseX, mouseY)) {
            return false;
        }
        View view = this.getView(this.chapter);
        float oldScale = view.scale;
        float newScale = MathHelper.clamp(oldScale + (verticalAmount > 0 ? 0.1F : -0.1F), MIN_SCALE, MAX_SCALE);
        if (newScale != oldScale) {
            double mapMouseX = mouseX - this.mapLeft();
            double mapMouseY = mouseY - this.mapTop();
            double worldX = mapMouseX / oldScale - view.originX;
            double worldY = mapMouseY / oldScale - view.originY;
            view.scale = newScale;
            view.originX = mapMouseX / newScale - worldX;
            view.originY = mapMouseY / newScale - worldY;
        }
        return true;
    }

    private void openQuestEditor(Quest quest) {
        Chapter chapter = this.book.getChapterOf(quest.id());
        if (this.client != null && chapter != null) {
            this.client.setScreen(new QuestEditScreen(this, chapter.id(), quest));
        }
    }

    private void toggleDependency(Quest target) {
        Quest source = this.book.getQuest(this.linkSourceId);
        Chapter chapter = source != null ? this.book.getChapterOf(source.id()) : null;
        if (source == null || chapter == null) {
            return;
        }
        List<String> dependencies = new ArrayList<>(source.dependencies());
        if (!dependencies.remove(target.id())) {
            dependencies.add(target.id());
        }
        ClientEditor.send(new EditQuestPacket(chapter.id(), source.withDependencies(dependencies)));
        this.playClick();
    }

    private void openQuestMenu(Quest quest, int mouseX, int mouseY) {
        this.menu = new ContextMenu()
                .add(Text.translatable("gui.questz.editor.menu.edit_quest"), () -> this.openQuestEditor(quest))
                .add(Text.translatable("gui.questz.editor.menu.dependencies"), () -> this.linkSourceId = quest.id())
                .add(Text.translatable("gui.questz.editor.menu.copy_id"), () -> {
                    if (this.client != null) {
                        this.client.keyboard.setClipboard(quest.id());
                    }
                })
                .addDangerous(Text.translatable("gui.questz.editor.menu.delete_quest"), () -> this.confirmDeleteQuest(quest))
                .open(this.textRenderer, mouseX, mouseY, this.width, this.height);
    }

    private void openMapMenu(int mouseX, int mouseY) {
        ContextMenu menu = new ContextMenu();
        if (this.chapter != null) {
            View view = this.getView(this.chapter);
            double x = snap(this.toGridX(view, mouseX));
            double y = snap(this.toGridY(view, mouseY));
            Chapter chapter = this.chapter;
            menu.add(Text.translatable("gui.questz.editor.menu.new_quest"), () -> this.createQuest(chapter, x, y));
        }
        this.addChapterEntries(menu);
        this.menu = menu.open(this.textRenderer, mouseX, mouseY, this.width, this.height);
    }

    private void openChapterMenu(int mouseX, int mouseY) {
        ContextMenu menu = new ContextMenu();
        this.addChapterEntries(menu);
        this.menu = menu.open(this.textRenderer, mouseX, mouseY, this.width, this.height);
    }

    private void addChapterEntries(ContextMenu menu) {
        menu.add(Text.translatable("gui.questz.editor.menu.new_chapter"), this::createChapter);
        if (this.chapter != null) {
            Chapter chapter = this.chapter;
            menu.add(Text.translatable("gui.questz.editor.menu.edit_chapter"), () -> {
                if (this.client != null) {
                    this.client.setScreen(new ChapterEditScreen(this, chapter, false));
                }
            });
        }
    }

    private void createQuest(Chapter chapter, double x, double y) {
        Text title = Text.literal(Text.translatable("gui.questz.editor.new_quest_title").getString());
        Quest quest = new Quest(this.book.generateId(), title, List.of(), Optional.empty(), x, y, 1.0F, QuestShape.TASK, List.of(), DependencyMode.ALL, QuestVisibility.ALWAYS,
                List.of(), List.of(), false, false);
        if (this.client != null) {
            this.client.setScreen(new QuestEditScreen(this, chapter.id(), quest));
        }
    }

    private void createChapter() {
        int order = this.book.getChapters().stream().mapToInt(Chapter::order).max().orElse(-1) + 1;
        Chapter chapter = new Chapter(this.book.generateId(), Text.literal(Text.translatable("gui.questz.editor.new_chapter_title").getString()), new ItemStack(Items.BOOK), order,
                Optional.empty(), LineStyle.DIRECT, List.of());
        if (this.client != null) {
            this.client.setScreen(new ChapterEditScreen(this, chapter, true));
        }
    }

    private void confirmDeleteQuest(Quest quest) {
        if (this.client == null) {
            return;
        }
        this.client.setScreen(new ConfirmScreen(confirmed -> {
            if (confirmed) {
                ClientEditor.send(new DeleteQuestPacket(quest.id()));
            }
            this.client.setScreen(this);
        }, Text.translatable("gui.questz.editor.delete_quest.title"), Text.translatable("gui.questz.editor.delete_quest.message", QuestTexts.title(quest))));
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE && (this.menu != null || this.linkSourceId != null)) {
            this.menu = null;
            this.linkSourceId = null;
            return true;
        }
        if (this.isEditing() && keyCode == GLFW.GLFW_KEY_DELETE && this.client != null) {
            double mouseX = this.client.mouse.getX() * this.width / this.client.getWindow().getWidth();
            double mouseY = this.client.mouse.getY() * this.height / this.client.getWindow().getHeight();
            Quest quest = this.getQuestAt(mouseX, mouseY);
            if (quest != null) {
                this.confirmDeleteQuest(quest);
                return true;
            }
        }
        if (this.client != null && (KeyInit.screenKey.matchesKey(keyCode, scanCode) || this.client.options.inventoryKey.matchesKey(keyCode, scanCode))) {
            this.close();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void playClick() {
        this.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 1.0F);
    }

    private void playSound(SoundEvent sound, float pitch) {
        if (this.client != null) {
            this.client.getSoundManager().play(PositionedSoundInstance.master(sound, pitch));
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

}
