package com.kyuwei.hordeapocalypse.ai;

import com.kyuwei.hordeapocalypse.HordeApocalypse;
import com.kyuwei.hordeapocalypse.config.ModConfig;
import com.kyuwei.hordeapocalypse.spawner.HordeSpawner;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.EnumSet;

public class BlockBreakGoal extends Goal {
    private final MobEntity mob;
    private BlockPos targetPos;
    private float breakProgress = 0.0f;
    private int breakingCooldown = 0;
    
    public BlockBreakGoal(MobEntity mob) {
        this.mob = mob;
        this.setControls(EnumSet.of(Control.MOVE, Control.LOOK));
    }
    
    @Override
    public boolean canStart() {
        // Uniquement pour les mobs de la horde
        if (!HordeSpawner.isHordeMob(mob)) {
            return false;
        }
        
        if (breakingCooldown > 0) {
            breakingCooldown--;
            return false;
        }
        
        // Chercher un bloc à casser à proximité
        targetPos = findBlockToBreak();
        return targetPos != null;
    }
    
    @Override
    public boolean shouldContinue() {
        if (targetPos == null) return false;
        
        World world = mob.getWorld();
        BlockState state = world.getBlockState(targetPos);
        
        return !state.isAir() && canBreakBlock(state) && 
               mob.squaredDistanceTo(targetPos.getX(), targetPos.getY(), targetPos.getZ()) < 9.0;
    }
    
    @Override
    public void tick() {
        if (targetPos == null) return;
        
        World world = mob.getWorld();
        BlockState state = world.getBlockState(targetPos);
        
        if (state.isAir() || !canBreakBlock(state)) {
            stop();
            return;
        }
        
        // Regarder le bloc
        mob.getLookControl().lookAt(
            targetPos.getX() + 0.5,
            targetPos.getY() + 0.5,
            targetPos.getZ() + 0.5
        );
        
        // Se rapprocher si trop loin
        double distance = mob.squaredDistanceTo(targetPos.getX(), targetPos.getY(), targetPos.getZ());
        if (distance > 4.0) {
            mob.getNavigation().startMovingTo(targetPos.getX(), targetPos.getY(), targetPos.getZ(), 1.0);
            return;
        }
        
        // Casser le bloc progressivement
        ModConfig config = HordeApocalypse.getConfig();
        breakProgress += (float)config.blockBreakSpeed;
        
        if (breakProgress >= 1.0f) {
            world.breakBlock(targetPos, true);
            breakProgress = 0.0f;
            breakingCooldown = 40; // 2 secondes de cooldown
            stop();
        }
    }
    
    @Override
    public void stop() {
        targetPos = null;
        breakProgress = 0.0f;
        mob.getNavigation().stop();
    }
    
    private BlockPos findBlockToBreak() {
        BlockPos mobPos = mob.getBlockPos();
        
        // Chercher dans un rayon de 3 blocs
        for (int x = -3; x <= 3; x++) {
            for (int y = -2; y <= 2; y++) {
                for (int z = -3; z <= 3; z++) {
                    BlockPos pos = mobPos.add(x, y, z);
                    BlockState state = mob.getWorld().getBlockState(pos);
                    
                    if (!state.isAir() && canBreakBlock(state)) {
                        return pos;
                    }
                }
            }
        }
        
        return null;
    }
    
    private boolean canBreakBlock(BlockState state) {
        ModConfig config = HordeApocalypse.getConfig();
        int currentDay = HordeApocalypse.getDayTracker().getCurrentDay();
        Block block = state.getBlock();
        
        // Ne jamais casser bedrock, obsidienne, netherite
        if (block == Blocks.BEDROCK || block == Blocks.OBSIDIAN || 
            block == Blocks.NETHERITE_BLOCK || block == Blocks.ANCIENT_DEBRIS) {
            return false;
        }
        
        // Jour 1+ : blocs en bois
        if (currentDay >= config.woodBreakStartDay) {
            if (state.isIn(BlockTags.PLANKS) || 
                state.isIn(BlockTags.WOODEN_DOORS) ||
                state.isIn(BlockTags.WOODEN_FENCES) ||
                state.isIn(BlockTags.LOGS)) {
                return true;
            }
        }
        
        // Jour 50+ : blocs en pierre
        if (currentDay >= config.stoneBreakStartDay) {
            if (block == Blocks.STONE || block == Blocks.COBBLESTONE ||
                block == Blocks.STONE_BRICKS || block == Blocks.ANDESITE ||
                block == Blocks.DIORITE || block == Blocks.GRANITE) {
                return true;
            }
        }
        
        // Jour 100+ : blocs durs
        if (currentDay >= config.hardBreakStartDay) {
            if (block == Blocks.IRON_BLOCK || block == Blocks.DIAMOND_BLOCK ||
                block == Blocks.GOLD_BLOCK || block == Blocks.EMERALD_BLOCK) {
                return true;
            }
        }
        
        return false;
    }
}