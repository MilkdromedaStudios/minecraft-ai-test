package com.milkdromeda.aiassistant.entity.goal;

import com.milkdromeda.aiassistant.entity.AiAssistantEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;

/**
 * Walks the assistant over to nearby dropped items it wants — loot from a fight,
 * a sword on the ground, mined ores, anything it has room for. The actual
 * transfer into the inventory/equipment happens in {@link AiAssistantEntity}'s
 * pickup hook once it's close enough.
 *
 * <p>Runs only while idle, following or guarding, so it never abandons combat or
 * an active build/task (those goals sit at a higher priority and pre-empt it).
 */
public class CollectItemsGoal extends Goal {

    private static final double SEARCH_RADIUS = 10.0;

    private final AiAssistantEntity entity;
    private ItemEntity target;
    private int scanCooldown;

    public CollectItemsGoal(AiAssistantEntity entity) {
        this.entity = entity;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    private boolean modeAllows() {
        return switch (entity.getMode()) {
            case IDLE, FOLLOWING, GUARDING -> true;
            default -> false;
        };
    }

    @Override
    public boolean canUse() {
        if (!modeAllows()) return false;
        if (scanCooldown > 0) { scanCooldown--; return false; }
        scanCooldown = 10;
        target = findItem();
        return target != null;
    }

    @Override
    public boolean canContinueToUse() {
        return modeAllows() && target != null && target.isAlive() && !target.getItem().isEmpty();
    }

    @Override
    public void tick() {
        if (target == null) return;
        entity.getLookControl().setLookAt(target, 30f, 30f);
        entity.getNavigation().moveTo(target, 1.1);
    }

    @Override
    public void stop() {
        target = null;
        entity.getNavigation().stop();
    }

    private ItemEntity findItem() {
        AABB box = AABB.ofSize(entity.position(), SEARCH_RADIUS * 2, 6, SEARCH_RADIUS * 2);
        List<ItemEntity> items = entity.level().getEntitiesOfClass(ItemEntity.class, box,
                it -> it.isAlive() && !it.getItem().isEmpty() && entity.canTake(it.getItem()));
        return items.stream()
                .min(Comparator.comparingDouble(entity::distanceToSqr))
                .orElse(null);
    }
}
