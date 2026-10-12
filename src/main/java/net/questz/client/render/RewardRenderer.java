package net.questz.client.render;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.questz.reward.Reward;

@Environment(EnvType.CLIENT)
public interface RewardRenderer<T extends Reward> {

    Text getTitle(T reward);

    ItemStack getIcon(T reward);

}
