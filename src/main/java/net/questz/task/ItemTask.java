package net.questz.task;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.questz.book.QuestCodecs;
import net.questz.init.TaskTypeInit;

import java.util.List;

public class ItemTask extends Task implements PollingTask {

    public static final MapCodec<ItemTask> CODEC = RecordCodecBuilder.<ItemTask>mapCodec(instance -> instance.group(
            Task.<ItemTask>idField(),
            QuestCodecs.ITEM_STACKS.optionalFieldOf("item", List.of()).forGetter(ItemTask::getItems),
            QuestCodecs.singleOrList(TagKey.codec(RegistryKeys.ITEM)).optionalFieldOf("tag", List.of()).forGetter(ItemTask::getTags),
            Codec.LONG.optionalFieldOf("count", 1L).forGetter(ItemTask::getTarget),
            Codec.BOOL.optionalFieldOf("consume", false).forGetter(ItemTask::isConsume)
    ).apply(instance, ItemTask::new)).validate(task -> task.items.isEmpty() && task.tags.isEmpty()
            ? DataResult.error(() -> "An item task needs at least one item or tag") : DataResult.success(task));

    private final List<ItemStack> items;
    private final List<TagKey<Item>> tags;
    private final long count;
    private final boolean consume;

    public ItemTask(String id, List<ItemStack> items, List<TagKey<Item>> tags, long count, boolean consume) {
        super(id);
        this.items = List.copyOf(items);
        this.tags = List.copyOf(tags);
        this.count = Math.max(1L, count);
        this.consume = consume;
    }

    public List<ItemStack> getItems() {
        return this.items;
    }

    public List<TagKey<Item>> getTags() {
        return this.tags;
    }

    public boolean isConsume() {
        return this.consume;
    }

    public boolean matches(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        for (ItemStack item : this.items) {
            if (QuestCodecs.matchesStack(item, stack)) {
                return true;
            }
        }
        for (TagKey<Item> tag : this.tags) {
            if (stack.isIn(tag)) {
                return true;
            }
        }
        return false;
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
