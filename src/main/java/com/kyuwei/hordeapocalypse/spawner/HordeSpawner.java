package com.kyuwei.hordeapocalypse.spawner;

import com.kyuwei.hordeapocalypse.HordeApocalypse;
import com.kyuwei.hordeapocalypse.ai.BlockBreakGoal;
import com.kyuwei.hordeapocalypse.config.ModConfig;
import com.kyuwei.hordeapocalypse.mixin.CreeperEntityAccessor;
import com.kyuwei.hordeapocalypse.mixin.MobEntityAccessor;
import com.kyuwei.hordeapocalypse.state.HordePersistentState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.mob.CreeperEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.TypeFilter;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Heightmap;

import java.util.List;
import java.util.UUID;

public final class HordeSpawner {
    /** Persistent command tag identifying horde mobs across restarts. */
    public static final String HORDE_TAG = "HordeMob";

    private static final int SPAWN_ATTEMPTS = 12;
    private static final int RADIUS_JITTER = 30;
    /** Never spawn closer than this, even when falling back toward the player. */
    private static final int MIN_SPAWN_DISTANCE = 32;

    private HordeSpawner() {}

    /**
     * Spawns the horde composition around the player, respecting the global
     * concurrent-mob budget. Returns the number of mobs actually spawned.
     */
    public static int spawnHorde(ServerWorld world, Vec3d center, int day, int globalBudget) {
        if (globalBudget <= 0) return 0;
        ModConfig config = HordeApocalypse.getConfig();
        HordePersistentState state = HordePersistentState.get(world);
        Random random = world.getRandom();

        int spawned = 0;

        spawned += spawnBatch(world, EntityType.ZOMBIE, center, config.hordeZombieCount,
                              config.hordeSpawnDistance, random, state, globalBudget - spawned);
        spawned += spawnBatch(world, EntityType.SKELETON, center, config.hordeSkeletonCount,
                              config.hordeSpawnDistance, random, state, globalBudget - spawned);
        spawned += spawnCreeperBatch(world, center, config.hordeCreeperCount,
                                     config.hordeSpawnDistance, random, state, globalBudget - spawned);

        if (day >= config.maxDifficultyDay) {
            spawned += spawnBatch(world, EntityType.WARDEN, center, config.finalDayWardenCount,
                                  config.hordeSpawnDistance, random, state, globalBudget - spawned);
            spawned += spawnBatch(world, EntityType.WITHER, center, config.finalDayWitherCount,
                                  config.hordeSpawnDistance + 50, random, state, globalBudget - spawned);
            spawned += spawnBatch(world, EntityType.PILLAGER, center, config.finalDayPillagerCount,
                                  config.hordeSpawnDistance, random, state, globalBudget - spawned);
        }

        state.markDirty();
        return spawned;
    }

    private static int spawnBatch(ServerWorld world, EntityType<? extends MobEntity> type, Vec3d center,
                                  int count, int radius, Random random, HordePersistentState state, int budget) {
        int actual = Math.min(count, budget);
        int spawned = 0;
        for (int i = 0; i < actual; i++) {
            MobEntity mob = spawnMob(world, type, center, radius, random);
            if (mob != null) {
                state.hordeMobIds.add(mob.getUuid());
                spawned++;
            }
        }
        return spawned;
    }

    private static int spawnCreeperBatch(ServerWorld world, Vec3d center, int count, int radius,
                                         Random random, HordePersistentState state, int budget) {
        int actual = Math.min(count, budget);
        int spawned = 0;
        for (int i = 0; i < actual; i++) {
            MobEntity mob = spawnMob(world, EntityType.CREEPER, center, radius, random);
            if (mob instanceof CreeperEntity creeper) {
                creeper.getDataTracker().set(CreeperEntityAccessor.getChargedKey(), true);
                state.hordeMobIds.add(creeper.getUuid());
                spawned++;
            }
        }
        return spawned;
    }

