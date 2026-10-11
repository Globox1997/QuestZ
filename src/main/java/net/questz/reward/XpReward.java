package net.questz.reward;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.network.ServerPlayerEntity;
import net.questz.init.RewardTypeInit;

public class XpReward extends Reward {

    public static final MapCodec<XpReward> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Reward.<XpReward>idField(),
            Reward.<XpReward>teamRewardField(),
            Codec.INT.fieldOf("amount").forGetter(XpReward::getAmount),
            Codec.BOOL.optionalFieldOf("levels", false).forGetter(XpReward::isLevels)
    ).apply(instance, XpReward::new));

    private final int amount;
    private final boolean levels;

    public XpReward(String id, boolean teamReward, int amount, boolean levels) {
        super(id, teamReward);
        this.amount = amount;
        this.levels = levels;
    }

    public int getAmount() {
        return this.amount;
    }

    public boolean isLevels() {
        return this.levels;
    }

    @Override
    public RewardType<?> getType() {
        return RewardTypeInit.XP;
    }

    @Override
    public void claim(ServerPlayerEntity player) {
        if (this.levels) {
            player.addExperienceLevels(this.amount);
        } else {
            player.addExperience(this.amount);
        }
    }

}
