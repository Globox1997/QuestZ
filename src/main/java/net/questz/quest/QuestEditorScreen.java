package net.questz.quest;

import com.google.gson.GsonBuilder;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.advancement.PlacedAdvancement;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.questz.mixin.client.TextFieldWidgetAccessor;
import net.questz.network.packet.QuestCreationPacket;
import org.jetbrains.annotations.Nullable;

import java.util.*;

@Environment(EnvType.CLIENT)
public class QuestEditorScreen extends Screen {

    @Nullable
    private PlacedAdvancement placedAdvancement;

    private TextFieldWidget titleField;
    private MultilineTextFieldWidget descField;
    private TextFieldWidget iconField;
    private TextFieldWidget parentField;
//    private TextFieldWidget requirementInfoField;

    private ButtonWidget frameButton;
    private String currentFrame = "task";

    private boolean showToast = false;
    private boolean announceChat = false;
    private boolean isHidden = false;

    // Criteria management
    private final List<CriteriaEntry> criteriaEntries = new ArrayList<>();
    private ButtonWidget addCriteriaButton;
    private int scrollOffset = 0;
    private static final int MAX_VISIBLE_CRITERIA = 5;

    public QuestEditorScreen(@Nullable QuestWidget questWidget) {
        super(questWidget != null && questWidget.getAdvancement().getAdvancement().name().isPresent() ?
                questWidget.getAdvancement().getAdvancement().name().get() : Text.translatable("gui.questz.newQuest"));
        this.placedAdvancement = questWidget != null ? questWidget.getAdvancement() : null;
    }

    @Override
    protected void init() {
        int leftColumnWidth = 320;
        int rightColumnWidth = 320;
        int columnGap = 20;
        int leftX = 20;
        int rightX = leftX + leftColumnWidth + columnGap;
        int y = 30;
        int leftY = y;

        this.titleField = new TextFieldWidget(this.textRenderer, leftX, leftY, leftColumnWidth, 20, Text.translatable("gui.questz.title"));
        this.titleField.setPlaceholder(Text.translatable("gui.questz.example"));
        if (this.placedAdvancement != null && this.placedAdvancement.getAdvancement().display().isPresent()) {
            this.titleField.setText(this.placedAdvancement.getAdvancement().display().get().getTitle().getString());
        }
        this.addSelectableChild(this.titleField);
        leftY += 32;

        this.descField = new MultilineTextFieldWidget(this.textRenderer, leftX, leftY, leftColumnWidth, 60);
        this.descField.setPlaceholder(Text.translatable("gui.questz.example"));
        this.descField.setMaxLength(500);
        if (this.placedAdvancement != null && this.placedAdvancement.getAdvancement().display().isPresent()) {
            this.descField.setText(this.placedAdvancement.getAdvancement().display().get().getDescription().getString());
        }
        this.addSelectableChild(this.descField);
        leftY += 72;

        this.iconField = new TextFieldWidget(this.textRenderer, leftX, leftY, leftColumnWidth, 20, Text.translatable("gui.questz.icon"));
        this.iconField.setPlaceholder(Text.literal("minecraft:stone"));
        if (this.placedAdvancement != null && this.placedAdvancement.getAdvancement().display().isPresent()) {
            this.iconField.setText(Registries.ITEM.getId(this.placedAdvancement.getAdvancement().display().get().getIcon().getItem()).toString());
        }
        this.addSelectableChild(this.iconField);
        leftY += 32;

        this.parentField = new TextFieldWidget(this.textRenderer, leftX, leftY, leftColumnWidth, 20, Text.translatable("gui.questz.parent"));
        if (this.placedAdvancement != null && this.placedAdvancement.getParent() != null) {
            this.parentField.setText(this.placedAdvancement.getParent().getAdvancementEntry().id().toString());
        }
        this.addSelectableChild(this.parentField);
        leftY += 32;

//        this.requirementInfoField = new TextFieldWidget(this.textRenderer, leftX, leftY, leftColumnWidth, 20, Text.literal("Requirement Info"));
//        this.requirementInfoField.setPlaceholder(Text.literal("\\nRequires crafting:"));
//        this.requirementInfoField.setText("\\nRequires crafting:");
//        this.addSelectableChild(this.requirementInfoField);
//        leftY += 32;

        this.frameButton = ButtonWidget.builder(Text.translatable("gui.questz.frame", currentFrame.toUpperCase()), (button) -> {
            if (currentFrame.equals("task")) currentFrame = "goal";
            else if (currentFrame.equals("goal")) currentFrame = "challenge";
            else currentFrame = "task";
            button.setMessage(Text.translatable("gui.questz.frame", currentFrame.toUpperCase()));
        }).dimensions(leftX, leftY, leftColumnWidth, 20).build();
        this.addDrawableChild(this.frameButton);
        leftY += 25;

        int btnWidth = (leftColumnWidth - 4) / 3;
        this.addDrawableChild(ButtonWidget.builder(Text.translatable("gui.questz.toast", showToast), (btn) -> {
            showToast = !showToast;
            btn.setMessage(Text.translatable("gui.questz.toast", showToast));
        }).dimensions(leftX, leftY, btnWidth, 20).build());

        this.addDrawableChild(ButtonWidget.builder(Text.translatable("gui.questz.chat", announceChat), (btn) -> {
            announceChat = !announceChat;
            btn.setMessage(Text.translatable("gui.questz.chat", announceChat));
        }).dimensions(leftX + btnWidth + 2, leftY, btnWidth, 20).build());

        this.addDrawableChild(ButtonWidget.builder(Text.translatable("gui.questz.hidden", isHidden), (btn) -> {
            isHidden = !isHidden;
            btn.setMessage(Text.translatable("gui.questz.hidden", isHidden));
        }).dimensions(leftX + (btnWidth + 2) * 2, leftY, btnWidth, 20).build());
        leftY += 25;

        this.addDrawableChild(ButtonWidget.builder(Text.translatable("gui.questz.save"), (button) -> {
            this.saveAndSend();
        }).dimensions(leftX, leftY, leftColumnWidth, 20).build());

        this.addCriteriaButton = ButtonWidget.builder(Text.literal("+ Add Criteria"), (button) -> {
            this.addCriteriaEntry();
        }).dimensions(rightX, y, rightColumnWidth, 20).build();
        this.addDrawableChild(this.addCriteriaButton);

        updateCriteriaWidgets();
    }