    /**
     * Spawns one mob at a validated surface position. Returns null if no valid
     * position is found in the loaded area.
     */
    private static MobEntity spawnMob(ServerWorld world, EntityType<? extends MobEntity> type,
                                      Vec3d center, int radius, Random random) {
        BlockPos pos = findValidSpawnPos(world, center, radius, random);
        if (pos == null) return null;

        Entity created = type.create(world, SpawnReason.EVENT);
        if (!(created instanceof MobEntity mob)) {
            if (created != null) created.discard();
            return null;
        }

        mob.refreshPositionAndAngles(pos, random.nextFloat() * 360.0f, 0);
        mob.setPersistent();
        mob.addCommandTag(HORDE_TAG);
        // Attach block-breaking AI dynamically to every horde mob (zombie,
        // skeleton, creeper, pillager, warden). Avoids polluting unrelated
        // mobs via a global mixin. goalSelector is protected, hence the accessor.
        ((MobEntityAccessor) mob).getGoalSelector().add(3, new BlockBreakGoal(mob));

        if (!world.spawnEntity(mob)) {
            return null;
        }
        return mob;
    }

    /**
     * Picks a valid spawn position around the center. Tries multiple random
     * directions, only on loaded chunks, with 2-block air clearance.
     * <p>
     * The configured spawn distance (200 by default) usually exceeds the
     * loaded-chunk radius (default view distance 10 chunks = 160 blocks), so
     * later attempts shrink toward the player rather than force-loading
     * chunks or silently failing.
     */
    private static BlockPos findValidSpawnPos(ServerWorld world, Vec3d center, int radius, Random random) {
        for (int attempt = 0; attempt < SPAWN_ATTEMPTS; attempt++) {
            double angle = random.nextDouble() * (2 * Math.PI);
            // 100% of the radius on the first attempt, down to 25% on the last.
            double shrink = 1.0 - 0.75 * attempt / (double) (SPAWN_ATTEMPTS - 1);
            double distance = Math.max(MIN_SPAWN_DISTANCE, radius * shrink + random.nextInt(RADIUS_JITTER));
            int x = (int) Math.round(center.x + Math.cos(angle) * distance);
            int z = (int) Math.round(center.z + Math.sin(angle) * distance);

            int chunkX = x >> 4;
            int chunkZ = z >> 4;
            if (!world.getChunkManager().isChunkLoaded(chunkX, chunkZ)) {
                continue;
            }

            BlockPos surface = world.getTopPosition(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, 0, z));
            if (!isValidSurface(world, surface)) {
                continue;
            }
            return surface;
        }
        return null;
    }

    private static boolean isValidSurface(ServerWorld world, BlockPos pos) {
        if (pos.getY() <= world.getBottomY() + 1) return false;
        // Spot must have 2 blocks of air for mob clearance.
        if (!world.getBlockState(pos).isAir()) return false;
        if (!world.getBlockState(pos.up()).isAir()) return false;
        // Block directly below must be solid (not fluid, not air, not leaves).
        var below = world.getBlockState(pos.down());
        if (below.isAir() || !below.getFluidState().isEmpty()) return false;
        return true;
    }

    /**
     * Removes all currently-tracked horde mobs from the world.
     * Falls back to scanning by command tag for survivors loaded after a
     * crash (lookup by UUID may miss entities that aren't yet in memory).
     */
    public static int clearHordeMobs(ServerWorld world) {
        HordePersistentState state = HordePersistentState.get(world);
        int removed = 0;

        // First: targeted removal via tracked UUIDs.
        for (UUID id : List.copyOf(state.hordeMobIds)) {
            Entity entity = world.getEntity(id);
            if (entity != null) {
                entity.discard();
                removed++;
            }
        }
        state.hordeMobIds.clear();

        // Then: belt-and-braces sweep by tag, catches any mob whose UUID
        // wasn't tracked (e.g. surviving a corrupted save).
        List<MobEntity> tagged = world.getEntitiesByType(
                TypeFilter.instanceOf(MobEntity.class),
                mob -> mob.getCommandTags().contains(HORDE_TAG));
        for (MobEntity mob : tagged) {
            mob.discard();
            removed++;
        }

        state.markDirty();
        return removed;
    }

    public static boolean isHordeMob(MobEntity mob) {
        return mob.getCommandTags().contains(HORDE_TAG);
    }
}
