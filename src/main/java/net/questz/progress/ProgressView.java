package net.questz.progress;

public interface ProgressView {

    boolean isCompleted(String questId);

    long getProgress(String questId, String taskId);

}
