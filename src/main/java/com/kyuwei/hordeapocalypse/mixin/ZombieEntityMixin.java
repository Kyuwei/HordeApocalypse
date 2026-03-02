package com.kyuwei.hordeapocalypse.mixin;

import com.kyuwei.hordeapocalypse.ai.BlockBreakGoal;
import net.minecraft.entity.mob.ZombieEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ZombieEntity.class)
public abstract class ZombieEntityMixin {

    /**
     * Inject at TAIL so all vanilla goals are already registered.
     * Priority 3 ensures block breaking runs only when the mob cannot reach its target
     * (melee attack at priority 2 takes precedence when in range).
     * The goal uses no controls, so it runs in parallel with navigation.
     */
    @Inject(method = "initGoals", at = @At("TAIL"))
    private void addBlockBreakGoal(CallbackInfo ci) {
        ZombieEntity zombie = (ZombieEntity) (Object) this;
        zombie.goalSelector.add(3, new BlockBreakGoal(zombie));
    }
}