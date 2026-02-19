package net.questz.quest;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.advancement.AdvancementCriterion;
import net.minecraft.advancement.AdvancementDisplay;
import net.minecraft.advancement.AdvancementRewards;
import net.minecraft.advancement.PlacedAdvancement;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.questz.access.CriterionAccess;
import net.questz.access.RewardAccess;
import net.questz.init.ConfigInit;
import net.questz.mixin.client.TextFieldWidgetAccessor;
import net.questz.network.packet.QuestCreationPacket;
import net.questz.util.CriterionDataExtractor;
import org.jetbrains.annotations.Nullable;

import java.util.*;

@Environment(EnvType.CLIENT)
public class QuestEditorScreen extends Screen {

    @Nullable
    private final PlacedAdvancement placedAdvancement;

    private TextFieldWidget positionXField;
    private TextFieldWidget positionYField;
    private final int initialX;
    private final int initialY;

    private TextFieldWidget titleField;
    private MultilineTextFieldWidget descField;
    private TextFieldWidget iconField;
    private TextFieldWidget parentField;
    private TextFieldWidget commandsField;
    private MultilineTextFieldWidget itemsField;
    private TextFieldWidget textField;

    private ButtonWidget frameButton;
    private String currentFrame = "task";

    private final List<String> availableParents = new ArrayList<>();
    private int parentScrollOffset = 0;
    private static final int MAX_VISIBLE_PARENTS = 5;
    private boolean showParentList = false;
    private int parentListY = 0;
    private int parentListX = 0;
    private int parentListWidth = 0;

    private boolean showToast = false;
    private boolean announceChat = false;
    private boolean isHidden = false;

    private final List<CriteriaEntry> criteriaEntries = new ArrayList<>();
    private ButtonWidget addCriteriaButton;
    private int scrollOffset = 0;
    private static final int MAX_VISIBLE_CRITERIA = 3;

    private String loadedCommands = "";
    private String loadedItems = "";
    private String loadedText = "";

    private String savedTitle = "";
    private String savedDesc = "";
    private String savedIcon = "";
    private String savedParent = "";
    private String savedCommands = "";
    private String savedItems = "";
    private String savedText = "";

    public QuestEditorScreen(@Nullable QuestWidget questWidget, int clickX, int clickY) {
        super(questWidget != null && questWidget.getAdvancement().getAdvancement().name().isPresent() ? questWidget.getAdvancement().getAdvancement().name().get() : Text.translatable("gui.questz.newQuest"));
        this.placedAdvancement = questWidget != null ? questWidget.getAdvancement() : null;

        if (this.placedAdvancement != null && this.placedAdvancement.getAdvancement().display().isPresent()) {
            AdvancementDisplay display = this.placedAdvancement.getAdvancement().display().get();
            this.initialX = (int) display.getX();
            this.initialY = (int) display.getY();
        } else {
            this.initialX = clickX;
            this.initialY = clickY;
        }

        if (this.placedAdvancement != null) {
            loadExistingAdvancementData();
        }
    }

    public QuestEditorScreen(@Nullable QuestWidget questWidget) {
        this(questWidget, 0, 0);
    }

    private void loadExistingAdvancementData() {
        var advancement = this.placedAdvancement.getAdvancement();

        if (advancement.display().isPresent()) {
            var display = advancement.display().get();

            this.currentFrame = display.getFrame().asString();

            this.showToast = display.shouldShowToast();
            this.announceChat = display.shouldAnnounceToChat();
            this.isHidden = display.isHidden();

            savedTitle = display.getTitle().getString();
            savedDesc = display.getDescription().getString();
            savedIcon = Registries.ITEM.getId(display.getIcon().getItem()).toString();
        }

        if (this.placedAdvancement.getParent() != null) {
            savedParent = this.placedAdvancement.getParent().getAdvancementEntry().id().toString();
        }

        if (advancement.rewards() != null) {
            AdvancementRewards rewards = advancement.rewards();
            loadRewardsFromMixin(rewards);

            savedCommands = loadedCommands;
            savedItems = loadedItems;
            savedText = loadedText;
        }

        loadCriteriaFromAdvancement();
    }

