package net.questz.mixin;

import net.minecraft.block.Block;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.Item;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.stat.Stat;
import net.minecraft.stat.Stats;
import net.questz.init.CriteriaInit;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayerEntity.class)
public class ServerPlayerEntityMixin {

    // Gets called pretty often
    @Inject(method = "increaseStat", at = @At("TAIL"))
    private void increaseStatMixin(Stat<?> stat, int amount, CallbackInfo info) {
        if (stat.getType().equals(Stats.USED)) {
            if (stat.getValue() instanceof Item) {
                CriteriaInit.PLACED_BLOCK_COUNT.trigger((ServerPlayerEntity) (Object) this, (Item) stat.getValue(), null, null, 0);
            }
        } else if (stat.getType().equals(Stats.MINED)) {
            if (stat.getValue() instanceof Block) {
                CriteriaInit.MINED_BLOCK_COUNT.trigger((ServerPlayerEntity) (Object) this, null, (Block) stat.getValue(), null, 1);
            }
        } else if (stat.getType().equals(Stats.KILLED)) {
            if (stat.getValue() instanceof LivingEntity) {
                CriteriaInit.KILLED_MOB_COUNT.trigger((ServerPlayerEntity) (Object) this, null, null, (LivingEntity) stat.getValue(), 2);
            }
        } else if (stat.getType().equals(Stats.CRAFTED)) {
            if (stat.getValue() instanceof Item) {
                CriteriaInit.CRAFT_ITEM_COUNT.trigger((ServerPlayerEntity) (Object) this, (Item) stat.getValue(), null, null, 3);
            }
        }
    }
}
