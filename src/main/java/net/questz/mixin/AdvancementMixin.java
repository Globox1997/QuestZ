package net.questz.mixin;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.advancement.Advancement;
import net.minecraft.advancement.AdvancementCriterion;
import net.minecraft.advancement.AdvancementRewards;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.registry.RegistryOps;
import net.minecraft.util.Identifier;
import net.questz.access.AdvancementAccess;
import net.questz.init.ConfigInit;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@Mixin(Advancement.class)
public class AdvancementMixin implements AdvancementAccess {

    @Mutable
    @Shadow
    @Final
    private AdvancementRewards rewards;

    @Mutable
    @Shadow
    @Final
    private Map<String, AdvancementCriterion<?>> criteria;

    @Shadow
    @Final
    private Optional<Identifier> parent;

    @Override
    public void questz$setCriteria(Map<String, AdvancementCriterion<?>> criteria) {
        this.criteria = criteria;
    }

    @Override
    public void questz$setRewards(AdvancementRewards rewards) {
        this.rewards = rewards;
    }

    @Inject(method = "write", at = @At("TAIL"))
    private void writeMixin(RegistryByteBuf buf, CallbackInfo info) {
        boolean isQuestz = this.parent.map(identifier -> {
            for (String namespace : ConfigInit.CONFIG.questAdvancementNamespaceIds) {
                if (identifier.getNamespace().equals(namespace)) {
                    return true;
                }
            }
            return false;
        }).orElse(false);

        buf.writeBoolean(this.parent.isEmpty() || isQuestz);
        if (this.parent.isEmpty() || isQuestz) {
            PacketCodecs.codec(AdvancementRewards.CODEC).encode(buf, this.rewards);
            buf.writeVarInt(this.criteria.size());
            this.criteria.forEach((name, criterion) -> {
                buf.writeString(name, 262144);
                JsonElement json = AdvancementCriterion.CODEC.encodeStart(RegistryOps.of(JsonOps.INSTANCE, buf.getRegistryManager()), criterion).result().orElse(null);
                buf.writeString(json != null ? json.toString() : "{}", 262144);
            });
        }
    }

    @Inject(method = "read", at = @At("TAIL"))
    private static void readMixin(RegistryByteBuf buf, CallbackInfoReturnable<Advancement> info) {
        Advancement result = info.getReturnValue();

        boolean hasExtraData = buf.readBoolean();
        if (!hasExtraData) {
            return;
        }
        AdvancementRewards rewards;
        try {
            rewards = PacketCodecs.codec(AdvancementRewards.CODEC).decode(buf);
        } catch (Exception e) {
            rewards = AdvancementRewards.NONE;
        }

        Map<String, AdvancementCriterion<?>> criteria = new LinkedHashMap<>();
        try {
            int size = buf.readVarInt();
            for (int i = 0; i < size; i++) {
                String name = buf.readString(262144);
                String jsonStr = buf.readString(262144);
                if (jsonStr.equals("{}")) {
                    continue;
                }
                try {
                    JsonElement json = JsonParser.parseString(jsonStr);
                    AdvancementCriterion.CODEC.parse(RegistryOps.of(JsonOps.INSTANCE, buf.getRegistryManager()), json).result().ifPresent(criterion -> criteria.put(name, criterion));
                } catch (Exception ignored) {
                }
            }
        } catch (Exception ignored) {
        }

        ((AdvancementAccess) (Object) result).questz$setRewards(rewards);
        ((AdvancementAccess) (Object) result).questz$setCriteria(criteria);
    }

    @ModifyArg(method = "<init>(Ljava/util/Optional;Ljava/util/Optional;Lnet/minecraft/advancement/AdvancementRewards;Ljava/util/Map;Lnet/minecraft/advancement/AdvancementRequirements;Z)V", at = @At(value = "INVOKE", target = "Ljava/util/Map;copyOf(Ljava/util/Map;)Ljava/util/Map;"))
    private static Map<?, ?> preserveOrder(Map<?, ?> original) {
        return new LinkedHashMap<>(original);
    }

}
