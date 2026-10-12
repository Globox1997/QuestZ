package net.questz.reward;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.questz.init.RewardTypeInit;

import java.util.List;

public class ChoiceReward extends Reward {

    public static final MapCodec<ChoiceReward> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Reward.<ChoiceReward>idField(),
            Reward.<ChoiceReward>teamRewardField(),
            ItemStack.CODEC.listOf().fieldOf("options").forGetter(ChoiceReward::getOptions)
    ).apply(instance, ChoiceReward::new));

    private final List<ItemStack> options;

    public ChoiceReward(String id, boolean teamReward, List<ItemStack> options) {
        super(id, teamReward);
        this.options = List.copyOf(options);
    }

    public List<ItemStack> getOptions() {
        return this.options;
    }

    @Override
    public boolean requiresChoice() {
        return true;
    }

    @Override
    public RewardType<?> getType() {
        return RewardTypeInit.CHOICE;
    }

    @Override
    public void claim(ServerPlayerEntity player) {
    }

    public void claim(ServerPlayerEntity player, int option) {
        if (option >= 0 && option < this.options.size()) {
            player.getInventory().offerOrDrop(this.options.get(option).copy());
        }
    }

}
