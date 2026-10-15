package net.questz.client.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;
import net.questz.QuestzMain;

@Environment(EnvType.CLIENT)
public class QuestzGui {

    public static final Identifier PANEL_TEXTURE = QuestzMain.identifierOf("textures/gui/task.png");
    public static final Identifier SLIDER_TEXTURE = QuestzMain.identifierOf("textures/gui/slider.png");
    public static final Identifier SLOT = Identifier.ofVanilla("container/slot");
    public static final Identifier CHECK = QuestzMain.identifierOf("textures/gui/check.png");
    public static final Identifier CLOSE = QuestzMain.identifierOf("textures/gui/close.png");
    public static final Identifier MARKER = QuestzMain.identifierOf("textures/gui/marker.png");
    public static final Identifier TEAM = QuestzMain.identifierOf("textures/gui/team.png");
    public static final Identifier STAR = QuestzMain.identifierOf("textures/gui/star.png");
    public static final Identifier ARROW_LEFT = QuestzMain.identifierOf("textures/gui/arrow_left.png");
    public static final Identifier ARROW_RIGHT = QuestzMain.identifierOf("textures/gui/arrow_right.png");
    public static final Identifier ARROW_UP = QuestzMain.identifierOf("textures/gui/arrow_up.png");
    public static final Identifier ARROW_DOWN = QuestzMain.identifierOf("textures/gui/arrow_down.png");
    public static final Identifier REFRESH = QuestzMain.identifierOf("textures/gui/refresh.png");
    public static final Identifier BUTTON = Identifier.ofVanilla("widget/button");
    public static final Identifier BUTTON_HIGHLIGHTED = Identifier.ofVanilla("widget/button_highlighted");
    public static final Identifier BUTTON_DISABLED = Identifier.ofVanilla("widget/button_disabled");
    public static final int PANEL_BORDER = 4;
    public static final int INSET_BORDER = 1;
    public static final int SCROLLBAR_WIDTH = 8;
    public static final int SLOT_SIZE = 18;
    public static final int COLOR_LABEL = 0xFF404040;
    private static final int PANEL_TEXTURE_SIZE = 15;
    private static final int SLIDER_TEXTURE_SIZE = 256;
    private static final int INSET_SIZE = 9;
    private static final int TRACK_U = 9;
    private static final int TRACK_HEIGHT = 9;
    private static final int HANDLE_AVAILABLE_U = 17;
    private static final int HANDLE_UNAVAILABLE_U = 23;
    private static final int HANDLE_WIDTH = 6;
    private static final int HANDLE_HEIGHT = 29;
    private static final int ICON_TEXTURE_SIZE = 16;
    private static final float ICON_IDLE_BRIGHTNESS = 0.7F;

    public static void drawPanel(DrawContext context, int x, int y, int width, int height) {
        drawNineSlice(context, PANEL_TEXTURE, PANEL_TEXTURE_SIZE, 0, 0, PANEL_TEXTURE_SIZE, PANEL_BORDER, x, y, width, height);
    }

    public static void drawInset(DrawContext context, int x, int y, int width, int height) {
        drawNineSlice(context, SLIDER_TEXTURE, SLIDER_TEXTURE_SIZE, 0, 0, INSET_SIZE, INSET_BORDER, x, y, width, height);
    }

