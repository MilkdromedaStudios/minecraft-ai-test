package com.milkdromeda.aiassistant.entity.goal;

import com.milkdromeda.aiassistant.ai.ActionStep;
import com.milkdromeda.aiassistant.entity.AiAssistantEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.List;

public class ExecuteTaskGoal extends Goal {
    private final AiAssistantEntity entity;
    private ActionStep currentStep;
    private int stepTimer = 0;
    private int waitRemaining = 0;

    public ExecuteTaskGoal(AiAssistantEntity entity) {
        this.entity = entity;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.TARGET));
    }

    @Override
    public boolean canUse() {
        return entity.getMode() == AiAssistantEntity.Mode.EXECUTING
                && entity.getTaskManager().hasPlan();
    }

    @Override
    public boolean canContinueToUse() {
        return entity.getMode() == AiAssistantEntity.Mode.EXECUTING
                && (entity.getTaskManager().hasPlan() || currentStep != null);
    }

    @Override
    public void tick() {
        entity.getTaskManager().tick();

        if (waitRemaining > 0) { waitRemaining--; return; }

        if (currentStep == null) {
            currentStep = entity.getTaskManager().pollNextStep();
            stepTimer = 0;
            if (currentStep == null) { entity.finishTask(); return; }
        }

        boolean done = executeStep(currentStep);
        stepTimer++;

        if (done || stepTimer > 200) {
            currentStep = null;
            // Only apply inter-step delay for non-WAIT steps (WAIT sets its own delay)
            if (currentStep == null) {
                waitRemaining = com.milkdromeda.aiassistant.config.ModConfig.get().actionTickDelay;
            }
        }
    }

    @Override
    public void stop() { currentStep = null; stepTimer = 0; }

    private boolean executeStep(ActionStep step) {
        return switch (step.type()) {
            case MOVE_TO        -> execMoveTo(step);
            case PLACE_BLOCK    -> execPlaceBlock(step);
            case BREAK_BLOCK    -> execBreakBlock(step);
            case ATTACK_NEAREST -> execAttackNearest(step);
            case FOLLOW_PLAYER  -> execFollowPlayer(step);
            case LOOK_AT        -> execLookAt(step);
            case CHAT           -> execChat(step);
            case WAIT           -> execWait(step);
            case COLLECT_ITEM   -> execCollectItem(step);
            case STOP           -> { entity.setMode(AiAssistantEntity.Mode.IDLE); yield true; }
        };
    }

    private boolean execMoveTo(ActionStep step) {
        double x = step.getDouble("x", entity.getX()), y = step.getDouble("y", entity.getY()),
               z = step.getDouble("z", entity.getZ());
        if (entity.distanceToSqr(x, y, z) < 4) return true;
        entity.getNavigation().moveTo(x, y, z, 1.0);
        entity.getLookControl().setLookAt(x, y, z, 30f, 30f);
        return false;
    }

    private boolean execPlaceBlock(ActionStep step) {
        int x = step.getInt("x", (int) entity.getX()),
            y = step.getInt("y", (int) entity.getY()),
            z = step.getInt("z", (int) entity.getZ());
        String blockId = step.getString("block", "minecraft:stone");
        BlockPos pos = new BlockPos(x, y, z);

        if (entity.distanceToSqr(Vec3.atCenterOf(pos)) > 25) {
            entity.getNavigation().moveTo(x, y, z, 1.0);
            return false;
        }
        Level level = entity.level();
        if (!level.isClientSide() && level.getBlockState(pos).canBeReplaced()) {
            Identifier id = Identifier.tryParse(blockId);
            if (id != null) {
                BuiltInRegistries.BLOCK.get(id).ifPresent(holder -> {
                    level.setBlock(pos, holder.value().defaultBlockState(), Block.UPDATE_ALL);
                    entity.swing(InteractionHand.MAIN_HAND);
                });
            }
        }
        return true;
    }

    private boolean execBreakBlock(ActionStep step) {
        int x = step.getInt("x", (int) entity.getX()),
            y = step.getInt("y", (int) entity.getY()),
            z = step.getInt("z", (int) entity.getZ());
        BlockPos pos = new BlockPos(x, y, z);

        if (entity.distanceToSqr(Vec3.atCenterOf(pos)) > 25) {
            entity.getNavigation().moveTo(x, y, z, 1.0);
            return false;
        }
        Level level = entity.level();
        if (!level.isClientSide()) {
            level.destroyBlock(pos, true, entity);
            entity.swing(InteractionHand.MAIN_HAND);
        }
        return true;
    }

    private boolean execAttackNearest(ActionStep step) {
        double range = step.getDouble("range", 16.0);
        AABB box = AABB.ofSize(entity.position(), range * 2, 10, range * 2);
        List<Monster> hostiles = entity.level().getEntitiesOfClass(Monster.class, box, LivingEntity::isAlive);
        if (hostiles.isEmpty()) return true;

        LivingEntity target = hostiles.stream()
                .min((a, b) -> Double.compare(entity.distanceToSqr(a), entity.distanceToSqr(b)))
                .orElse(null);
        if (target == null) return true;

        entity.getLookControl().setLookAt(target, 30f, 30f);
        entity.getNavigation().moveTo(target, 1.2);

        if (entity.distanceToSqr(target) < 9 && entity.level() instanceof ServerLevel sl) {
            entity.swing(InteractionHand.MAIN_HAND);
            entity.doHurtTarget(sl, target);
            return !target.isAlive();
        }
        return false;
    }

    private boolean execFollowPlayer(ActionStep step) {
        String name = step.getString("name", "");
        double dist = step.getDouble("distance", 3.0);

        Player player = null;
        if (!name.isBlank() && entity.level() instanceof ServerLevel sl) {
            player = sl.getServer().getPlayerList().getPlayerByName(name);
        }
        if (player == null) player = entity.getOwnerPlayer();
        if (player == null) return true;

        if (entity.distanceToSqr(player) > dist * dist) {
            entity.getNavigation().moveTo(player, 1.0);
            entity.getLookControl().setLookAt(player, 30f, 30f);
            return false;
        }
        return true;
    }

    private boolean execLookAt(ActionStep step) {
        entity.getLookControl().setLookAt(
                step.getDouble("x", entity.getX()),
                step.getDouble("y", entity.getY()),
                step.getDouble("z", entity.getZ()), 30f, 30f);
        return true;
    }

    private boolean execChat(ActionStep step) {
        String msg = step.getString("message", "...");
        if (!entity.level().isClientSide()) {
            entity.level().players().forEach(p ->
                    p.sendSystemMessage(Component.literal("[" + entity.getAssistantName() + "] " + msg)));
        }
        return true;
    }

    private boolean execWait(ActionStep step) {
        // Set wait and skip the normal inter-step delay (return false keeps currentStep set)
        waitRemaining = step.getInt("ticks", 20);
        currentStep = null; // consume step now so we don't re-enter
        return true;
    }

    private boolean execCollectItem(ActionStep step) {
        double x = step.getDouble("x", entity.getX()),
               y = step.getDouble("y", entity.getY()),
               z = step.getDouble("z", entity.getZ());
        if (entity.distanceToSqr(x, y, z) > 4) {
            entity.getNavigation().moveTo(x, y, z, 1.0);
            return false;
        }
        return true;
    }
}
