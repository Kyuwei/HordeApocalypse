package com.kyuwei.hordeapocalypse.ai;

import com.kyuwei.hordeapocalypse.HordeApocalypse;
import com.kyuwei.hordeapocalypse.config.ModConfig;

/**
 * Server-wide ceiling on how many blocks the whole horde may destroy in a
 * single tick. Every destroyed block triggers neighbour updates and forces
 * nearby mobs to recompute their paths, so an unbounded horde of 300 diggers
 * can stall the server on its own.
 */
public final class BlockBreakBudget {
    private static int remaining = 0;

    private BlockBreakBudget() {}

    /** Called once per server tick, before the entities are ticked. */
    public static void refill() {
        ModConfig config = HordeApocalypse.getConfig();
        remaining = (config != null) ? config.maxBlockBreaksPerTick : 8;
    }

    /** Reserves one block break; false when this tick's budget is exhausted. */
    public static boolean tryConsume() {
        if (remaining <= 0) return false;
        remaining--;
        return true;
    }
}
