package net.questz.reward;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.questz.init.RewardTypeInit;

public class CommandReward extends Reward {

    public static final MapCodec<CommandReward> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Reward.<CommandReward>idField(),
            Reward.<CommandReward>teamRewardField(),
            Codec.STRING.fieldOf("command").forGetter(CommandReward::getCommand)
    ).apply(instance, CommandReward::new));

    private final String command;

    public CommandReward(String id, boolean teamReward, String command) {
        super(id, teamReward);
        this.command = command;
    }

    public String getCommand() {
        return this.command;
    }

    @Override
    public RewardType<?> getType() {
        return RewardTypeInit.COMMAND;
    }

    @Override
    public void claim(ServerPlayerEntity player) {
        MinecraftServer server = player.getServer();
        if (server != null) {
            server.getCommandManager().executeWithPrefix(player.getCommandSource().withLevel(2).withSilent(), this.command);
        }
    }

}
