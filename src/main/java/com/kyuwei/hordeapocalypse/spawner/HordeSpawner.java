package com.kyuwei.hordeapocalypse.spawner;

import com.kyuwei.hordeapocalypse.HordeApocalypse;
import com.kyuwei.hordeapocalypse.ai.BlockBreakGoal;
import com.kyuwei.hordeapocalypse.config.ModConfig;
import com.kyuwei.hordeapocalypse.mixin.CreeperAccessor;
import com.kyuwei.hordeapocalypse.mixin.MobAccessor;
import com.kyuwei.hordeapocalypse.state.HordeState;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.UUID;

/**
 * Materialises horde mobs around the players.
 *
 * <p>Spawns are queued and drained over several ticks: dropping 300 entities in
 * one tick is a guaranteed lag spike at the start of every horde night.
 */
public final class HordeSpawner {
    /** Persistent tag identifying horde mobs across restarts. */
    public static final String HORDE_TAG = "hordeapocalypse.horde";

    private static final int SPAWN_ATTEMPTS = 12;
    private static final int RADIUS_JITTER = 30;
    private static final int MIN_SPAWN_DISTANCE = 32;
    /** Below this depth relative to the surface, spawn at the player's level. */
    private static final int UNDERGROUND_THRESHOLD = 12;

    private static final Deque<PendingSpawn> QUEUE = new ArrayDeque<>();

    private record PendingSpawn(EntityType<? extends Mob> type, Vec3 center, int radius, boolean charged) {}

    private HordeSpawner() {}

    /**
     * Queues the standard horde composition around one cluster of players.
     *
     * @param budget how many mobs this cluster may still add
     * @return the number of mobs queued
     */
    public static int queueHorde(Vec3 center, int budget) {
        ModConfig config = HordeApocalypse.getConfig();
        int queued = 0;
        queued += enqueue(EntityTypes.ZOMBIE, center, config.hordeSpawnDistance,
                          Math.min(config.hordeZombieCount, budget - queued), false);
        queued += enqueue(EntityTypes.SKELETON, center, config.hordeSpawnDistance,
                          Math.min(config.hordeSkeletonCount, budget - queued), false);
        queued += enqueue(EntityTypes.CREEPER, center, config.hordeSpawnDistance,
                          Math.min(config.hordeCreeperCount, budget - queued), true);
        return queued;
    }

    /**
     * Queues the day-100 bosses. Called once per horde — never once per player
     * cluster, or a four-group server would face twelve Withers.
     */
    public static int queueApocalypseBosses(Vec3 center, int budget) {
        ModConfig config = HordeApocalypse.getConfig();
        int queued = 0;
        queued += enqueue(EntityTypes.WARDEN, center, config.hordeSpawnDistance,
                          Math.min(config.finalDayWardenCount, budget - queued), false);
        queued += enqueue(EntityTypes.WITHER, center, config.hordeSpawnDistance + 50,
                          Math.min(config.finalDayWitherCount, budget - queued), false);
        queued += enqueue(EntityTypes.PILLAGER, center, config.hordeSpawnDistance,
                          Math.min(config.finalDayPillagerCount, budget - queued), false);
        return queued;
    }

    private static int enqueue(EntityType<? extends Mob> type, Vec3 center, int radius,
                               int count, boolean charged) {
        int queued = 0;
        for (int i = 0; i < count; i++) {
            QUEUE.add(new PendingSpawn(type, center, radius, charged));
            queued++;
        }
        return queued;
    }

    /** Drains part of the spawn queue. Called once per server tick. */
    public static void drainQueue(ServerLevel level) {
        if (QUEUE.isEmpty()) return;
        ModConfig config = HordeApocalypse.getConfig();
        HordeState state = HordeState.get(level);
        int budget = (config != null) ? config.maxSpawnsPerTick : 20;

        for (int i = 0; i < budget && !QUEUE.isEmpty(); i++) {
            PendingSpawn pending = QUEUE.poll();
            Mob mob = spawnMob(level, pending);
            if (mob != null) state.trackMob(mob.getUUID());
        }
    }

    public static void clearQueue() {
        QUEUE.clear();
    }

    public static int queuedCount() {
        return QUEUE.size();
    }

