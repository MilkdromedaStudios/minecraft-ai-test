package com.milkdromeda.aiassistant.command;

import com.milkdromeda.aiassistant.ModEntities;
import com.milkdromeda.aiassistant.config.ModConfig;
import com.milkdromeda.aiassistant.entity.AiAssistantEntity;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.phys.AABB;

import java.util.Comparator;
import java.util.List;

public class AiCommands {

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, env) ->
                dispatcher.register(Commands.literal("ai")
                        .requires(src -> true)

                        // /ai summon [name]
                        .then(Commands.literal("summon")
                                .executes(ctx -> summon(ctx, "ARIA"))
                                .then(Commands.argument("name", StringArgumentType.word())
                                        .executes(ctx -> summon(ctx, StringArgumentType.getString(ctx, "name")))))

                        // /ai stop
                        .then(Commands.literal("stop")
                                .executes(AiCommands::stop))

                        // /ai settings — show or change config
                        .then(Commands.literal("settings")
                                .executes(AiCommands::showSettings)

                                .then(Commands.literal("hf_token")
                                        .then(Commands.argument("value", StringArgumentType.greedyString())
                                                .executes(ctx -> setSetting(ctx, "hf_token",
                                                        StringArgumentType.getString(ctx, "value")))))

                                .then(Commands.literal("model")
                                        .then(Commands.argument("value", StringArgumentType.greedyString())
                                                .executes(ctx -> setSetting(ctx, "model",
                                                        StringArgumentType.getString(ctx, "value")))))

                                .then(Commands.literal("temperature")
                                        .then(Commands.argument("value", DoubleArgumentType.doubleArg(0.0, 2.0))
                                                .executes(ctx -> setSettingDouble(ctx, "temperature",
                                                        DoubleArgumentType.getDouble(ctx, "value")))))

                                .then(Commands.literal("max_tokens")
                                        .then(Commands.argument("value", IntegerArgumentType.integer(32, 2048))
                                                .executes(ctx -> setSettingInt(ctx, "max_tokens",
                                                        IntegerArgumentType.getInteger(ctx, "value")))))

                                .then(Commands.literal("follow_distance")
                                        .then(Commands.argument("value", DoubleArgumentType.doubleArg(1.0, 32.0))
                                                .executes(ctx -> setSettingDouble(ctx, "follow_distance",
                                                        DoubleArgumentType.getDouble(ctx, "value")))))

                                .then(Commands.literal("guard_radius")
                                        .then(Commands.argument("value", DoubleArgumentType.doubleArg(4.0, 64.0))
                                                .executes(ctx -> setSettingDouble(ctx, "guard_radius",
                                                        DoubleArgumentType.getDouble(ctx, "value")))))

                                .then(Commands.literal("name")
                                        .then(Commands.argument("value", StringArgumentType.word())
                                                .executes(ctx -> renameAssistant(ctx,
                                                        StringArgumentType.getString(ctx, "value"))))))

                        // /ai <task> — must be last (greedy)
                        .then(Commands.argument("task", StringArgumentType.greedyString())
                                .executes(ctx -> doTask(ctx, StringArgumentType.getString(ctx, "task"))))
                )
        );
    }

    // ── /ai summon ────────────────────────────────────────────────────────────

    private static int summon(CommandContext<CommandSourceStack> ctx, String name) {
        ServerPlayer player = getPlayer(ctx);
        if (player == null) return 0;

        ServerLevel level = player.level();
        AiAssistantEntity entity = ModEntities.AI_ASSISTANT.create(level, EntitySpawnReason.COMMAND);
        if (entity == null) return 0;

        entity.setAssistantName(name);
        entity.setOwnerUuid(player.getUUID());
        entity.setPos(player.getX() + 1.5, player.getY(), player.getZ());
        entity.setMode(AiAssistantEntity.Mode.FOLLOWING);
        level.addFreshEntity(entity);

        player.sendSystemMessage(Component.literal(
                "[" + name + "] Ready! Use /ai <task> to give me instructions."));
        return 1;
    }

    // ── /ai stop ──────────────────────────────────────────────────────────────

    private static int stop(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = getPlayer(ctx);
        if (player == null) return 0;

        AiAssistantEntity ai = nearest(player, 128);
        if (ai == null) return noAi(player);

        ai.getTaskManager().clearPlan();
        ai.setMode(AiAssistantEntity.Mode.FOLLOWING);
        player.sendSystemMessage(Component.literal("[" + ai.getAssistantName() + "] Stopped. Standing by."));
        return 1;
    }

    // ── /ai settings ─────────────────────────────────────────────────────────

    private static int showSettings(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = getPlayer(ctx);
        if (player == null) return 0;

        ModConfig cfg = ModConfig.get();
        AiAssistantEntity ai = nearest(player, 128);
        String aiName = ai != null ? ai.getAssistantName() : "none nearby";
        String mode   = ai != null ? ai.getMode().name() : "-";
        String task   = ai != null ? ai.getTaskManager().getPlanDescription() : "-";

        player.sendSystemMessage(Component.literal(
                "§6=== AI Assistant Settings ===\n" +
                "§eAssistant:     §f" + aiName + "  (mode: " + mode + ")\n" +
                "§eCurrent task:  §f" + task + "\n" +
                "§eHF model:      §f" + cfg.hfModel + "\n" +
                "§eHF token:      §f" + (cfg.hasApiToken() ? "set ✓" : "§cnot set — /ai settings hf_token <token>") + "\n" +
                "§eTemperature:   §f" + cfg.temperature + "\n" +
                "§eMax tokens:    §f" + cfg.maxNewTokens + "\n" +
                "§eFollow dist:   §f" + cfg.followDistance + "\n" +
                "§eGuard radius:  §f" + cfg.guardRadius + "\n" +
                "§7Use /ai settings <key> <value> to change any setting."
        ));
        return 1;
    }

    private static int setSetting(CommandContext<CommandSourceStack> ctx, String key, String value) {
        ServerPlayer player = getPlayer(ctx);
        if (player == null) return 0;
        ModConfig cfg = ModConfig.get();

        switch (key) {
            case "hf_token" -> cfg.hfToken = value;
            case "model"    -> cfg.hfModel = value;
            default -> { player.sendSystemMessage(Component.literal("Unknown key: " + key)); return 0; }
        }

        ModConfig.save();
        player.sendSystemMessage(Component.literal("§a[AI Settings] §f" + key + " §7= §f" + (key.equals("hf_token") ? "***" : value)));
        return 1;
    }

    private static int setSettingDouble(CommandContext<CommandSourceStack> ctx, String key, double value) {
        ServerPlayer player = getPlayer(ctx);
        if (player == null) return 0;
        ModConfig cfg = ModConfig.get();

        switch (key) {
            case "temperature"     -> cfg.temperature = value;
            case "follow_distance" -> cfg.followDistance = value;
            case "guard_radius"    -> cfg.guardRadius = value;
        }

        ModConfig.save();
        player.sendSystemMessage(Component.literal("§a[AI Settings] §f" + key + " §7= §f" + value));
        return 1;
    }

    private static int setSettingInt(CommandContext<CommandSourceStack> ctx, String key, int value) {
        ServerPlayer player = getPlayer(ctx);
        if (player == null) return 0;
        ModConfig cfg = ModConfig.get();

        if ("max_tokens".equals(key)) cfg.maxNewTokens = value;

        ModConfig.save();
        player.sendSystemMessage(Component.literal("§a[AI Settings] §f" + key + " §7= §f" + value));
        return 1;
    }

    private static int renameAssistant(CommandContext<CommandSourceStack> ctx, String newName) {
        ServerPlayer player = getPlayer(ctx);
        if (player == null) return 0;

        AiAssistantEntity ai = nearest(player, 32);
        if (ai == null) return noAi(player);

        String old = ai.getAssistantName();
        ai.setAssistantName(newName);
        player.sendSystemMessage(Component.literal("§a[AI Settings] §fRenamed '" + old + "' → '" + newName + "'"));
        return 1;
    }

    // ── /ai <task> ────────────────────────────────────────────────────────────

    private static int doTask(CommandContext<CommandSourceStack> ctx, String task) {
        ServerPlayer player = getPlayer(ctx);
        if (player == null) return 0;

        AiAssistantEntity ai = nearest(player, 128);
        if (ai == null) return noAi(player);

        if (!ModConfig.get().hasApiToken()) {
            player.sendSystemMessage(Component.literal(
                    "§c[AI] No HuggingFace token set. Run: §f/ai settings hf_token <your_token>"));
            return 0;
        }

        ai.giveTask(task, player);
        return 1;
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private static ServerPlayer getPlayer(CommandContext<CommandSourceStack> ctx) {
        try { return ctx.getSource().getPlayerOrException(); } catch (Exception e) { return null; }
    }

    private static AiAssistantEntity nearest(ServerPlayer player, double range) {
        AABB box = AABB.ofSize(player.position(), range * 2, range, range * 2);
        List<AiAssistantEntity> list = player.level()
                .getEntitiesOfClass(AiAssistantEntity.class, box, e -> true);
        // Return the closest one
        return list.stream()
                .min(Comparator.comparingDouble(a -> a.distanceToSqr(player)))
                .orElse(null);
    }

    private static int noAi(ServerPlayer player) {
        player.sendSystemMessage(Component.literal(
                "§cNo AI assistant nearby. Spawn one with §f/ai summon"));
        return 0;
    }
}