    public static void drawIcon(DrawContext context, Identifier icon, int x, int y, int size, boolean highlighted) {
        RenderSystem.enableBlend();
        if (!highlighted) {
            RenderSystem.setShaderColor(ICON_IDLE_BRIGHTNESS, ICON_IDLE_BRIGHTNESS, ICON_IDLE_BRIGHTNESS, 1.0F);
        }
        context.drawTexture(icon, x, y, size, size, 0.0F, 0.0F, ICON_TEXTURE_SIZE, ICON_TEXTURE_SIZE, ICON_TEXTURE_SIZE, ICON_TEXTURE_SIZE);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    public static void drawSlot(DrawContext context, int x, int y) {
        context.drawGuiTexture(SLOT, x, y, SLOT_SIZE, SLOT_SIZE);
    }

    public static void drawScrollbar(DrawContext context, int x, int top, int height, double scroll, double maxScroll) {
        slice(context, SLIDER_TEXTURE, SLIDER_TEXTURE_SIZE, x, top, SCROLLBAR_WIDTH, 1, TRACK_U, 0, SCROLLBAR_WIDTH, 1);
        slice(context, SLIDER_TEXTURE, SLIDER_TEXTURE_SIZE, x, top + 1, SCROLLBAR_WIDTH, height - 2, TRACK_U, 1, SCROLLBAR_WIDTH, TRACK_HEIGHT - 2);
        slice(context, SLIDER_TEXTURE, SLIDER_TEXTURE_SIZE, x, top + height - 1, SCROLLBAR_WIDTH, 1, TRACK_U, TRACK_HEIGHT - 1, SCROLLBAR_WIDTH, 1);
        int handleHeight = handleHeight(height);
        int handleU = maxScroll > 0 ? HANDLE_AVAILABLE_U : HANDLE_UNAVAILABLE_U;
        slice(context, SLIDER_TEXTURE, SLIDER_TEXTURE_SIZE, x + 1, handleY(top, height, scroll, maxScroll), HANDLE_WIDTH, handleHeight, handleU, 0, HANDLE_WIDTH, handleHeight);
    }

    public static int handleHeight(int trackHeight) {
        return Math.max(0, Math.min(HANDLE_HEIGHT, trackHeight - 2));
    }

    public static int handleY(int top, int trackHeight, double scroll, double maxScroll) {
        int travel = trackHeight - 2 - handleHeight(trackHeight);
        return top + 1 + (maxScroll > 0 ? (int) Math.round(travel * scroll / maxScroll) : 0);
    }

    public static double scrollForHandle(int top, int trackHeight, double handleTop, double maxScroll) {
        int travel = trackHeight - 2 - handleHeight(trackHeight);
        if (travel <= 0 || maxScroll <= 0) {
            return 0.0D;
        }
        return Math.max(0.0D, Math.min(maxScroll, (handleTop - top - 1) / travel * maxScroll));
    }

    public static void drawButton(DrawContext context, int x, int y, int width, int height, boolean active, boolean hovered) {
        context.drawGuiTexture(!active ? BUTTON_DISABLED : hovered ? BUTTON_HIGHLIGHTED : BUTTON, x, y, width, height);
    }

    private static void drawNineSlice(DrawContext context, Identifier texture, int textureSize, int u, int v, int size, int border, int x, int y, int width, int height) {
        int center = size - border * 2;
        int innerWidth = width - border * 2;
        int innerHeight = height - border * 2;
        int far = size - border;
        slice(context, texture, textureSize, x, y, border, border, u, v, border, border);
        slice(context, texture, textureSize, x + border, y, innerWidth, border, u + border, v, center, border);
        slice(context, texture, textureSize, x + width - border, y, border, border, u + far, v, border, border);
        slice(context, texture, textureSize, x, y + border, border, innerHeight, u, v + border, border, center);
        slice(context, texture, textureSize, x + border, y + border, innerWidth, innerHeight, u + border, v + border, center, center);
        slice(context, texture, textureSize, x + width - border, y + border, border, innerHeight, u + far, v + border, border, center);
        slice(context, texture, textureSize, x, y + height - border, border, border, u, v + far, border, border);
        slice(context, texture, textureSize, x + border, y + height - border, innerWidth, border, u + border, v + far, center, border);
        slice(context, texture, textureSize, x + width - border, y + height - border, border, border, u + far, v + far, border, border);
    }

    private static void slice(DrawContext context, Identifier texture, int textureSize, int x, int y, int width, int height, int u, int v, int uWidth, int vHeight) {
        if (width > 0 && height > 0) {
            context.drawTexture(texture, x, y, width, height, u, v, uWidth, vHeight, textureSize, textureSize);
        }
    }

}
