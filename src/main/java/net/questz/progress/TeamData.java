package net.questz.progress;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Uuids;

import java.util.*;

public class TeamData implements ProgressView {

    public static final Codec<TeamData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Uuids.INT_STREAM_CODEC.fieldOf("id").forGetter(TeamData::getId),
            Codec.STRING.fieldOf("name").forGetter(TeamData::getName),
            Uuids.INT_STREAM_CODEC.fieldOf("owner").forGetter(TeamData::getOwner),
            Codec.BOOL.fieldOf("party").forGetter(TeamData::isParty),
            Uuids.LINKED_SET_CODEC.optionalFieldOf("members", Set.of()).forGetter(TeamData::getMembers),
            Codec.unboundedMap(Codec.STRING, Codec.LONG).optionalFieldOf("progress", Map.of()).forGetter(team -> team.progress),
            Codec.unboundedMap(Codec.STRING, Codec.LONG).optionalFieldOf("completed", Map.of()).forGetter(team -> team.completed),
            Codec.unboundedMap(Codec.STRING, Uuids.LINKED_SET_CODEC).optionalFieldOf("claimed", Map.of()).forGetter(team -> team.claimed),
            Codec.STRING.listOf().optionalFieldOf("team_claimed", List.of()).forGetter(team -> List.copyOf(team.teamClaimed)),
            Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("completions", Map.of()).forGetter(team -> team.completions)
    ).apply(instance, TeamData::new));

    private final UUID id;
    private String name;
    private UUID owner;
    private final boolean party;
    private final Set<UUID> members;
    private final Map<String, Long> progress;
    private final Map<String, Long> completed;
    private final Map<String, Set<UUID>> claimed;
    private final Set<String> teamClaimed;
    private final Map<String, Integer> completions;

    private TeamData(UUID id, String name, UUID owner, boolean party, Set<UUID> members, Map<String, Long> progress, Map<String, Long> completed, Map<String, Set<UUID>> claimed,
                     List<String> teamClaimed, Map<String, Integer> completions) {
        this.id = id;
        this.name = name;
        this.owner = owner;
        this.party = party;
        this.members = new LinkedHashSet<>(members);
        this.progress = new HashMap<>(progress);
        this.completed = new HashMap<>(completed);
        this.claimed = new HashMap<>();
        claimed.forEach((key, players) -> this.claimed.put(key, new LinkedHashSet<>(players)));
        this.teamClaimed = new HashSet<>(teamClaimed);
        this.completions = new HashMap<>(completions);
    }

    public static TeamData createPersonal(UUID player, String name) {
        return new TeamData(player, name, player, false, Set.of(player), Map.of(), Map.of(), Map.of(), List.of(), Map.of());
    }

    public static TeamData createParty(UUID id, String name, UUID owner) {
        return new TeamData(id, name, owner, true, Set.of(owner), Map.of(), Map.of(), Map.of(), List.of(), Map.of());
    }

    public static String key(String questId, String entryId) {
        return questId + "/" + entryId;
    }

    public UUID getId() {
        return this.id;
    }

    public String getName() {
        return this.name;
    }

    void setName(String name) {
        this.name = name;
    }

    public UUID getOwner() {
        return this.owner;
    }

    void setOwner(UUID owner) {
        this.owner = owner;
    }

    public boolean isParty() {
        return this.party;
    }

    public Set<UUID> getMembers() {
        return Collections.unmodifiableSet(this.members);
    }

    void addMember(UUID player) {
        this.members.add(player);
    }

    void removeMember(UUID player) {
        this.members.remove(player);
    }

    public List<ServerPlayerEntity> getOnlineMembers(MinecraftServer server) {
        List<ServerPlayerEntity> online = new ArrayList<>();
        for (UUID member : this.members) {
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(member);
            if (player != null) {
                online.add(player);
            }
        }
        return online;
    }

    public Map<String, Long> getProgressMap() {
        return Collections.unmodifiableMap(this.progress);
    }

    public Map<String, Long> getCompletedMap() {
        return Collections.unmodifiableMap(this.completed);
    }

    public Set<String> getClaimedRewardKeys(UUID player) {
        Set<String> keys = new HashSet<>(this.teamClaimed);
        this.claimed.forEach((key, players) -> {
            if (players.contains(player)) {
                keys.add(key);
            }
        });
        return keys;
    }

    @Override
    public long getProgress(String questId, String taskId) {
        return this.progress.getOrDefault(key(questId, taskId), 0L);
    }

    void setProgress(String questId, String taskId, long value) {
        if (value <= 0L) {
            this.progress.remove(key(questId, taskId));
        } else {
            this.progress.put(key(questId, taskId), value);
        }
    }

    @Override
    public boolean isCompleted(String questId) {
        return this.completed.containsKey(questId);
    }

    public long getCompletedAt(String questId) {
        return this.completed.getOrDefault(questId, 0L);
    }

    void setCompleted(String questId, long time) {
        this.completed.put(questId, time);
        this.completions.merge(questId, 1, Integer::sum);
    }

    @Override
    public int getCompletions(String questId) {
        return this.completions.getOrDefault(questId, 0);
    }

    public Map<String, Integer> getCompletionsMap() {
        return Collections.unmodifiableMap(this.completions);
    }

    public boolean isRewardClaimed(String questId, String rewardId, UUID player, boolean teamReward) {
        String key = key(questId, rewardId);
        if (teamReward) {
            return this.teamClaimed.contains(key);
        }
        Set<UUID> players = this.claimed.get(key);
        return players != null && players.contains(player);
    }

    void setRewardClaimed(String questId, String rewardId, UUID player, boolean teamReward) {
        String key = key(questId, rewardId);
        if (teamReward) {
            this.teamClaimed.add(key);
        } else {
            this.claimed.computeIfAbsent(key, k -> new LinkedHashSet<>()).add(player);
        }
    }

    void resetQuest(String questId, boolean keepCompletions) {
        String prefix = questId + "/";
        this.progress.keySet().removeIf(key -> key.startsWith(prefix));
        this.claimed.keySet().removeIf(key -> key.startsWith(prefix));
        this.teamClaimed.removeIf(key -> key.startsWith(prefix));
        this.completed.remove(questId);
        if (!keepCompletions) {
            this.completions.remove(questId);
        }
    }

}
