package net.questz.client.editor;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

@Environment(EnvType.CLIENT)
public class ContextMenu {

    private static final int ENTRY_HEIGHT = 12;

    private record Entry(Text label, int color, Runnable action) {
    }

    private final List<Entry> entries = new ArrayList<>();
    private int x;
    private int y;
    private int width;

    public ContextMenu add(Text label, Runnable action) {
        this.entries.add(new Entry(label, 0xFFFFFFFF, action));
        return this;
    }

    public ContextMenu addDangerous(Text label, Runnable action) {
        this.entries.add(new Entry(label, 0xFFFF5555, action));
        return this;
    }

    public ContextMenu open(TextRenderer textRenderer, int mouseX, int mouseY, int screenWidth, int screenHeight) {
        this.width = 0;
        for (Entry entry : this.entries) {
            this.width = Math.max(this.width, textRenderer.getWidth(entry.label()) + 10);
        }
        int height = this.entries.size() * ENTRY_HEIGHT + 4;
        this.x = Math.min(mouseX, screenWidth - this.width - 2);
        this.y = Math.min(mouseY, screenHeight - height - 2);
        return this;
    }

    public void render(DrawContext context, TextRenderer textRenderer, int mouseX, int mouseY) {
        int height = this.entries.size() * ENTRY_HEIGHT + 4;
        context.getMatrices().push();
        context.getMatrices().translate(0.0F, 0.0F, 500.0F);
        context.fill(this.x, this.y, this.x + this.width, this.y + height, 0xF0100010);
        context.drawBorder(this.x, this.y, this.width, height, 0xFF5A5A5A);
        for (int i = 0; i < this.entries.size(); i++) {
            Entry entry = this.entries.get(i);
            int entryY = this.y + 2 + i * ENTRY_HEIGHT;
            if (mouseX >= this.x && mouseX < this.x + this.width && mouseY >= entryY && mouseY < entryY + ENTRY_HEIGHT) {
                context.fill(this.x + 1, entryY, this.x + this.width - 1, entryY + ENTRY_HEIGHT, 0x40FFFFFF);
            }
            context.drawText(textRenderer, entry.label(), this.x + 5, entryY + 2, entry.color(), false);
        }
        context.getMatrices().pop();
    }

    public boolean click(double mouseX, double mouseY) {
        for (int i = 0; i < this.entries.size(); i++) {
            int entryY = this.y + 2 + i * ENTRY_HEIGHT;
            if (mouseX >= this.x && mouseX < this.x + this.width && mouseY >= entryY && mouseY < entryY + ENTRY_HEIGHT) {
                this.entries.get(i).action().run();
                return true;
            }
        }
        return false;
    }

}
