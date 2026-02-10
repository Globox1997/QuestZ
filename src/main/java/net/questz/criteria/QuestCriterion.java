package net.questz.criteria;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.advancement.criterion.AbstractCriterion;
import net.minecraft.block.Block;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.Item;
import net.minecraft.predicate.entity.EntityPredicate;
import net.minecraft.predicate.entity.LootContextPredicate;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.stat.Stats;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

public class QuestCriterion extends AbstractCriterion<QuestCriterion.Conditions> {

    @Override
    public Codec<Conditions> getConditionsCodec() {
        return Conditions.CODEC;
    }

    public void trigger(ServerPlayerEntity player, @Nullable Item item, @Nullable Block block, @Nullable LivingEntity livingEntity, int code) {
        this.trigger(player, conditions -> conditions.matches(player, item, block, livingEntity, code));
    }

    public record Conditions(Optional<LootContextPredicate> player, ObjectPredicate objectPredicate, CountPredicate countPredicate) implements AbstractCriterion.Conditions {

        public static final Codec<Conditions> CODEC = RecordCodecBuilder.create(instance ->
                instance.group(
                        EntityPredicate.LOOT_CONTEXT_PREDICATE_CODEC.optionalFieldOf("player").forGetter(Conditions::player),
                        Identifier.CODEC.fieldOf("object").forGetter(c -> c.objectPredicate().objectId()),
                        Codec.INT.fieldOf("count").forGetter(c -> c.countPredicate().count())
                ).apply(instance, (player, objectId, count) ->
                        new Conditions(player, new ObjectPredicate(objectId), new CountPredicate(count))
                ));

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
    }

    public record ObjectPredicate(Identifier objectId) {
        public static final Codec<ObjectPredicate> CODEC = RecordCodecBuilder.create(instance ->
                instance.group(
                        Identifier.CODEC.fieldOf("object").forGetter(ObjectPredicate::objectId)
                ).apply(instance, ObjectPredicate::new)
        );

        public boolean test(Object object, int code) {
            return switch (code) {
                case 0 -> Registries.ITEM.getId((Item) object).equals(this.objectId);
                case 1 -> Registries.BLOCK.getId((Block) object).equals(this.objectId);
                case 2 -> Registries.ENTITY_TYPE.getId(((LivingEntity) object).getType()).equals(this.objectId);
                default -> false;
            };
        }
    }

    public record CountPredicate(int count) {
        public static final Codec<CountPredicate> CODEC = RecordCodecBuilder.create(instance ->
                instance.group(
                        Codec.INT.fieldOf("count").forGetter(CountPredicate::count)
                ).apply(instance, CountPredicate::new)
        );

        public boolean test(int inputCount) {
            return this.count == inputCount;
        }
    }
}
