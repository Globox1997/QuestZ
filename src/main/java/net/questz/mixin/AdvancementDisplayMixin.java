package net.questz.mixin;

import com.mojang.datafixers.kinds.App;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.advancement.AdvancementDisplay;
import net.minecraft.advancement.AdvancementFrame;
import net.minecraft.item.ItemStack;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.text.TextCodecs;
import net.minecraft.util.Identifier;
import net.questz.QuestzMain;
import net.questz.access.DisplayAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;
import java.util.function.Function;

@Mixin(AdvancementDisplay.class)
public class AdvancementDisplayMixin implements DisplayAccess {

    @Unique
    private int manualX = 0;

    @Unique
    private int manualY = 0;

    @Shadow
    private float x;
    @Shadow
    private float y;

    @Override
    public void setManualPosition(int x, int y) {
        this.manualX = x;
        this.manualY = y;

        ((AdvancementDisplay) (Object) this).setPos(x, y);
    }

    @Override
    public int getManualX() {
        return this.manualX;
    }

    @Override
    public int getManualY() {
        return this.manualY;
    }

    @Inject(method = "setPos", at = @At("HEAD"), cancellable = true)
    private void onSetPosMixin(float x, float y, CallbackInfo info) {
        if (this.manualX != 0 || this.manualY != 0) {
            this.x = this.manualX;
            this.y = this.manualY;
            info.cancel();
        }
    }

    @Redirect(method = "<clinit>", at = @At(value = "INVOKE", target = "Lcom/mojang/serialization/codecs/RecordCodecBuilder;create(Ljava/util/function/Function;)Lcom/mojang/serialization/Codec;"))
    private static Codec<AdvancementDisplay> redirectCodec(Function<RecordCodecBuilder.Instance<AdvancementDisplay>, ? extends App<RecordCodecBuilder.Mu<AdvancementDisplay>, AdvancementDisplay>> builder) {
        return RecordCodecBuilder.create(instance -> instance.group(
                ItemStack.VALIDATED_CODEC.fieldOf("icon").forGetter(AdvancementDisplay::getIcon),
                TextCodecs.CODEC.fieldOf("title").forGetter(AdvancementDisplay::getTitle),
                TextCodecs.CODEC.fieldOf("description").forGetter(AdvancementDisplay::getDescription),
                Codec.INT.optionalFieldOf("x_manual").forGetter(d -> Optional.empty()),
                Codec.INT.optionalFieldOf("y_manual").forGetter(d -> Optional.empty()),
                Identifier.CODEC.optionalFieldOf("background").forGetter(AdvancementDisplay::getBackground),
                AdvancementFrame.CODEC.optionalFieldOf("frame", AdvancementFrame.TASK).forGetter(AdvancementDisplay::getFrame),
                Codec.BOOL.optionalFieldOf("show_toast", true).forGetter(AdvancementDisplay::shouldShowToast),
                Codec.BOOL.optionalFieldOf("announce_to_chat", true).forGetter(AdvancementDisplay::shouldAnnounceToChat),
                Codec.BOOL.optionalFieldOf("hidden", false).forGetter(AdvancementDisplay::isHidden)
        ).apply(instance, (icon, title, description, xManual, yManual, background, frame, showToast, announceToChat, hidden) -> {
            AdvancementDisplay advancementDisplay = new AdvancementDisplay(icon, title, description, background, frame, showToast, announceToChat, hidden);
            if (xManual.isPresent() && yManual.isPresent() && advancementDisplay instanceof DisplayAccess displayAccess) {
                displayAccess.setManualPosition(xManual.get(), yManual.get());
            }
            return advancementDisplay;
        }));
    }

    @Inject(method = "toPacket", at = @At("TAIL"))
    private void toPacketMixin(RegistryByteBuf buf, CallbackInfo info) {
        buf.writeInt(this.manualX);
        buf.writeInt(this.manualY);
    }

    @Inject(method = "fromPacket", at = @At("RETURN"))
    private static void fromPacketMixin(RegistryByteBuf buf, CallbackInfoReturnable<AdvancementDisplay> info) {
        if (info.getReturnValue() instanceof DisplayAccess displayAccess) {
            int manualX = buf.readInt();
            int manualY = buf.readInt();

            if (manualX != 0 || manualY != 0) {
                displayAccess.setManualPosition(manualX, manualY);
            }
        }
    }

}