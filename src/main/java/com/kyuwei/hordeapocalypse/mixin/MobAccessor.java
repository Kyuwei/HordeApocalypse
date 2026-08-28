package com.kyuwei.hordeapocalypse.mixin;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes {@link Mob}'s protected {@code goalSelector} so the horde AI can be
 * attached to a mob from outside the entity class.
 */
@Mixin(Mob.class)
public interface MobAccessor {
    @Accessor("goalSelector")
    GoalSelector hordeapocalypse$getGoalSelector();
}