    private void addCriteriaEntry() {
        CriteriaEntry entry = new CriteriaEntry();
        criteriaEntries.add(entry);
        updateCriteriaWidgets();
    }

    private void removeCriteriaEntry(CriteriaEntry entry) {
        criteriaEntries.remove(entry);
        updateCriteriaWidgets();
    }

    private void updateCriteriaWidgets() {
        this.clearChildren();

        int leftColumnWidth = 320;
        int rightColumnWidth = 320;
        int columnGap = 20;
        int leftX = 20;
        int rightX = leftX + leftColumnWidth + columnGap;
        int y = 30;
        int leftY = y;

        this.addSelectableChild(this.titleField);
        this.addSelectableChild(this.descField);
        this.addSelectableChild(this.iconField);
        this.addSelectableChild(this.parentField);
//        this.addSelectableChild(this.requirementInfoField);
        this.addDrawableChild(this.frameButton);

        int toggleY = y + 32 + 72 + 32 + 32 + 32 + 25;
        int btnWidth = (leftColumnWidth - 4) / 3;

        this.addDrawableChild(ButtonWidget.builder(Text.translatable("gui.questz.toast", showToast), (btn) -> {
            showToast = !showToast;
            btn.setMessage(Text.translatable("gui.questz.toast", showToast));
        }).dimensions(leftX, toggleY, btnWidth, 20).build());

        this.addDrawableChild(ButtonWidget.builder(Text.translatable("gui.questz.chat", announceChat), (btn) -> {
            announceChat = !announceChat;
            btn.setMessage(Text.translatable("gui.questz.chat", announceChat));
        }).dimensions(leftX + btnWidth + 2, toggleY, btnWidth, 20).build());

        this.addDrawableChild(ButtonWidget.builder(Text.translatable("gui.questz.hidden", isHidden), (btn) -> {
            isHidden = !isHidden;
            btn.setMessage(Text.translatable("gui.questz.hidden", isHidden));
        }).dimensions(leftX + (btnWidth + 2) * 2, toggleY, btnWidth, 20).build());

        this.addDrawableChild(ButtonWidget.builder(Text.translatable("gui.questz.save"), (button) -> {
            this.saveAndSend();
        }).dimensions(leftX, toggleY + 25, leftColumnWidth, 20).build());

        this.addDrawableChild(this.addCriteriaButton);

        int criteriaY = y + 25;
        int maxCriteriaY = this.height - 20;

        for (int i = scrollOffset; i < criteriaEntries.size(); i++) {
            CriteriaEntry entry = criteriaEntries.get(i);

            int entryHeight = 80;
            if (criteriaY + entryHeight > maxCriteriaY) {
                break;
            }

            criteriaY += 5;

            entry.nameField = new TextFieldWidget(this.textRenderer, rightX, criteriaY, 200, 20, Text.literal("Criteria Name"));
            entry.nameField.setPlaceholder(Text.literal("wooden_axe"));
            entry.nameField.setText(entry.name);
            entry.nameField.setChangedListener(text -> entry.name = text);
            this.addSelectableChild(entry.nameField);

            ButtonWidget removeBtn = ButtonWidget.builder(Text.literal("X"), (button) -> {
                removeCriteriaEntry(entry);
            }).dimensions(rightX + 205, criteriaY, 25, 20).build();
            this.addDrawableChild(removeBtn);

            entry.triggerButton = ButtonWidget.builder(Text.literal(getTriggerDisplayName(entry.trigger)), (button) -> {
                cycleTrigger(entry);
                button.setMessage(Text.literal(getTriggerDisplayName(entry.trigger)));
            }).dimensions(rightX + 235, criteriaY, 85, 20).build();
            this.addDrawableChild(entry.triggerButton);

            criteriaY += 25;

            if (entry.trigger.equals("inventory_changed") || entry.trigger.equals("placed_block_count")) {
                int objectFieldWidth = entry.trigger.equals("placed_block_count") ? 200 : 310;
                entry.objectField = new TextFieldWidget(this.textRenderer, rightX + 10, criteriaY, objectFieldWidth, 20, Text.literal("Item/Block"));
                entry.objectField.setPlaceholder(Text.literal("minecraft:wooden_axe"));
                entry.objectField.setText(entry.object);
                entry.objectField.setChangedListener(text -> entry.object = text);
                this.addSelectableChild(entry.objectField);

                if (entry.trigger.equals("placed_block_count")) {
                    entry.countField = new TextFieldWidget(this.textRenderer, rightX + 215, criteriaY, 105, 20, Text.literal("Count"));
                    entry.countField.setPlaceholder(Text.literal("1"));
                    entry.countField.setText(String.valueOf(entry.count));
                    entry.countField.setChangedListener(text -> {
                        try {
                            entry.count = Integer.parseInt(text);
                        } catch (NumberFormatException ignored) {
                        }
                    });
                    this.addSelectableChild(entry.countField);
                }

                criteriaY += 25;
            }

            entry.requirementDisplayField = new TextFieldWidget(this.textRenderer, rightX + 10, criteriaY, 310, 20, Text.literal("Display Text"));
            entry.requirementDisplayField.setPlaceholder(Text.literal("\\n§e- %I% Wooden Axe"));
            entry.requirementDisplayField.setText(entry.requirementDisplay);
            entry.requirementDisplayField.setChangedListener(text -> entry.requirementDisplay = text);
            this.addSelectableChild(entry.requirementDisplayField);

            criteriaY += 30;
        }

        if (scrollOffset > 0 || criteriaY > maxCriteriaY) {
            // Could add scroll indicators here if needed
        }
    }

