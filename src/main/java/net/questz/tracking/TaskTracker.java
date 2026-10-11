package net.questz.tracking;

import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.stat.Stat;
import net.questz.book.Quest;
import net.questz.book.QuestBookManager;
import net.questz.progress.QuestEngine;
import net.questz.progress.TeamData;
import net.questz.progress.TeamManager;
import net.questz.task.ItemTask;
import net.questz.task.PollingTask;
import net.questz.task.StatTask;
import net.questz.task.Task;

public class TaskTracker {

    public static final int POLL_INTERVAL = 20;

    public static void onStat(ServerPlayerEntity player, Stat<?> stat, int amount) {
        MinecraftServer server = player.getServer();
        if (amount <= 0 || server == null) {
            return;
        }
        TeamData team = TeamManager.getTeam(player);
        for (ActiveTasks.Entry entry : ActiveTasks.get(team)) {
            if (entry.task() instanceof StatTask statTask && statTask.matches(stat)) {
                QuestEngine.addProgress(server, team, entry.quest(), statTask, amount);
            }
        }
    }

    public static void tick(MinecraftServer server) {
        if (server.getTicks() % POLL_INTERVAL != 0) {
            return;
        }
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            TeamData team = TeamManager.getTeam(player);
            for (ActiveTasks.Entry entry : ActiveTasks.get(team)) {
                if (entry.task() instanceof PollingTask pollingTask && !QuestEngine.isTaskComplete(team, entry.quest(), entry.task())) {
                    long value = pollingTask.poll(player);
                    if (value > team.getProgress(entry.quest().id(), entry.task().getId())) {
                        QuestEngine.setProgress(server, team, entry.quest(), entry.task(), value);
                    }
                }
            }
        }
    }

    public static long submit(ServerPlayerEntity player, Quest quest) {
        MinecraftServer server = player.getServer();
        TeamData team = TeamManager.getTeam(player);
        if (server == null || !QuestEngine.canProgress(QuestBookManager.getBook(), team, quest)) {
            return 0L;
        }
        PlayerInventory inventory = player.getInventory();
        long submitted = 0L;
        for (Task task : quest.tasks()) {
            if (!(task instanceof ItemTask itemTask) || !itemTask.isConsume()) {
                continue;
            }
            long remaining = itemTask.getTarget() - team.getProgress(quest.id(), itemTask.getId());
            long taken = 0L;
            for (int slot = 0; slot < inventory.size() && remaining > 0L; slot++) {
                ItemStack stack = inventory.getStack(slot);
                if (itemTask.matches(stack)) {
                    int amount = (int) Math.min(remaining, stack.getCount());
                    stack.decrement(amount);
                    remaining -= amount;
                    taken += amount;
                }
            }
            if (taken > 0L) {
                inventory.markDirty();
                submitted += taken;
                QuestEngine.addProgress(server, team, quest, itemTask, taken);
            }
        }
        return submitted;
    }

}
