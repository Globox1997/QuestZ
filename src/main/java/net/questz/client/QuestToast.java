package net.questz.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.advancement.AdvancementFrame;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.toast.Toast;
import net.minecraft.client.toast.ToastManager;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundEvents;
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
    private final AdvancementFrame frame;
    private boolean soundPlayed;

    public QuestToast(Quest quest, boolean rewardsWaiting) {
        this.title = QuestTexts.title(quest);
        this.header = rewardsWaiting
                ? Text.translatable("toast.questz.completed_rewards", KeyInit.screenKey.getBoundKeyLocalizedText())
                : Text.translatable("toast.questz.completed");
        this.icon = QuestTexts.icon(quest);
        this.frame = quest.shape().getFrame();
    }

    @Override
    public Visibility draw(DrawContext context, ToastManager manager, long startTime) {
        TextRenderer textRenderer = manager.getClient().textRenderer;
        if (!this.soundPlayed && startTime > 0L) {
            this.soundPlayed = true;
            if (this.frame == AdvancementFrame.CHALLENGE) {
                manager.getClient().getSoundManager().play(PositionedSoundInstance.master(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.0F, 1.0F));
            }
        }
        context.drawGuiTexture(TEXTURE, 0, 0, this.getWidth(), this.getHeight());
        context.drawText(textRenderer, this.header, 30, 7, 0xFFFFFF00, false);
        StringVisitable trimmed = textRenderer.trimToWidth(this.title, this.getWidth() - 36);
        context.drawText(textRenderer, Language.getInstance().reorder(trimmed), 30, 18, 0xFFFFFFFF, false);
        context.drawItemWithoutEntity(this.icon, 8, 8);
        return startTime >= DURATION ? Visibility.HIDE : Visibility.SHOW;
    }

}
