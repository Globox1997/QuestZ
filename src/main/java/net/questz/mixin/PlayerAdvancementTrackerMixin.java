package net.questz.mixin;

import net.minecraft.advancement.AdvancementEntry;
import net.minecraft.advancement.PlayerAdvancementTracker;
import net.minecraft.server.ServerAdvancementLoader;
import net.minecraft.server.network.ServerPlayerEntity;
import net.questz.tracking.CriterionBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerAdvancementTracker.class)
public class PlayerAdvancementTrackerMixin {

    @Shadow
    private ServerPlayerEntity owner;

    @Inject(method = "grantCriterion", at = @At("HEAD"), cancellable = true)
    private void grantCriterionMixin(AdvancementEntry advancement, String criterionName, CallbackInfoReturnable<Boolean> info) {
        if (CriterionBridge.handleGrant(this.owner, advancement)) {
            info.setReturnValue(false);
        }
    }

    @Inject(method = "clearCriteria", at = @At("TAIL"))
    private void clearCriteriaMixin(CallbackInfo info) {
        CriterionBridge.onTrackerCleared(this.owner);
    }

    @Inject(method = "reload", at = @At("TAIL"))
    private void reloadMixin(ServerAdvancementLoader advancementLoader, CallbackInfo info) {
        if (this.owner != null && this.owner.getServer() != null) {
            CriterionBridge.sync(this.owner);
        }
    }

}
