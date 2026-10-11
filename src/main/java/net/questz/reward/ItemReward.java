package net.questz.reward;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.questz.init.RewardTypeInit;

public class ItemReward extends Reward {

    public static final MapCodec<ItemReward> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Reward.<ItemReward>idField(),
            Reward.<ItemReward>teamRewardField(),
            ItemStack.UNCOUNTED_CODEC.fieldOf("item").forGetter(ItemReward::getItem),
            Codec.INT.optionalFieldOf("count", 1).forGetter(ItemReward::getCount)
    ).apply(instance, ItemReward::new));

    private final ItemStack item;
    private final int count;

    public ItemReward(String id, boolean teamReward, ItemStack item, int count) {
        super(id, teamReward);
        this.item = item;
        this.count = Math.max(1, count);
    }

    public ItemStack getItem() {
        return this.item;
    }

    public int getCount() {
        return this.count;
    }

    @Override
    public RewardType<?> getType() {
        return RewardTypeInit.ITEM;
    }

    @Override
    public void claim(ServerPlayerEntity player) {
        int remaining = this.count;
        while (remaining > 0) {
            int size = Math.min(remaining, this.item.getMaxCount());
            player.getInventory().offerOrDrop(this.item.copyWithCount(size));
            remaining -= size;
        }
    }

}
