package net.questz.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.questz.network.packet.QuestStatePacket;
import net.questz.network.packet.TeamSyncPacket;
import net.questz.progress.ProgressView;
import net.questz.progress.TeamData;

import java.util.*;

@Environment(EnvType.CLIENT)
public class ClientTeam implements ProgressView {

    private final UUID id;
    private final String name;
    private final UUID owner;
    private final boolean party;
    private final Map<UUID, String> members;
    private final Map<String, Long> progress;
    private final Map<String, Long> completed;
    private final Set<String> claimed;

    public ClientTeam(TeamSyncPacket packet) {
        this.id = packet.teamId();
        this.name = packet.name();
        this.owner = packet.owner();
        this.party = packet.party();
        this.members = new LinkedHashMap<>(packet.members());
        this.progress = new HashMap<>(packet.progress());
        this.completed = new HashMap<>(packet.completed());
        this.claimed = new HashSet<>(packet.claimed());
    }

    public UUID getId() {
        return this.id;
    }

    public String getName() {
        return this.name;
    }

    public UUID getOwner() {
        return this.owner;
    }

    public boolean isParty() {
        return this.party;
    }

    public Map<UUID, String> getMembers() {
        return Collections.unmodifiableMap(this.members);
    }

    @Override
    public boolean isCompleted(String questId) {
        return this.completed.containsKey(questId);
    }

    public long getCompletedAt(String questId) {
        return this.completed.getOrDefault(questId, 0L);
    }

    @Override
    public long getProgress(String questId, String taskId) {
        return this.progress.getOrDefault(TeamData.key(questId, taskId), 0L);
    }

    public boolean isRewardClaimed(String questId, String rewardId) {
        return this.claimed.contains(TeamData.key(questId, rewardId));
    }

    void setProgress(String questId, String taskId, long value) {
        if (value <= 0L) {
            this.progress.remove(TeamData.key(questId, taskId));
        } else {
            this.progress.put(TeamData.key(questId, taskId), value);
        }
    }

    void applyQuestState(QuestStatePacket packet) {
        String prefix = packet.questId() + "/";
        this.progress.keySet().removeIf(key -> key.startsWith(prefix));
        this.claimed.removeIf(key -> key.startsWith(prefix));
        packet.progress().forEach((taskId, value) -> this.progress.put(TeamData.key(packet.questId(), taskId), value));
        packet.claimedRewards().forEach(rewardId -> this.claimed.add(TeamData.key(packet.questId(), rewardId)));
        if (packet.completedAt() > 0L) {
            this.completed.put(packet.questId(), packet.completedAt());
        } else {
            this.completed.remove(packet.questId());
        }
    }

}
