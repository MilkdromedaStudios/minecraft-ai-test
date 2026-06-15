package com.milkdromeda.aiassistant.entity;

import com.milkdromeda.aiassistant.ai.AiTaskManager;
import com.milkdromeda.aiassistant.config.ModConfig;
import com.milkdromeda.aiassistant.entity.goal.*;
import com.milkdromeda.aiassistant.entity.goal.FollowOwnerGoal;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import java.util.UUID;

public class AiAssistantEntity extends PathfinderMob {

    public enum Mode {
        IDLE, FOLLOWING, BUILDING, FIGHTING, GUARDING, EXECUTING
    }

    private Mode mode = Mode.IDLE;
    private String assistantName = "ARIA";
    private UUID ownerUuid;
    private String pendingTask;
    private final AiTaskManager taskManager;
    private BuildGoal buildGoal;
    private int idleMessageTimer = 0;

    public AiAssistantEntity(EntityType<? extends AiAssistantEntity> type, Level level) {
        super(type, level);
        this.taskManager = new AiTaskManager(this);
    }

    @Override
    protected void registerGoals() {
        buildGoal = new BuildGoal(this);
        ExecuteTaskGoal executeGoal = new ExecuteTaskGoal(this);

        goalSelector.addGoal(1, new FloatGoal(this));
        goalSelector.addGoal(2, new CombatAssistGoal(this));
        goalSelector.addGoal(3, executeGoal);
        goalSelector.addGoal(4, buildGoal);
        goalSelector.addGoal(5, new com.milkdromeda.aiassistant.entity.goal.FollowOwnerGoal(this, 1.0, ModConfig.get().followDistance, 64.0));
        goalSelector.addGoal(6, new RandomLookAroundGoal(this));
        goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 0.8));
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.ATTACK_DAMAGE, 6.0)
                .add(Attributes.FOLLOW_RANGE, 40.0)
                .add(Attributes.ARMOR, 4.0);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide()) return;

        taskManager.tick();

        if (idleMessageTimer == 20 && mode == Mode.IDLE) {
            messageOwner("Ready! Tell me what to do with /ai <task>");
        }
        idleMessageTimer++;

        if (mode == Mode.FOLLOWING && getLastHurtByMob() != null) {
            mode = Mode.GUARDING;
        }

        if (getHealth() < getMaxHealth() * 0.25f && mode == Mode.FIGHTING) {
            broadcastMessage("Taking heavy damage, retreating!");
            mode = Mode.FOLLOWING;
        }

        if (mode == Mode.EXECUTING && !taskManager.isWaiting() && !taskManager.hasPlan()) {
            finishTask();
        }
    }

    public void giveTask(String task, ServerPlayer issuer) {
        if (!ModConfig.get().hasApiToken()) {
            issuer.sendSystemMessage(Component.literal(
                    "[AI] No HuggingFace token. Use: /ai settings hf_token <token>"));
            return;
        }
        pendingTask = task;
        mode = Mode.EXECUTING;
        taskManager.clearPlan();
        broadcastMessage("Thinking: " + task);
        taskManager.requestPlan(task);
    }

    public void finishTask() {
        broadcastMessage("Done: " + (pendingTask != null ? pendingTask : "task complete"));
        pendingTask = null;
        mode = Mode.FOLLOWING;
    }

    public void broadcastMessage(String msg) {
        if (!level().isClientSide()) {
            level().players().forEach(p ->
                    p.sendSystemMessage(Component.literal("[" + assistantName + "] " + msg)));
        }
    }

    private void messageOwner(String msg) {
        Player owner = getOwnerPlayer();
        if (owner != null) {
            owner.sendSystemMessage(Component.literal("[" + assistantName + "] " + msg));
        } else {
            broadcastMessage(msg);
        }
    }

    // ---- NBT ----

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putString("AssistantName", assistantName);
        output.putString("Mode", mode.name());
        if (ownerUuid != null) output.store("OwnerUuid", UUIDUtil.STRING_CODEC, ownerUuid);
        if (pendingTask != null) output.putString("PendingTask", pendingTask);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        assistantName = input.getStringOr("AssistantName", "ARIA");
        String modeStr = input.getStringOr("Mode", "FOLLOWING");
        try { mode = Mode.valueOf(modeStr); } catch (IllegalArgumentException ignored) { mode = Mode.FOLLOWING; }
        input.read("OwnerUuid", UUIDUtil.STRING_CODEC).ifPresent(uuid -> ownerUuid = uuid);
        pendingTask = input.getString("PendingTask").orElse(null);
    }

    // ---- Getters / Setters ----

    public Mode getMode() { return mode; }
    public void setMode(Mode mode) { this.mode = mode; }
    public String getAssistantName() { return assistantName; }
    public void setAssistantName(String name) { this.assistantName = name; }
    public UUID getOwnerUuid() { return ownerUuid; }
    public void setOwnerUuid(UUID uuid) { this.ownerUuid = uuid; }
    public AiTaskManager getTaskManager() { return taskManager; }

    public Player getOwnerPlayer() {
        if (ownerUuid == null) return null;
        if (level() instanceof ServerLevel sl) return sl.getPlayerByUUID(ownerUuid);
        return null;
    }

    @Override
    protected Component getTypeName() {
        return Component.literal(assistantName);
    }
}
