package com.kyuwei.hordeapocalypse.state;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Uuids;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateType;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Persistent horde state attached to the overworld save directory.
 * Survives server restarts so an interrupted horde resumes correctly
 * and mob UUIDs are not lost.
 */
public class HordePersistentState extends PersistentState {
    public static final String STATE_ID = "hordeapocalypse_state";

    public boolean hordeActive;
    public int hordeTicksRemaining;
    public int hordeStartDay;
    public final Set<UUID> hordeMobIds;

    public HordePersistentState() {
        this(false, 0, 0, List.of());
    }

    public HordePersistentState(boolean active, int ticksRemaining, int startDay, List<UUID> mobIds) {
        this.hordeActive = active;
        this.hordeTicksRemaining = ticksRemaining;
        this.hordeStartDay = startDay;
        this.hordeMobIds = new LinkedHashSet<>(mobIds);
    }

    public static final Codec<HordePersistentState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.BOOL.optionalFieldOf("hordeActive", false).forGetter(s -> s.hordeActive),
            Codec.INT.optionalFieldOf("hordeTicksRemaining", 0).forGetter(s -> s.hordeTicksRemaining),
            Codec.INT.optionalFieldOf("hordeStartDay", 0).forGetter(s -> s.hordeStartDay),
            Uuids.CODEC.listOf().optionalFieldOf("hordeMobIds", List.of()).forGetter(s -> List.copyOf(s.hordeMobIds))
    ).apply(instance, HordePersistentState::new));

    public static final PersistentStateType<HordePersistentState> TYPE = new PersistentStateType<>(
            STATE_ID,
            HordePersistentState::new,
            CODEC,
            null
    );

    public static HordePersistentState get(ServerWorld overworld) {
        return overworld.getPersistentStateManager().getOrCreate(TYPE);
    }

    public void reset() {
        hordeActive = false;
        hordeTicksRemaining = 0;
        hordeStartDay = 0;
        hordeMobIds.clear();
        markDirty();
    }
}
