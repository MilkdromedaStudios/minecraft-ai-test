package com.milkdromeda.aiassistant.client.gui;

import com.milkdromeda.aiassistant.network.ConfigData;
import com.milkdromeda.aiassistant.network.ConfigUpdatePayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ScrollableLayout;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * A real settings menu for the AI assistant: text fields, toggles and sliders
 * in a single scrollable column. Opened with {@code /ai menu}. Saving sends the
 * values to the server, so it works in both singleplayer and multiplayer.
 *
 * <p>The body scrolls (mouse wheel + scrollbar) inside a {@link ScrollableLayout}
 * so it always fits on screen, while the title and the Save / Apply / Cancel
 * action bar stay pinned. A collapsible <b>Developer Mode</b> section exposes
 * low-level settings that can cause lag or crashes if misused; each shows an
 * inline warning.
 */
public class AiConfigScreen extends Screen {

    private static final int W = 240;           // content column width
    private static final int FIELD_H = 18;
    private static final int LABEL_H = 9;
    private static final int SPACING = 3;
    private static final int HEADER = 22;
    private static final int FOOTER = 28;
    private static final Component SAVED_MSG =
            Component.literal("Settings applied ✓").withStyle(s -> s.withColor(0x55FF55));

    private final ConfigData initial;

    // Widgets
    private CycleButton<Boolean> listenButton;
    private CycleButton<Boolean> activeButton;
    private CycleButton<Boolean> debugButton;
    private CycleButton<Boolean> commandsButton;
    private CycleButton<String> presetButton;
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

    // Developer mode widgets (null when dev mode is off)
    private OptionSlider actionTickDelaySlider;
    private OptionSlider maxTaskSecondsSlider;
    private OptionSlider fleeHealthSlider;

    /** Whether the developer section is expanded. Survives rebuilds. */
    private boolean devMode = false;

    /** Dev-field values tracked independently so presets can set them while hidden. */
    private int pendingActionTickDelay;
    private int pendingMaxTaskSeconds;
    private double pendingFleeHealth;

    private ConfigData baseline;
    private boolean tokenSet;
    private boolean saveOnClose = true;
    private StringWidget appliedLabel;
    private long appliedFeedbackUntil;

    public AiConfigScreen(ConfigData initial) {
        super(Component.literal("AI Assistant Settings"));
        this.initial = initial;
        this.tokenSet = initial.tokenSet();
        this.pendingActionTickDelay = initial.actionTickDelay();
        this.pendingMaxTaskSeconds = initial.maxTaskSeconds();
        this.pendingFleeHealth = initial.fleeHealthPercent();
    }

