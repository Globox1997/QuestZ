package net.questz.reward;

import com.google.gson.JsonElement;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.questz.api.QuestzRegistries;
import net.questz.book.LenientCodec;
import net.questz.book.QuestBook;

public abstract class Reward {

    public static final Codec<Reward> CODEC = QuestzRegistries.REWARD_TYPE.getCodec().dispatch("type", Reward::getType, RewardType::codec);

    public static final Codec<Reward> LENIENT_CODEC = new LenientCodec<>(CODEC, (id, typeId, raw, error) -> {
        JsonElement teamReward = raw.get("team_reward");
        boolean team = teamReward != null && teamReward.isJsonPrimitive() && teamReward.getAsJsonPrimitive().isBoolean() && teamReward.getAsBoolean();
        return new UnresolvedReward(id, team, typeId, raw, error);
    }, reward -> reward instanceof UnresolvedReward unresolved ? unresolved.getRaw() : null);

    private final String id;
    private final boolean teamReward;

    protected Reward(String id, boolean teamReward) {
        this.id = id;
        this.teamReward = teamReward;
    }

    public String getId() {
        return this.id;
    }

    public boolean isTeamReward() {
        return this.teamReward;
    }

    public abstract RewardType<?> getType();

    public Identifier getTypeId() {
        return QuestzRegistries.REWARD_TYPE.getId(this.getType());
    }

    public abstract void claim(ServerPlayerEntity player);

    protected static <T extends Reward> RecordCodecBuilder<T, String> idField() {
        return QuestBook.ID_CODEC.fieldOf("id").forGetter(Reward::getId);
    }

    protected static <T extends Reward> RecordCodecBuilder<T, Boolean> teamRewardField() {
        return Codec.BOOL.optionalFieldOf("team_reward", false).forGetter(Reward::isTeamReward);
    }

}
