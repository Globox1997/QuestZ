package net.questz.progress;

public interface ProgressView {

    boolean isCompleted(String questId);

    long getProgress(String questId, String taskId);

    int getCompletions(String questId);

    default boolean hasEverCompleted(String questId) {
        return this.isCompleted(questId) || this.getCompletions(questId) > 0;
    }

}
