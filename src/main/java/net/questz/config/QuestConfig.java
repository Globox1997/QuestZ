package net.questz.config;

import java.util.ArrayList;
import java.util.List;

import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.Config;
import me.shedaniel.cloth.clothconfig.shadowed.blue.endless.jankson.Comment;

@Config(name = "questz")
@Config.Gui.Background("minecraft:textures/block/stone.png")
public class QuestConfig implements ConfigData {

    public int maxTextWidth = 180;
    public int minTextWidth = 150;
    @Comment("How many ongoing quests on one branch will get shown")
    public int questDisplayDepth = 18;
    public boolean showRootRequirements = false;
    @Comment("namespace ids of advancements only shown in the quest screen")
    public ArrayList<String> questAdvancementNamespaceIds = new ArrayList<>(List.of("questz"));

}
