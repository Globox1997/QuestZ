package net.questz.progress;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.questz.api.QuestzEvents;

import java.util.*;

public class TeamManager {

    public static final int MAX_NAME_LENGTH = 32;

    private static final Map<UUID, Set<UUID>> INVITES = new HashMap<>();

    public static TeamData getTeam(ServerPlayerEntity player) {
        QuestzState state = QuestzState.get(player.getServer());
        TeamData team = state.getActiveTeam(player.getUuid());
        return team != null ? team : ensurePersonalTeam(state, player);
    }

    public static void onJoin(ServerPlayerEntity player) {
        QuestzState state = QuestzState.get(player.getServer());
        TeamData personal = ensurePersonalTeam(state, player);
        String name = player.getGameProfile().getName();
        if (!personal.getName().equals(name)) {
            personal.setName(name);
            state.markDirty();
        }
    }

    private static TeamData ensurePersonalTeam(QuestzState state, ServerPlayerEntity player) {
        TeamData personal = state.getPersonalTeam(player.getUuid());
        if (personal == null) {
            personal = TeamData.createPersonal(player.getUuid(), player.getGameProfile().getName());
            state.addTeam(personal);
        }
        return personal;
    }

    public static TeamResult createParty(ServerPlayerEntity player, String name) {
        String trimmed = name.trim();
        if (trimmed.isEmpty() || trimmed.length() > MAX_NAME_LENGTH) {
            return TeamResult.INVALID_NAME;
        }
        TeamData previous = getTeam(player);
        if (previous.isParty()) {
            return TeamResult.ALREADY_IN_PARTY;
        }
        QuestzState state = QuestzState.get(player.getServer());
        UUID partyId;
        do {
            partyId = UUID.randomUUID();
        } while (state.getTeam(partyId) != null);

        TeamData party = TeamData.createParty(partyId, trimmed, player.getUuid());
        state.addTeam(party);
        changed(player, previous, party);
        return TeamResult.SUCCESS;
    }

    public static TeamResult invite(ServerPlayerEntity owner, ServerPlayerEntity target) {
        if (owner == target) {
            return TeamResult.SELF;
        }
        TeamData party = getTeam(owner);
        if (!party.isParty()) {
            return TeamResult.NOT_IN_PARTY;
        }
        if (!party.getOwner().equals(owner.getUuid())) {
            return TeamResult.NOT_OWNER;
        }
        if (party.getMembers().contains(target.getUuid())) {
            return TeamResult.ALREADY_MEMBER;
        }
        INVITES.computeIfAbsent(target.getUuid(), uuid -> new LinkedHashSet<>()).add(party.getId());
        QuestzEvents.TEAM_INVITED.invoker().onTeamInvited(owner.getServer(), target, party);
        return TeamResult.SUCCESS;
    }

    public static Set<UUID> getInvites(ServerPlayerEntity player) {
        return Collections.unmodifiableSet(INVITES.getOrDefault(player.getUuid(), Set.of()));
    }

    public static TeamResult accept(ServerPlayerEntity player, UUID partyId) {
        Set<UUID> invites = INVITES.get(player.getUuid());
        QuestzState state = QuestzState.get(player.getServer());
        TeamData party = state.getTeam(partyId);
        if (invites == null || !invites.contains(partyId) || party == null || !party.isParty()) {
            return TeamResult.NO_INVITE;
        }
        TeamData previous = getTeam(player);
        if (previous.isParty()) {
            return TeamResult.ALREADY_IN_PARTY;
        }
        invites.remove(partyId);
        party.addMember(player.getUuid());
        state.setActiveParty(player.getUuid(), partyId);
        changed(player, previous, party);
        QuestzEvents.TEAM_UPDATED.invoker().onTeamUpdated(player.getServer(), party);
        return TeamResult.SUCCESS;
    }

    public static TeamResult leave(ServerPlayerEntity player) {
        TeamData party = getTeam(player);
        if (!party.isParty()) {
            return TeamResult.NOT_IN_PARTY;
        }
        removeFromParty(player.getServer(), party, player.getUuid());
        return TeamResult.SUCCESS;
    }

    public static TeamResult kick(ServerPlayerEntity owner, UUID target) {
        if (owner.getUuid().equals(target)) {
            return TeamResult.SELF;
        }
        TeamData party = getTeam(owner);
        if (!party.isParty()) {
            return TeamResult.NOT_IN_PARTY;
        }
        if (!party.getOwner().equals(owner.getUuid())) {
            return TeamResult.NOT_OWNER;
        }
        if (!party.getMembers().contains(target)) {
            return TeamResult.NOT_MEMBER;
        }
        removeFromParty(owner.getServer(), party, target);
        return TeamResult.SUCCESS;
    }

    public static TeamResult rename(ServerPlayerEntity owner, String name) {
        String trimmed = name.trim();
        if (trimmed.isEmpty() || trimmed.length() > MAX_NAME_LENGTH) {
            return TeamResult.INVALID_NAME;
        }
        TeamData party = getTeam(owner);
        if (!party.isParty()) {
            return TeamResult.NOT_IN_PARTY;
        }
        if (!party.getOwner().equals(owner.getUuid())) {
            return TeamResult.NOT_OWNER;
        }
        party.setName(trimmed);
        QuestzState.get(owner.getServer()).markDirty();
        QuestzEvents.TEAM_UPDATED.invoker().onTeamUpdated(owner.getServer(), party);
        return TeamResult.SUCCESS;
    }

    public static TeamResult disband(ServerPlayerEntity owner) {
        TeamData party = getTeam(owner);
        if (!party.isParty()) {
            return TeamResult.NOT_IN_PARTY;
        }
        if (!party.getOwner().equals(owner.getUuid())) {
            return TeamResult.NOT_OWNER;
        }
        for (UUID member : List.copyOf(party.getMembers())) {
            removeFromParty(owner.getServer(), party, member);
        }
        return TeamResult.SUCCESS;
    }

    private static void removeFromParty(MinecraftServer server, TeamData party, UUID member) {
        QuestzState state = QuestzState.get(server);
        party.removeMember(member);
        state.setActiveParty(member, null);
        if (party.getMembers().isEmpty()) {
            state.removeTeam(party.getId());
            INVITES.values().forEach(invites -> invites.remove(party.getId()));
        } else if (party.getOwner().equals(member)) {
            party.setOwner(party.getMembers().iterator().next());
        }

        ServerPlayerEntity player = server.getPlayerManager().getPlayer(member);
        if (player != null) {
            changed(player, party, getTeam(player));
        }
        QuestzEvents.TEAM_UPDATED.invoker().onTeamUpdated(server, party);
    }

    private static void changed(ServerPlayerEntity player, TeamData previous, TeamData current) {
        QuestzEvents.TEAM_CHANGED.invoker().onTeamChanged(player.getServer(), player, previous, current);
        QuestEngine.checkTeam(player.getServer(), current);
        QuestEngine.claimAutoRewards(player, current);
    }

    public static void clearInvites() {
        INVITES.clear();
    }

}