    @Override
    protected void init() {
        // -- pinned title --
        addRenderableWidget(new StringWidget(0, 6, this.width, 12, this.title, this.font));

        // -- scrollable body (single column) --
        LinearLayout body = LinearLayout.vertical().spacing(SPACING);

        nameBox = bodyBox(body, "Assistant name", initial.defaultName(), 32);
        skinBox = bodyBox(body, "Default skin", initial.defaultSkin(), 64);
        modelBox = bodyBox(body, "Model", initial.model(), 128);
        apiUrlBox = bodyBox(body, "API URL", initial.apiUrl(), 256);
        tokenBox = bodyBox(body, "API token", "", 256);
        tokenBox.setHint(Component.literal(initial.tokenSet() ? "set - blank keeps it" : "not set"));

        String currentPreset = initial.performancePreset() != null ? initial.performancePreset() : "normal";
        presetButton = body.addChild(CycleButton.<String>builder(s -> Component.literal(presetLabel(s)), currentPreset)
                .withValues("normal", "opus", "potato")
                .create(0, 0, W, FIELD_H, Component.literal("Preset"),
                        (btn, val) -> applyPreset(val)));

        listenButton = bodyToggle(body, "Chat listening", initial.chatListening());
        activeButton = bodyToggle(body, "Active analysis", initial.activeMode());
        commandsButton = bodyToggle(body, "Allow commands", initial.allowCommands());
        debugButton = bodyToggle(body, "Debug logging", initial.debugLogging());

        temperatureSlider = bodySlider(body, "Temperature", 0.0, 2.0, initial.temperature(), false);
        maxTokensSlider = bodySlider(body, "Max tokens", 32, 2048, initial.maxTokens(), true);
        followSlider = bodySlider(body, "Follow distance", 1, 32, initial.followDistance(), false);
        guardSlider = bodySlider(body, "Guard radius", 4, 64, initial.guardRadius(), false);
        commandLevelSlider = bodySlider(body, "Command perm level", 0, 4, initial.commandPermissionLevel(), true);

        // -- developer mode --
        body.addChild(Button.builder(
                        Component.literal(devMode ? "▼ Developer Mode  [ON]" : "► Developer Mode  [OFF]"),
                        b -> { devMode = !devMode; rebuildWidgets(); })
                .bounds(0, 0, W, FIELD_H).build());

        if (devMode) {
            body.addChild(new StringWidget(W, LABEL_H,
                    Component.literal("⚠  These can cause lag or crash the game. See developer.md.")
                            .withStyle(s -> s.withColor(0xFF5555)),
                    this.font));
            actionTickDelaySlider = bodySlider(body, "Action tick delay (0=every tick!)", 0, 40,
                    pendingActionTickDelay, true);
            maxTaskSecondsSlider = bodySlider(body, "Task watchdog sec (0=disabled!)", 0, 600,
                    pendingMaxTaskSeconds, true);
            fleeHealthSlider = bodySlider(body, "Flee health % (0=never flees!)", 0.0, 1.0,
                    pendingFleeHealth, false);
        } else {
            actionTickDelaySlider = null;
            maxTaskSecondsSlider = null;
            fleeHealthSlider = null;
        }

        int maxHeight = Math.max(FIELD_H, this.height - HEADER - FOOTER);
        ScrollableLayout scroll = new ScrollableLayout(this.minecraft, body, maxHeight);
        scroll.setMinWidth(W + 12);
        scroll.arrangeElements();
        scroll.setX(this.width / 2 - (W + 12) / 2);
        scroll.setY(HEADER);
        scroll.visitWidgets(this::addRenderableWidget);

        // -- pinned action bar --
        int bw = 100;
        int gap = 8;
        int barW = bw * 3 + gap * 2;
        int bx = this.width / 2 - barW / 2;
        int by = this.height - FIELD_H - 6;
        addRenderableWidget(Button.builder(Component.literal("Save"), b -> saveAndClose())
                .bounds(bx, by, bw, FIELD_H).build());
        addRenderableWidget(Button.builder(Component.literal("Apply"), b -> apply())
                .bounds(bx + bw + gap, by, bw, FIELD_H).build());
        addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> cancel())
                .bounds(bx + (bw + gap) * 2, by, bw, FIELD_H).build());

        appliedLabel = new StringWidget(0, by - 12, this.width, LABEL_H, Component.empty(), this.font);
        addRenderableWidget(appliedLabel);

        baseline = buildData();
    }

    // -- preset helpers --

    private static String presetLabel(String preset) {
        return switch (preset) {
            case "opus"   -> "Preset: Opus  ***";
            case "potato" -> "Preset: Potato  (low)";
            default       -> "Preset: Normal";
        };
    }

    private void applyPreset(String preset) {
        switch (preset) {
            case "opus" -> {
                listenButton.setValue(true);
                activeButton.setValue(true);
                temperatureSlider.setCurrent(0.8);
                maxTokensSlider.setCurrent(1024);
                pendingActionTickDelay = 2;
                pendingMaxTaskSeconds = 600;
                pendingFleeHealth = 0.2;
            }
            case "potato" -> {
                listenButton.setValue(true);
                activeButton.setValue(false);
                temperatureSlider.setCurrent(0.5);
                maxTokensSlider.setCurrent(256);
                pendingActionTickDelay = 20;
                pendingMaxTaskSeconds = 120;
                pendingFleeHealth = 0.25;
            }
            default -> {
                listenButton.setValue(true);
                activeButton.setValue(true);
                temperatureSlider.setCurrent(0.7);
                maxTokensSlider.setCurrent(512);
                pendingActionTickDelay = 8;
                pendingMaxTaskSeconds = 300;
                pendingFleeHealth = 0.25;
            }
        }
        if (actionTickDelaySlider != null) actionTickDelaySlider.setCurrent(pendingActionTickDelay);
        if (maxTaskSecondsSlider != null)  maxTaskSecondsSlider.setCurrent(pendingMaxTaskSeconds);
        if (fleeHealthSlider != null)      fleeHealthSlider.setCurrent(pendingFleeHealth);
    }

    // -- body widget factories --

    private EditBox bodyBox(LinearLayout body, String label, String value, int maxLen) {
        body.addChild(new StringWidget(W, LABEL_H, Component.literal(label), this.font));
        EditBox box = new EditBox(this.font, 0, 0, W, FIELD_H, Component.literal(label));
        box.setMaxLength(maxLen);
        if (value != null) box.setValue(value);
        return body.addChild(box);
    }

    private CycleButton<Boolean> bodyToggle(LinearLayout body, String label, boolean value) {
        return body.addChild(CycleButton.onOffBuilder(value)
                .create(0, 0, W, FIELD_H, Component.literal(label)));
    }

    private OptionSlider bodySlider(LinearLayout body, String label, double min, double max,
                                    double value, boolean integer) {
        return body.addChild(new OptionSlider(0, 0, W, FIELD_H, label, min, max, value, integer));
    }

    // -- data --

    private ConfigData buildData() {
        if (actionTickDelaySlider != null) pendingActionTickDelay = (int) Math.round(actionTickDelaySlider.current());
        if (maxTaskSecondsSlider  != null) pendingMaxTaskSeconds  = (int) Math.round(maxTaskSecondsSlider.current());
        if (fleeHealthSlider      != null) pendingFleeHealth      = fleeHealthSlider.current();

        return new ConfigData(
                listenButton.getValue(),
                activeButton.getValue(),
                debugButton.getValue(),
                nameBox.getValue(),
                tokenBox.getValue(),
                tokenSet,
                modelBox.getValue(),
                apiUrlBox.getValue(),
                temperatureSlider.current(),
                (int) Math.round(maxTokensSlider.current()),
                followSlider.current(),
                guardSlider.current(),
                commandsButton.getValue(),
                (int) Math.round(commandLevelSlider.current()),
                skinBox.getValue(),
                pendingActionTickDelay,
                pendingMaxTaskSeconds,
                pendingFleeHealth,
                presetButton.getValue());
    }

    private void sendCurrent() {
        ClientPlayNetworking.send(new ConfigUpdatePayload(buildData()));
        if (!tokenBox.getValue().isBlank()) {
            tokenSet = true;
            tokenBox.setValue("");
            tokenBox.setHint(Component.literal("set - blank keeps it"));
        }
        baseline = buildData();
    }

    private boolean isDirty() {
        if (!tokenBox.getValue().isBlank()) return true;
        ConfigData c = buildData();
        return c.chatListening() != baseline.chatListening()
                || c.activeMode() != baseline.activeMode()
                || c.debugLogging() != baseline.debugLogging()
                || c.allowCommands() != baseline.allowCommands()
                || c.maxTokens() != baseline.maxTokens()
                || c.commandPermissionLevel() != baseline.commandPermissionLevel()
                || Double.compare(c.temperature(), baseline.temperature()) != 0
                || Double.compare(c.followDistance(), baseline.followDistance()) != 0
                || Double.compare(c.guardRadius(), baseline.guardRadius()) != 0
                || !eq(c.defaultName(), baseline.defaultName())
                || !eq(c.model(), baseline.model())
                || !eq(c.apiUrl(), baseline.apiUrl())
                || !eq(c.defaultSkin(), baseline.defaultSkin())
                || c.actionTickDelay() != baseline.actionTickDelay()
                || c.maxTaskSeconds() != baseline.maxTaskSeconds()
                || Double.compare(c.fleeHealthPercent(), baseline.fleeHealthPercent()) != 0
                || !eq(c.performancePreset(), baseline.performancePreset());
    }

    private static boolean eq(String a, String b) {
        return (a == null ? "" : a).equals(b == null ? "" : b);
    }

    private void apply() {
        sendCurrent();
        appliedLabel.setMessage(SAVED_MSG);
        appliedFeedbackUntil = System.currentTimeMillis() + 1500;
    }

    private void saveAndClose() {
        sendCurrent();
        saveOnClose = false;
        onClose();
    }

    private void cancel() {
        saveOnClose = false;
        onClose();
    }

    @Override
    public void onClose() {
        if (saveOnClose && isDirty()) {
            sendCurrent();
        }
        super.onClose();
    }

    @Override
    public void tick() {
        super.tick();
        if (appliedFeedbackUntil != 0 && System.currentTimeMillis() >= appliedFeedbackUntil) {
            appliedFeedbackUntil = 0;
            appliedLabel.setMessage(Component.empty());
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
