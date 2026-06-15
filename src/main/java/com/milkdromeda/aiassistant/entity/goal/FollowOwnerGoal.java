package com.milkdromeda.aiassistant.entity.goal;

import com.milkdromeda.aiassistant.entity.AiAssistantEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;

import java.util.EnumSet;

public class FollowOwnerGoal extends Goal {
    private final AiAssistantEntity entity;
    private Player owner;
    private final double speed;
    private final double minDist;
    private final double maxDist;

    public FollowOwnerGoal(AiAssistantEntity entity, double speed, double minDist, double maxDist) {
        this.entity = entity;
        this.speed = speed;
        this.minDist = minDist;
        this.maxDist = maxDist;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (entity.getMode() != AiAssistantEntity.Mode.FOLLOWING) return false;
        owner = entity.getOwnerPlayer();
        return owner != null && entity.distanceToSqr(owner) > minDist * minDist;
    }

    @Override
    public boolean canContinueToUse() {
        return entity.getMode() == AiAssistantEntity.Mode.FOLLOWING
                && owner != null
                && entity.distanceToSqr(owner) > minDist * minDist;
    }

    @Override
    public void tick() {
        if (owner == null) return;
        entity.getLookControl().setLookAt(owner, 30f, 30f);

        if (entity.distanceToSqr(owner) > maxDist * maxDist) {
            entity.setPos(owner.getX(), owner.getY(), owner.getZ());
        } else {
            entity.getNavigation().moveTo(owner, speed);
        }
    }

    @Override
    public void stop() {
        entity.getNavigation().stop();
    }
}
