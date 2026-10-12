package net.questz.client.screen;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ConfirmScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.questz.client.ClientQuestData;
import net.questz.client.ClientTeam;
import net.questz.progress.TeamManager;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Environment(EnvType.CLIENT)
public class TeamScreen extends Screen {

    private static final int ROW_HEIGHT = 12;
    private static final int GROUP_HEIGHT = 52;

    private final Screen parent;
    private int revision = -1;
    private String nameValue = "";
    private String inviteValue = "";
    private final List<UUID> memberOrder = new ArrayList<>();
    private int panelX;
    private int panelY;
    private int panelWidth;
    private int panelHeight;
    private final List<Text> labels = new ArrayList<>();
    private final List<int[]> labelPositions = new ArrayList<>();

    public TeamScreen(Screen parent) {
        super(Text.translatable("gui.questz.team"));
        this.parent = parent;
    }

    private int columnWidth() {
        return (this.panelWidth - 14 - 6) / 2;
    }

    private int insetX() {
        return this.panelX + 7;
    }

    private int insetY() {
        return this.panelY + 22;
    }

    private int insetHeight() {
        return this.panelHeight - 22 - 30;
    }

    private int rightX() {
        return this.insetX() + this.columnWidth() + 6;
    }

    private int membersTop() {
        return this.insetY() + 16;
    }

    private int invitesTop(ClientTeam team) {
        return this.membersTop() + team.getMembers().size() * ROW_HEIGHT + 18;
    }

    @Override
    protected void init() {
        if (this.parent.width != this.width || this.parent.height != this.height) {
            this.parent.init(this.client, this.width, this.height);
        }
        this.panelWidth = Math.min(this.width - 20, 300);
        this.panelHeight = Math.min(this.height - 12, 206);
        this.panelX = (this.width - this.panelWidth) / 2;
        this.panelY = (this.height - this.panelHeight) / 2;
        this.revision = ClientQuestData.getRevision();
        this.labels.clear();
        this.labelPositions.clear();

        ClientTeam team = ClientQuestData.getTeam();
        int right = this.rightX();
        int column = this.columnWidth();
        int y = this.insetY();

        if (team != null && this.client != null && this.client.player != null) {
            boolean owner = team.getOwner().equals(this.client.player.getUuid());
            if (!team.isParty()) {
                this.label("gui.questz.team.label.create", right, y);
                TextFieldWidget nameField = this.field(right, y + 10, column, "gui.questz.team.name", this.nameValue);
                nameField.setChangedListener(value -> this.nameValue = value);
                this.addDrawableChild(ButtonWidget.builder(Text.translatable("gui.questz.team.create"), button -> this.run("create " + this.nameValue.trim()))
                        .dimensions(right, y + 30, column, 18).build());
            } else {
                this.label("gui.questz.team.label.invite", right, y);
                TextFieldWidget inviteField = this.field(right, y + 10, column, "gui.questz.team.player_name", this.inviteValue);
                inviteField.setChangedListener(value -> this.inviteValue = value);
                ButtonWidget invite = this.addDrawableChild(ButtonWidget.builder(Text.translatable("gui.questz.team.invite"), button -> this.run("invite " + this.inviteValue.trim()))
                        .dimensions(right, y + 30, column, 18).build());
                invite.active = owner;
                y += GROUP_HEIGHT;
                if (owner) {
                    this.label("gui.questz.team.label.rename", right, y);
                    TextFieldWidget nameField = this.field(right, y + 10, column, "gui.questz.team.name", this.nameValue);
                    nameField.setChangedListener(value -> this.nameValue = value);
                    this.addDrawableChild(ButtonWidget.builder(Text.translatable("gui.questz.team.rename"), button -> this.run("rename " + this.nameValue.trim()))
                            .dimensions(right, y + 30, column, 18).build());
                    y += GROUP_HEIGHT;
                }
                this.addDrawableChild(ButtonWidget.builder(Text.translatable("gui.questz.team.leave"), button -> this.confirm("gui.questz.team.leave.confirm", "leave"))
                        .dimensions(right, y + 4, owner ? column / 2 - 1 : column, 18).build());
                if (owner) {
                    this.addDrawableChild(ButtonWidget.builder(Text.translatable("gui.questz.team.disband").formatted(Formatting.RED), button -> this.confirm("gui.questz.team.disband.confirm", "disband"))
                            .dimensions(right + column / 2 + 1, y + 4, column / 2 - 1, 18).build());
                }
            }
            int inviteY = this.invitesTop(team);
            for (Map.Entry<UUID, String> invite : team.getInvites().entrySet()) {
                ButtonWidget accept = this.addDrawableChild(ButtonWidget.builder(Text.translatable("gui.questz.team.accept"), button -> this.run("accept " + invite.getValue()))
                        .dimensions(this.insetX() + column - 54, inviteY - 2, 50, 14).build());
                accept.active = !team.isParty();
                inviteY += 16;
            }
        }
        this.addDrawableChild(ButtonWidget.builder(ScreenTexts.BACK, button -> this.close())
                .dimensions(this.panelX + this.panelWidth / 2 - 50, this.panelY + this.panelHeight - 25, 100, 20).build());
    }

