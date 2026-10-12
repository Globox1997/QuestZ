package net.questz.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.toast.Toast;
import net.minecraft.client.toast.ToastManager;
import net.minecraft.item.ItemStack;
import net.minecraft.text.StringVisitable;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.Language;
import net.questz.book.Quest;
import net.questz.client.screen.QuestTexts;
import net.questz.init.KeyInit;

@Environment(EnvType.CLIENT)
public class QuestToast implements Toast {

    private static final Identifier TEXTURE = Identifier.ofVanilla("toast/advancement");
    private static final long DURATION = 5000L;

    private final Text title;
    private final Text header;
    private final ItemStack icon;

    public QuestToast(Quest quest, boolean rewardsWaiting) {
        this.title = QuestTexts.title(quest);
        this.header = rewardsWaiting
                ? Text.translatable("toast.questz.completed_rewards", KeyInit.screenKey.getBoundKeyLocalizedText())
                : Text.translatable("toast.questz.completed");
        this.icon = QuestTexts.icon(quest);
    }

    @Override
    public Visibility draw(DrawContext context, ToastManager manager, long startTime) {
        TextRenderer textRenderer = manager.getClient().textRenderer;
        context.drawGuiTexture(TEXTURE, 0, 0, this.getWidth(), this.getHeight());
        context.drawText(textRenderer, this.header, 30, 7, 0xFFFFFF00, false);
        StringVisitable trimmed = textRenderer.trimToWidth(this.title, this.getWidth() - 36);
        context.drawText(textRenderer, Language.getInstance().reorder(trimmed), 30, 18, 0xFFFFFFFF, false);
        context.drawItemWithoutEntity(this.icon, 8, 8);
        return startTime >= DURATION ? Visibility.HIDE : Visibility.SHOW;
    }

}
