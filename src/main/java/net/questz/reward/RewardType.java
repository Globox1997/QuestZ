package net.questz.reward;

import com.mojang.serialization.MapCodec;

public record RewardType<T extends Reward>(MapCodec<T> codec) {
}