    private void label(String key, int x, int y) {
        this.labels.add(Text.translatable(key));
        this.labelPositions.add(new int[]{x, y});
    }

    private TextFieldWidget field(int x, int y, int width, String key, String value) {
        TextFieldWidget field = new TextFieldWidget(this.textRenderer, x, y, width, 18, Text.translatable(key));
        field.setMaxLength(TeamManager.MAX_NAME_LENGTH);
        field.setText(value);
        field.setPlaceholder(Text.translatable(key).formatted(Formatting.DARK_GRAY));
        return this.addDrawableChild(field);
    }

    private void run(String command) {
        if (this.client != null && this.client.getNetworkHandler() != null) {
            this.client.getNetworkHandler().sendChatCommand("questz team " + command);
        }
    }

    private void confirm(String messageKey, String command) {
        if (this.client == null) {
            return;
        }
        this.client.setScreen(new ConfirmScreen(confirmed -> {
            if (confirmed) {
                this.run(command);
            }
            this.client.setScreen(this);
        }, Text.translatable("gui.questz.team"), Text.translatable(messageKey)));
    }

    @Override
    public void tick() {
        if (this.revision != ClientQuestData.getRevision()) {
            this.clearAndInit();
        }
    }

    @Override
    public void resize(MinecraftClient client, int width, int height) {
        this.parent.resize(client, width, height);
        super.resize(client, width, height);
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.parent.render(context, -1, -1, delta);
        MatrixStack matrices = context.getMatrices();
        matrices.push();
        matrices.translate(0.0F, 0.0F, 400.0F);
        context.fill(0, 0, this.width, this.height, 0x99000000);
        QuestzGui.drawPanel(context, this.panelX, this.panelY, this.panelWidth, this.panelHeight);
        QuestzGui.drawInset(context, this.insetX(), this.insetY(), this.columnWidth(), this.insetHeight());
        for (int i = 0; i < this.labels.size(); i++) {
            int[] position = this.labelPositions.get(i);
            context.drawText(this.textRenderer, this.labels.get(i), position[0], position[1], QuestzGui.COLOR_LABEL, false);
        }
        this.drawTeam(context, mouseX, mouseY);
        super.render(context, mouseX, mouseY, delta);
        matrices.pop();
    }