    private String getTriggerDisplayName(String trigger) {
        switch (trigger) {
            case "inventory_changed":
                return "Inventory";
            case "placed_block_count":
                return "Place Block";
            case "minecraft:location":
                return "Location";
            case "minecraft:player_killed_entity":
                return "Kill Entity";
            case "minecraft:item_used_on_block":
                return "Use Item";
            default:
                return trigger;
        }
    }

    private void cycleTrigger(CriteriaEntry entry) {
        String[] triggers = {
                "inventory_changed",
                "placed_block_count",
                "minecraft:location",
                "minecraft:player_killed_entity",
                "minecraft:item_used_on_block"
        };

        int currentIndex = Arrays.asList(triggers).indexOf(entry.trigger);
        entry.trigger = triggers[(currentIndex + 1) % triggers.length];
        updateCriteriaWidgets();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);

        int leftColumnWidth = 320;
        int rightColumnWidth = 320;
        int columnGap = 20;
        int leftX = 20;
        int rightX = leftX + leftColumnWidth + columnGap;
        int y = 30;

        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 10, 0xFFFFFF);

        context.drawTextWithShadow(this.textRenderer, Text.translatable("gui.questz.title"), leftX, y - 10, 0xA0A0A0);
        context.drawTextWithShadow(this.textRenderer, Text.translatable("gui.questz.description"), leftX, y + 22, 0xA0A0A0);
        context.drawTextWithShadow(this.textRenderer, Text.translatable("gui.questz.icon"), leftX, y + 94, 0xA0A0A0);
        context.drawTextWithShadow(this.textRenderer, Text.translatable("gui.questz.parent"), leftX, y + 126, 0xA0A0A0);
        context.drawTextWithShadow(this.textRenderer, Text.literal("Requirement Info"), leftX, y + 158, 0xA0A0A0);

        this.titleField.render(context, mouseX, mouseY, delta);
        this.descField.render(context, mouseX, mouseY, delta);
        this.iconField.render(context, mouseX, mouseY, delta);
        this.parentField.render(context, mouseX, mouseY, delta);
