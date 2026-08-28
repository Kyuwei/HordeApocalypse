package com.kyuwei.hordeapocalypse.state;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.kyuwei.hordeapocalypse.HordeApocalypse;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Horde state persisted in the overworld save folder, so an interrupted horde
 * resumes correctly and spawned mobs stay accounted for across restarts.
 *
 * <p>UUIDs are stored as plain strings rather than through a UUID codec: it
 * keeps the on-disk format obvious and avoids depending on a helper class.
 */
public class HordeState extends SavedData {
    public static final Identifier ID =
            Identifier.fromNamespaceAndPath(HordeApocalypse.MOD_ID, "horde_state");

    public boolean hordeActive;
    public int hordeTicksRemaining;
    public int hordeStartDay;
    public final Set<UUID> hordeMobIds;

    public HordeState() {
        this(false, 0, 0, List.of());
    }

    public HordeState(boolean active, int ticksRemaining, int startDay, List<String> mobIds) {
        this.hordeActive = active;
        this.hordeTicksRemaining = ticksRemaining;
        this.hordeStartDay = startDay;
        this.hordeMobIds = new LinkedHashSet<>();
        for (String raw : mobIds) {
            try {
                this.hordeMobIds.add(UUID.fromString(raw));
            } catch (IllegalArgumentException ignored) {
                // Skip malformed entries rather than fail the whole world load.
            }
        }
    }

    public static final Codec<HordeState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.BOOL.optionalFieldOf("hordeActive", false).forGetter(s -> s.hordeActive),
            Codec.INT.optionalFieldOf("hordeTicksRemaining", 0).forGetter(s -> s.hordeTicksRemaining),
            Codec.INT.optionalFieldOf("hordeStartDay", 0).forGetter(s -> s.hordeStartDay),
            Codec.STRING.listOf().optionalFieldOf("hordeMobIds", List.of()).forGetter(HordeState::mobIdStrings)
    ).apply(instance, HordeState::new));

    public static final SavedDataType<HordeState> TYPE =
            new SavedDataType<>(ID, HordeState::new, CODEC, null);

    private List<String> mobIdStrings() {
        List<String> out = new ArrayList<>(hordeMobIds.size());
        for (UUID id : hordeMobIds) out.add(id.toString());
        return out;
    }

    public static HordeState get(ServerLevel overworld) {
        return overworld.getDataStorage().computeIfAbsent(TYPE);
    }

    public void beginHorde(int day, int durationTicks) {
        hordeActive = true;
        hordeStartDay = day;
        hordeTicksRemaining = durationTicks;
        setDirty();
    }

    public boolean isHordeMob(java.util.UUID id) {
        return hordeMobIds.contains(id);
    }

    /**
     * Ends the horde. {@link #hordeStartDay} is kept so the same night cannot
     * trigger a second horde, and {@link #hordeMobIds} is kept so mobs that
     * were out of reach (unloaded chunks) can still be destroyed when they
     * load again — nothing else would ever remove them.
     */
    public void endHorde() {
        hordeActive = false;
        hordeTicksRemaining = 0;
        setDirty();
    }

    public void trackMob(UUID id) {
        hordeMobIds.add(id);
        setDirty();
    }

    public void untrackMob(UUID id) {
        if (hordeMobIds.remove(id)) setDirty();
    }
}
