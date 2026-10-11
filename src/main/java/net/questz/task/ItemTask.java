package net.questz.task;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.questz.init.TaskTypeInit;

public class ItemTask extends Task implements PollingTask {

    public static final MapCodec<ItemTask> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Task.<ItemTask>idField(),
            ItemStack.UNCOUNTED_CODEC.fieldOf("item").forGetter(ItemTask::getItem),
            Codec.LONG.optionalFieldOf("count", 1L).forGetter(ItemTask::getTarget),
            Codec.BOOL.optionalFieldOf("consume", false).forGetter(ItemTask::isConsume)
    ).apply(instance, ItemTask::new));

    private final ItemStack item;
    private final long count;
    private final boolean consume;

    public ItemTask(String id, ItemStack item, long count, boolean consume) {
        super(id);
        this.item = item;
        this.count = Math.max(1L, count);
        this.consume = consume;
    }

    public ItemStack getItem() {
        return this.item;
    }

    public boolean isConsume() {
        return this.consume;
    }

    public boolean matches(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        if (this.item.getComponentChanges().isEmpty()) {
            return stack.isOf(this.item.getItem());
        }
        return ItemStack.areItemsAndComponentsEqual(stack, this.item);
    }

    @Override
    public long poll(ServerPlayerEntity player) {
        if (this.consume) {
            return 0L;
        }
        PlayerInventory inventory = player.getInventory();
        long found = 0L;
        for (int slot = 0; slot < inventory.size(); slot++) {
            ItemStack stack = inventory.getStack(slot);
            if (this.matches(stack)) {
                found += stack.getCount();
            }
        }
        return found;
    }

    @Override
    public long getTarget() {
        return this.count;
    }

    @Override
    public TaskType<?> getType() {
        return TaskTypeInit.ITEM;
    }

}
