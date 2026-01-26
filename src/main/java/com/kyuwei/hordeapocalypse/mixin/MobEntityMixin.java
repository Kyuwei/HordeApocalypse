package com.kyuwei.hordeapocalypse.mixin;

import com.kyuwei.hordeapocalypse.HordeApocalypse;
import com.kyuwei.hordeapocalypse.config.ModConfig;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MobEntity.class)
public abstract class MobEntityMixin {
    
    @Inject(method = "<init>", at = @At("RETURN"))
    private void onMobSpawn(EntityType<?> entityType, World world, CallbackInfo ci) {
        if (world.isClient) return;
        
        MobEntity mob = (MobEntity) (Object) this;
        int currentDay = HordeApocalypse.getDayTracker().getCurrentDay();
        ModConfig config = HordeApocalypse.getConfig();
        
        // Appliquer les multiplicateurs de difficulté progressifs
        double healthMultiplier = config.getHealthMultiplier(currentDay);
        double damageMultiplier = config.getDamageMultiplier(currentDay);
        double speedMultiplier = config.getSpeedMultiplier(currentDay);
        
        // Santé
        if (mob.getAttributeInstance(EntityAttributes.MAX_HEALTH) != null) {
            double baseHealth = mob.getAttributeInstance(EntityAttributes.MAX_HEALTH).getBaseValue();
            mob.getAttributeInstance(EntityAttributes.MAX_HEALTH).setBaseValue(baseHealth * healthMultiplier);
            mob.setHealth((float)(baseHealth * healthMultiplier));
        }
        
        // Dégâts
        if (mob.getAttributeInstance(EntityAttributes.ATTACK_DAMAGE) != null) {
            double baseDamage = mob.getAttributeInstance(EntityAttributes.ATTACK_DAMAGE).getBaseValue();
            mob.getAttributeInstance(EntityAttributes.ATTACK_DAMAGE).setBaseValue(baseDamage * damageMultiplier);
        }
        
        // Vitesse
        if (mob.getAttributeInstance(EntityAttributes.MOVEMENT_SPEED) != null) {
            double baseSpeed = mob.getAttributeInstance(EntityAttributes.MOVEMENT_SPEED).getBaseValue();
            mob.getAttributeInstance(EntityAttributes.MOVEMENT_SPEED).setBaseValue(baseSpeed * speedMultiplier);
        }
    }
}