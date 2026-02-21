package net.questz.access;

import net.minecraft.advancement.AdvancementCriterion;
import net.minecraft.advancement.AdvancementRewards;

import java.util.Map;

public interface AdvancementAccess {

    void questz$setCriteria(Map<String, AdvancementCriterion<?>> criteria);

    void questz$setRewards(AdvancementRewards rewards);
}
