package com.kyuwei.hordeapocalypse.scaling;

import com.kyuwei.hordeapocalypse.HordeApocalypse;
import com.kyuwei.hordeapocalypse.config.ModConfig;
import com.kyuwei.hordeapocalypse.tracker.DayTracker;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.world.ServerWorld;

/**
 * Idempotent scaling of hostile mobs.
 * <p>
 * Triggered once per mob via {@link ServerEntityEvents#ENTITY_LOAD}. A
 * persistent command tag {@code HAScaled} prevents re-scaling on load —
 * critical, because the original mixin re-multiplied attributes every time
 * the entity was constructed, causing exponential growth across restarts.
 */
public final class MobScalingHandler {
    public static final String SCALED_TAG = "HAScaled";

    private MobScalingHandler() {}

    public static void register() {
        ServerEntityEvents.ENTITY_LOAD.register(MobScalingHandler::onEntityLoad);
    }

    private static void onEntityLoad(Entity entity, ServerWorld world) {
        if (!(entity instanceof HostileEntity hostile)) return;
        if (hostile.getCommandTags().contains(SCALED_TAG)) return;

        ModConfig config = HordeApocalypse.getConfig();
        DayTracker tracker = HordeApocalypse.getDayTracker();
        if (config == null || tracker == null) return;

        int currentDay = tracker.getCurrentDay();
        if (currentDay <= 1) {
            // Day 1: multipliers are 1.0 anyway, but still tag to avoid re-checking.
            hostile.addCommandTag(SCALED_TAG);
            return;
        }

        applyMultiplier(hostile, EntityAttributes.MAX_HEALTH,
                        config.getHealthMultiplier(currentDay), true);
        applyMultiplier(hostile, EntityAttributes.ATTACK_DAMAGE,
                        config.getDamageMultiplier(currentDay), false);
        applyMultiplier(hostile, EntityAttributes.MOVEMENT_SPEED,
                        config.getSpeedMultiplier(currentDay), false);

        hostile.addCommandTag(SCALED_TAG);
    }

    private static void applyMultiplier(MobEntity mob,
                                        RegistryEntry<EntityAttribute> attribute,
                                        double multiplier, boolean fullyHeal) {
        EntityAttributeInstance instance = mob.getAttributeInstance(attribute);
        if (instance == null || multiplier == 1.0) return;
        double scaled = instance.getBaseValue() * multiplier;
        instance.setBaseValue(scaled);
        if (fullyHeal) {
            mob.setHealth((float) scaled);
        }
    }
}
