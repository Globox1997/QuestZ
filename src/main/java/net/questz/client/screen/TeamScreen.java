package net.questz.client.screen;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ConfirmScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
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

    private static final int ROW_HEIGHT = 14;

    private final Screen parent;
    private int revision = -1;
    private String nameValue = "";
    private String inviteValue = "";
    private final List<UUID> memberOrder = new ArrayList<>();

    public TeamScreen(Screen parent) {
        super(Text.translatable("gui.questz.team"));
        this.parent = parent;
    }

    private int getTotalWidth() {
        return Math.min(this.width - 20, 320);
    }

    private int getLeft() {
        return (this.width - this.getTotalWidth()) / 2;
    }

    private int getColumnWidth() {
        return (this.getTotalWidth() - 10) / 2;
    }

    private int getRightX() {
        return this.getLeft() + this.getColumnWidth() + 10;
    }

    @Override
    protected void init() {
        this.revision = ClientQuestData.getRevision();
        ClientTeam team = ClientQuestData.getTeam();
        int right = this.getRightX();
        int column = this.getColumnWidth();
        int y = 40;

        if (team != null && this.client != null && this.client.player != null) {
            boolean owner = team.getOwner().equals(this.client.player.getUuid());
            if (!team.isParty()) {
                TextFieldWidget nameField = this.field(right, y, column, "gui.questz.team.name", this.nameValue);
                nameField.setChangedListener(value -> this.nameValue = value);
                this.addDrawableChild(ButtonWidget.builder(Text.translatable("gui.questz.team.create"), button -> this.run("create " + this.nameValue.trim()))
                        .dimensions(right, y + 22, column, 20).build());
            } else {
                TextFieldWidget inviteField = this.field(right, y, column, "gui.questz.team.player_name", this.inviteValue);
                inviteField.setChangedListener(value -> this.inviteValue = value);
                ButtonWidget invite = this.addDrawableChild(ButtonWidget.builder(Text.translatable("gui.questz.team.invite"), button -> this.run("invite " + this.inviteValue.trim()))
                        .dimensions(right, y + 22, column, 20).build());
                invite.active = owner;
                y += 50;
                if (owner) {
                    TextFieldWidget nameField = this.field(right, y, column, "gui.questz.team.name", this.nameValue);
                    nameField.setChangedListener(value -> this.nameValue = value);
                    this.addDrawableChild(ButtonWidget.builder(Text.translatable("gui.questz.team.rename"), button -> this.run("rename " + this.nameValue.trim()))
                            .dimensions(right, y + 22, column, 20).build());
                    y += 50;
                }
                this.addDrawableChild(ButtonWidget.builder(Text.translatable("gui.questz.team.leave"), button -> this.confirm("gui.questz.team.leave.confirm", "leave"))
                        .dimensions(right, y, owner ? column / 2 - 1 : column, 20).build());
                if (owner) {
                    this.addDrawableChild(ButtonWidget.builder(Text.translatable("gui.questz.team.disband").formatted(Formatting.RED), button -> this.confirm("gui.questz.team.disband.confirm", "disband"))
                            .dimensions(right + column / 2 + 1, y, column / 2 - 1, 20).build());
                }
            }
            int inviteY = this.getInvitesTop(team);
            for (Map.Entry<UUID, String> invite : team.getInvites().entrySet()) {
                ButtonWidget accept = this.addDrawableChild(ButtonWidget.builder(Text.translatable("gui.questz.team.accept"), button -> this.run("accept " + invite.getValue()))
                        .dimensions(this.getLeft() + column - 50, inviteY, 50, 14).build());
                accept.active = !team.isParty();
                inviteY += 16;
            }
        }
        this.addDrawableChild(ButtonWidget.builder(ScreenTexts.BACK, button -> this.close()).dimensions(this.width / 2 - 50, this.height - 26, 100, 20).build());
    }

    private int getInvitesTop(ClientTeam team) {
        return 40 + team.getMembers().size() * ROW_HEIGHT + 22;
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
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        ClientTeam team = ClientQuestData.getTeam();
        int left = this.getLeft();
        int column = this.getColumnWidth();
        if (team == null || this.client == null || this.client.player == null) {
            context.drawCenteredTextWithShadow(this.textRenderer, Text.translatable("gui.questz.team.loading"), this.width / 2, 40, 0xFFAAAAAA);
            return;
        }
        Text type = Text.translatable(team.isParty() ? "command.questz.team.party" : "command.questz.team.personal").formatted(Formatting.GRAY);
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(team.getName()).append(" ").append(type), this.width / 2, 12, 0xFFFFFFFF);

        context.drawText(this.textRenderer, Text.translatable("gui.questz.team.members"), left, 28, 0xFFFFAA00, false);
        boolean owner = team.getOwner().equals(this.client.player.getUuid());
        this.memberOrder.clear();
        int y = 40;
        for (Map.Entry<UUID, String> member : team.getMembers().entrySet()) {
            this.memberOrder.add(member.getKey());
            boolean isOwner = member.getKey().equals(team.getOwner());
            Text name = Text.literal((isOwner ? "★ " : "  ") + member.getValue()).formatted(isOwner ? Formatting.GOLD : Formatting.WHITE);
            context.drawText(this.textRenderer, name, left, y + 3, 0xFFFFFFFF, false);
            if (owner && team.isParty() && !member.getKey().equals(this.client.player.getUuid())) {
                boolean hovered = mouseX >= left + column - 12 && mouseX < left + column && mouseY >= y && mouseY < y + ROW_HEIGHT;
                context.drawText(this.textRenderer, Text.literal("✕"), left + column - 10, y + 3, hovered ? 0xFFFF5555 : 0xFF888888, false);
            }
            y += ROW_HEIGHT;
        }

        int inviteY = this.getInvitesTop(team);
        context.drawText(this.textRenderer, Text.translatable("gui.questz.team.invites"), left, inviteY - 12, 0xFFFFAA00, false);
        if (team.getInvites().isEmpty()) {
            context.drawText(this.textRenderer, Text.translatable("gui.questz.team.no_invites"), left, inviteY + 3, 0xFF888888, false);
        }
        for (String partyName : team.getInvites().values()) {
            context.drawText(this.textRenderer, Text.literal(partyName), left, inviteY + 3, 0xFFFFFFFF, false);
            inviteY += 16;
        }
        if (team.isParty() && !team.getInvites().isEmpty()) {
            context.drawText(this.textRenderer, Text.translatable("gui.questz.team.leave_first"), left, inviteY + 3, 0xFF888888, false);
        }
    }

    @Nullable
    private UUID getKickTarget(double mouseX, double mouseY) {
        ClientTeam team = ClientQuestData.getTeam();
        if (team == null || this.client == null || this.client.player == null || !team.isParty() || !team.getOwner().equals(this.client.player.getUuid())) {
            return null;
        }
        int left = this.getLeft();
        int column = this.getColumnWidth();
        if (mouseX < left + column - 12 || mouseX >= left + column) {
            return null;
        }
        int index = (int) ((mouseY - 40) / ROW_HEIGHT);
        if (mouseY < 40 || index >= this.memberOrder.size()) {
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

}
