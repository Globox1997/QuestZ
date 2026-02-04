package net.questz.quest;

import java.util.List;
import java.util.Map;

public class QuestData {

    public static class AdvancementData {
        public String parent;
        public DisplayData display;
        public Map<String, CriterionData> criteria;
        public List<List<String>> requirements;
    }

    public static class DisplayData {
        public IconData icon;
        public TextComponent title;
        public TextComponent description;
        public Map<String, List<String>> requirement;
        public String frame;
        public boolean show_toast;
        public boolean announce_to_chat;
        public boolean hidden;
    }

    public static class IconData {
        public String id;
    }

    public static class TextComponent {
        public String text;
        public TextComponent(String text) { this.text = text; }
    }

    public static class CriterionData {
        public String trigger;
        public Map<String, Object> conditions;
    }
}

