package net.questz.mixin;

import net.minecraft.advancement.Advancement;
import net.minecraft.advancement.AdvancementRewards;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodecs;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Advancement.class)
public class AdvancementMixin {

    @Shadow
    @Final
    private AdvancementRewards rewards;


    @Inject(method = "write", at = @At(value = "INVOKE", target = "Lnet/minecraft/advancement/AdvancementRequirements;writeRequirements(Lnet/minecraft/network/PacketByteBuf;)V"))
    private void writeMixin(RegistryByteBuf buf, CallbackInfo info) {
        PacketCodecs.codec(AdvancementRewards.CODEC).encode(buf, this.rewards);
    }

    @Redirect(method = "read", at = @At(value = "FIELD", target = "Lnet/minecraft/advancement/AdvancementRewards;NONE:Lnet/minecraft/advancement/AdvancementRewards;", opcode = Opcodes.GETSTATIC))
    private static AdvancementRewards readCustomRewards(RegistryByteBuf buf) {
        return PacketCodecs.codec(AdvancementRewards.CODEC).decode(buf);
    }

}
