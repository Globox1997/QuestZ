package net.questz.mixin;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.stat.Stat;
import net.questz.tracking.TaskTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayerEntity.class)
public class ServerPlayerEntityMixin {

    @Inject(method = "increaseStat", at = @At("TAIL"))
    private void increaseStatMixin(Stat<?> stat, int amount, CallbackInfo info) {
        TaskTracker.onStat((ServerPlayerEntity) (Object) this, stat, amount);
    }
}
