package com.milkdromeda.aiassistant.client.gui;

import com.milkdromeda.aiassistant.network.ConfigData;
import com.milkdromeda.aiassistant.network.ConfigUpdatePayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * A real settings menu for the AI assistant: toggles, text fields and sliders
 * laid out in two columns. Opened with {@code /ai menu}. Saving sends the values
 * to the server, so it works in both singleplayer and multiplayer.
 *
 * <p>Text is drawn with {@link StringWidget}s (added as render-only widgets)
 * rather than immediate-mode draw calls, to suit the retained-mode GUI.
 */
public class AiConfigScreen extends Screen {

    private static final int COL_W = 150;
    private static final int COL_GAP = 20;
    private static final int FIELD_H = 20;
    private static final int ROW_H = 28;
    private static final int TOP = 46;

    private final ConfigData initial;

    private CycleButton<Boolean> listenButton;
    private CycleButton<Boolean> activeButton;
    private CycleButton<Boolean> debugButton;
    private CycleButton<Boolean> commandsButton;
    private EditBox nameBox;
    private EditBox tokenBox;
    private EditBox apiUrlBox;
    private EditBox skinBox;
    private EditBox modelBox;
    private OptionSlider temperatureSlider;
    private OptionSlider maxTokensSlider;
    private OptionSlider followSlider;
    private OptionSlider guardSlider;
    private OptionSlider commandLevelSlider;

    public AiConfigScreen(ConfigData initial) {
        super(Component.literal("AI Assistant Settings"));
        this.initial = initial;
    }

    @Override
    protected void init() {
        int totalW = COL_W * 2 + COL_GAP;
        int left = this.width / 2 - totalW / 2;
        int right = left + COL_W + COL_GAP;

        addRenderableOnly(new StringWidget(0, 16, this.width, 12, this.title, this.font));

        // ── left column: behaviour toggles + free-text fields ──
        int ly = TOP;
        listenButton = CycleButton.onOffBuilder(initial.chatListening())
                .create(left, ly, COL_W, FIELD_H, Component.literal("Chat listening"));
        addRenderableWidget(listenButton);
        ly += ROW_H;

        activeButton = CycleButton.onOffBuilder(initial.activeMode())
                .create(left, ly, COL_W, FIELD_H, Component.literal("Active analysis"));
        addRenderableWidget(activeButton);
        ly += ROW_H;

        debugButton = CycleButton.onOffBuilder(initial.debugLogging())
                .create(left, ly, COL_W, FIELD_H, Component.literal("Debug logging"));
        addRenderableWidget(debugButton);
        ly += ROW_H;

        commandsButton = CycleButton.onOffBuilder(initial.allowCommands())
                .create(left, ly, COL_W, FIELD_H, Component.literal("Allow commands"));
        addRenderableWidget(commandsButton);
        ly += ROW_H;

        nameBox = labeledBox(left, ly, "Assistant name", initial.defaultName(), 32);
        ly += ROW_H;

        tokenBox = labeledBox(left, ly, "API token", "", 256);
        tokenBox.setHint(Component.literal(initial.tokenSet() ? "set — blank keeps it" : "not set"));
        ly += ROW_H;

        apiUrlBox = labeledBox(left, ly, "API URL", initial.apiUrl(), 256);
        ly += ROW_H;

        skinBox = labeledBox(left, ly, "Default skin", initial.defaultSkin(), 64);
        ly += ROW_H;

        // ── right column: model + numeric sliders ──
        int ry = TOP;
        modelBox = labeledBox(right, ry, "Model", initial.model(), 128);
        ry += ROW_H;

        temperatureSlider = new OptionSlider(right, ry, COL_W, FIELD_H,
                "Temperature", 0.0, 2.0, initial.temperature(), false);
        addRenderableWidget(temperatureSlider);
        ry += ROW_H;

        maxTokensSlider = new OptionSlider(right, ry, COL_W, FIELD_H,
                "Max tokens", 32, 2048, initial.maxTokens(), true);
        addRenderableWidget(maxTokensSlider);
        ry += ROW_H;

        followSlider = new OptionSlider(right, ry, COL_W, FIELD_H,
                "Follow distance", 1, 32, initial.followDistance(), false);
        addRenderableWidget(followSlider);
        ry += ROW_H;

        guardSlider = new OptionSlider(right, ry, COL_W, FIELD_H,
                "Guard radius", 4, 64, initial.guardRadius(), false);
        addRenderableWidget(guardSlider);
        ry += ROW_H;

        commandLevelSlider = new OptionSlider(right, ry, COL_W, FIELD_H,
                "Command perm level", 0, 4, initial.commandPermissionLevel(), true);
        addRenderableWidget(commandLevelSlider);
        ry += ROW_H;

        // ── Save / Cancel ──
        int by = Math.max(ly, ry) + 14;
        int bw = 120;
        addRenderableWidget(Button.builder(Component.literal("Save"), b -> save())
                .bounds(this.width / 2 - bw - 5, by, bw, FIELD_H).build());
        addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> onClose())
                .bounds(this.width / 2 + 5, by, bw, FIELD_H).build());
    }

    /** Adds an EditBox with a small caption above it (as a render-only StringWidget). */
    private EditBox labeledBox(int x, int y, String label, String value, int maxLen) {
        addRenderableOnly(new StringWidget(x, y - 11, COL_W, 10, Component.literal(label), this.font));
        EditBox box = new EditBox(this.font, x, y, COL_W, FIELD_H, Component.literal(label));
        box.setMaxLength(maxLen);
        if (value != null) box.setValue(value);
        addRenderableWidget(box);
        return box;
    }

    private void save() {
        ConfigData data = new ConfigData(
                listenButton.getValue(),
                activeButton.getValue(),
                debugButton.getValue(),
                nameBox.getValue(),
                tokenBox.getValue(),          // blank = keep existing, handled server-side
                initial.tokenSet(),
                modelBox.getValue(),
                apiUrlBox.getValue(),
                temperatureSlider.current(),
                (int) Math.round(maxTokensSlider.current()),
                followSlider.current(),
                guardSlider.current(),
                commandsButton.getValue(),
                (int) Math.round(commandLevelSlider.current()),
                skinBox.getValue());
        ClientPlayNetworking.send(new ConfigUpdatePayload(data));
        onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
