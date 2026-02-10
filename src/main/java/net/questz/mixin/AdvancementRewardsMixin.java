package net.questz.mixin;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.advancement.AdvancementRewards;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.function.LazyContainer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.questz.access.RewardAccess;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Mixin(AdvancementRewards.class)
public abstract class AdvancementRewardsMixin implements RewardAccess {

    @Unique
    private List<String> commands = new ArrayList<>();
    @Unique
    private Map<Identifier, Integer> items = Map.of();
    @Unique
    private String text = "";

    @Override
    public void questz$setCommands(List<String> commands) {
        this.commands = commands;
    }

    @Override
    public List<String> questz$getCommands() {
        return this.commands;
    }

    @Override
    public void questz$setItems(Map<Identifier, Integer> items) {
        this.items = items;
    }

    @Override
    public Map<Identifier, Integer> questz$getItems() {
        return this.items;
    }

    @Override
    public void questz$setText(String text) {
        this.text = text;
    }

    @Override
    public String questz$getText() {
        return this.text;
    }

    @Inject(method = "apply", at = @At("TAIL"))
    private void executeCustomCommands(ServerPlayerEntity player, CallbackInfo info) {
        if (!this.commands.isEmpty()) {
            MinecraftServer server = player.getServer();
            for (String command : this.commands) {
                server.getCommandManager().executeWithPrefix(player.getCommandSource().withSilent(), command);
            }
        }
        if (!this.items.isEmpty()) {
            for (Map.Entry<Identifier, Integer> entry : this.items.entrySet()) {
                Item item = Registries.ITEM.get(entry.getKey());
                ItemStack stack = new ItemStack(item, entry.getValue());
                if (!player.giveItemStack(stack)) {
                    player.dropItem(stack, false);
                }
            }
        }
    }

    @Shadow
    @Mutable
    @Final
    public static Codec<AdvancementRewards> CODEC;

    @Inject(method = "<clinit>", at = @At("RETURN"))
    private static void injectCustomCodec(CallbackInfo info) {
        CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.INT.optionalFieldOf("experience", 0).forGetter(AdvancementRewards::experience),
                RegistryKey.createCodec(RegistryKeys.LOOT_TABLE).listOf().optionalFieldOf("loot", List.of()).forGetter(AdvancementRewards::loot),
                Identifier.CODEC.listOf().optionalFieldOf("recipes", List.of()).forGetter(AdvancementRewards::recipes),
                LazyContainer.CODEC.optionalFieldOf("function").forGetter(AdvancementRewards::function),

                Codec.STRING.listOf().optionalFieldOf("commands", List.of()).forGetter(r -> ((RewardAccess) (Object) r).questz$getCommands()),
                Codec.unboundedMap(Identifier.CODEC, Codec.INT).optionalFieldOf("items", Map.of()).forGetter(r -> ((RewardAccess) (Object) r).questz$getItems()),
                Codec.STRING.optionalFieldOf("text", "").forGetter(r -> ((RewardAccess) (Object) r).questz$getText())

        ).apply(instance, (exp, loot, recipes, function, commands, items, text) -> {
            AdvancementRewards rewards = new AdvancementRewards(exp, loot, recipes, function);
            RewardAccess duck = (RewardAccess) (Object) rewards;
            duck.questz$setCommands(commands);
            duck.questz$setItems(items);
            duck.questz$setText(text);
            return rewards;
        }));
    }

}
