package net.questz.init;

import net.minecraft.advancement.criterion.Criteria;
import net.minecraft.util.Identifier;
import net.questz.criteria.QuestCriterion;

public class CriteriaInit {

    public static final QuestCriterion PLACED_BLOCK_COUNT = Criteria.register(new QuestCriterion(new Identifier("questz", "placed_block_count")));
    public static final QuestCriterion KILLED_MOB_COUNT = Criteria.register(new QuestCriterion(new Identifier("questz", "killed_mob_count")));
    public static final QuestCriterion MINED_BLOCK_COUNT = Criteria.register(new QuestCriterion(new Identifier("questz", "mined_block_count")));
    public static final QuestCriterion CRAFT_ITEM_COUNT = Criteria.register(new QuestCriterion(new Identifier("questz", "craft_item_count")));

    public static void init() {
    }

}
