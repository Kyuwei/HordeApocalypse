package com.kyuwei.hordeapocalypse.scaling;

import com.kyuwei.hordeapocalypse.HordeApocalypse;
import com.kyuwei.hordeapocalypse.config.ModConfig;
import com.kyuwei.hordeapocalypse.tracker.DayTracker;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;

/**
 * Scales hostile mobs with the survival day.
 *
 * <p>The scaling is expressed as <em>transient attribute modifiers</em> keyed by
 * a stable id rather than by multiplying the base value. That matters: the
 * original implementation multiplied the base attribute on every entity
 * construction, so a zombie's health compounded (x3, then x9, then x27...) every
 * time the world was reloaded. Transient modifiers are never written to disk and
 * are recomputed from the current day each time the mob loads, which makes the
 * operation naturally idempotent and keeps mobs progressing over time.
 */
public final class MobScalingHandler {
    private static final Identifier HEALTH_ID =
            Identifier.fromNamespaceAndPath(HordeApocalypse.MOD_ID, "day_scaling_health");
    private static final Identifier DAMAGE_ID =
            Identifier.fromNamespaceAndPath(HordeApocalypse.MOD_ID, "day_scaling_damage");
    private static final Identifier SPEED_ID =
            Identifier.fromNamespaceAndPath(HordeApocalypse.MOD_ID, "day_scaling_speed");

    private MobScalingHandler() {}

    public static void applyScaling(Entity entity, ServerLevel level) {
        if (!(entity instanceof Monster monster)) return;

        ModConfig config = HordeApocalypse.getConfig();
        DayTracker tracker = HordeApocalypse.getDayTracker();
        // Before the tracker has seen the world the reported day is a
        // placeholder; scaling now would freeze every mob loaded at startup.
        if (config == null || tracker == null || !tracker.isInitialised()) return;

        int day = tracker.getCurrentDay();
        boolean wasAtFullHealth = monster.getHealth() >= monster.getMaxHealth() - 1.0e-4f;

        applyMultiplier(monster, Attributes.MAX_HEALTH, HEALTH_ID, config.getHealthMultiplier(day));
        applyMultiplier(monster, Attributes.ATTACK_DAMAGE, DAMAGE_ID, config.getDamageMultiplier(day));
        applyMultiplier(monster, Attributes.MOVEMENT_SPEED, SPEED_ID, config.getSpeedMultiplier(day));

        // Top the mob up only if it was undamaged, so a wounded mob is not
        // silently healed every time its chunk reloads.
        if (wasAtFullHealth) {
            monster.setHealth(monster.getMaxHealth());
        }
    }

    private static void applyMultiplier(LivingEntity entity, Holder<Attribute> attribute,
                                        Identifier id, double multiplier) {
        AttributeInstance instance = entity.getAttribute(attribute);
        // Skeletons, creepers and the Wither have no ATTACK_DAMAGE attribute:
        // their damage comes from projectiles and explosions, so the attack
        // multiplier legitimately does nothing for them.
        if (instance == null) return;

        double amount = multiplier - 1.0;
        if (amount <= 0.0) {
            instance.removeModifier(id);
            return;
        }
        instance.addOrUpdateTransientModifier(
                new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
    }
}
