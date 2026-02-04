package net.questz.init;

import net.minecraft.advancement.criterion.Criterion;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import net.questz.QuestzMain;
import net.questz.criteria.QuestCriterion;

public class CriteriaInit {

    public static final QuestCriterion PLACED_BLOCK_COUNT = register(QuestzMain.identifierOf("placed_block_count"), new QuestCriterion());
    public static final QuestCriterion KILLED_MOB_COUNT = register(QuestzMain.identifierOf("killed_mob_count"), new QuestCriterion());
    public static final QuestCriterion MINED_BLOCK_COUNT = register(QuestzMain.identifierOf("mined_block_count"), new QuestCriterion());
    public static final QuestCriterion CRAFT_ITEM_COUNT = register(QuestzMain.identifierOf("craft_item_count"), new QuestCriterion());

    private static <T extends Criterion<?>> T register(Identifier id, T criterion) {
        return Registry.register(Registries.CRITERION, id, criterion);
    }

    public static void init() {
    }

}
