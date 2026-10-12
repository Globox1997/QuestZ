package net.questz.client.render;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.questz.task.Task;

@Environment(EnvType.CLIENT)
public interface TaskRenderer<T extends Task> {

    Text getTitle(T task);

    ItemStack getIcon(T task);

}
