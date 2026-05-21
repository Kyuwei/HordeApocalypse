package com.kyuwei.hordeapocalypse.mixin;

import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.mob.CreeperEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes {@link CreeperEntity}'s private static {@code CHARGED} TrackedData key
 * so we can flip a creeper to powered without a full NBT round-trip.
 */
@Mixin(CreeperEntity.class)
public interface CreeperEntityAccessor {
    @Accessor("CHARGED")
    static TrackedData<Boolean> getChargedKey() {
        throw new AssertionError("Mixin accessor body must be replaced at runtime");
    }
}
