package net.questz.command;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.command.CommandSource;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.command.argument.GameProfileArgumentType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.UserCache;
import net.questz.book.Chapter;
import net.questz.book.Quest;
import net.questz.book.QuestBook;
import net.questz.book.QuestBookManager;
import net.questz.progress.*;
import net.questz.task.Task;
import net.questz.tracking.TaskTracker;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public class QuestzCommand {

    private static final DynamicCommandExceptionType UNKNOWN_QUEST = new DynamicCommandExceptionType(id -> Text.translatable("command.questz.unknown_quest", id));

    private static final SuggestionProvider<ServerCommandSource> QUEST_SUGGESTIONS = (context, builder) ->
            CommandSource.suggestMatching(QuestBookManager.getBook().getQuests().stream().map(Quest::id), builder);

    private static final SuggestionProvider<ServerCommandSource> INVITE_SUGGESTIONS = (context, builder) -> {
        ServerPlayerEntity player = context.getSource().getPlayer();
        if (player == null) {
            return builder.buildFuture();
        }
        QuestzState state = QuestzState.get(context.getSource().getServer());
        return CommandSource.suggestMatching(TeamManager.getInvites(player).stream().map(state::getTeam).filter(Objects::nonNull).map(TeamData::getName), builder);
    };

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(literal("questz")
                .then(literal("reload").requires(source -> source.hasPermissionLevel(2)).executes(QuestzCommand::reload))
                .then(literal("quest").requires(source -> source.hasPermissionLevel(2))
                        .then(literal("list")
                                .executes(context -> listQuests(context, context.getSource().getPlayerOrThrow()))
                                .then(argument("player", EntityArgumentType.player())
                                        .executes(context -> listQuests(context, EntityArgumentType.getPlayer(context, "player")))))
                        .then(literal("progress").then(argument("quest", StringArgumentType.word()).suggests(QUEST_SUGGESTIONS)
                                .executes(context -> progress(context, context.getSource().getPlayerOrThrow()))
                                .then(argument("player", EntityArgumentType.player())
                                        .executes(context -> progress(context, EntityArgumentType.getPlayer(context, "player"))))))
                        .then(literal("complete").then(argument("quest", StringArgumentType.word()).suggests(QUEST_SUGGESTIONS)
                                .executes(context -> complete(context, context.getSource().getPlayerOrThrow()))
                                .then(argument("player", EntityArgumentType.player())
                                        .executes(context -> complete(context, EntityArgumentType.getPlayer(context, "player"))))))
                        .then(literal("reset").then(argument("quest", StringArgumentType.word()).suggests(QUEST_SUGGESTIONS)
                                .executes(context -> reset(context, context.getSource().getPlayerOrThrow()))
                                .then(argument("player", EntityArgumentType.player())
                                        .executes(context -> reset(context, EntityArgumentType.getPlayer(context, "player"))))))
                        .then(literal("completeall")
                                .executes(context -> completeAll(context, List.of(context.getSource().getPlayerOrThrow())))
                                .then(argument("players", EntityArgumentType.players())
                                        .executes(context -> completeAll(context, EntityArgumentType.getPlayers(context, "players")))))
                        .then(literal("resetall")
                                .executes(context -> resetAll(context, List.of(context.getSource().getPlayerOrThrow())))
                                .then(argument("players", EntityArgumentType.players())
                                        .executes(context -> resetAll(context, EntityArgumentType.getPlayers(context, "players"))))))
                .then(literal("claim").then(argument("quest", StringArgumentType.word()).suggests(QUEST_SUGGESTIONS).executes(QuestzCommand::claim)))
                .then(literal("submit").then(argument("quest", StringArgumentType.word()).suggests(QUEST_SUGGESTIONS).executes(QuestzCommand::submit)))
                .then(literal("team")
                        .then(literal("info")
                                .executes(context -> teamInfo(context, context.getSource().getPlayerOrThrow()))
                                .then(argument("player", EntityArgumentType.player())
                                        .executes(context -> teamInfo(context, EntityArgumentType.getPlayer(context, "player")))))
                        .then(literal("list").executes(QuestzCommand::listTeams))
                        .then(literal("create").then(argument("name", StringArgumentType.greedyString()).executes(QuestzCommand::createTeam)))
                        .then(literal("invite").then(argument("player", EntityArgumentType.player()).executes(QuestzCommand::invite)))
                        .then(literal("accept")
                                .executes(context -> accept(context, null))
                                .then(argument("name", StringArgumentType.greedyString()).suggests(INVITE_SUGGESTIONS)
                                        .executes(context -> accept(context, StringArgumentType.getString(context, "name")))))
                        .then(literal("leave").executes(QuestzCommand::leave))
                        .then(literal("kick").then(argument("player", GameProfileArgumentType.gameProfile()).executes(QuestzCommand::kick)))
                        .then(literal("rename").then(argument("name", StringArgumentType.greedyString()).executes(QuestzCommand::rename)))
                        .then(literal("disband").executes(QuestzCommand::disband))));
    }

    public static Text questName(Quest quest) {
        return quest.title().getString().isEmpty() ? Text.literal(quest.id()) : quest.title();
    }

    private static Quest getQuest(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        String id = StringArgumentType.getString(context, "quest");
        Quest quest = QuestBookManager.getBook().getQuest(id);
        if (quest == null) {
            throw UNKNOWN_QUEST.create(id);
        }
        return quest;
    }

    private static Text statusText(QuestBook book, TeamData team, Quest quest) {
        if (team.isCompleted(quest.id())) {
            return Text.translatable("command.questz.status.completed").formatted(Formatting.GREEN);
        }
        if (QuestEngine.areDependenciesMet(book, team, quest)) {
            return Text.translatable("command.questz.status.available").formatted(Formatting.YELLOW);
        }
        return Text.translatable("command.questz.status.locked").formatted(Formatting.GRAY);
    }

    private static int reload(CommandContext<ServerCommandSource> context) {
        MinecraftServer server = context.getSource().getServer();
        QuestBookManager.load(server);
        QuestBook book = QuestBookManager.getBook();

        QuestEngine.checkOnlineTeams(server);

        context.getSource().sendFeedback(() -> Text.translatable("command.questz.reload", book.getChapters().size(), book.getQuests().size()), true);
        if (!book.getProblems().isEmpty()) {
            context.getSource().sendFeedback(() -> Text.translatable("command.questz.reload.problems", book.getProblems().size()).formatted(Formatting.GOLD), false);
        }
        return book.getQuests().size();
    }

    private static int listQuests(CommandContext<ServerCommandSource> context, ServerPlayerEntity player) {
        QuestBook book = QuestBookManager.getBook();
        TeamData team = TeamManager.getTeam(player);
        ServerCommandSource source = context.getSource();

        source.sendFeedback(() -> Text.translatable("command.questz.quest.list", team.getName()), false);
        if (book.getQuests().isEmpty()) {
            source.sendFeedback(() -> Text.translatable("command.questz.quest.list.empty"), false);
            return 0;
        }
        for (Chapter chapter : book.getChapters()) {
            Text chapterName = chapter.title().getString().isEmpty() ? Text.literal(chapter.id()) : chapter.title();
            source.sendFeedback(() -> Text.translatable("command.questz.quest.list.chapter", chapterName).formatted(Formatting.BOLD), false);
            for (Quest quest : chapter.quests()) {
                source.sendFeedback(() -> Text.translatable("command.questz.quest.list.entry", Text.literal(quest.id()).formatted(Formatting.DARK_GRAY), statusText(book, team, quest), questName(quest)), false);
            }
        }
        return book.getQuests().size();
    }

    private static int progress(CommandContext<ServerCommandSource> context, ServerPlayerEntity player) throws CommandSyntaxException {
        Quest quest = getQuest(context);
        TeamData team = TeamManager.getTeam(player);
        ServerCommandSource source = context.getSource();

        source.sendFeedback(() -> Text.translatable("command.questz.quest.progress", questName(quest), team.getName(), statusText(QuestBookManager.getBook(), team, quest)), false);
        for (Task task : quest.tasks()) {
            String type = String.valueOf(task.getTypeId());
            long current = team.getProgress(quest.id(), task.getId());
            source.sendFeedback(() -> Text.translatable("command.questz.quest.progress.entry", task.getId(), type, current, task.getTarget()), false);
        }
        return quest.tasks().size();
    }

    private static int complete(CommandContext<ServerCommandSource> context, ServerPlayerEntity player) throws CommandSyntaxException {
        Quest quest = getQuest(context);
        TeamData team = TeamManager.getTeam(player);
        QuestEngine.forceComplete(context.getSource().getServer(), team, quest);
        context.getSource().sendFeedback(() -> Text.translatable("command.questz.quest.complete", questName(quest), team.getName()), true);
        return 1;
    }

    private static int reset(CommandContext<ServerCommandSource> context, ServerPlayerEntity player) throws CommandSyntaxException {
        Quest quest = getQuest(context);
        TeamData team = TeamManager.getTeam(player);
        QuestEngine.reset(context.getSource().getServer(), team, quest);
        context.getSource().sendFeedback(() -> Text.translatable("command.questz.quest.reset", questName(quest), team.getName()), true);
        return 1;
    }

    private static Collection<TeamData> teamsOf(Collection<ServerPlayerEntity> players) {
        Map<UUID, TeamData> teams = new LinkedHashMap<>();
        for (ServerPlayerEntity player : players) {
            TeamData team = TeamManager.getTeam(player);
            teams.putIfAbsent(team.getId(), team);
        }
        return teams.values();
    }

    private static int completeAll(CommandContext<ServerCommandSource> context, Collection<ServerPlayerEntity> players) {
        MinecraftServer server = context.getSource().getServer();
        List<Quest> quests = QuestBookManager.getBook().getQuests().stream().toList();
        Collection<TeamData> teams = teamsOf(players);
        for (TeamData team : teams) {
            for (Quest quest : quests) {
                QuestEngine.forceComplete(server, team, quest);
            }
            context.getSource().sendFeedback(() -> Text.translatable("command.questz.quest.complete_all", quests.size(), team.getName()), true);
        }
        return teams.size();
    }

    private static int resetAll(CommandContext<ServerCommandSource> context, Collection<ServerPlayerEntity> players) {
        MinecraftServer server = context.getSource().getServer();
        List<Quest> quests = QuestBookManager.getBook().getQuests().stream().toList();
        Collection<TeamData> teams = teamsOf(players);
        for (TeamData team : teams) {
            for (Quest quest : quests) {
                QuestEngine.reset(server, team, quest);
            }
            context.getSource().sendFeedback(() -> Text.translatable("command.questz.quest.reset_all", quests.size(), team.getName()), true);
        }
        return teams.size();
    }

    private static int claim(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        Quest quest = getQuest(context);
        TeamData team = TeamManager.getTeam(player);
        if (!team.isCompleted(quest.id())) {
            context.getSource().sendError(Text.translatable("command.questz.claim.not_completed", questName(quest)));
            return 0;
        }
        int claimed = QuestEngine.claimRewards(player, team, quest);
        if (claimed == 0) {
            context.getSource().sendFeedback(() -> Text.translatable("command.questz.claim.nothing", questName(quest)), false);
        } else {
            context.getSource().sendFeedback(() -> Text.translatable("command.questz.claim.success", claimed, questName(quest)), false);
        }
        return claimed;
    }

    private static int submit(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        Quest quest = getQuest(context);
        long submitted = TaskTracker.submit(player, quest);
        if (submitted == 0L) {
            context.getSource().sendError(Text.translatable("command.questz.submit.nothing", questName(quest)));
            return 0;
        }
        context.getSource().sendFeedback(() -> Text.translatable("command.questz.submit.success", submitted, questName(quest)), false);
        return (int) Math.min(Integer.MAX_VALUE, submitted);
    }

    private static Text playerName(MinecraftServer server, UUID uuid) {
        ServerPlayerEntity online = server.getPlayerManager().getPlayer(uuid);
        if (online != null) {
            return online.getDisplayName();
        }
        UserCache cache = server.getUserCache();
        return Text.literal(cache == null ? uuid.toString() : cache.getByUuid(uuid).map(GameProfile::getName).orElse(uuid.toString())).formatted(Formatting.GRAY);
    }

    private static int teamInfo(CommandContext<ServerCommandSource> context, ServerPlayerEntity player) {
        MinecraftServer server = context.getSource().getServer();
        TeamData team = TeamManager.getTeam(player);
        Text type = Text.translatable(team.isParty() ? "command.questz.team.party" : "command.questz.team.personal");
        MutableText members = Text.empty();
        Iterator<UUID> iterator = team.getMembers().iterator();
        while (iterator.hasNext()) {
            members.append(playerName(server, iterator.next()));
            if (iterator.hasNext()) {
                members.append(", ");
            }
        }
        context.getSource().sendFeedback(() -> Text.translatable("command.questz.team.info", team.getName(), type), false);
        context.getSource().sendFeedback(() -> Text.translatable("command.questz.team.info.owner", playerName(server, team.getOwner())), false);
        context.getSource().sendFeedback(() -> Text.translatable("command.questz.team.info.members", members), false);
        return team.getMembers().size();
    }

    private static int listTeams(CommandContext<ServerCommandSource> context) {
        List<TeamData> parties = QuestzState.get(context.getSource().getServer()).getTeams().stream().filter(TeamData::isParty).toList();
        if (parties.isEmpty()) {
            context.getSource().sendFeedback(() -> Text.translatable("command.questz.team.list.empty"), false);
            return 0;
        }
        context.getSource().sendFeedback(() -> Text.translatable("command.questz.team.list"), false);
        for (TeamData party : parties) {
            context.getSource().sendFeedback(() -> Text.translatable("command.questz.team.list.entry", party.getName(), party.getMembers().size()), false);
        }
        return parties.size();
    }

    private static int fail(CommandContext<ServerCommandSource> context, TeamResult result) {
        context.getSource().sendError(Text.translatable("command.questz.team.error." + result.name().toLowerCase(Locale.ROOT), TeamManager.MAX_NAME_LENGTH));
        return 0;
    }

    private static void notifyMembers(MinecraftServer server, TeamData team, Text message, @Nullable UUID exclude) {
        for (ServerPlayerEntity member : team.getOnlineMembers(server)) {
            if (!member.getUuid().equals(exclude)) {
                member.sendMessage(message, false);
            }
        }
    }

    private static int createTeam(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        TeamResult result = TeamManager.createParty(player, StringArgumentType.getString(context, "name"));
        if (result != TeamResult.SUCCESS) {
            return fail(context, result);
        }
        String name = TeamManager.getTeam(player).getName();
        context.getSource().sendFeedback(() -> Text.translatable("command.questz.team.created", name), false);
        return 1;
    }

    private static int invite(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        ServerPlayerEntity target = EntityArgumentType.getPlayer(context, "player");
        TeamResult result = TeamManager.invite(player, target);
        if (result != TeamResult.SUCCESS) {
            return fail(context, result);
        }
        String name = TeamManager.getTeam(player).getName();
        Text accept = Text.translatable("command.questz.team.invite.accept").styled(style -> style.withColor(Formatting.GREEN)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/questz team accept " + name))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal("/questz team accept " + name))));
        target.sendMessage(Text.translatable("command.questz.team.invite.received", player.getDisplayName(), name, accept), false);
        context.getSource().sendFeedback(() -> Text.translatable("command.questz.team.invite", target.getDisplayName(), name), false);
        return 1;
    }

    private static int accept(CommandContext<ServerCommandSource> context, @Nullable String name) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        QuestzState state = QuestzState.get(context.getSource().getServer());
        List<TeamData> invites = TeamManager.getInvites(player).stream().map(state::getTeam).filter(Objects::nonNull).toList();

        TeamData party;
        if (name == null) {
            if (invites.size() > 1) {
                context.getSource().sendError(Text.translatable("command.questz.team.error.which_invite", invites.stream().map(TeamData::getName).collect(Collectors.joining(", "))));
                return 0;
            }
            party = invites.isEmpty() ? null : invites.getFirst();
        } else {
            party = invites.stream().filter(invite -> invite.getName().equalsIgnoreCase(name.trim())).findFirst().orElse(null);
        }
        if (party == null) {
            return fail(context, TeamResult.NO_INVITE);
        }

        TeamResult result = TeamManager.accept(player, party.getId());
        if (result != TeamResult.SUCCESS) {
            return fail(context, result);
        }
        notifyMembers(context.getSource().getServer(), party, Text.translatable("command.questz.team.member_joined", player.getDisplayName()), player.getUuid());
        context.getSource().sendFeedback(() -> Text.translatable("command.questz.team.joined", party.getName()), false);
        return 1;
    }

    private static int leave(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        TeamData party = TeamManager.getTeam(player);
        TeamResult result = TeamManager.leave(player);
        if (result != TeamResult.SUCCESS) {
            return fail(context, result);
        }
        notifyMembers(context.getSource().getServer(), party, Text.translatable("command.questz.team.member_left", player.getDisplayName()), player.getUuid());
        context.getSource().sendFeedback(() -> Text.translatable("command.questz.team.left", party.getName()), false);
        return 1;
    }

    private static int kick(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        Collection<GameProfile> profiles = GameProfileArgumentType.getProfileArgument(context, "player");
        if (profiles.size() != 1) {
            throw GameProfileArgumentType.UNKNOWN_PLAYER_EXCEPTION.create();
        }
        GameProfile profile = profiles.iterator().next();
        TeamData party = TeamManager.getTeam(player);
        TeamResult result = TeamManager.kick(player, profile.getId());
        if (result != TeamResult.SUCCESS) {
            return fail(context, result);
        }
        MinecraftServer server = context.getSource().getServer();
        ServerPlayerEntity target = server.getPlayerManager().getPlayer(profile.getId());
        if (target != null) {
            target.sendMessage(Text.translatable("command.questz.team.kicked.notice", party.getName()), false);
        }
        notifyMembers(server, party, Text.translatable("command.questz.team.member_kicked", profile.getName()), player.getUuid());
        context.getSource().sendFeedback(() -> Text.translatable("command.questz.team.kicked", profile.getName()), false);
        return 1;
    }

    private static int rename(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        TeamResult result = TeamManager.rename(player, StringArgumentType.getString(context, "name"));
        if (result != TeamResult.SUCCESS) {
            return fail(context, result);
        }
        TeamData party = TeamManager.getTeam(player);
        notifyMembers(context.getSource().getServer(), party, Text.translatable("command.questz.team.renamed", party.getName()), null);
        return 1;
    }

    private static int disband(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        TeamData party = TeamManager.getTeam(player);
        List<ServerPlayerEntity> members = party.getOnlineMembers(context.getSource().getServer());
        TeamResult result = TeamManager.disband(player);
        if (result != TeamResult.SUCCESS) {
            return fail(context, result);
        }
        for (ServerPlayerEntity member : members) {
            if (member != player) {
                member.sendMessage(Text.translatable("command.questz.team.disbanded.notice", party.getName()), false);
            }
        }
        context.getSource().sendFeedback(() -> Text.translatable("command.questz.team.disbanded", party.getName()), false);
        return 1;
    }

}
