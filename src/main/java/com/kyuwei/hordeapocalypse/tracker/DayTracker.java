package com.kyuwei.hordeapocalypse.tracker;

import com.kyuwei.hordeapocalypse.HordeApocalypse;
import com.kyuwei.hordeapocalypse.config.ModConfig;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

/**
 * Tracks the survival day.
 *
 * <p>The day number is derived from {@link ServerLevel#getGameTime()}, the
 * monotonic tick counter. Unlike the visual clock it cannot be rewound with
 * {@code /time set} nor frozen by {@code doDaylightCycle}, so the horde schedule
 * cannot be gamed. The flip side is that it drifts away from the clock players
 * see whenever they sleep or an operator changes the time — {@code
 * /hordeapocalypse day <n>} exists to realign it for testing and support.
 */
public class DayTracker {
    private static final long TICKS_PER_DAY = 24000L;
    private static final long UNINITIALIZED = Long.MIN_VALUE;

    private long lastObservedDayIndex = UNINITIALIZED;
    /** Operator-supplied offset, in days, applied on top of the derived day. */
    private int dayOffset = 0;
    private boolean initialised = false;

    public void tick(MinecraftServer server) {
        if (server == null) return;
        ServerLevel overworld = server.overworld();
        if (overworld == null) return;

        long currentDayIndex = overworld.getGameTime() / TICKS_PER_DAY;

        if (lastObservedDayIndex == UNINITIALIZED) {
            lastObservedDayIndex = currentDayIndex;
            initialised = true;
            return;
        }

        if (currentDayIndex != lastObservedDayIndex) {
            lastObservedDayIndex = currentDayIndex;
            HordeApocalypse.LOGGER.info("Survival day is now {}", getCurrentDay());
        }
    }

    /** Current survival day, 1-based. */
    public int getCurrentDay() {
        long index = lastObservedDayIndex == UNINITIALIZED ? 0 : lastObservedDayIndex;
        long day = index + 1 + dayOffset;
        return (int) Math.max(1, Math.min(Integer.MAX_VALUE, day));
    }

    /**
     * True once the tracker has seen the world at least one tick. Until then the
     * reported day is a placeholder and must not be used to bake in mob scaling.
     */
    public boolean isInitialised() {
        return initialised;
    }

    /** Shifts the reported day so it becomes {@code targetDay}. Debug aid. */
    public void setCurrentDay(int targetDay) {
        dayOffset += targetDay - getCurrentDay();
    }

    /**
     * Clears all state. Called when a server starts: in singleplayer the mod
     * instance outlives a world, and a world with a smaller game time would
     * otherwise report the previous world's day.
     */
    public void reset() {
        lastObservedDayIndex = UNINITIALIZED;
        dayOffset = 0;
        initialised = false;
    }

    /**
     * Whether today brings a horde. Besides every {@code hordeDayInterval} days,
     * the final day itself always qualifies — otherwise the day-100 apocalypse
     * would never fire, 100 not being a multiple of 7.
     */
    public boolean isHordeDay() {
        ModConfig config = HordeApocalypse.getConfig();
        if (config == null) return false;
        int day = getCurrentDay();
        if (day == config.maxDifficultyDay) return true;
        int interval = config.hordeDayInterval;
        return interval > 0 && day % interval == 0;
    }

    /** Whether the day-100 bosses should join the horde. */
    public boolean isApocalypseDay() {
        ModConfig config = HordeApocalypse.getConfig();
        return config != null && getCurrentDay() >= config.maxDifficultyDay;
    }
}
