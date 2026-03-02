package com.kyuwei.hordeapocalypse.tracker;

import com.kyuwei.hordeapocalypse.HordeApocalypse;
import net.minecraft.server.MinecraftServer;

/**
 * Tracks in-game days using total world time (getTime()), which is persistent
 * across server restarts — no need for separate save/load.
 */
public class DayTracker {
    private long lastDayCycle = -1;
    private boolean dayChanged = false;

    public void tick(MinecraftServer server) {
        long totalTime = server.getOverworld().getTime();
        long currentDayCycle = totalTime / 24000L;

        if (lastDayCycle == -1) {
            // First tick after server start: initialize without triggering a horde
            lastDayCycle = currentDayCycle;
            return;
        }

        if (currentDayCycle != lastDayCycle) {
            lastDayCycle = currentDayCycle;
            dayChanged = true;
            HordeApocalypse.LOGGER.info("Day changed to: {}", getCurrentDay());
        }
    }

    /**
     * Returns the current day number (1-based).
     * Derived from total world time so it persists across restarts.
     */
    public int getCurrentDay() {
        return (int) Math.max(1, lastDayCycle + 1);
    }

    /**
     * Checks and consumes the day-changed flag.
     * Returns true only once per day transition.
     */
    public boolean hasDayChanged() {
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