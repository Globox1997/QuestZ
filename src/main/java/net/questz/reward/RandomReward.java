package net.questz.reward;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.questz.init.RewardTypeInit;

import java.util.List;

public class RandomReward extends Reward {

    public record Entry(ItemStack item, int weight) {

        public static final Codec<Entry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                ItemStack.CODEC.fieldOf("item").forGetter(Entry::item),
                Codec.intRange(1, 1000000).optionalFieldOf("weight", 1).forGetter(Entry::weight)
        ).apply(instance, Entry::new));
    }

    public static final MapCodec<RandomReward> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Reward.<RandomReward>idField(),
            Reward.<RandomReward>teamRewardField(),
            Entry.CODEC.listOf().fieldOf("entries").forGetter(RandomReward::getEntries),
            Codec.intRange(1, 64).optionalFieldOf("rolls", 1).forGetter(RandomReward::getRolls)
    ).apply(instance, RandomReward::new));

    private final List<Entry> entries;
    private final int rolls;

    public RandomReward(String id, boolean teamReward, List<Entry> entries, int rolls) {
        super(id, teamReward);
        this.entries = List.copyOf(entries);
        this.rolls = rolls;
    }

    public List<Entry> getEntries() {
        return this.entries;
    }

    public int getRolls() {
        return this.rolls;
    }

    @Override
    public RewardType<?> getType() {
        return RewardTypeInit.RANDOM;
    }

    @Override
    public void claim(ServerPlayerEntity player) {
        int totalWeight = this.entries.stream().mapToInt(Entry::weight).sum();
        if (totalWeight <= 0) {
            return;
        }
        for (int roll = 0; roll < this.rolls; roll++) {
            int pick = player.getRandom().nextInt(totalWeight);
            for (Entry entry : this.entries) {
                pick -= entry.weight();
                if (pick < 0) {
                    player.getInventory().offerOrDrop(entry.item().copy());
                    break;
                }
            }
        }
    }

}
