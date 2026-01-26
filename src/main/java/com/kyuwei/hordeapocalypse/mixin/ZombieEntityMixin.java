package com.kyuwei.hordeapocalypse.mixin;

import com.kyuwei.hordeapocalypse.ai.BlockBreakGoal;
import com.kyuwei.hordeapocalypse.spawner.HordeSpawner;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ZombieEntity.class)
public abstract class ZombieEntityMixin {
    
    @Inject(method = "initGoals", at = @At("HEAD"))
    private void addBlockBreakGoal(CallbackInfo ci) {
        ZombieEntity zombie = (ZombieEntity) (Object) this;
        zombie.goalSelector.add(1, new BlockBreakGoal(zombie));
    }
}