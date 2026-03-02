package com.kyuwei.hordeapocalypse.mixin;

import com.kyuwei.hordeapocalypse.HordeApocalypse;
import com.kyuwei.hordeapocalypse.config.ModConfig;
import com.kyuwei.hordeapocalypse.tracker.DayTracker;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.HostileEntity;
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

        // Only scale hostile mobs — not animals, villagers, iron golems, etc.
        if (!(mob instanceof HostileEntity)) return;

        // Safety: config and tracker may not exist yet during world load
        ModConfig config = HordeApocalypse.getConfig();
        DayTracker tracker = HordeApocalypse.getDayTracker();
        if (config == null || tracker == null) return;

        int currentDay = tracker.getCurrentDay();
        double healthMult = config.getHealthMultiplier(currentDay);
        double damageMult = config.getDamageMultiplier(currentDay);
        double speedMult  = config.getSpeedMultiplier(currentDay);

        // Health
        EntityAttributeInstance healthAttr = mob.getAttributeInstance(EntityAttributes.MAX_HEALTH);
        if (healthAttr != null) {
            double base = healthAttr.getBaseValue();
            healthAttr.setBaseValue(base * healthMult);
            mob.setHealth((float) (base * healthMult));
        }

        // Damage
        EntityAttributeInstance damageAttr = mob.getAttributeInstance(EntityAttributes.ATTACK_DAMAGE);
        if (damageAttr != null) {
            damageAttr.setBaseValue(damageAttr.getBaseValue() * damageMult);
        }

        // Speed
        EntityAttributeInstance speedAttr = mob.getAttributeInstance(EntityAttributes.MOVEMENT_SPEED);
        if (speedAttr != null) {
            speedAttr.setBaseValue(speedAttr.getBaseValue() * speedMult);
        }
    }
}