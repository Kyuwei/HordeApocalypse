package com.kyuwei.hordeapocalypse.event;

import com.kyuwei.hordeapocalypse.HordeApocalypse;
import com.kyuwei.hordeapocalypse.config.ModConfig;
import com.kyuwei.hordeapocalypse.spawner.HordeSpawner;
import com.kyuwei.hordeapocalypse.state.HordePersistentState;
import com.kyuwei.hordeapocalypse.tracker.DayTracker;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class HordeEventHandler {
    /** Horde lasts one Minecraft day = 24000 ticks. */
    private static final int HORDE_DURATION_TICKS = 24000;

    private HordeEventHandler() {}

    public static void onServerTick(MinecraftServer server) {
        DayTracker tracker = HordeApocalypse.getDayTracker();
        ServerWorld overworld = server.getOverworld();
        if (tracker == null || overworld == null) return;

        HordePersistentState state = HordePersistentState.get(overworld);

        if (tracker.consumeDayChanged() && tracker.isHordeDay() && !state.hordeActive) {
            startHorde(server, overworld, state, tracker.getCurrentDay());
        }

        if (state.hordeActive) {
            state.hordeTicksRemaining--;
            if (state.hordeTicksRemaining <= 0) {
                endHorde(server, overworld, state);
            } else {
                // Persist countdown occasionally so a restart doesn't lose progress.
                if (state.hordeTicksRemaining % 200 == 0) {
                    state.markDirty();
                }
            }
        }
    }

    public static void onServerStopping(MinecraftServer server) {
        ServerWorld overworld = server.getOverworld();
        if (overworld == null) return;
        // Force a flush of the persistent state so we resume correctly.
        HordePersistentState.get(overworld).markDirty();
    }

    /**
     * Forcibly starts a horde for the given player. Used by the debug command.
     */
    public static void forceStartHorde(MinecraftServer server, ServerPlayerEntity initiator) {
        ServerWorld overworld = server.getOverworld();
        if (overworld == null) return;
        HordePersistentState state = HordePersistentState.get(overworld);
        if (state.hordeActive) return;
        DayTracker tracker = HordeApocalypse.getDayTracker();
        int day = tracker != null ? tracker.getCurrentDay() : 1;
        startHordeAround(server, overworld, state, day, List.of(initiator));
    }

    public static void forceEndHorde(MinecraftServer server) {
        ServerWorld overworld = server.getOverworld();
        if (overworld == null) return;
        HordePersistentState state = HordePersistentState.get(overworld);
        if (state.hordeActive) {
            endHorde(server, overworld, state);
        } else {
            // No active horde, but sweep any leftover tagged mobs just in case.
            HordeSpawner.clearHordeMobs(overworld);
        }
    }

    private static void startHorde(MinecraftServer server, ServerWorld overworld,
                                   HordePersistentState state, int day) {
        startHordeAround(server, overworld, state, day,
                         server.getPlayerManager().getPlayerList());
    }

    /**
     * Starts a horde, spawning one cluster of mobs around each group of close
     * players. Caps total mobs to {@code maxConcurrentHordeMobs} from config.
     */
    private static void startHordeAround(MinecraftServer server, ServerWorld overworld,
                                          HordePersistentState state, int day,
                                          List<? extends ServerPlayerEntity> targets) {
        ModConfig config = HordeApocalypse.getConfig();
        state.hordeActive = true;
        state.hordeTicksRemaining = HORDE_DURATION_TICKS;
        state.hordeStartDay = day;
        state.markDirty();

        Text message = Text.literal(day >= config.maxDifficultyDay
                ? "§4§l[APOCALYPSE FINALE] Les ténèbres déferlent !"
                : "§c§l[HORDE - Jour " + day + "] Une horde approche !");
        server.getPlayerManager().broadcast(message, false);

        // Spawn only for overworld players. Skip nether/end to avoid bizarre
        // top-of-world spawns. Could be revisited if the mod ever supports
        // other dimensions.
        List<ServerPlayerEntity> eligible = new ArrayList<>();
        for (ServerPlayerEntity player : targets) {
            if (player.getWorld().getRegistryKey() == World.OVERWORLD) {
                eligible.add(player);
            }
        }
        if (eligible.isEmpty()) {
            HordeApocalypse.LOGGER.info("Horde started on day {} but no overworld players online", day);
            return;
        }

        List<Vec3d> clusters = mergeIntoClusters(eligible, config.clusterMergeDistance);
        int totalSpawned = 0;
        for (Vec3d center : clusters) {
            int budget = config.maxConcurrentHordeMobs - totalSpawned;
            if (budget <= 0) break;
            totalSpawned += HordeSpawner.spawnHorde(overworld, center, day, budget);
        }

        HordeApocalypse.LOGGER.info("Horde started on day {} ({} mobs across {} cluster(s))",
                day, totalSpawned, clusters.size());
    }

    private static void endHorde(MinecraftServer server, ServerWorld overworld, HordePersistentState state) {
        int removed = HordeSpawner.clearHordeMobs(overworld);
        state.reset();

        server.getPlayerManager().broadcast(
                Text.literal("§a§l[HORDE] La horde s'est dissipée..."), false);
        HordeApocalypse.LOGGER.info("Horde ended, {} mobs removed", removed);
    }

    /**
     * Groups players whose horizontal distance is below {@code mergeDistance}
     * into single clusters, returning the centroid of each cluster.
     * Union-find over a simple distance graph — O(n²) but n is the player count.
     */
    private static List<Vec3d> mergeIntoClusters(List<ServerPlayerEntity> players, int mergeDistance) {
        int n = players.size();
        int[] parent = new int[n];
        for (int i = 0; i < n; i++) parent[i] = i;

        double mergeSq = mergeDistance <= 0 ? -1.0 : (double) mergeDistance * mergeDistance;
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                if (mergeSq < 0) break;
                Vec3d a = players.get(i).getPos();
                Vec3d b = players.get(j).getPos();
                double dx = a.x - b.x;
                double dz = a.z - b.z;
                if (dx * dx + dz * dz <= mergeSq) {
                    union(parent, i, j);
                }
            }
        }

        Map<Integer, List<ServerPlayerEntity>> groups = new HashMap<>();
        for (int i = 0; i < n; i++) {
            groups.computeIfAbsent(find(parent, i), k -> new ArrayList<>()).add(players.get(i));
        }

        List<Vec3d> centers = new ArrayList<>(groups.size());
        for (List<ServerPlayerEntity> group : groups.values()) {
            if (group.isEmpty()) continue;
            double cx = 0, cy = 0, cz = 0;
            for (ServerPlayerEntity p : group) {
                Vec3d pos = p.getPos();
                cx += pos.x; cy += pos.y; cz += pos.z;
            }
            int size = group.size();
            centers.add(new Vec3d(cx / size, cy / size, cz / size));
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

    public static boolean isHordeActive(ServerWorld overworld) {
        return overworld != null && HordePersistentState.get(overworld).hordeActive;
    }
}
