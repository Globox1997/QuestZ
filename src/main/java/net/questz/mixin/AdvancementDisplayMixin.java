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
import net.questz.access.DisplayAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

@Mixin(AdvancementDisplay.class)
public class AdvancementDisplayMixin implements DisplayAccess {

    @Unique
    private int manualX = 0;

    @Unique
    private int manualY = 0;

    @Unique
    private int tabOrder = 0;

    @Shadow
    private float x;
    @Shadow
    private float y;

    @Unique
    private Map<String, String> criterionOverrides = new HashMap<>();

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

    @Override
    public void setTabOrder(int order) {
        this.tabOrder = order;
    }

    @Override
    public int getTabOrder() {
        return this.tabOrder;
    }

    @Override
    public void setCriterionOverrides(Map<String, String> overrides) {
        this.criterionOverrides = overrides != null ? overrides : new HashMap<>();
    }

    @Override
    public Map<String, String> getCriterionOverrides() {
        return this.criterionOverrides;
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
                Codec.INT.optionalFieldOf("tab_order", 0).forGetter(d -> d instanceof DisplayAccess da ? da.getTabOrder() : 0),
                Codec.unboundedMap(Codec.STRING, Codec.STRING).optionalFieldOf("criterion_overrides", Map.of())
                        .forGetter(d -> d instanceof DisplayAccess da ? da.getCriterionOverrides() : Map.of()),
                Identifier.CODEC.optionalFieldOf("background").forGetter(AdvancementDisplay::getBackground),
                AdvancementFrame.CODEC.optionalFieldOf("frame", AdvancementFrame.TASK).forGetter(AdvancementDisplay::getFrame),
                Codec.BOOL.optionalFieldOf("show_toast", true).forGetter(AdvancementDisplay::shouldShowToast),
                Codec.BOOL.optionalFieldOf("announce_to_chat", true).forGetter(AdvancementDisplay::shouldAnnounceToChat),
                Codec.BOOL.optionalFieldOf("hidden", false).forGetter(AdvancementDisplay::isHidden)
        ).apply(instance, (icon, title, description, xManual, yManual, tabOrder, criterionOverrides, background, frame, showToast, announceToChat, hidden) -> {
            AdvancementDisplay advancementDisplay = new AdvancementDisplay(icon, title, description, background, frame, showToast, announceToChat, hidden);
            if (advancementDisplay instanceof DisplayAccess displayAccess) {
                if (xManual.isPresent() && yManual.isPresent()) {
                    displayAccess.setManualPosition(xManual.get(), yManual.get());
                }
                displayAccess.setTabOrder(tabOrder);
                displayAccess.setCriterionOverrides(criterionOverrides);
            }
            return advancementDisplay;
        }));
    }

    @Inject(method = "toPacket", at = @At("TAIL"))
    private void toPacketMixin(RegistryByteBuf buf, CallbackInfo info) {
        buf.writeInt(this.manualX);
        buf.writeInt(this.manualY);
        buf.writeInt(this.tabOrder);

        buf.writeInt(this.criterionOverrides.size());
        for (Map.Entry<String, String> entry : this.criterionOverrides.entrySet()) {
            buf.writeString(entry.getKey());
            buf.writeString(entry.getValue());
        }
    }

    @Inject(method = "fromPacket", at = @At("RETURN"))
    private static void fromPacketMixin(RegistryByteBuf buf, CallbackInfoReturnable<AdvancementDisplay> info) {
        if (info.getReturnValue() instanceof DisplayAccess displayAccess) {
            int manualX = buf.readInt();
            int manualY = buf.readInt();
            int tabOrder = buf.readInt();

            if (manualX != 0 || manualY != 0) {
                displayAccess.setManualPosition(manualX, manualY);
            }
            displayAccess.setTabOrder(tabOrder);

            int count = buf.readInt();
            Map<String, String> overrides = new HashMap<>();
            for (int i = 0; i < count; i++) {
                String key = buf.readString();
                String value = buf.readString();
                overrides.put(key, value);
            }
            displayAccess.setCriterionOverrides(overrides);
        }
    }

}