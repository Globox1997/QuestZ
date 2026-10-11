package net.questz.init;

import com.mojang.serialization.MapCodec;
import net.minecraft.registry.Registry;
import net.questz.QuestzMain;
import net.questz.api.QuestzRegistries;
import net.questz.reward.CommandReward;
import net.questz.reward.ItemReward;
import net.questz.reward.LootTableReward;
import net.questz.reward.Reward;
import net.questz.reward.RewardType;
import net.questz.reward.UnresolvedReward;
import net.questz.reward.XpReward;

public class RewardTypeInit {

    public static final RewardType<ItemReward> ITEM = register("item", ItemReward.CODEC);
    public static final RewardType<XpReward> XP = register("xp", XpReward.CODEC);
    public static final RewardType<CommandReward> COMMAND = register("command", CommandReward.CODEC);
    public static final RewardType<LootTableReward> LOOT_TABLE = register("loot_table", LootTableReward.CODEC);
    public static final RewardType<UnresolvedReward> UNRESOLVED = register("unresolved", UnresolvedReward.CODEC);

    private static <T extends Reward> RewardType<T> register(String name, MapCodec<T> codec) {
        return Registry.register(QuestzRegistries.REWARD_TYPE, QuestzMain.identifierOf(name), new RewardType<>(codec));
    }

    public static void init() {
    }

}
