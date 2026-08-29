package com.kyuwei.hordeapocalypse.util;

import net.minecraft.server.level.ServerLevel;

/**
 * Single point of access to the world clock.
 *
 * <p>26.2 renamed the old {@code getDayTime()}: {@link net.minecraft.world.level.Level}
 * now exposes {@code getOverworldClockTime()} and {@code getDefaultClockTime()}.
 * We want the overworld's day/night cycle specifically — the horde is an
 * overworld event — so the former is both the accurate choice and the one that
 * stays correct whichever level it is called on.
 *
 * <p>Note the distinction that the whole horde schedule rests on:
 * <ul>
 *   <li>{@code getGameTime()} — monotonic tick counter, never rewound or frozen.
 *       Used for the survival day number.</li>
 *   <li>the time of day below — the visual clock, moved by {@code /time set},
 *       by sleeping, and frozen by {@code doDaylightCycle}. Used to decide when
 *       dusk falls.</li>
 * </ul>
 */
public final class LevelTime {
    public static final long TICKS_PER_DAY = 24000L;

    private LevelTime() {}

    /** Time of day in {@code [0, 24000)}; 0 is sunrise, ~13000 is dusk. */
    public static long timeOfDay(ServerLevel level) {
        return Math.floorMod(level.getOverworldClockTime(), TICKS_PER_DAY);
    }
}
