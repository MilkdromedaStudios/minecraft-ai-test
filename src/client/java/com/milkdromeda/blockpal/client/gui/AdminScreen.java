package com.milkdromeda.blockpal.client.gui;

import com.milkdromeda.blockpal.network.AdminActionPayload;
import com.milkdromeda.blockpal.network.AdminStatsData;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ScrollableLayout;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.Locale;

/**
 * The Blockpal admin panel, opened with {@code /ai admin menu} (ops only). It is
 * a read-out of server-wide state ({@link AdminStatsData}) — bot totals, per-player
 * bot counts and FPS, a row per bot — plus action buttons (kill all bots, disable
 * / enable the mod, adjust the bot cap, refresh).
 *
 * <p>The screen is "dumb": every button sends an {@link AdminActionPayload}; the
 * server re-checks permission, performs the action and sends back a fresh snapshot,
 * which re-opens this screen with up-to-date values. (Mirrors {@link AiConfigScreen}'s
 * pinned-title + scrollable-body + pinned-action-bar layout.)
 */
public class AdminScreen extends Screen {

    private static final int W = 320;        // content column width
    private static final int FIELD_H = 18;
    private static final int LABEL_H = 11;
    private static final int SPACING = 1;
    private static final int BODY_TOP = 26;
    private static final int FOOTER = 50;    // room for two action rows

    private final AdminStatsData d;

    public AdminScreen(AdminStatsData data) {
        super(Component.literal("Blockpal — Admin"));
        this.d = data;
    }

    @Override
    protected void init() {
        // -- pinned title --
        addRenderableWidget(new StringWidget(0, 6, this.width, 12, this.title, this.font));

        // -- scrollable body --
        LinearLayout body = LinearLayout.vertical().spacing(SPACING);

        line(body, "§6Overview");
        line(body, "§eBots: §f" + d.totalBots() + " §7/ " + (d.maxBots() == 0 ? "∞" : d.maxBots()));
        line(body, "§eMod status: " + (d.modDisabled() ? "§cDISABLED" : "§aactive"));
        line(body, "§eAllow commands: §f" + (d.allowCommands() ? "on (lvl " + d.commandLevel() + ")" : "off"));
        line(body, "§eAdmin level: §f" + d.adminLevel());
        line(body, "§eAPI token: §f" + (d.tokenSet()
                ? ("set ✓" + (d.tokenFromEnv() ? " (from env)" : "")) : "§cnot set"));

        line(body, " ");
        line(body, "§6Players online (" + d.players().size() + ")");
        if (d.players().isEmpty()) line(body, "§7  none");
        for (AdminStatsData.PlayerRow p : d.players()) {
            line(body, "§f  " + p.name() + " §7— bots: §f" + p.bots()
                    + " §7— fps: §f" + (p.fps() < 0 ? "?" : p.fps()));
        }

        line(body, " ");
        line(body, "§6Bots (" + d.bots().size() + ")");
        if (d.bots().isEmpty()) line(body, "§7  none");
        for (AdminStatsData.BotRow b : d.bots()) {
            line(body, "§f  " + b.name() + " §7(" + b.owner() + ") — "
                    + b.mode().toLowerCase(Locale.ROOT) + " — " + b.dim() + " — hp " + b.health()
                    + " §8@ " + b.x() + "," + b.y() + "," + b.z());
        }

        int maxHeight = Math.max(FIELD_H, this.height - BODY_TOP - FOOTER);
        ScrollableLayout scroll = new ScrollableLayout(this.minecraft, body, maxHeight);
        scroll.setMinWidth(W + 12);
        scroll.arrangeElements();
        scroll.setX(this.width / 2 - (W + 12) / 2);
        scroll.setY(BODY_TOP);
        scroll.visitWidgets(this::addRenderableWidget);

        // -- pinned action bar (two rows) --
        int bw = 100, gap = 8;
        int barW = bw * 3 + gap * 2;
        int bx = this.width / 2 - barW / 2;
        int row2 = this.height - FIELD_H - 6;
        int row1 = row2 - FIELD_H - 4;

        addRenderableWidget(withTip(Button.builder(Component.literal("Kill all bots"),
                        b -> send("killall", 0)).bounds(bx, row1, bw, FIELD_H).build(),
                "Remove every Blockpal entity on the server."));
        addRenderableWidget(Button.builder(
                        Component.literal(d.modDisabled() ? "Enable bots" : "Disable bots"),
                        b -> send(d.modDisabled() ? "enable" : "disable", 0))
                .bounds(bx + bw + gap, row1, bw, FIELD_H).build());
        addRenderableWidget(Button.builder(Component.literal("Refresh"),
                        b -> send("refresh", 0))
                .bounds(bx + (bw + gap) * 2, row1, bw, FIELD_H).build());

        // Bot-cap controls: [ − ] [ Max: N ] [ + ]
        addRenderableWidget(withTip(Button.builder(Component.literal("Max bots −"),
                        b -> send("maxbots", Math.max(0, d.maxBots() - 1)))
                .bounds(bx, row2, bw, FIELD_H).build(),
                "Lower the server-wide bot cap (0 = unlimited)."));
        StringWidget maxLabel = new StringWidget(bx + bw + gap, row2, bw, FIELD_H,
                Component.literal("Max: " + (d.maxBots() == 0 ? "∞" : d.maxBots())), this.font);
        addRenderableWidget(maxLabel);
        addRenderableWidget(withTip(Button.builder(Component.literal("Max bots +"),
                        b -> send("maxbots", Math.min(50, d.maxBots() + 1)))
                .bounds(bx + (bw + gap) * 2, row2, bw, FIELD_H).build(),
                "Raise the server-wide bot cap (max 50)."));
    }

    private void line(LinearLayout body, String text) {
        body.addChild(new StringWidget(W, LABEL_H, Component.literal(text), this.font));
    }

    private static Button withTip(Button button, String tip) {
        button.setTooltip(Tooltip.create(Component.literal(tip)));
        return button;
    }

    private void send(String action, int value) {
        if (ClientPlayNetworking.canSend(AdminActionPayload.TYPE)) {
            ClientPlayNetworking.send(new AdminActionPayload(action, value));
        }
        // The server replies with a fresh AdminSyncPayload, which reopens this screen.
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
