package com.kyuwei.hordeapocalypse.spawner;

import com.kyuwei.hordeapocalypse.HordeApocalypse;
import com.kyuwei.hordeapocalypse.config.ModConfig;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.*;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

public class HordeSpawner {
    private static final List<UUID> hordeMobIds = new ArrayList<>();
    private static final String HORDE_TAG = "HordeMob";
    
    public static void spawnHorde(ServerWorld world, ServerPlayerEntity player, int day) {
        ModConfig config = HordeApocalypse.getConfig();
        Random random = new Random();
        Vec3d playerPos = player.getPos();
        
        // Spawn zombies
        for (int i = 0; i < config.hordeZombieCount; i++) {
            BlockPos spawnPos = getRandomSpawnPos(playerPos, config.hordeSpawnDistance, random);
            ZombieEntity zombie = EntityType.ZOMBIE.create(world, SpawnReason.EVENT);
            if (zombie != null) {
                prepareHordeMob(zombie, spawnPos, day);
                world.spawnEntity(zombie);
                markAsHordeMob(zombie);
            }
        }
        
        // Spawn skeletons
        for (int i = 0; i < config.hordeSkeletonCount; i++) {
            BlockPos spawnPos = getRandomSpawnPos(playerPos, config.hordeSpawnDistance, random);
            SkeletonEntity skeleton = EntityType.SKELETON.create(world, SpawnReason.EVENT);
            if (skeleton != null) {
                prepareHordeMob(skeleton, spawnPos, day);
                world.spawnEntity(skeleton);
                markAsHordeMob(skeleton);
            }
        }
        
        // Spawn creepers (charged)
        for (int i = 0; i < config.hordeCreeperCount; i++) {
            BlockPos spawnPos = getRandomSpawnPos(playerPos, config.hordeSpawnDistance, random);
            CreeperEntity creeper = EntityType.CREEPER.create(world, SpawnReason.EVENT);
            if (creeper != null) {
                prepareHordeMob(creeper, spawnPos, day);
                
                // Rendre le creeper chargé
                NbtCompound nbt = new NbtCompound();
                creeper.writeNbt(nbt);
                nbt.putBoolean("powered", true);
                creeper.readNbt(nbt);
                
                world.spawnEntity(creeper);
                markAsHordeMob(creeper);
            }
        }
        
        // Jour 100 : boss final
        if (day == 100) {
            spawnFinalDayBosses(world, playerPos, config, random);
        }
    }
    
    private static void spawnFinalDayBosses(ServerWorld world, Vec3d playerPos, ModConfig config, Random random) {
        // Spawn Wardens
        for (int i = 0; i < config.finalDayWardenCount; i++) {
            BlockPos spawnPos = getRandomSpawnPos(playerPos, config.hordeSpawnDistance, random);
            WardenEntity warden = EntityType.WARDEN.create(world, SpawnReason.EVENT);
            if (warden != null) {
                warden.refreshPositionAndAngles(spawnPos, 0, 0);
                world.spawnEntity(warden);
                markAsHordeMob(warden);
            }
        }
        
        // Spawn Withers
        for (int i = 0; i < config.finalDayWitherCount; i++) {
            BlockPos spawnPos = getRandomSpawnPos(playerPos, config.hordeSpawnDistance + 50, random);
            WitherEntity wither = EntityType.WITHER.create(world, SpawnReason.EVENT);
            if (wither != null) {
                wither.refreshPositionAndAngles(spawnPos, 0, 0);
                world.spawnEntity(wither);
                markAsHordeMob(wither);
            }
        }
        
        // Spawn Pillagers
        for (int i = 0; i < config.finalDayPillagerCount; i++) {
            BlockPos spawnPos = getRandomSpawnPos(playerPos, config.hordeSpawnDistance, random);
            PillagerEntity pillager = EntityType.PILLAGER.create(world, SpawnReason.EVENT);
            if (pillager != null) {
                prepareHordeMob(pillager, spawnPos, HordeApocalypse.getConfig().maxDifficultyDay);
                world.spawnEntity(pillager);
                markAsHordeMob(pillager);
            }
        }
    }
    
    private static void prepareHordeMob(MobEntity mob, BlockPos pos, int day) {
        ModConfig config = HordeApocalypse.getConfig();
        mob.refreshPositionAndAngles(pos, 0, 0);
        mob.setPersistent();
        
        // Appliquer les bonus de difficulté
        double healthMultiplier = config.getHealthMultiplier(day);
        double damageMultiplier = config.getDamageMultiplier(day);
        double speedMultiplier = config.getSpeedMultiplier(day);
        
        if (mob.getAttributeInstance(EntityAttributes.MAX_HEALTH) != null) {
            double baseHealth = mob.getAttributeInstance(EntityAttributes.MAX_HEALTH).getBaseValue();
            mob.getAttributeInstance(EntityAttributes.MAX_HEALTH).setBaseValue(baseHealth * healthMultiplier);
            mob.setHealth((float)(baseHealth * healthMultiplier));
        }
        
        if (mob.getAttributeInstance(EntityAttributes.ATTACK_DAMAGE) != null) {
            double baseDamage = mob.getAttributeInstance(EntityAttributes.ATTACK_DAMAGE).getBaseValue();
            mob.getAttributeInstance(EntityAttributes.ATTACK_DAMAGE).setBaseValue(baseDamage * damageMultiplier);
        }
        
        if (mob.getAttributeInstance(EntityAttributes.MOVEMENT_SPEED) != null) {
            double baseSpeed = mob.getAttributeInstance(EntityAttributes.MOVEMENT_SPEED).getBaseValue();
            mob.getAttributeInstance(EntityAttributes.MOVEMENT_SPEED).setBaseValue(baseSpeed * speedMultiplier);
        }
    }
    
    private static BlockPos getRandomSpawnPos(Vec3d center, int radius, Random random) {
        double angle = random.nextDouble() * 2 * Math.PI;
        double distance = radius + random.nextInt(50);
        
        int x = (int)(center.x + Math.cos(angle) * distance);
        int z = (int)(center.z + Math.sin(angle) * distance);
        int y = (int)center.y;
        
        return new BlockPos(x, y, z);
    }
    
    private static void markAsHordeMob(MobEntity mob) {
        hordeMobIds.add(mob.getUuid());
        NbtCompound nbt = new NbtCompound();
        mob.writeNbt(nbt);
        nbt.putBoolean(HORDE_TAG, true);
        mob.readNbt(nbt);
    }
    
    public static void clearHordeMobs(ServerWorld world) {
        world.iterateEntities().forEach(entity -> {
            if (entity instanceof MobEntity mob && hordeMobIds.contains(mob.getUuid())) {
                mob.discard();
            }
        });
        hordeMobIds.clear();
    }
    
    public static boolean isHordeMob(MobEntity mob) {
        return hordeMobIds.contains(mob.getUuid());
    }
}