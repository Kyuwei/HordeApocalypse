package com.kyuwei.hordeapocalypse.event;

import com.kyuwei.hordeapocalypse.HordeApocalypse;
import com.kyuwei.hordeapocalypse.config.ModConfig;
import com.kyuwei.hordeapocalypse.spawner.HordeSpawner;
import com.kyuwei.hordeapocalypse.state.HordeState;
import com.kyuwei.hordeapocalypse.tracker.DayTracker;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Drives the horde lifecycle: it starts at dusk on a horde day and is over by
 * sunrise, when daylight finishes off whatever the players did not.
 */
public final class HordeEventHandler {
    private static final int TICKS_PER_DAY = 24000;
    /** Time of day at which the sun is back up and the undead start burning. */
    private static final int DAWN = 23500;

    /** Time of day observed last tick, to detect crossing dusk. */
    private static long previousDayTime = -1;

    private HordeEventHandler() {}

    public static void onServerTick(MinecraftServer server) {
        DayTracker tracker = HordeApocalypse.getDayTracker();
        ServerLevel overworld = server.overworld();
        if (tracker == null || overworld == null) return;

        HordeState state = HordeState.get(overworld);
        long dayTime = Math.floorMod(overworld.getDayTime(), TICKS_PER_DAY);

        if (!state.hordeActive) {
            maybeStartHorde(server, overworld, state, tracker, dayTime);
        } else {
            HordeSpawner.drainQueue(overworld);
            state.hordeTicksRemaining--;
            boolean daybreak = dayTime >= DAWN || dayTime < previousDayTime;
            if (state.hordeTicksRemaining <= 0 || daybreak) {
                endHorde(server, overworld, state);
            }
        }

        previousDayTime = dayTime;
    }

    private static void maybeStartHorde(MinecraftServer server, ServerLevel overworld,
                                        HordeState state, DayTracker tracker, long dayTime) {
        ModConfig config = HordeApocalypse.getConfig();
        if (config == null || !tracker.isHordeDay()) return;
        if (state.hordeStartDay == tracker.getCurrentDay()) return; // already done today

        // Fire on the tick the clock crosses dusk (or immediately if the server
        // starts up in the middle of the night on a horde day).
        long dusk = config.hordeStartTimeOfDay;
        boolean crossedDusk = previousDayTime >= 0
                ? (previousDayTime < dusk && dayTime >= dusk)
                : (dayTime >= dusk && dayTime < DAWN);
        if (!crossedDusk) return;

        startHorde(server, overworld, state, tracker.getCurrentDay(),
                   server.getPlayerList().getPlayers());
    }

    public static void onServerStopping(MinecraftServer server) {
        HordeSpawner.clearQueue();
        ServerLevel overworld = server.overworld();
        if (overworld != null) HordeState.get(overworld).setDirty();
    }

    /** Starts a horde immediately for one player. Used by the debug command. */
    public static boolean forceStartHorde(MinecraftServer server, ServerPlayer initiator) {
        ServerLevel overworld = server.overworld();
        if (overworld == null) return false;
        HordeState state = HordeState.get(overworld);
        if (state.hordeActive) return false;
        DayTracker tracker = HordeApocalypse.getDayTracker();
        int day = tracker != null ? tracker.getCurrentDay() : 1;
        return startHorde(server, overworld, state, day, List.of(initiator));
    }

    public static void forceEndHorde(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        if (overworld == null) return;
        endHorde(server, overworld, HordeState.get(overworld));
    }

