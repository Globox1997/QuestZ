package net.questz.access;

import net.minecraft.util.Identifier;

import java.util.List;
import java.util.Map;

public interface RewardAccess {

    List<String> questz$getCommands();

    void questz$setCommands(List<String> commands);

    Map<Identifier, Integer> questz$getItems();

    void questz$setItems(Map<Identifier, Integer> items);

    String questz$getText();

    void questz$setText(String text);

}