    private static Mob spawnMob(ServerLevel level, PendingSpawn pending) {
        RandomSource random = level.getRandom();
        BlockPos pos = findValidSpawnPos(level, pending.center(), pending.radius(), random);
        if (pos == null) return null;

        Mob mob = pending.type().create(level, EntitySpawnReason.EVENT);
        if (mob == null) return null;

        mob.moveTo(pos, random.nextFloat() * 360.0f, 0.0f);
        // Without finalizeSpawn a skeleton has no bow and a pillager no
        // crossbow: they spawn unarmed and cannot attack at all.
        mob.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), EntitySpawnReason.EVENT, null);
        mob.setPersistenceRequired();
        // Tag before spawning: the entity-load handler keys off it to attach
        // the horde AI, and it fires from inside addFreshEntity.
        mob.addTag(HORDE_TAG);

        if (pending.charged() && mob instanceof Creeper creeper) {
            creeper.getEntityData().set(CreeperAccessor.getPoweredKey(), true);
        }

        if (!level.addFreshEntity(mob)) {
            mob.discard();
            return null;
        }
        return mob;
    }

    /**
     * Attaches the block-breaking AI. Goals are not serialised, so this has to
     * run again every time a horde mob is loaded from disk, not just at spawn.
     */
    public static void attachHordeAi(Mob mob) {
        ((MobAccessor) mob).hordeapocalypse$getGoalSelector().addGoal(3, new BlockBreakGoal(mob));
    }

    private static BlockPos findValidSpawnPos(ServerLevel level, Vec3 center, int radius, RandomSource random) {
        for (int attempt = 0; attempt < SPAWN_ATTEMPTS; attempt++) {
            double angle = random.nextDouble() * (2 * Math.PI);
            // The configured distance usually exceeds the loaded-chunk radius,
            // so later attempts close in on the player instead of failing.
            double shrink = 1.0 - 0.75 * attempt / (double) (SPAWN_ATTEMPTS - 1);
            double distance = Math.max(MIN_SPAWN_DISTANCE, radius * shrink + random.nextInt(RADIUS_JITTER));
            int x = (int) Math.round(center.x + Math.cos(angle) * distance);
            int z = (int) Math.round(center.z + Math.sin(angle) * distance);

            if (!level.getChunkSource().hasChunk(x >> 4, z >> 4)) continue;

            BlockPos surface = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, 0, z));

            // A player holed up underground should still be besieged.
            int centerY = (int) Math.round(center.y);
            if (centerY < surface.getY() - UNDERGROUND_THRESHOLD) {
                BlockPos cave = findCavePos(level, x, centerY, z);
                if (cave != null) return cave;
                continue;
            }

            if (isValidStandingSpot(level, surface)) return surface;
        }
        return null;
    }

    /** Looks for breathable space near the player's own depth. */
    private static BlockPos findCavePos(ServerLevel level, int x, int centerY, int z) {
        for (int dy = 0; dy <= 8; dy++) {
            for (int sign : new int[] {1, -1}) {
                BlockPos candidate = new BlockPos(x, centerY + dy * sign, z);
                if (isValidStandingSpot(level, candidate)) return candidate;
                if (dy == 0) break;
            }
        }
        return null;
    }

    private static boolean isValidStandingSpot(Level level, BlockPos pos) {
        if (!level.getBlockState(pos).isAir()) return false;
        if (!level.getBlockState(pos.above()).isAir()) return false;
        BlockState below = level.getBlockState(pos.below());
        // Solid ground, and not standing on water or lava.
        return !below.isAir() && below.getFluidState().isEmpty();
    }

    /**
     * Removes every tracked horde mob that is currently loaded. Mobs sitting in
     * unloaded chunks cannot be reached here; they are disposed of when they
     * next load, see {@code HordeApocalypse}'s entity-load handler.
     */
    public static int clearHordeMobs(ServerLevel level) {
        clearQueue();
        HordeState state = HordeState.get(level);
        int removed = 0;
        for (UUID id : List.copyOf(state.hordeMobIds)) {
            Entity entity = level.getEntity(id);
            if (entity != null) {
                entity.discard();
                removed++;
            }
        }
        state.endHorde();
        return removed;
    }

    public static boolean isHordeMob(Entity entity) {
        return entity.getTags().contains(HORDE_TAG);
    }
}