    /**
     * @return false when nothing was started, so the caller can report honestly
     */
    private static boolean startHorde(MinecraftServer server, ServerLevel overworld,
                                      HordeState state, int day,
                                      List<? extends ServerPlayer> candidates) {
        ModConfig config = HordeApocalypse.getConfig();

        // Only overworld players: a nether-bound player would otherwise get a
        // horde dropped on the nether roof.
        List<ServerPlayer> eligible = new ArrayList<>();
        for (ServerPlayer player : candidates) {
            if (player.level().dimension() == Level.OVERWORLD) eligible.add(player);
        }
        // Check eligibility *before* committing, so an empty overworld does not
        // burn the day's horde on nobody.
        if (eligible.isEmpty()) {
            HordeApocalypse.LOGGER.info("Day {} horde skipped: no player in the overworld", day);
            return false;
        }

        state.beginHorde(day, config.hordeDurationTicks());
        HordeSpawner.clearQueue();

        List<Vec3> clusters = mergeIntoClusters(eligible, config.clusterMergeDistance);
        int queued = 0;
        for (Vec3 center : clusters) {
            int budget = config.maxConcurrentHordeMobs - queued;
            if (budget <= 0) break;
            queued += HordeSpawner.queueHorde(center, budget);
        }
        // The apocalypse bosses are a single set for the whole server, not one
        // set per group of players.
        boolean apocalypse = day >= config.maxDifficultyDay;
        if (apocalypse) {
            int budget = config.maxConcurrentHordeMobs - queued;
            if (budget > 0) queued += HordeSpawner.queueApocalypseBosses(clusters.get(0), budget);
        }

        server.getPlayerList().broadcastSystemMessage(Component.literal(apocalypse
                ? "§4§l[APOCALYPSE FINALE] Les ténèbres déferlent !"
                : "§c§l[HORDE - Jour " + day + "] Une horde approche !"), false);

        HordeApocalypse.LOGGER.info("Horde started on day {}: {} mobs queued across {} cluster(s)",
                day, queued, clusters.size());
        return true;
    }

    private static void endHorde(MinecraftServer server, ServerLevel overworld, HordeState state) {
        boolean wasActive = state.hordeActive;
        int removed = HordeSpawner.clearHordeMobs(overworld);
        if (wasActive) {
            server.getPlayerList().broadcastSystemMessage(
                    Component.literal("§a§l[HORDE] La horde s'est dissipée..."), false);
            HordeApocalypse.LOGGER.info("Horde ended, {} mobs removed", removed);
        }
    }

    /**
     * Groups players closer than {@code mergeDistance} and returns one centroid
     * per group, so a party of friends faces a single horde rather than one
     * each. Union-find over the distance graph; n is the player count.
     */
    private static List<Vec3> mergeIntoClusters(List<ServerPlayer> players, int mergeDistance) {
        int n = players.size();
        int[] parent = new int[n];
        Vec3[] positions = new Vec3[n];
        for (int i = 0; i < n; i++) {
            parent[i] = i;
            positions[i] = players.get(i).position();
        }

        if (mergeDistance > 0) {
            double mergeSq = (double) mergeDistance * mergeDistance;
            for (int i = 0; i < n; i++) {
                for (int j = i + 1; j < n; j++) {
                    double dx = positions[i].x - positions[j].x;
                    double dz = positions[i].z - positions[j].z;
                    if (dx * dx + dz * dz <= mergeSq) union(parent, i, j);
                }
            }
        }

        Map<Integer, List<Integer>> groups = new HashMap<>();
        for (int i = 0; i < n; i++) {
            groups.computeIfAbsent(find(parent, i), k -> new ArrayList<>()).add(i);
        }

        List<Vec3> centers = new ArrayList<>(groups.size());
        for (List<Integer> group : groups.values()) {
            double cx = 0, cy = 0, cz = 0;
            for (int i : group) {
                cx += positions[i].x;
                cy += positions[i].y;
                cz += positions[i].z;
            }
            int size = group.size();
            centers.add(new Vec3(cx / size, cy / size, cz / size));
        }
        return centers;
    }

    private static int find(int[] parent, int i) {
        while (parent[i] != i) {
            parent[i] = parent[parent[i]];
            i = parent[i];
        }
        return i;
    }

    private static void union(int[] parent, int a, int b) {
        int ra = find(parent, a);
        int rb = find(parent, b);
        if (ra != rb) parent[ra] = rb;
    }

    /** Resets per-server runtime state. */
    public static void reset() {
        previousDayTime = -1;
        HordeSpawner.clearQueue();
    }
}
