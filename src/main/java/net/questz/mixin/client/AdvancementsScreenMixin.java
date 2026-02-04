package net.questz.mixin.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.advancement.PlacedAdvancement;
import net.minecraft.client.gui.screen.advancement.AdvancementsScreen;
import net.questz.init.ConfigInit;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(AdvancementsScreen.class)
public class AdvancementsScreenMixin {

    @Inject(method = "onRootAdded", at = @At(value = "INVOKE", target = "Ljava/util/Map;put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"), cancellable = true)
    private void onRootAddedMixin(PlacedAdvancement root, CallbackInfo info) {
        if (ConfigInit.CONFIG.questAdvancementNamespaceIds.contains(root.getAdvancementEntry().id().getNamespace())) {
            info.cancel();
        }
    }

}
