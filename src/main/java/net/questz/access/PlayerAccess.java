package net.questz.access;

import net.questz.quest.QuestTab;

public interface PlayerAccess {

    void setQuestTab(QuestTab questTab);

    QuestTab getQuestTab();
}
