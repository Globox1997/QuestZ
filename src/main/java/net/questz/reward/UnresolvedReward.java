package net.questz.reward;

import com.google.gson.JsonElement;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.questz.book.QuestBook;
import net.questz.init.RewardTypeInit;

public class UnresolvedReward extends Reward {

    public static final MapCodec<UnresolvedReward> CODEC = QuestBook.ID_CODEC.fieldOf("id").flatXmap(
            id -> DataResult.error(() -> "Unresolved rewards cannot be decoded directly"),
            reward -> DataResult.success(reward.getId()));

    private final Identifier typeId;
    private final JsonElement raw;
    private final String error;

    public UnresolvedReward(String id, boolean teamReward, Identifier typeId, JsonElement raw, String error) {
        super(id, teamReward);
        this.typeId = typeId;
        this.raw = raw;
        this.error = error;
    }

    public JsonElement getRaw() {
        return this.raw;
    }

    public String getError() {
        return this.error;
    }

    @Override
    public Identifier getTypeId() {
        return this.typeId;
    }

    @Override
    public RewardType<?> getType() {
        return RewardTypeInit.UNRESOLVED;
    }

    @Override
    public void claim(ServerPlayerEntity player) {
    }

}
