package net.questz.mixin;

import net.minecraft.advancement.AdvancementDisplays;
import net.minecraft.advancement.PlacedAdvancement;
import net.questz.init.ConfigInit;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(AdvancementDisplays.class)
public class AdvancementDisplaysMixin {

    @ModifyConstant(method = "calculateDisplay", constant = @Constant(intValue = 2))
    private static int calculateDisplayModifyMixin(int original) {
        return ConfigInit.CONFIG.questDisplayDepth;
    }

    @ModifyConstant(method = "shouldDisplay(Lit/unimi/dsi/fastutil/Stack;)Z", constant = @Constant(intValue = 2))
    private static int shouldDisplayModifyMixin(int original) {
        return ConfigInit.CONFIG.questDisplayDepth;
    }

    @ModifyVariable(method = "shouldDisplay(Lnet/minecraft/advancement/PlacedAdvancement;Lit/unimi/dsi/fastutil/Stack;Ljava/util/function/Predicate;Lnet/minecraft/advancement/AdvancementDisplays$ResultConsumer;)Z", at = @At(value = "INVOKE", target = "Lit/unimi/dsi/fastutil/Stack;pop()Ljava/lang/Object;"), ordinal = 2)
    private static boolean shouldDisplayMixin(boolean original, PlacedAdvancement advancement) {
        if (!original && ConfigInit.CONFIG.questAdvancementNamespaceIds.contains(advancement.getAdvancementEntry().id().getNamespace())) {
            return true;
        }
        return original;
    }

}
