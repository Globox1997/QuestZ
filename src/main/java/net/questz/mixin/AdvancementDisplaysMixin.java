package net.questz.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

import net.minecraft.advancement.AdvancementDisplays;
import net.questz.init.ConfigInit;

@Mixin(AdvancementDisplays.class)
public class AdvancementDisplaysMixin {

    @ModifyConstant(method = "calculateDisplay", constant = @Constant(intValue = 2))
    private static int calculateDisplayModifyMixin(int original) {
        return ConfigInit.CONFIG.questDisplayDepth;
    }

    @ModifyConstant(method = "shouldDisplay", constant = @Constant(intValue = 2))
    private static int shouldDisplayModifyMixin(int original) {
        return ConfigInit.CONFIG.questDisplayDepth;
    }

}
