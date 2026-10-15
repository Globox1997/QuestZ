package net.questz.book;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import net.minecraft.component.ComponentType;
import net.minecraft.item.ItemStack;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public class QuestCodecs {

    public static final Codec<List<ItemStack>> ITEM_STACKS = singleOrList(ItemStack.UNCOUNTED_CODEC);

    public static <T> Codec<List<T>> singleOrList(Codec<T> codec) {
        return Codec.either(codec, codec.listOf()).xmap(either -> either.map(List::of, List::copyOf),
                values -> values.size() == 1 ? Either.left(values.getFirst()) : Either.right(values));
    }

    public static boolean matchesStack(ItemStack pattern, ItemStack stack) {
        if (stack.isEmpty() || !stack.isOf(pattern.getItem())) {
            return false;
        }
        for (Map.Entry<ComponentType<?>, Optional<?>> entry : pattern.getComponentChanges().entrySet()) {
            Object actual = stack.get(entry.getKey());
            if (entry.getValue().isPresent() ? !entry.getValue().get().equals(actual) : actual != null) {
                return false;
            }
        }
        return true;
    }

}
