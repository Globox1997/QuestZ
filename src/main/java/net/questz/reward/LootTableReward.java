package net.questz.reward;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.loot.LootTable;
import net.minecraft.loot.context.LootContextParameterSet;
import net.minecraft.loot.context.LootContextParameters;
import net.minecraft.loot.context.LootContextTypes;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.questz.init.RewardTypeInit;

public class LootTableReward extends Reward {

    public static final MapCodec<LootTableReward> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Reward.<LootTableReward>idField(),
            Reward.<LootTableReward>teamRewardField(),
            RegistryKey.createCodec(RegistryKeys.LOOT_TABLE).fieldOf("loot_table").forGetter(LootTableReward::getLootTable)
    ).apply(instance, LootTableReward::new));

    private final RegistryKey<LootTable> lootTable;

    public LootTableReward(String id, boolean teamReward, RegistryKey<LootTable> lootTable) {
        super(id, teamReward);
        this.lootTable = lootTable;
    }

    public RegistryKey<LootTable> getLootTable() {
        return this.lootTable;
    }

    @Override
    public RewardType<?> getType() {
        return RewardTypeInit.LOOT_TABLE;
    }

    @Override
    public void claim(ServerPlayerEntity player) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        LootTable table = server.getReloadableRegistries().getLootTable(this.lootTable);
        LootContextParameterSet parameters = new LootContextParameterSet.Builder(player.getServerWorld())
                .add(LootContextParameters.THIS_ENTITY, player)
                .add(LootContextParameters.ORIGIN, player.getPos())
                .luck(player.getLuck())
                .build(LootContextTypes.GIFT);
        table.generateLoot(parameters).forEach(stack -> player.getInventory().offerOrDrop(stack));
    }

}