    private void drawTeam(DrawContext context, int mouseX, int mouseY) {
        ClientTeam team = ClientQuestData.getTeam();
        int left = this.insetX() + 4;
        int column = this.columnWidth();
        if (team == null || this.client == null || this.client.player == null) {
            context.drawTextWithShadow(this.textRenderer, Text.translatable("gui.questz.team.loading"), this.panelX + 8, this.panelY + 8, 0xFFFFFFFF);
            return;
        }
        Text type = Text.translatable(team.isParty() ? "command.questz.team.party" : "command.questz.team.personal").formatted(Formatting.GRAY);
        context.drawTextWithShadow(this.textRenderer, Text.literal(team.getName()).append(" ").append(type), this.panelX + 8, this.panelY + 8, 0xFFFFFFFF);

        context.enableScissor(this.insetX() + 1, this.insetY() + 1, this.insetX() + column - 1, this.insetY() + this.insetHeight() - 1);
        context.drawText(this.textRenderer, Text.translatable("gui.questz.team.members"), left, this.insetY() + 4, 0xFFFFAA00, true);
        boolean owner = team.getOwner().equals(this.client.player.getUuid());
        this.memberOrder.clear();
        int y = this.membersTop();
        for (Map.Entry<UUID, String> member : team.getMembers().entrySet()) {
            this.memberOrder.add(member.getKey());
            boolean isOwner = member.getKey().equals(team.getOwner());
            if (isOwner) {
                QuestzGui.drawIcon(context, QuestzGui.STAR, left - 2, y, 12, true);
            }
            Text name = Text.literal(member.getValue()).formatted(isOwner ? Formatting.GOLD : Formatting.WHITE);
            context.drawText(this.textRenderer, name, left + 11, y + 2, 0xFFFFFFFF, true);
            if (owner && team.isParty() && !member.getKey().equals(this.client.player.getUuid())) {
                boolean hovered = this.getKickTarget(mouseX, mouseY) == member.getKey();
                QuestzGui.drawIcon(context, QuestzGui.CLOSE, this.insetX() + column - 14, y, 12, hovered);
            }
            y += ROW_HEIGHT;
        }

        int inviteY = this.invitesTop(team);
        context.drawText(this.textRenderer, Text.translatable("gui.questz.team.invites"), left, inviteY - 14, 0xFFFFAA00, true);
        if (team.getInvites().isEmpty()) {
            context.drawText(this.textRenderer, Text.translatable("gui.questz.team.no_invites"), left, inviteY + 1, 0xFFA0A0A0, true);
        }
        for (String partyName : team.getInvites().values()) {
            context.drawText(this.textRenderer, Text.literal(partyName), left, inviteY + 1, 0xFFFFFFFF, true);
            inviteY += 16;
        }
        if (team.isParty() && !team.getInvites().isEmpty()) {
            context.drawText(this.textRenderer, Text.translatable("gui.questz.team.leave_first"), left, inviteY + 1, 0xFFA0A0A0, true);
        }
        context.disableScissor();
    }

    @Nullable
    private UUID getKickTarget(double mouseX, double mouseY) {
        ClientTeam team = ClientQuestData.getTeam();
        if (team == null || this.client == null || this.client.player == null || !team.isParty() || !team.getOwner().equals(this.client.player.getUuid())) {
            return null;
        }
        int right = this.insetX() + this.columnWidth();
        if (mouseX < right - 14 || mouseX >= right - 2) {
            return null;
        }
        int index = (int) Math.floor((mouseY - this.membersTop()) / ROW_HEIGHT);
        if (mouseY < this.membersTop() || index >= this.memberOrder.size()) {
            return null;
        }
        UUID target = this.memberOrder.get(index);
        return target.equals(this.client.player.getUuid()) ? null : target;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        UUID target = button == 0 ? this.getKickTarget(mouseX, mouseY) : null;
        ClientTeam team = ClientQuestData.getTeam();
        if (target != null && team != null && this.client != null) {
            String name = team.getMembers().get(target);
            this.client.setScreen(new ConfirmScreen(confirmed -> {
                if (confirmed) {
                    this.run("kick " + name);
                }
                this.client.setScreen(this);
            }, Text.translatable("gui.questz.team"), Text.translatable("gui.questz.team.kick.confirm", name)));
            return true;
        }
        return false;
    }

    @Override
    public void close() {
        if (this.client != null) {
            this.client.setScreen(this.parent);
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

}
