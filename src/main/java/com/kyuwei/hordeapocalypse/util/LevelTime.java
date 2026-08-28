package com.kyuwei.hordeapocalypse.util;

import net.minecraft.server.level.ServerLevel;

/**
 * Single point of access to the world clock.
 *
 * <p>Isolated on purpose: the time-of-day accessor is the one Minecraft API in
 * this mod that could not be verified against a compiler before shipping, so it
 * is worth having exactly one line to adjust if the name differs.
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
        return Math.floorMod(level.getTimeOfDay(), TICKS_PER_DAY);
    }
}
