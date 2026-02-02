package net.questz.criteria;

import com.google.gson.JsonObject;

import org.jetbrains.annotations.Nullable;

import net.minecraft.advancement.criterion.AbstractCriterion;
import net.minecraft.advancement.criterion.AbstractCriterionConditions;
import net.minecraft.block.Block;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.Item;
import net.minecraft.predicate.entity.AdvancementEntityPredicateDeserializer;
import net.minecraft.predicate.entity.AdvancementEntityPredicateSerializer;
import net.minecraft.predicate.entity.LootContextPredicate;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.stat.Stats;
import net.minecraft.util.Identifier;
import net.questz.criteria.predicate.CountPredicate;
import net.questz.criteria.predicate.ObjectPredicate;

public class QuestCriterion extends AbstractCriterion<QuestCriterion.Conditions> {
    private final Identifier id;

    public QuestCriterion(Identifier id) {
        this.id = id;
    }

    @Override
    public Identifier getId() {
        return this.id;
    }

    @Override
    public Conditions conditionsFromJson(JsonObject jsonObject, LootContextPredicate lootContextPredicate, AdvancementEntityPredicateDeserializer advancementEntityPredicateDeserializer) {
        ObjectPredicate objectPredicate = ObjectPredicate.fromJson(jsonObject.get("object"));
        CountPredicate countPredicate = CountPredicate.fromJson(jsonObject.get("count"));
        return new Conditions(this.id, lootContextPredicate, objectPredicate, countPredicate);
    }

    public void trigger(ServerPlayerEntity player, @Nullable Item item, @Nullable Block block, @Nullable LivingEntity livingEntity, int code) {
        this.trigger(player, conditions -> conditions.matches(player, item, block, livingEntity, code));
    }

    public static class Conditions extends AbstractCriterionConditions {
        private final ObjectPredicate objectPredicate;
        private final CountPredicate countPredicate;

        public Conditions(Identifier id, LootContextPredicate entity, ObjectPredicate objectPredicate, CountPredicate countPredicate) {
            super(id, entity);
            this.objectPredicate = objectPredicate;
            this.countPredicate = countPredicate;
        }

        public boolean matches(ServerPlayerEntity player, @Nullable Item item, @Nullable Block block, @Nullable LivingEntity livingEntity, int code) {
            if (item != null) {
                if (!this.objectPredicate.test(item, code)) {
                    return false;
                }
                if (code == 0) {
                    if (this.countPredicate.test(player.getStatHandler().getStat(Stats.USED.getOrCreateStat(item)))) {
                        return true;
                    }
                } else if (code == 3) {
                    if (this.countPredicate.test(player.getStatHandler().getStat(Stats.CRAFTED.getOrCreateStat(item)))) {
                        return true;
                    }
                }
            } else if (block != null) {
                if (!this.objectPredicate.test(block, code)) {
                    return false;
                }
                if (this.countPredicate.test(player.getStatHandler().getStat(Stats.MINED.getOrCreateStat(block)))) {
                    return true;
                }
            } else if (livingEntity != null) {
                if (!this.objectPredicate.test(livingEntity, code)) {
                    return false;
                }
                if (this.countPredicate.test(player.getStatHandler().getStat(Stats.KILLED.getOrCreateStat(livingEntity.getType())))) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public JsonObject toJson(AdvancementEntityPredicateSerializer predicateSerializer) {
            JsonObject jsonObject = super.toJson(predicateSerializer);
            jsonObject.add("object", this.objectPredicate.toJson());
            jsonObject.add("count", this.countPredicate.toJson());
            return jsonObject;
        }
    }
}
