package net.questz.client.editor;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.StringVisitable;
import net.minecraft.text.Text;
import net.minecraft.util.Language;
import net.minecraft.util.math.MathHelper;
import net.questz.client.screen.QuestzGui;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.function.IntConsumer;

@Environment(EnvType.CLIENT)
public class PopupList {

    public static final int ROW_HEIGHT = 12;
    private static final int COLOR_SELECTED = 0x50FFFFFF;
    private static final int COLOR_HOVER = 0x28FFFFFF;

    private final TextRenderer textRenderer;
    private final IntConsumer onPick;
    private List<Text> entries = List.of();
    private int x;
    private int y;
    private int width;
    private int maxRows;
    private int selected = -1;
    private int scroll;
    private boolean visible;

    public PopupList(TextRenderer textRenderer, IntConsumer onPick) {
        this.textRenderer = textRenderer;
        this.onPick = onPick;
    }

    public void show(int x, int y, int width, int maxRows, List<Text> entries, int selected) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.maxRows = Math.max(1, maxRows);
        this.entries = List.copyOf(entries);
        this.visible = !entries.isEmpty();
        this.scroll = 0;
        this.select(selected);
    }

    public void hide() {
        this.visible = false;
    }

    public boolean isVisible() {
        return this.visible;
    }

    public int getSelected() {
        return this.selected;
    }

    public int getVisibleRows() {
        return Math.min(this.entries.size(), this.maxRows);
    }

    public int getHeight() {
        return this.getVisibleRows() * ROW_HEIGHT + 2;
    }

    private int getMaxScroll() {
        return Math.max(0, this.entries.size() - this.maxRows);
    }

    private boolean hasScrollbar() {
        return this.getMaxScroll() > 0;
    }

    public boolean isMouseOver(double mouseX, double mouseY) {
        return this.visible && mouseX >= this.x && mouseX < this.x + this.width && mouseY >= this.y && mouseY < this.y + this.getHeight();
    }

    private void select(int index) {
        if (this.entries.isEmpty()) {
            this.selected = -1;
            return;
        }
        this.selected = index < 0 ? -1 : Math.floorMod(index, this.entries.size());
        if (this.selected >= 0) {
            if (this.selected < this.scroll) {
                this.scroll = this.selected;
            } else if (this.selected >= this.scroll + this.maxRows) {
                this.scroll = this.selected - this.maxRows + 1;
            }
        }
    }

    private int getEntryAt(double mouseX, double mouseY) {
        int textRight = this.hasScrollbar() ? this.x + this.width - QuestzGui.SCROLLBAR_WIDTH : this.x + this.width;
        if (!this.isMouseOver(mouseX, mouseY) || mouseX >= textRight) {
            return -1;
        }
        int row = (int) ((mouseY - this.y - 1) / ROW_HEIGHT);
        int index = this.scroll + row;
        return row >= 0 && row < this.getVisibleRows() && index < this.entries.size() ? index : -1;
    }

    public void render(DrawContext context, int mouseX, int mouseY) {
        if (!this.visible) {
            return;
        }
        context.getMatrices().push();
        context.getMatrices().translate(0.0F, 0.0F, 500.0F);
        int height = this.getHeight();
        int textRight = this.x + this.width - 1;
        if (this.hasScrollbar()) {
            textRight = this.x + this.width - QuestzGui.SCROLLBAR_WIDTH - 1;
            QuestzGui.drawInset(context, this.x, this.y, this.width - QuestzGui.SCROLLBAR_WIDTH, height);
            QuestzGui.drawScrollbar(context, this.x + this.width - QuestzGui.SCROLLBAR_WIDTH, this.y, height, this.scroll, this.getMaxScroll());
        } else {
            QuestzGui.drawInset(context, this.x, this.y, this.width, height);
        }
        int hovered = this.getEntryAt(mouseX, mouseY);
        for (int row = 0; row < this.getVisibleRows(); row++) {
            int index = this.scroll + row;
            int rowY = this.y + 1 + row * ROW_HEIGHT;
            if (index == this.selected) {
                context.fill(this.x + 1, rowY, textRight, rowY + ROW_HEIGHT, COLOR_SELECTED);
            } else if (index == hovered) {
                context.fill(this.x + 1, rowY, textRight, rowY + ROW_HEIGHT, COLOR_HOVER);
            }
            Text entry = this.entries.get(index);
            StringVisitable trimmed = this.textRenderer.trimToWidth(entry, textRight - this.x - 5);
            context.drawText(this.textRenderer, Language.getInstance().reorder(trimmed), this.x + 3, rowY + 2, index == this.selected ? 0xFFFFFF55 : 0xFFFFFFFF, true);
        }
        context.getMatrices().pop();
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!this.isMouseOver(mouseX, mouseY)) {
            return false;
        }
        int index = this.getEntryAt(mouseX, mouseY);
        if (button == 0 && index >= 0) {
            this.visible = false;
            this.onPick.accept(index);
        }
        return true;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double verticalAmount) {
        if (!this.isMouseOver(mouseX, mouseY)) {
            return false;
        }
        this.scroll = MathHelper.clamp(this.scroll - (int) Math.signum(verticalAmount), 0, this.getMaxScroll());
        return true;
    }

    public boolean keyPressed(int keyCode) {
        if (!this.visible) {
            return false;
        }
        switch (keyCode) {
            case GLFW.GLFW_KEY_UP -> this.select(this.selected <= 0 ? this.entries.size() - 1 : this.selected - 1);
            case GLFW.GLFW_KEY_DOWN -> this.select(this.selected + 1);
            case GLFW.GLFW_KEY_PAGE_UP -> this.select(Math.max(0, this.selected - this.maxRows));
            case GLFW.GLFW_KEY_PAGE_DOWN -> this.select(Math.min(this.entries.size() - 1, this.selected + this.maxRows));
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER, GLFW.GLFW_KEY_TAB -> {
                if (this.selected < 0) {
                    return false;
                }
                this.visible = false;
                this.onPick.accept(this.selected);
            }
            case GLFW.GLFW_KEY_ESCAPE -> this.visible = false;
            default -> {
                return false;
            }
        }
        return true;
    }

}