//        this.requirementInfoField.render(context, mouseX, mouseY, delta);

        context.drawTextWithShadow(this.textRenderer, Text.literal("§lCriteria"), rightX, y - 10, 0xFFFFFF);

        if (!criteriaEntries.isEmpty()) {
            int visibleCount = Math.min(criteriaEntries.size() - scrollOffset, MAX_VISIBLE_CRITERIA);
            String scrollHint = String.format("Showing %d-%d of %d",
                    scrollOffset + 1,
                    scrollOffset + visibleCount,
                    criteriaEntries.size());
            context.drawTextWithShadow(this.textRenderer, Text.literal(scrollHint), rightX + 150, y - 10, 0x808080);

            if (criteriaEntries.size() > MAX_VISIBLE_CRITERIA) {
                context.drawTextWithShadow(this.textRenderer, Text.literal("(Scroll to see more)"), rightX + 150, y + 2, 0x606060);
            }
        }

        for (CriteriaEntry entry : criteriaEntries) {
            if (entry.nameField != null) entry.nameField.render(context, mouseX, mouseY, delta);
            if (entry.objectField != null) entry.objectField.render(context, mouseX, mouseY, delta);
            if (entry.countField != null) entry.countField.render(context, mouseX, mouseY, delta);
            if (entry.requirementDisplayField != null) entry.requirementDisplayField.render(context, mouseX, mouseY, delta);
        }
    }

    @Override
    public void close() {
        this.client.setScreen(new QuestScreen(client.player.networkHandler.getAdvancementHandler(), true));
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (criteriaEntries.size() > MAX_VISIBLE_CRITERIA) {
            scrollOffset = Math.max(0, Math.min(scrollOffset - (int) verticalAmount, criteriaEntries.size() - MAX_VISIBLE_CRITERIA));
            updateCriteriaWidgets();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    private void saveAndSend() {
        Map<String, Object> advancement = new LinkedHashMap<>();

        if (!this.parentField.getText().isEmpty()) {
            advancement.put("parent", this.parentField.getText());
        }

        Map<String, Object> display = new LinkedHashMap<>();

        Map<String, String> icon = new LinkedHashMap<>();
        icon.put("id", this.iconField.getText().isEmpty() ? "minecraft:stone" : this.iconField.getText());
        display.put("icon", icon);

        Map<String, String> title = new LinkedHashMap<>();
        title.put("text", this.titleField.getText());
        display.put("title", title);

        Map<String, String> description = new LinkedHashMap<>();
        description.put("text", this.descField.getText());
        display.put("description", description);

        Map<String, Object> requirement = new LinkedHashMap<>();
        requirement.put("info", this.requirementInfoField.getText());

        for (CriteriaEntry entry : criteriaEntries) {
            if (!entry.name.isEmpty() && !entry.requirementDisplay.isEmpty()) {
                requirement.put(entry.name, Collections.singletonList(entry.requirementDisplay));
            }
        }
        display.put("requirement", requirement);

        display.put("frame", this.currentFrame);
        display.put("show_toast", this.showToast);
        display.put("announce_to_chat", this.announceChat);
        display.put("hidden", this.isHidden);

        advancement.put("display", display);

        Map<String, Object> criteria = new LinkedHashMap<>();
        List<List<String>> requirements = new ArrayList<>();

        for (CriteriaEntry entry : criteriaEntries) {
            if (entry.name.isEmpty()) continue;

            Map<String, Object> criterion = new LinkedHashMap<>();
            criterion.put("trigger", entry.trigger);

            Map<String, Object> conditions = new LinkedHashMap<>();

            if (entry.trigger.equals("inventory_changed")) {
                List<Map<String, Object>> items = new ArrayList<>();
                Map<String, Object> itemEntry = new LinkedHashMap<>();
                itemEntry.put("items", Collections.singletonList(entry.object));
                items.add(itemEntry);
                conditions.put("items", items);
            } else if (entry.trigger.equals("placed_block_count")) {
                conditions.put("object", entry.object);
                conditions.put("count", entry.count);
            }

            criterion.put("conditions", conditions);
            criteria.put(entry.name, criterion);

            requirements.add(Collections.singletonList(entry.name));
        }

        advancement.put("criteria", criteria);
        advancement.put("requirements", requirements);

        String fileName = this.titleField.getText().toLowerCase().replace(" ", "_");
        if (fileName.isEmpty()) fileName = "new_quest";

        String jsonString = new GsonBuilder().setPrettyPrinting().create().toJson(advancement);

        ClientPlayNetworking.send(new QuestCreationPacket(fileName, jsonString));
        this.close();
    }

    private static class CriteriaEntry {
        String name = "";
        String trigger = "inventory_changed";
        String object = "";
        int count = 1;
        String requirementDisplay = "";

        TextFieldWidget nameField;
        ButtonWidget triggerButton;
        TextFieldWidget objectField;
        TextFieldWidget countField;
        TextFieldWidget requirementDisplayField;
    }

    private static class MultilineTextFieldWidget extends TextFieldWidget {
        private final int displayHeight;
        private Text placeholder;
        private final TextRenderer textRenderer;

        public MultilineTextFieldWidget(TextRenderer textRenderer, int x, int y, int width, int height) {
            super(textRenderer, x, y, width, 20, Text.empty());
            this.displayHeight = height;
            this.textRenderer = textRenderer;

            this.setMaxLength(1024);
        }

        @Override
        public void setPlaceholder(Text placeholder) {
            this.placeholder = placeholder;
            super.setPlaceholder(placeholder);
        }

        @Override
        public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            if (this.isActive() && keyCode == 257) {
                this.write("\n");
                return true;
            }
            return super.keyPressed(keyCode, scanCode, modifiers);
        }

        @Override
        public void write(String text) {
            String string = text.equals("\n") ? text : this.manualStrip(text);

            int i = Math.min(((TextFieldWidgetAccessor) this).getSelectionStart(), ((TextFieldWidgetAccessor) this).getSelectionEnd());
            int j = Math.max(((TextFieldWidgetAccessor) this).getSelectionStart(), ((TextFieldWidgetAccessor) this).getSelectionEnd());
            int k = ((TextFieldWidgetAccessor) this).getMaxLength() - this.getText().length() - (i - j);

            if (k > 0) {
                int l = string.length();
                if (k < l) {
                    string = string.substring(0, k);
                    l = k;
                }

                String newText = new StringBuilder(this.getText()).replace(i, j, string).toString();

                this.setText(newText);
                this.setSelectionStart(i + l);
                this.setSelectionEnd(((TextFieldWidgetAccessor) this).getSelectionStart());
            }
        }

        private String manualStrip(String text) {
            StringBuilder sb = new StringBuilder();
            for (char c : text.toCharArray()) {
                if (net.minecraft.util.StringHelper.isValidChar(c) || c == '\n') {
                    sb.append(c);
                }
            }
            return sb.toString();
        }

        @Override
        public void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
            if (!this.isVisible()) return;

            int color = this.isFocused() ? 0xFFFFFFFF : 0xFFA0A0A0;
            context.fill(this.getX() - 1, this.getY() - 1, this.getX() + this.width + 1, this.getY() + this.displayHeight + 1, color);
            context.fill(this.getX(), this.getY(), this.getX() + this.width, this.getY() + this.displayHeight, 0xFF000000);

            String text = this.getText();
            if (text.isEmpty() && !this.isFocused() && this.placeholder != null) {
                context.drawTextWithShadow(this.textRenderer, this.placeholder, this.getX() + 4, this.getY() + 4, 0x707070);
            } else {
                List<String> lines = wrapText(text, this.width - 8);
                int yOffset = 4;
                int maxLines = (this.displayHeight - 8) / (this.textRenderer.fontHeight + 2);

                for (int i = 0; i < Math.min(lines.size(), maxLines); i++) {
                    context.drawTextWithShadow(this.textRenderer, lines.get(i), this.getX() + 4, this.getY() + yOffset, 0xE0E0E0);
                    yOffset += this.textRenderer.fontHeight + 2;
                }

                if (this.isFocused() && (System.currentTimeMillis() / 500) % 2 == 0) {
                    renderCursor(context, text, lines);
                }
            }
        }

        private void renderCursor(DrawContext context, String fullText, List<String> wrappedLines) {
            int cursorPosition = this.getCursor();
            int currentChars = 0;
            int lineY = 4;

            for (String line : wrappedLines) {
                if (cursorPosition <= currentChars + line.length()) {
                    int relativeCursorX = cursorPosition - currentChars;
                    int cursorX = this.getX() + 4 + this.textRenderer.getWidth(line.substring(0, Math.min(relativeCursorX, line.length())));
                    context.fill(cursorX, this.getY() + lineY, cursorX + 1, this.getY() + lineY + this.textRenderer.fontHeight, 0xFFD0D0D0);
                    return;
                }
                currentChars += line.length() + (fullText.length() > currentChars + line.length() ? 1 : 0);
                lineY += this.textRenderer.fontHeight + 2;
            }

            if (!wrappedLines.isEmpty() && cursorPosition >= fullText.length()) {
                String lastLine = wrappedLines.get(wrappedLines.size() - 1);
                int cursorX = this.getX() + 4 + this.textRenderer.getWidth(lastLine);
                context.fill(cursorX, this.getY() + lineY - (this.textRenderer.fontHeight + 2), cursorX + 1, this.getY() + lineY - 2, 0xFFD0D0D0);
            }
        }

        private List<String> wrapText(String text, int maxWidth) {
            List<String> lines = new ArrayList<>();
            if (text.isEmpty()) {
                return lines;
            }

            String[] paragraphs = text.split("\n", -1);
            for (String paragraph : paragraphs) {
                if (paragraph.isEmpty()) {
                    lines.add("");
                    continue;
                }

                StringBuilder currentLine = new StringBuilder();
                String[] words = paragraph.split(" ");

                for (String word : words) {
                    String testLine = currentLine.isEmpty() ? word : currentLine + " " + word;
                    if (this.textRenderer.getWidth(testLine) <= maxWidth) {
                        if (!currentLine.isEmpty()) {
                            currentLine.append(" ");
                        }
                        currentLine.append(word);
                    } else {
                        if (!currentLine.isEmpty()) {
                            lines.add(currentLine.toString());
                            currentLine = new StringBuilder(word);
                        } else {
                            lines.add(word);
                        }
                    }
                }

                if (!currentLine.isEmpty()) {
                    lines.add(currentLine.toString());
                }
            }

            return lines;
        }
    }
}