    private void loadRewardsFromMixin(AdvancementRewards rewards) {
        try {
            RewardAccess rewardAccess = (RewardAccess) (Object) rewards;

            List<String> commands = rewardAccess.questz$getCommands();
            if (commands != null && !commands.isEmpty()) {
                loadedCommands = String.join(", ", commands);
            }

            Map<Identifier, Integer> items = rewardAccess.questz$getItems();
            if (items != null && !items.isEmpty()) {
                StringBuilder itemsBuilder = new StringBuilder();
                boolean first = true;
                for (Map.Entry<Identifier, Integer> entry : items.entrySet()) {
                    if (!first) itemsBuilder.append(", ");
                    itemsBuilder.append(entry.getKey().toString()).append(":").append(entry.getValue());
                    first = false;
                }
                loadedItems = itemsBuilder.toString();
            }

            String text = rewardAccess.questz$getText();
            if (text != null && !text.isEmpty()) {
                loadedText = text;
            }

        } catch (Exception e) {
            System.err.println("Error loading rewards from mixin: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void loadCriteriaFromAdvancement() {
        try {
            var advancement = this.placedAdvancement.getAdvancement();
            var criteria = advancement.criteria();

            for (Map.Entry<String, AdvancementCriterion<?>> entry : criteria.entrySet()) {
                CriteriaEntry criteriaEntry = new CriteriaEntry();
                criteriaEntry.name = entry.getKey();

                var criterion = entry.getValue();

                CriterionAccess criterionAccess = (CriterionAccess) (Object) criterion;
                Identifier triggerId = criterionAccess.questz$getTriggerId();
                criteriaEntry.trigger = triggerId.toString();

                JsonObject criterionJson = CriterionDataExtractor.toJson(criterion);
                Map<String, String> conditionData = CriterionDataExtractor.extractConditionData(criterionJson);

                criteriaEntry.fieldValues.putAll(conditionData);

                criteriaEntries.add(criteriaEntry);
            }


        } catch (Exception e) {
            System.err.println("Error loading criteria from advancement: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    protected void init() {
        if (this.titleField != null) {
            savedTitle = this.titleField.getText();
            savedDesc = this.descField.getText();
            savedIcon = this.iconField.getText();
            savedParent = this.parentField.getText();
            savedCommands = this.commandsField.getText();
            savedItems = this.itemsField.getText();
            savedText = this.textField.getText();
        }

        loadAvailableParents();

        int leftColumnWidth = 320;
        int rightColumnWidth = 320;
        int columnGap = 20;
        int leftX = 20;
        int rightX = leftX + leftColumnWidth + columnGap;
        int y = 30;
        int leftY = y;

        this.titleField = new TextFieldWidget(this.textRenderer, leftX, leftY, leftColumnWidth, 20, Text.translatable("gui.questz.title"));
        this.titleField.setPlaceholder(Text.translatable("gui.questz.example").formatted(Formatting.DARK_GRAY));
        this.titleField.setText(savedTitle);
        this.addSelectableChild(this.titleField);
        leftY += 32;

        this.descField = new MultilineTextFieldWidget(this.textRenderer, leftX, leftY, leftColumnWidth, 60);
        this.descField.setPlaceholder(Text.translatable("gui.questz.example").formatted(Formatting.DARK_GRAY));
        this.descField.setText(savedDesc);
        this.addSelectableChild(this.descField);
        leftY += 72;

        this.iconField = new TextFieldWidget(this.textRenderer, leftX, leftY, leftColumnWidth, 20, Text.translatable("gui.questz.icon"));
        this.iconField.setPlaceholder(Text.literal("minecraft:stone").formatted(Formatting.DARK_GRAY));
        this.iconField.setText(savedIcon);
        this.addSelectableChild(this.iconField);
        leftY += 32;

        this.parentField = new TextFieldWidget(this.textRenderer, leftX, leftY, leftColumnWidth, 20, Text.translatable("gui.questz.parent"));
        this.parentField.setText(savedParent);
        this.addSelectableChild(this.parentField);

        parentListX = leftX;
        parentListY = leftY + 25;
        parentListWidth = leftColumnWidth;

        leftY += 32;

        int posFieldWidth = (leftColumnWidth - 5) / 2;
        this.positionXField = new TextFieldWidget(this.textRenderer, leftX, leftY, posFieldWidth, 20, Text.literal("Position X"));
        this.positionXField.setPlaceholder(Text.literal("0.0").formatted(Formatting.DARK_GRAY));
        this.positionXField.setText(String.valueOf(this.initialX));
        this.addSelectableChild(this.positionXField);

        this.positionYField = new TextFieldWidget(this.textRenderer, leftX + posFieldWidth + 5, leftY, posFieldWidth, 20, Text.literal("Position Y"));
        this.positionYField.setPlaceholder(Text.literal("0.0").formatted(Formatting.DARK_GRAY));
        this.positionYField.setText(String.valueOf(this.initialY));
        this.addSelectableChild(this.positionYField);
        leftY += 32;

        this.commandsField = new TextFieldWidget(this.textRenderer, leftX, leftY, leftColumnWidth, 20, Text.literal("Commands"));
        this.commandsField.setPlaceholder(Text.literal("say Hello, give @s diamond").formatted(Formatting.DARK_GRAY));
        this.commandsField.setText(savedCommands);
        this.addSelectableChild(this.commandsField);
        leftY += 32;

        this.itemsField = new MultilineTextFieldWidget(this.textRenderer, leftX, leftY, leftColumnWidth, 40);
        this.itemsField.setPlaceholder(Text.literal("minecraft:diamond:5, minecraft:iron_ingot:10").formatted(Formatting.DARK_GRAY));
        this.itemsField.setText(savedItems);
        this.addSelectableChild(this.itemsField);
        leftY += 52;

        this.textField = new TextFieldWidget(this.textRenderer, leftX, leftY, leftColumnWidth, 20, Text.literal("Reward Text"));
        this.textField.setPlaceholder(Text.literal("Congratulations!").formatted(Formatting.DARK_GRAY));
        this.textField.setText(savedText);
        this.addSelectableChild(this.textField);
        leftY += 32;

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

        this.addCriteriaButton = ButtonWidget.builder(Text.translatable("gui.questz.addCriteria"), (button) -> {
            this.addCriteriaEntry();
        }).dimensions(rightX, y, rightColumnWidth, 20).build();
        this.addDrawableChild(this.addCriteriaButton);

        updateCriteriaWidgets();
    }

    private void loadAvailableParents() {
        availableParents.clear();

        if (this.client != null && this.client.player != null) {
            var advancementHandler = this.client.player.networkHandler.getAdvancementHandler();
            var manager = advancementHandler.getManager();

            for (PlacedAdvancement advancement : manager.getRoots()) {
                collectAdvancements(advancement, availableParents);
            }
            Collections.sort(availableParents);
        }
    }

    private void collectAdvancements(PlacedAdvancement advancement, List<String> list) {
        String id = advancement.getAdvancementEntry().id().toString();

        if (!ConfigInit.CONFIG.questAdvancementNamespaceIds.contains(advancement.getAdvancementEntry().id().getNamespace())) {
            return;
        }

        if (this.placedAdvancement == null || !id.equals(this.placedAdvancement.getAdvancementEntry().id().toString())) {
            list.add(id);
        }

        for (PlacedAdvancement child : advancement.getChildren()) {
            collectAdvancements(child, list);
        }
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

        this.addSelectableChild(this.titleField);
        this.addSelectableChild(this.descField);
        this.addSelectableChild(this.iconField);
        this.addSelectableChild(this.parentField);
        this.addSelectableChild(this.positionXField);
        this.addSelectableChild(this.positionYField);
        this.addSelectableChild(this.commandsField);
        this.addSelectableChild(this.itemsField);
        this.addSelectableChild(this.textField);
        this.addDrawableChild(this.frameButton);

        int toggleY = y + 32 + 72 + 32 + 32 + 32 + 32 + 52 + 32 + 25;
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

            int entryHeight = calculateEntryHeight(entry);
            if (criteriaY + entryHeight > maxCriteriaY) {
                break;
            }

            criteriaY = renderCriteriaEntry(entry, rightX, rightColumnWidth, criteriaY);
        }
    }

    private int calculateEntryHeight(CriteriaEntry entry) {
        TriggerConfig config = TriggerConfig.get(entry.trigger);
        int lines = 3;
        lines += config.fields.size();
        return lines * 25 + 10;
    }

    private int renderCriteriaEntry(CriteriaEntry entry, int rightX, int rightColumnWidth, int startY) {
        entry.nameField = null;
        entry.triggerButton = null;
        entry.removeButton = null;
        entry.dynamicFields.clear();

        int currentY = startY + 5;

        entry.nameField = new TextFieldWidget(this.textRenderer, rightX, currentY, 200, 20, Text.translatable("gui.questz.criteriaName"));
        entry.nameField.setPlaceholder(Text.literal("criteria_name").formatted(Formatting.DARK_GRAY));
        entry.nameField.setText(entry.name);
        entry.nameField.setChangedListener(text -> entry.name = text);
        this.addSelectableChild(entry.nameField);

        entry.removeButton = ButtonWidget.builder(Text.literal("X"), (button) -> {
            removeCriteriaEntry(entry);
        }).dimensions(rightX + 205, currentY, 20, 20).build();

        entry.triggerButton = ButtonWidget.builder(Text.literal(getTriggerDisplayName(entry.trigger)), (button) -> {
            openTriggerSelectionScreen(entry);
        }).dimensions(rightX + 230, currentY, 90, 20).build();

        currentY += 25;

        TriggerConfig config = TriggerConfig.get(entry.trigger);

        for (TriggerField field : config.fields) {
            TextFieldWidget fieldWidget = new TextFieldWidget(
                    this.textRenderer,
                    rightX + 10,
                    currentY,
                    rightColumnWidth - 20,
                    20,
                    Text.literal(field.name)
            );
            fieldWidget.setPlaceholder(Text.literal(field.placeholder).formatted(Formatting.DARK_GRAY));

            String currentValue = entry.fieldValues.getOrDefault(field.key, field.defaultValue);
            fieldWidget.setText(currentValue);

            final String fieldKey = field.key;
            fieldWidget.setChangedListener(text -> entry.fieldValues.put(fieldKey, text));

            this.addSelectableChild(fieldWidget);
            entry.dynamicFields.put(field.key, fieldWidget);

            currentY += 25;
        }

        currentY += 5;
        return currentY;
    }

    private void openTriggerSelectionScreen(CriteriaEntry entry) {
        this.client.setScreen(new TriggerSelectionScreen(this, entry));
    }

    private String getTriggerDisplayName(String trigger) {
        String shortName = trigger.replace("minecraft:", "");

        String[] parts = shortName.split("_");
        StringBuilder result = new StringBuilder();
        for (String part : parts) {
            if (!result.isEmpty()) result.append(" ");
            result.append(part.substring(0, 1).toUpperCase()).append(part.substring(1));
        }
        return result.toString();
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
        context.drawTextWithShadow(this.textRenderer, Text.translatable("gui.questz.position"), leftX, y + 158, 0xA0A0A0);
        context.drawTextWithShadow(this.textRenderer, Text.translatable("gui.questz.commands"), leftX, y + 190, 0xA0A0A0);
        context.drawTextWithShadow(this.textRenderer, Text.translatable("gui.questz.items"), leftX, y + 222, 0xA0A0A0);
        context.drawTextWithShadow(this.textRenderer, Text.translatable("gui.questz.text"), leftX, y + 274, 0xA0A0A0);

        this.titleField.render(context, mouseX, mouseY, delta);
        this.descField.render(context, mouseX, mouseY, delta);
        this.iconField.render(context, mouseX, mouseY, delta);
        this.parentField.render(context, mouseX, mouseY, delta);
        this.positionXField.render(context, mouseX, mouseY, delta);
        this.positionYField.render(context, mouseX, mouseY, delta);
        this.commandsField.render(context, mouseX, mouseY, delta);
        this.itemsField.render(context, mouseX, mouseY, delta);
        this.textField.render(context, mouseX, mouseY, delta);

        if (showParentList) {
            renderParentSelectionList(context, mouseX, mouseY);
        }

        context.drawTextWithShadow(this.textRenderer, Text.translatable("gui.questz.criteria"), rightX, y - 10, 0xFFFFFF);

        if (!criteriaEntries.isEmpty()) {
            int visibleCount = Math.min(criteriaEntries.size() - scrollOffset, MAX_VISIBLE_CRITERIA);
            String scrollHint = String.format("Showing %d-%d of %d",
                    scrollOffset + 1,
                    scrollOffset + visibleCount,
                    criteriaEntries.size());
            context.drawTextWithShadow(this.textRenderer, Text.literal(scrollHint), rightX + 130, y - 10, 0x808080);

            if (criteriaEntries.size() > MAX_VISIBLE_CRITERIA) {
                context.drawTextWithShadow(this.textRenderer, Text.translatable("gui.questz.scroll"), rightX + 132 + this.textRenderer.getWidth(scrollHint), y - 10, 0x606060);
            }
        }
        int criteriaY = y + 25;
        int maxCriteriaY = this.height - 20;

        for (int i = scrollOffset; i < criteriaEntries.size(); i++) {
            CriteriaEntry entry = criteriaEntries.get(i);

            int entryHeight = calculateEntryHeight(entry);
            if (criteriaY + entryHeight > maxCriteriaY) {
                break;
            }

            if (entry.nameField != null) {
                entry.nameField.render(context, mouseX, mouseY, delta);
            }

            if (entry.removeButton != null) {
                entry.removeButton.render(context, mouseX, mouseY, delta);
            }
            if (entry.triggerButton != null) {
                entry.triggerButton.render(context, mouseX, mouseY, delta);
            }

            for (TextFieldWidget field : entry.dynamicFields.values()) {
                if (field != null) {
                    field.render(context, mouseX, mouseY, delta);
                }
            }

            criteriaY += entryHeight;
        }
    }

    private void renderParentSelectionList(DrawContext context, int mouseX, int mouseY) {
        int listHeight = Math.min(MAX_VISIBLE_PARENTS, availableParents.size()) * 22 + 4;

        context.getMatrices().push();
        context.getMatrices().translate(0, 0, 100);
        context.fill(parentListX - 1, parentListY - 1, parentListX + parentListWidth + 1, parentListY + listHeight + 1, 0xFFFFFFFF);
        context.fill(parentListX, parentListY, parentListX + parentListWidth, parentListY + listHeight, 0xFF000000);

        int currentY = parentListY + 2;
        int endIndex = Math.min(parentScrollOffset + MAX_VISIBLE_PARENTS, availableParents.size());

        for (int i = parentScrollOffset; i < endIndex; i++) {
            String parent = availableParents.get(i);
            boolean isHovered = mouseX >= parentListX && mouseX <= parentListX + parentListWidth &&
                    mouseY >= currentY && mouseY <= currentY + 20;

            if (isHovered) {
                context.fill(parentListX + 1, currentY, parentListX + parentListWidth - 1, currentY + 20, 0xFF404040);
            }

            String displayName = parent;
            int maxWidth = parentListWidth - 8;
            if (this.textRenderer.getWidth(displayName) > maxWidth) {
                while (this.textRenderer.getWidth(displayName + "...") > maxWidth && displayName.length() > 0) {
                    displayName = displayName.substring(0, displayName.length() - 1);
                }
                displayName += "...";
            }

            context.drawTextWithShadow(this.textRenderer, Text.literal(displayName), parentListX + 4, currentY + 6, 0xE0E0E0);
            currentY += 22;
        }

        if (availableParents.size() > MAX_VISIBLE_PARENTS) {
            String scrollInfo = String.format("%d-%d of %d",
                    parentScrollOffset + 1,
                    endIndex,
                    availableParents.size());
            context.drawTextWithShadow(this.textRenderer, Text.literal(scrollInfo),
                    parentListX + parentListWidth - this.textRenderer.getWidth(scrollInfo) - 4,
                    parentListY + listHeight + 4, 0x808080);
        }
        context.getMatrices().pop();
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
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (parentField.isMouseOver(mouseX, mouseY)) {
            showParentList = !showParentList;
            parentScrollOffset = 0;
            return true;
        }

        if (showParentList && button == 0) {
            int listHeight = Math.min(MAX_VISIBLE_PARENTS, availableParents.size()) * 22 + 4;
            if (mouseX >= parentListX && mouseX <= parentListX + parentListWidth &&
                    mouseY >= parentListY && mouseY <= parentListY + listHeight) {

                int relativeY = (int) (mouseY - parentListY - 2);
                int clickedIndex = relativeY / 22;
                int actualIndex = parentScrollOffset + clickedIndex;

                if (actualIndex >= 0 && actualIndex < availableParents.size()) {
                    parentField.setText(availableParents.get(actualIndex));
                    showParentList = false;
                    return true;
                }
            } else {
                showParentList = false;
            }
        }

        int rightColumnWidth = 320;
        int columnGap = 20;
        int leftX = 20;
        int rightX = leftX + 320 + columnGap;
        int y = 30;
        int criteriaY = y + 25;
        int maxCriteriaY = this.height - 20;

        for (int i = scrollOffset; i < criteriaEntries.size(); i++) {
            CriteriaEntry entry = criteriaEntries.get(i);

            int entryHeight = calculateEntryHeight(entry);
            if (criteriaY + entryHeight > maxCriteriaY) {
                break;
            }

            if (entry.removeButton != null && entry.removeButton.isMouseOver(mouseX, mouseY)) {
                entry.removeButton.mouseClicked(mouseX, mouseY, button);
                return true;
            }

            if (entry.triggerButton != null && entry.triggerButton.isMouseOver(mouseX, mouseY)) {
                entry.triggerButton.mouseClicked(mouseX, mouseY, button);
                return true;
            }

            criteriaY += entryHeight;
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (showParentList) {
            int listHeight = Math.min(MAX_VISIBLE_PARENTS, availableParents.size()) * 22 + 4;
            if (mouseX >= parentListX && mouseX <= parentListX + parentListWidth &&
                    mouseY >= parentListY && mouseY <= parentListY + listHeight) {

                if (availableParents.size() > MAX_VISIBLE_PARENTS) {
                    parentScrollOffset = Math.max(0, Math.min(parentScrollOffset - (int) verticalAmount,
                            availableParents.size() - MAX_VISIBLE_PARENTS));
                    return true;
                }
            }
        }

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

        display.put("frame", this.currentFrame);
        display.put("show_toast", this.showToast);
        display.put("announce_to_chat", this.announceChat);
        display.put("hidden", this.isHidden);

        try {
            float posX = Float.parseFloat(this.positionXField.getText());
            float posY = Float.parseFloat(this.positionYField.getText());
            display.put("manual_x", posX);
            display.put("manual_y", posY);
        } catch (NumberFormatException e) {
            display.put("manual_x", this.initialX);
            display.put("manual_y", this.initialY);
        }

        advancement.put("display", display);

        Map<String, Object> criteria = new LinkedHashMap<>();
        List<List<String>> requirements = new ArrayList<>();

        for (CriteriaEntry entry : criteriaEntries) {
            if (entry.name.isEmpty()) continue;

            Map<String, Object> criterion = new LinkedHashMap<>();
            criterion.put("trigger", entry.trigger);

            Map<String, Object> conditions = new LinkedHashMap<>();

            TriggerConfig config = TriggerConfig.get(entry.trigger);
            for (TriggerField field : config.fields) {
                String value = entry.fieldValues.get(field.key);
                if (value != null && !value.isEmpty()) {
                    Object parsedValue = parseFieldValue(value, field.type);

                    if (field.jsonPath != null && !field.jsonPath.isEmpty()) {
                        setNestedValue(conditions, field.jsonPath, parsedValue);
                    } else {
                        conditions.put(field.key, parsedValue);
                    }
                }
            }

            criterion.put("conditions", conditions);
            criteria.put(entry.name, criterion);

            requirements.add(Collections.singletonList(entry.name));
        }

        advancement.put("criteria", criteria);
        advancement.put("requirements", requirements);

        Map<String, Object> rewards = new LinkedHashMap<>();

        // Parsing commands (format: command1, command2)
        if (!this.commandsField.getText().isEmpty()) {
            String[] commandArray = this.commandsField.getText().split(",");
            List<String> commandList = new ArrayList<>();
            for (String cmd : commandArray) {
                commandList.add(cmd.trim());
            }
            rewards.put("commands", commandList);
        }

        // Parsing items (format: minecraft:diamond:5, minecraft:iron_ingot:10)
        if (!this.itemsField.getText().isEmpty()) {
            Map<String, Integer> itemsMap = new LinkedHashMap<>();
            String[] itemEntries = this.itemsField.getText().split(",");
            for (String itemEntry : itemEntries) {
                String[] parts = itemEntry.trim().split(":");
                if (parts.length >= 2) {
                    String itemId = parts[0] + ":" + parts[1];
                    int amount = 1;
                    if (parts.length >= 3) {
                        try {
                            amount = Integer.parseInt(parts[2]);
                        } catch (NumberFormatException ignored) {
                        }
                    }
                    itemsMap.put(itemId, amount);
                }
            }
            if (!itemsMap.isEmpty()) {
                rewards.put("items", itemsMap);
            }
        }

        if (!this.textField.getText().isEmpty()) {
            rewards.put("text", this.textField.getText());
        }

        if (!rewards.isEmpty()) {
            advancement.put("rewards", rewards);
        }

        String fileName = this.titleField.getText().toLowerCase().replace(" ", "_");
        if (fileName.isEmpty()) fileName = "new_quest";

        String jsonString = new GsonBuilder().setPrettyPrinting().create().toJson(advancement);

        ClientPlayNetworking.send(new QuestCreationPacket(fileName, jsonString));
        this.close();
    }

    private void setNestedValue(Map<String, Object> map, String path, Object value) {
        String[] parts = path.split("\\.");
        Map<String, Object> current = map;

        for (int i = 0; i < parts.length - 1; i++) {
            String part = parts[i];
            current = (Map<String, Object>) current.computeIfAbsent(part, k -> new LinkedHashMap<String, Object>());
        }

        current.put(parts[parts.length - 1], value);
    }

    private Object parseFieldValue(String value, String type) {
        try {
            switch (type) {
                case "int":
                    return Integer.parseInt(value);
                case "double":
                    return Double.parseDouble(value);
                case "boolean":
                    return Boolean.parseBoolean(value);
                case "string_array":
                    return Arrays.asList(value.split(","));
                case "item_array":
                    List<Map<String, Object>> items = new ArrayList<>();
                    for (String part : value.split(",")) {
                        Map<String, Object> itemEntry = new LinkedHashMap<>();
                        String[] subParts = part.trim().split(";");

                        itemEntry.put("items", Collections.singletonList(subParts[0]));

                        if (subParts.length > 1) {
                            Map<String, Object> countMap = new LinkedHashMap<>();
                            countMap.put("min", Integer.parseInt(subParts[1]));
                            itemEntry.put("count", countMap);
                        }
                        items.add(itemEntry);
                    }
                    return items;
                default:
                    return value;
            }
        } catch (Exception e) {
            return value;
        }
    }

    private static class CriteriaEntry {
        String name = "";
        String trigger = "minecraft:inventory_changed";
        Map<String, String> fieldValues = new HashMap<>();

        TextFieldWidget nameField;
        ButtonWidget triggerButton;
        Map<String, TextFieldWidget> dynamicFields = new HashMap<>();
        ButtonWidget removeButton;
    }

    private static class TriggerField {
        String key;
        String name;
        String placeholder;
        String defaultValue;
        String type;
        String jsonPath;

        TriggerField(String key, String name, String placeholder, String defaultValue, String type) {
            this(key, name, placeholder, defaultValue, type, null);
        }

        TriggerField(String key, String name, String placeholder, String defaultValue, String type, String jsonPath) {
            this.key = key;
            this.name = name;
            this.placeholder = placeholder;
            this.defaultValue = defaultValue;
            this.type = type;
            this.jsonPath = jsonPath;
        }
    }

    private static class TriggerConfig {
        String trigger;
        List<TriggerField> fields;

        TriggerConfig(String trigger) {
            this.trigger = trigger;
            this.fields = new ArrayList<>();
        }

        void addField(TriggerField field) {
            this.fields.add(field);
        }

        private static final Map<String, TriggerConfig> CONFIGS = new HashMap<>();

        static {
            // QuestZ custom triggers
            TriggerConfig craftItemCount = new TriggerConfig("questz:craft_item_count");
            craftItemCount.addField(new TriggerField("object", "Item", "minecraft:stone", "", "string"));
            craftItemCount.addField(new TriggerField("count", "Count", "1", "1", "int"));
            CONFIGS.put("questz:craft_item_count", craftItemCount);

            TriggerConfig placedBlockCount = new TriggerConfig("questz:placed_block_count");
            placedBlockCount.addField(new TriggerField("object", "Block", "minecraft:stone", "", "string"));
            placedBlockCount.addField(new TriggerField("count", "Count", "1", "1", "int"));
            CONFIGS.put("questz:placed_block_count", placedBlockCount);

            TriggerConfig minedBlockCount = new TriggerConfig("questz:mined_block_count");
            minedBlockCount.addField(new TriggerField("object", "Block", "minecraft:stone", "", "string"));
            minedBlockCount.addField(new TriggerField("count", "Count", "1", "1", "int"));
            CONFIGS.put("questz:mined_block_count", minedBlockCount);

            TriggerConfig killedMobCount = new TriggerConfig("questz:killed_mob_count");
            killedMobCount.addField(new TriggerField("object", "Entity", "minecraft:zombie", "", "string"));
            killedMobCount.addField(new TriggerField("count", "Count", "1", "1", "int"));
            CONFIGS.put("questz:killed_mob_count", killedMobCount);

            // Inventory triggers
            TriggerConfig invChanged = new TriggerConfig("minecraft:inventory_changed");
            invChanged.addField(new TriggerField("items", "Items", "minecraft:diamond", "", "item_array", "items"));
            CONFIGS.put("minecraft:inventory_changed", invChanged);

            // Location trigger
            TriggerConfig location = new TriggerConfig("minecraft:location");
            location.addField(new TriggerField("biome", "Biome", "minecraft:plains", "", "string"));
            location.addField(new TriggerField("dimension", "Dimension", "minecraft:overworld", "", "string"));
            CONFIGS.put("minecraft:location", location);

            // Kill entity trigger
            TriggerConfig killEntity = new TriggerConfig("minecraft:player_killed_entity");
            killEntity.addField(new TriggerField("entity", "Entity Type", "minecraft:zombie", "", "string", "entity.type"));
            CONFIGS.put("minecraft:player_killed_entity", killEntity);

            // Item used on block
            TriggerConfig itemUsed = new TriggerConfig("minecraft:item_used_on_block");
            itemUsed.addField(new TriggerField("location_block", "Block", "minecraft:chest", "", "string", "location.block.blocks"));
            itemUsed.addField(new TriggerField("item", "Item", "minecraft:diamond", "", "string", "item.items"));
            CONFIGS.put("minecraft:item_used_on_block", itemUsed);

            // Bred animals
            TriggerConfig bredAnimals = new TriggerConfig("minecraft:bred_animals");
            bredAnimals.addField(new TriggerField("child", "Child Entity", "minecraft:cow", "", "string", "child.type"));
            bredAnimals.addField(new TriggerField("parent", "Parent Entity", "minecraft:cow", "", "string", "parent.type"));
            CONFIGS.put("minecraft:bred_animals", bredAnimals);

            // Brewed potion
            TriggerConfig brewedPotion = new TriggerConfig("minecraft:brewed_potion");
            brewedPotion.addField(new TriggerField("potion", "Potion", "minecraft:water", "", "string"));
            CONFIGS.put("minecraft:brewed_potion", brewedPotion);

            // Changed dimension
            TriggerConfig changedDimension = new TriggerConfig("minecraft:changed_dimension");
            changedDimension.addField(new TriggerField("from", "From Dimension", "minecraft:overworld", "", "string"));
            changedDimension.addField(new TriggerField("to", "To Dimension", "minecraft:the_nether", "", "string"));
            CONFIGS.put("minecraft:changed_dimension", changedDimension);

            // Channeled lightning
            TriggerConfig channeledLightning = new TriggerConfig("minecraft:channeled_lightning");
            channeledLightning.addField(new TriggerField("victims", "Victim Count", "1", "1", "int"));
            CONFIGS.put("minecraft:channeled_lightning", channeledLightning);

            // Construct beacon
            TriggerConfig constructBeacon = new TriggerConfig("minecraft:construct_beacon");
            constructBeacon.addField(new TriggerField("level", "Beacon Level", "4", "1", "int"));
            CONFIGS.put("minecraft:construct_beacon", constructBeacon);

            // Consume item
            TriggerConfig consumeItem = new TriggerConfig("minecraft:consume_item");
            consumeItem.addField(new TriggerField("item", "Item", "minecraft:apple", "", "string", "item.items"));
            CONFIGS.put("minecraft:consume_item", consumeItem);

            // Cured zombie villager
            TriggerConfig curedZombieVillager = new TriggerConfig("minecraft:cured_zombie_villager");
            curedZombieVillager.addField(new TriggerField("villager", "Villager Profession", "minecraft:farmer", "", "string", "villager.type"));
            CONFIGS.put("minecraft:cured_zombie_villager", curedZombieVillager);

            // Effects changed
            TriggerConfig effectsChanged = new TriggerConfig("minecraft:effects_changed");
            effectsChanged.addField(new TriggerField("effect", "Effect", "minecraft:speed", "", "string"));
            CONFIGS.put("minecraft:effects_changed", effectsChanged);

            // Enchanted item
            TriggerConfig enchantedItem = new TriggerConfig("minecraft:enchanted_item");
            enchantedItem.addField(new TriggerField("item", "Item", "minecraft:diamond_sword", "", "string", "item.items"));
            enchantedItem.addField(new TriggerField("levels", "Levels Used", "30", "1", "int"));
            CONFIGS.put("minecraft:enchanted_item", enchantedItem);

            // Enter block
            TriggerConfig enterBlock = new TriggerConfig("minecraft:enter_block");
            enterBlock.addField(new TriggerField("block", "Block", "minecraft:bubble_column", "", "string"));
            CONFIGS.put("minecraft:enter_block", enterBlock);

            // Entity hurt player
            TriggerConfig entityHurtPlayer = new TriggerConfig("minecraft:entity_hurt_player");
            entityHurtPlayer.addField(new TriggerField("source_entity", "Source Entity", "minecraft:zombie", "", "string", "damage.source_entity.type"));
            CONFIGS.put("minecraft:entity_hurt_player", entityHurtPlayer);

            // Filled bucket
            TriggerConfig filledBucket = new TriggerConfig("minecraft:filled_bucket");
            filledBucket.addField(new TriggerField("item", "Bucket Item", "minecraft:water_bucket", "", "string", "item.items"));
            CONFIGS.put("minecraft:filled_bucket", filledBucket);

            // Fishing rod hooked
            TriggerConfig fishingRodHooked = new TriggerConfig("minecraft:fishing_rod_hooked");
            fishingRodHooked.addField(new TriggerField("entity", "Hooked Entity", "minecraft:fish", "", "string", "entity.type"));
            CONFIGS.put("minecraft:fishing_rod_hooked", fishingRodHooked);

            // Hero of the village
            TriggerConfig heroOfVillage = new TriggerConfig("minecraft:hero_of_the_village");
            CONFIGS.put("minecraft:hero_of_the_village", heroOfVillage);

            // Impossible
            TriggerConfig impossible = new TriggerConfig("minecraft:impossible");
            CONFIGS.put("minecraft:impossible", impossible);

            // Item durability changed
            TriggerConfig itemDurabilityChanged = new TriggerConfig("minecraft:item_durability_changed");
            itemDurabilityChanged.addField(new TriggerField("item", "Item", "minecraft:diamond_pickaxe", "", "string", "item.items"));
            itemDurabilityChanged.addField(new TriggerField("delta", "Durability Delta", "1", "1", "int"));
            CONFIGS.put("minecraft:item_durability_changed", itemDurabilityChanged);

            // Killed by crossbow
            TriggerConfig killedByCrossbow = new TriggerConfig("minecraft:killed_by_crossbow");
            killedByCrossbow.addField(new TriggerField("unique_entity_types", "Unique Types", "3", "1", "int"));
            CONFIGS.put("minecraft:killed_by_crossbow", killedByCrossbow);

            // Levitation
            TriggerConfig levitation = new TriggerConfig("minecraft:levitation");
            levitation.addField(new TriggerField("distance", "Distance", "50", "1", "int"));
            CONFIGS.put("minecraft:levitation", levitation);

            // Lightning strike
            TriggerConfig lightningStrike = new TriggerConfig("minecraft:lightning_strike");
            lightningStrike.addField(new TriggerField("lightning", "Lightning Entity", "minecraft:lightning_bolt", "", "string", "lightning.type"));
            CONFIGS.put("minecraft:lightning_strike", lightningStrike);

            // Nether travel
            TriggerConfig netherTravel = new TriggerConfig("minecraft:nether_travel");
            netherTravel.addField(new TriggerField("distance", "Distance", "7000", "1", "int"));
            CONFIGS.put("minecraft:nether_travel", netherTravel);

            // Placed block
            TriggerConfig placedBlock = new TriggerConfig("minecraft:placed_block");
            placedBlock.addField(new TriggerField("block", "Block", "minecraft:stone", "", "string"));
            CONFIGS.put("minecraft:placed_block", placedBlock);

            // Player generates container loot
            TriggerConfig playerGeneratesLoot = new TriggerConfig("minecraft:player_generates_container_loot");
            playerGeneratesLoot.addField(new TriggerField("loot_table", "Loot Table", "minecraft:chests/simple_dungeon", "", "string"));
            CONFIGS.put("minecraft:player_generates_container_loot", playerGeneratesLoot);

            // Player hurt entity
            TriggerConfig playerHurtEntity = new TriggerConfig("minecraft:player_hurt_entity");
            playerHurtEntity.addField(new TriggerField("entity", "Entity", "minecraft:zombie", "", "string", "entity.type"));
            CONFIGS.put("minecraft:player_hurt_entity", playerHurtEntity);

            // Player interacted with entity
            TriggerConfig playerInteractedEntity = new TriggerConfig("minecraft:player_interacted_with_entity");
            playerInteractedEntity.addField(new TriggerField("entity", "Entity", "minecraft:villager", "", "string", "entity.type"));
            playerInteractedEntity.addField(new TriggerField("item", "Item Used", "minecraft:emerald", "", "string", "item.items"));
            CONFIGS.put("minecraft:player_interacted_with_entity", playerInteractedEntity);

            // Recipe unlocked
            TriggerConfig recipeUnlocked = new TriggerConfig("minecraft:recipe_unlocked");
            recipeUnlocked.addField(new TriggerField("recipe", "Recipe", "minecraft:diamond_sword", "", "string"));
            CONFIGS.put("minecraft:recipe_unlocked", recipeUnlocked);

            // Shot crossbow
            TriggerConfig shotCrossbow = new TriggerConfig("minecraft:shot_crossbow");
            shotCrossbow.addField(new TriggerField("item", "Crossbow Item", "minecraft:crossbow", "", "string", "item.items"));
            CONFIGS.put("minecraft:shot_crossbow", shotCrossbow);

            // Slept in bed
            TriggerConfig sleptInBed = new TriggerConfig("minecraft:slept_in_bed");
            CONFIGS.put("minecraft:slept_in_bed", sleptInBed);

            // Slide down block
            TriggerConfig slideDownBlock = new TriggerConfig("minecraft:slide_down_block");
            slideDownBlock.addField(new TriggerField("block", "Block", "minecraft:honey_block", "", "string"));
            CONFIGS.put("minecraft:slide_down_block", slideDownBlock);

            // Started riding
            TriggerConfig startedRiding = new TriggerConfig("minecraft:started_riding");
            CONFIGS.put("minecraft:started_riding", startedRiding);

            // Summoned entity
            TriggerConfig summonedEntity = new TriggerConfig("minecraft:summoned_entity");
            summonedEntity.addField(new TriggerField("entity", "Entity", "minecraft:iron_golem", "", "string", "entity.type"));
            CONFIGS.put("minecraft:summoned_entity", summonedEntity);

            // Tame animal
            TriggerConfig tameAnimal = new TriggerConfig("minecraft:tame_animal");
            tameAnimal.addField(new TriggerField("entity", "Entity", "minecraft:wolf", "", "string", "entity.type"));
            CONFIGS.put("minecraft:tame_animal", tameAnimal);

            // Target hit
            TriggerConfig targetHit = new TriggerConfig("minecraft:target_hit");
            targetHit.addField(new TriggerField("signal_strength", "Signal Strength", "15", "1", "int"));
            CONFIGS.put("minecraft:target_hit", targetHit);

            // Thrown item picked up by entity
            TriggerConfig thrownItemPickedUp = new TriggerConfig("minecraft:thrown_item_picked_up_by_entity");
            thrownItemPickedUp.addField(new TriggerField("item", "Item", "minecraft:diamond", "", "string", "item.items"));
            thrownItemPickedUp.addField(new TriggerField("entity", "Entity", "minecraft:villager", "", "string", "entity.type"));
            CONFIGS.put("minecraft:thrown_item_picked_up_by_entity", thrownItemPickedUp);

            // Tick
            TriggerConfig tick = new TriggerConfig("minecraft:tick");
            CONFIGS.put("minecraft:tick", tick);

            // Used ender eye
            TriggerConfig usedEnderEye = new TriggerConfig("minecraft:used_ender_eye");
            usedEnderEye.addField(new TriggerField("distance", "Distance to Stronghold", "12", "1", "double"));
            CONFIGS.put("minecraft:used_ender_eye", usedEnderEye);

            // Used totem
            TriggerConfig usedTotem = new TriggerConfig("minecraft:used_totem");
            usedTotem.addField(new TriggerField("item", "Totem Item", "minecraft:totem_of_undying", "", "string", "item.items"));
            CONFIGS.put("minecraft:used_totem", usedTotem);

            // Villager trade
            TriggerConfig villagerTrade = new TriggerConfig("minecraft:villager_trade");
            villagerTrade.addField(new TriggerField("villager", "Villager Type", "minecraft:farmer", "", "string", "villager.type"));
            villagerTrade.addField(new TriggerField("item", "Traded Item", "minecraft:wheat", "", "string", "item.items"));
            CONFIGS.put("minecraft:villager_trade", villagerTrade);

            // Voluntary exile
            TriggerConfig voluntaryExile = new TriggerConfig("minecraft:voluntary_exile");
            CONFIGS.put("minecraft:voluntary_exile", voluntaryExile);
        }

        static TriggerConfig get(String trigger) {
            return CONFIGS.getOrDefault(trigger, new TriggerConfig(trigger));
        }

        static List<String> getAllTriggers() {
            List<String> triggers = new ArrayList<>(CONFIGS.keySet());
            Collections.sort(triggers);
            return triggers;
        }
    }

    private static class TriggerSelectionScreen extends Screen {
        private final Screen parent;
        private final CriteriaEntry entry;
        private final List<String> allTriggers;
        private int scrollOffset = 0;
        private static final int TRIGGERS_PER_PAGE = 15;

        protected TriggerSelectionScreen(Screen parent, CriteriaEntry entry) {
            super(Text.literal("Select Trigger"));
            this.parent = parent;
            this.entry = entry;
            this.allTriggers = TriggerConfig.getAllTriggers();
        }

        @Override
        protected void init() {
            int startY = 40;
            int buttonWidth = 300;
            int buttonHeight = 20;
            int centerX = this.width / 2 - buttonWidth / 2;

            int maxVisible = Math.min(TRIGGERS_PER_PAGE, allTriggers.size() - scrollOffset);

            for (int i = 0; i < maxVisible; i++) {
                int index = scrollOffset + i;
                String trigger = allTriggers.get(index);
                String displayName = trigger.replace("minecraft:", "");

                this.addDrawableChild(ButtonWidget.builder(Text.literal(displayName), (button) -> {
                    entry.trigger = trigger;
                    entry.fieldValues.clear();
                    this.client.setScreen(parent);
                }).dimensions(centerX, startY + i * (buttonHeight + 2), buttonWidth, buttonHeight).build());
            }

            if (scrollOffset > 0) {
                this.addDrawableChild(ButtonWidget.builder(Text.literal("↑ Previous"), (button) -> {
                    scrollOffset = Math.max(0, scrollOffset - TRIGGERS_PER_PAGE);
                    this.clearAndInit();
                }).dimensions(centerX, startY + maxVisible * (buttonHeight + 2) + 10, 145, 20).build());
            }

            if (scrollOffset + TRIGGERS_PER_PAGE < allTriggers.size()) {
                this.addDrawableChild(ButtonWidget.builder(Text.literal("Next ↓"), (button) -> {
                    scrollOffset = Math.min(allTriggers.size() - TRIGGERS_PER_PAGE, scrollOffset + TRIGGERS_PER_PAGE);
                    this.clearAndInit();
                }).dimensions(centerX + 155, startY + maxVisible * (buttonHeight + 2) + 10, 145, 20).build());
            }

            this.addDrawableChild(ButtonWidget.builder(Text.literal("Back"), (button) -> {
                this.client.setScreen(parent);
            }).dimensions(centerX, this.height - 30, buttonWidth, 20).build());
        }

        @Override
        public void render(DrawContext context, int mouseX, int mouseY, float delta) {
            super.render(context, mouseX, mouseY, delta);
            context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 20, 0xFFFFFF);

            String pageInfo = String.format("Page %d of %d",
                    (scrollOffset / TRIGGERS_PER_PAGE) + 1,
                    (allTriggers.size() + TRIGGERS_PER_PAGE - 1) / TRIGGERS_PER_PAGE);
            context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(pageInfo), this.width / 2, this.height - 50, 0x808080);
        }

        @Override
        public void close() {
            this.client.setScreen(parent);
        }

        @Override
        public boolean shouldPause() {
            return false;
        }

        @Override
        public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
            if (allTriggers.size() > TRIGGERS_PER_PAGE) {
                int oldOffset = scrollOffset;
                scrollOffset = Math.max(0, Math.min(scrollOffset - (int) verticalAmount * 3, allTriggers.size() - TRIGGERS_PER_PAGE));
                if (scrollOffset != oldOffset) {
                    this.clearAndInit();
                    return true;
                }
            }
            return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
        }
    }

    private static class MultilineTextFieldWidget extends TextFieldWidget {
        private final int displayHeight;
        private Text placeholder;
        private final TextRenderer textRenderer;
        private List<String> cachedWrappedLines = new ArrayList<>();

        public MultilineTextFieldWidget(TextRenderer textRenderer, int x, int y, int width, int height) {
            super(textRenderer, x, y, width, 20, Text.empty());
            this.displayHeight = height;
            this.textRenderer = textRenderer;
            this.setMaxLength(2048);
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
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (button == 0 && this.isVisible()) {
                boolean isWithinBounds = mouseX >= this.getX() && mouseX < this.getX() + this.width &&
                        mouseY >= this.getY() && mouseY < this.getY() + this.displayHeight;

                if (isWithinBounds) {
                    this.setFocused(true);

                    int relativeX = (int) (mouseX - this.getX() - 4);
                    int relativeY = (int) (mouseY - this.getY() - 4);

                    int cursorPos = getCursorPositionFromMouse(relativeX, relativeY);
                    this.setCursor(cursorPos, false);
                    this.setSelectionStart(cursorPos);
                    this.setSelectionEnd(cursorPos);

                    return true;
                } else {
                    this.setFocused(false);
                }
            }
            return false;
        }

        private int getCursorPositionFromMouse(int mouseX, int mouseY) {
            String text = this.getText();
            if (text.isEmpty()) {
                return 0;
            }

            cachedWrappedLines = wrapText(text, this.width - 8);

            int lineHeight = this.textRenderer.fontHeight + 2;
            int clickedLine = Math.max(0, Math.min(mouseY / lineHeight, cachedWrappedLines.size() - 1));

            if (clickedLine >= cachedWrappedLines.size()) {
                return text.length();
            }

            int currentChars = 0;
            for (int i = 0; i < clickedLine; i++) {
                currentChars += cachedWrappedLines.get(i).length();
                if (currentChars < text.length() && (text.charAt(currentChars) == '\n' || i < clickedLine - 1)) {
                    currentChars++;
                }
            }

            String clickedLineText = cachedWrappedLines.get(clickedLine);

            int bestDistance = Integer.MAX_VALUE;
            int bestPosition = 0;

            for (int i = 0; i <= clickedLineText.length(); i++) {
                String substring = clickedLineText.substring(0, i);
                int textWidth = this.textRenderer.getWidth(substring);
                int distance = Math.abs(textWidth - mouseX);

                if (distance < bestDistance) {
                    bestDistance = distance;
                    bestPosition = i;
                }
            }

            return Math.min(currentChars + bestPosition, text.length());
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
