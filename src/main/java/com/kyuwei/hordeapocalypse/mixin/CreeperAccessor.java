package com.kyuwei.hordeapocalypse.mixin;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.monster.Creeper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes {@link Creeper}'s private {@code DATA_IS_POWERED} key so a horde
 * creeper can be charged directly, instead of round-tripping the entity
 * through NBT (which duplicated every field of the entity).
 */
@Mixin(Creeper.class)
public interface CreeperAccessor {
    @Accessor("DATA_IS_POWERED")
    static EntityDataAccessor<Boolean> getPoweredKey() {
        throw new UnsupportedOperationException("implemented via mixin");
    }
}
