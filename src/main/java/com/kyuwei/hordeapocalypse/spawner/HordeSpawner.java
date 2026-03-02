package com.kyuwei.hordeapocalypse.spawner;

import com.kyuwei.hordeapocalypse.HordeApocalypse;
import com.kyuwei.hordeapocalypse.config.ModConfig;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.mob.*;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class HordeSpawner {
    /** Command tag persisted in entity NBT — survives server restarts */
    public static final String HORDE_TAG = "HordeMob";

    public static void spawnHorde(ServerWorld world, ServerPlayerEntity player, int day) {
        ModConfig config = HordeApocalypse.getConfig();
        Random random = new Random();
        Vec3d playerPos = player.getPos();

        // Spawn zombies
        for (int i = 0; i < config.hordeZombieCount; i++) {
            spawnMob(world, EntityType.ZOMBIE, playerPos, config.hordeSpawnDistance, random);
        }

        // Spawn skeletons
        for (int i = 0; i < config.hordeSkeletonCount; i++) {
            spawnMob(world, EntityType.SKELETON, playerPos, config.hordeSpawnDistance, random);
        }

        // Spawn charged creepers
        for (int i = 0; i < config.hordeCreeperCount; i++) {
            MobEntity creeper = spawnMob(world, EntityType.CREEPER, playerPos, config.hordeSpawnDistance, random);
            if (creeper instanceof CreeperEntity creeperEntity) {
                NbtCompound nbt = new NbtCompound();
                creeperEntity.writeNbt(nbt);
                nbt.putBoolean("powered", true);
                creeperEntity.readNbt(nbt);
            }
        }

        // Day 100+: final bosses
        if (day >= config.maxDifficultyDay) {
            spawnFinalDayBosses(world, playerPos, config, random);
        }
    }

    /**
     * Spawn a single horde mob at a valid surface position near the player.
     * Returns the mob, or null if spawning failed.
     */
    private static MobEntity spawnMob(ServerWorld world, EntityType<? extends MobEntity> type,
                                       Vec3d center, int radius, Random random) {
        BlockPos spawnPos = findValidSpawnPos(world, center, radius, random);
        if (spawnPos == null) {
            return null;
        }

        MobEntity mob = type.create(world, SpawnReason.EVENT);
        if (mob == null) {
            return null;
        }

        mob.refreshPositionAndAngles(spawnPos, random.nextFloat() * 360.0f, 0);
        mob.setPersistent();
        mob.addCommandTag(HORDE_TAG);
        world.spawnEntity(mob);
        return mob;
    }

    private static void spawnFinalDayBosses(ServerWorld world, Vec3d playerPos, ModConfig config, Random random) {
        // Wardens
        for (int i = 0; i < config.finalDayWardenCount; i++) {
            spawnMob(world, EntityType.WARDEN, playerPos, config.hordeSpawnDistance, random);
        }

        // Withers (spawn further away for safety)
        for (int i = 0; i < config.finalDayWitherCount; i++) {
            spawnMob(world, EntityType.WITHER, playerPos, config.hordeSpawnDistance + 50, random);
        }

        // Pillagers
        for (int i = 0; i < config.finalDayPillagerCount; i++) {
            spawnMob(world, EntityType.PILLAGER, playerPos, config.hordeSpawnDistance, random);
        }
    }

    /**
     * Find a valid spawn position on the world surface with 2 blocks of air space.
     * Tries up to 10 random positions before falling back.
     */
    private static BlockPos findValidSpawnPos(ServerWorld world, Vec3d center, int radius, Random random) {
        for (int attempt = 0; attempt < 10; attempt++) {
            double angle = random.nextDouble() * 2 * Math.PI;
            double distance = radius + random.nextInt(30);

            int x = (int) (center.x + Math.cos(angle) * distance);
            int z = (int) (center.z + Math.sin(angle) * distance);

            BlockPos surfacePos = world.getTopPosition(
                    Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, 0, z));

            // Verify 2-block clearance for the mob
            if (world.getBlockState(surfacePos).isAir()
                    && world.getBlockState(surfacePos.up()).isAir()) {
                return surfacePos;
            }
        }

        // Fallback: cardinal direction at exact radius
        int x = (int) center.x + radius;
        int z = (int) center.z;
        return world.getTopPosition(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, 0, z));
    }

    /**
     * Remove all horde mobs from the world. Collects into a list first
     * to avoid ConcurrentModificationException during iteration.
     */
    public static void clearHordeMobs(ServerWorld world) {
        List<MobEntity> toRemove = new ArrayList<>();
        world.iterateEntities().forEach(entity -> {
            if (entity instanceof MobEntity mob && isHordeMob(mob)) {
                toRemove.add(mob);
            }
        });
        toRemove.forEach(MobEntity::discard);
    }

    /**
     * Check whether a mob is part of a horde. Uses command tags which are
     * persisted in entity NBT, so this survives server restarts.
     */
    public static boolean isHordeMob(MobEntity mob) {
        return mob.getCommandTags().contains(HORDE_TAG);
    }
}