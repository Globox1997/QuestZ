package net.questz.client.render;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.questz.init.RewardTypeInit;
import net.questz.reward.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Environment(EnvType.CLIENT)
public class RewardRenderers {

    private static final Map<RewardType<?>, RewardRenderer<?>> RENDERERS = new HashMap<>();

    public static <T extends Reward> void register(RewardType<T> type, RewardRenderer<T> renderer) {
        RENDERERS.put(type, renderer);
    }

    @SuppressWarnings("unchecked")
    private static <T extends Reward> Optional<RewardRenderer<T>> get(T reward) {
        return Optional.ofNullable((RewardRenderer<T>) RENDERERS.get(reward.getType()));
    }

    public static Text getTitle(Reward reward) {
        return get(reward).map(renderer -> renderer.getTitle(reward)).orElseGet(() -> Text.literal(String.valueOf(reward.getTypeId())));
    }

    public static ItemStack getIcon(Reward reward) {
        return get(reward).map(renderer -> renderer.getIcon(reward)).orElseGet(() -> new ItemStack(Items.PAPER));
    }

    public static void init() {
        register(RewardTypeInit.ITEM, new RewardRenderer<>() {
            public Text getTitle(ItemReward reward) {
                return Text.translatable("reward.questz.item", reward.getCount(), reward.getItem().getName());
            }

            public ItemStack getIcon(ItemReward reward) {
                return reward.getItem().copyWithCount(Math.min(reward.getCount(), reward.getItem().getMaxCount()));
            }
        });
        register(RewardTypeInit.XP, new RewardRenderer<>() {
            public Text getTitle(XpReward reward) {
                return Text.translatable(reward.isLevels() ? "reward.questz.xp_levels" : "reward.questz.xp", reward.getAmount());
            }

            public ItemStack getIcon(XpReward reward) {
                return new ItemStack(Items.EXPERIENCE_BOTTLE);
            }
        });
        register(RewardTypeInit.COMMAND, new RewardRenderer<>() {
            public Text getTitle(CommandReward reward) {
                return Text.translatable("reward.questz.command");
            }

            public ItemStack getIcon(CommandReward reward) {
                return new ItemStack(Items.NETHER_STAR);
            }
        });
        register(RewardTypeInit.LOOT_TABLE, new RewardRenderer<>() {
            public Text getTitle(LootTableReward reward) {
                return Text.translatable("reward.questz.loot_table", DisplayNames.humanize(reward.getLootTable().getValue().getPath()));
            }

            public ItemStack getIcon(LootTableReward reward) {
                return new ItemStack(Items.CHEST);
            }
        });
        register(RewardTypeInit.CHOICE, new RewardRenderer<>() {
            public Text getTitle(ChoiceReward reward) {
                return Text.translatable("reward.questz.choice", reward.getOptions().size());
            }

            public ItemStack getIcon(ChoiceReward reward) {
                return reward.getOptions().isEmpty() ? new ItemStack(Items.BUNDLE) : reward.getOptions().getFirst();
            }
        });
        register(RewardTypeInit.RANDOM, new RewardRenderer<>() {
            public Text getTitle(RandomReward reward) {
                return Text.translatable("reward.questz.random", reward.getRolls(), reward.getEntries().size());
            }

            public ItemStack getIcon(RandomReward reward) {
                return new ItemStack(Items.BUNDLE);
            }
        });
        register(RewardTypeInit.UNRESOLVED, new RewardRenderer<>() {
            public Text getTitle(UnresolvedReward reward) {
                return Text.translatable("reward.questz.unresolved", reward.getTypeId().toString());
            }

            public ItemStack getIcon(UnresolvedReward reward) {
                return new ItemStack(Items.BARRIER);
            }
        });
    }

}
