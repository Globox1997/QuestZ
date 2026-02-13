package net.questz.mixin;

import net.minecraft.advancement.AdvancementCriterion;
import net.minecraft.advancement.criterion.Criteria;
import net.minecraft.advancement.criterion.Criterion;
import net.minecraft.advancement.criterion.CriterionConditions;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import net.questz.access.CriterionAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(AdvancementCriterion.class)
public abstract class AdvancementCriterionMixin<T extends CriterionConditions> implements CriterionAccess {

    @Shadow
    public abstract Criterion<T> trigger();

    @Shadow
    public abstract T conditions();

    @Override
    public Identifier questz$getTriggerId() {
        Criterion<?> trigger = this.trigger();

//        Registry.register(Registries.CRITERION, id, criterion);
//        Criteria.
        return  Registries.CRITERION.getId(trigger);
    }

    @Override
    public CriterionConditions questz$getConditions() {
        return this.conditions();
    }
}