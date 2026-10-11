package net.questz.progress;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtOps;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.PersistentState;
import net.questz.QuestzMain;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class QuestzState extends PersistentState {

    public static final PersistentState.Type<QuestzState> TYPE = new PersistentState.Type<>(QuestzState::new, QuestzState::fromNbt, null);

    private final Map<UUID, TeamData> teams = new LinkedHashMap<>();
    private final Map<UUID, UUID> activeParties = new HashMap<>();

    public static QuestzState get(MinecraftServer server) {
        return server.getOverworld().getPersistentStateManager().getOrCreate(TYPE, "questz");
    }

    private static QuestzState fromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        QuestzState state = new QuestzState();
        if (nbt.contains("teams")) {
            TeamData.CODEC.listOf().parse(NbtOps.INSTANCE, nbt.get("teams"))
                    .resultOrPartial(error -> QuestzMain.LOGGER.error("Failed to load quest teams: {}", error))
                    .ifPresent(teams -> teams.forEach(state::addTeam));
        }
        return state;
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        TeamData.CODEC.listOf().encodeStart(NbtOps.INSTANCE, List.copyOf(this.teams.values()))
                .resultOrPartial(error -> QuestzMain.LOGGER.error("Failed to save quest teams: {}", error))
                .ifPresent(teams -> nbt.put("teams", teams));
        return nbt;
    }

    void addTeam(TeamData team) {
        this.teams.put(team.getId(), team);
        if (team.isParty()) {
            team.getMembers().forEach(member -> this.activeParties.put(member, team.getId()));
        }
        this.markDirty();
    }

    void removeTeam(UUID teamId) {
        TeamData team = this.teams.remove(teamId);
        if (team != null) {
            this.activeParties.values().removeIf(teamId::equals);
            this.markDirty();
        }
    }

    void setActiveParty(UUID player, @Nullable UUID partyId) {
        if (partyId == null) {
            this.activeParties.remove(player);
        } else {
            this.activeParties.put(player, partyId);
        }
        this.markDirty();
    }

    public Collection<TeamData> getTeams() {
        return Collections.unmodifiableCollection(this.teams.values());
    }

    @Nullable
    public TeamData getTeam(UUID teamId) {
        return this.teams.get(teamId);
    }

    @Nullable
    public TeamData getPersonalTeam(UUID player) {
        return this.teams.get(player);
    }

    @Nullable
    public TeamData getActiveTeam(UUID player) {
        UUID partyId = this.activeParties.get(player);
        if (partyId != null) {
            TeamData party = this.teams.get(partyId);
            if (party != null) {
                return party;
            }
        }
        return this.teams.get(player);
    }

}
