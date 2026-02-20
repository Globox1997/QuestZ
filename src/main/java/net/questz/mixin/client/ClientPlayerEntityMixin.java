package net.questz.mixin.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.network.ClientPlayerEntity;
import net.questz.access.PlayerAccess;
import net.questz.quest.QuestTab;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;

@Environment(EnvType.CLIENT)
@Mixin(ClientPlayerEntity.class)
public class ClientPlayerEntityMixin implements PlayerAccess {

    @Nullable
    private QuestTab questTab;

    @Override
    public void setQuestTab(QuestTab questTab) {
        this.questTab = questTab;
    }

    @Override
    public QuestTab getQuestTab() {
        return this.questTab;
    }
}
