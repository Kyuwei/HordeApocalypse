package com.kyuwei.hordeapocalypse.tracker;

import com.kyuwei.hordeapocalypse.HordeApocalypse;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;

/**
 * Derives the current in-game day from the overworld's total world time.
 * Because total world time is persistent and monotonic, no separate
 * save/load is required. /time set commands cannot rewind this counter.
 */
public class DayTracker {
    private static final long TICKS_PER_DAY = 24000L;
    private static final long UNINITIALIZED = Long.MIN_VALUE;

    private long lastObservedDayIndex = UNINITIALIZED;
    private boolean dayChanged = false;

    public void tick(MinecraftServer server) {
        if (server == null) return;
        ServerWorld overworld = server.getOverworld();
        if (overworld == null) return;

        long currentDayIndex = overworld.getTime() / TICKS_PER_DAY;

        if (lastObservedDayIndex == UNINITIALIZED) {
            lastObservedDayIndex = currentDayIndex;
            return;
        }

        if (currentDayIndex > lastObservedDayIndex) {
            lastObservedDayIndex = currentDayIndex;
            dayChanged = true;
            HordeApocalypse.LOGGER.info("Day changed to: {}", getCurrentDay());
        }
    }

    /** Current day number, 1-based, stable across restarts. */
    public int getCurrentDay() {
        long index = lastObservedDayIndex == UNINITIALIZED ? 0 : lastObservedDayIndex;
        return (int) Math.max(1, index + 1);
    }

    /**
     * Clears all state. Must be called when a server starts, because in
     * singleplayer the same mod instance survives across world loads — without
     * a reset, opening a world with a smaller total time than the previous one
     * would freeze day detection and report the wrong day.
     */
    public void reset() {
        lastObservedDayIndex = UNINITIALIZED;
        dayChanged = false;
    }

    /** Reads-and-clears the day-change flag. True exactly once per day transition. */
    public boolean consumeDayChanged() {
        if (dayChanged) {
            dayChanged = false;
            return true;
        }
        return false;
    }

    public boolean isHordeDay() {
        int interval = HordeApocalypse.getConfig().hordeDayInterval;
        return interval > 0 && getCurrentDay() % interval == 0;
    }

    public boolean isFinalDay() {
        return getCurrentDay() >= HordeApocalypse.getConfig().maxDifficultyDay;
    }
}
