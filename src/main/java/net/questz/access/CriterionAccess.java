package net.questz.access;

import net.minecraft.advancement.criterion.CriterionConditions;
import net.minecraft.util.Identifier;

public interface CriterionAccess {

    Identifier questz$getTriggerId();

    CriterionConditions questz$getConditions();
}
