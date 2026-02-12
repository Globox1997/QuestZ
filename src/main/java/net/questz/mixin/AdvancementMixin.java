package net.questz.mixin;

import net.minecraft.advancement.Advancement;
import net.minecraft.advancement.AdvancementCriterion;
import net.minecraft.advancement.AdvancementRewards;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashMap;
import java.util.Map;

@Mixin(Advancement.class)
public class AdvancementMixin {

    @Shadow
    @Final
    private AdvancementRewards rewards;

    @Shadow
    @Final
    private Map<String, AdvancementCriterion<?>> criteria;

    @Unique
    private static final PacketCodec<RegistryByteBuf, Map<String, AdvancementCriterion<?>>> CRITERIA_MAP_CODEC =
            PacketCodecs.map(
                    HashMap::new,
                    PacketCodecs.STRING,
                    PacketCodecs.registryCodec(AdvancementCriterion.CODEC)
            );

    @Inject(method = "write", at = @At(value = "INVOKE", target = "Lnet/minecraft/advancement/AdvancementRequirements;writeRequirements(Lnet/minecraft/network/PacketByteBuf;)V"))
    private void writeMixin(RegistryByteBuf buf, CallbackInfo info) {
        PacketCodecs.codec(AdvancementRewards.CODEC).encode(buf, this.rewards);
        CRITERIA_MAP_CODEC.encode(buf, this.criteria);
    }

    @Redirect(method = "read", at = @At(value = "FIELD", target = "Lnet/minecraft/advancement/AdvancementRewards;NONE:Lnet/minecraft/advancement/AdvancementRewards;", opcode = Opcodes.GETSTATIC))
    private static AdvancementRewards read2Mixin(RegistryByteBuf buf) {
        return PacketCodecs.codec(AdvancementRewards.CODEC).decode(buf);
    }

    @Redirect(method = "read", at = @At(value = "INVOKE", target = "Ljava/util/Map;of()Ljava/util/Map;", opcode = Opcodes.GETSTATIC))
    private static Map<String, AdvancementCriterion<?>> readMixin(RegistryByteBuf buf) {
        return CRITERIA_MAP_CODEC.decode(buf);
    }

}
