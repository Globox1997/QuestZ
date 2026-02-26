package net.questz.mixin;

import net.minecraft.loot.condition.LootCondition;
import net.minecraft.predicate.entity.LootContextPredicate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

@Mixin(LootContextPredicate.class)
public interface LootContextPredicateAccessor {

    @Accessor("conditions")
    List<LootCondition> getConditions();
}
