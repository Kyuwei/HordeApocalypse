package com.kyuwei.hordeapocalypse.ai;

import com.kyuwei.hordeapocalypse.HordeApocalypse;
import com.kyuwei.hordeapocalypse.config.ModConfig;
import com.kyuwei.hordeapocalypse.spawner.HordeSpawner;
import com.kyuwei.hordeapocalypse.tracker.DayTracker;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.EnumSet;

/**
 * Goal that makes horde mobs break blocks in their path toward their target.
 * Uses NO controls so it can run alongside attack/navigation goals simultaneously,
 * allowing the mob to walk toward the player AND break blocks in the way.
 */
public class BlockBreakGoal extends Goal {
    private final MobEntity mob;
    private BlockPos targetPos;
    private int breakProgress;
    private int totalBreakTicks;
    private int checkCooldown;

    private static final int CHECK_INTERVAL = 10; // Only scan for blocks every 10 ticks
    private static final double MAX_BREAK_DISTANCE_SQ = 6.25; // 2.5 blocks squared

    public BlockBreakGoal(MobEntity mob) {
        this.mob = mob;
        // No controls claimed - this goal runs in parallel with attack/navigation
        this.setControls(EnumSet.noneOf(Control.class));
    }

    @Override
    public boolean canStart() {
        if (!HordeSpawner.isHordeMob(mob)) {
            return false;
        }

        // Throttle expensive block scanning
        if (checkCooldown > 0) {
            checkCooldown--;
            return false;
        }
        checkCooldown = CHECK_INTERVAL;

        // Need a target to determine which direction to break
        LivingEntity target = mob.getTarget();
        if (target == null) {
            return false;
        }

        targetPos = findBlockInPath(target);
        return targetPos != null;
    }

    @Override
    public boolean shouldContinue() {
        if (targetPos == null) {
            return false;
        }

        World world = mob.getWorld();
        BlockState state = world.getBlockState(targetPos);

        if (state.isAir() || !canBreakBlock(state)) {
            return false;
        }

        // Stop if the mob has moved too far from the block
        double distSq = mob.squaredDistanceTo(
                targetPos.getX() + 0.5,
                targetPos.getY() + 0.5,
                targetPos.getZ() + 0.5
        );
        return distSq < MAX_BREAK_DISTANCE_SQ;
    }

    @Override
    public void start() {
        breakProgress = 0;
        ModConfig config = HordeApocalypse.getConfig();
        // blockBreakSpeed=0.1 default → 10 ticks to break a block
        totalBreakTicks = Math.max(1, (int) (1.0 / config.blockBreakSpeed));
    }

    @Override
    public void tick() {
        if (targetPos == null) {
            return;
        }

        breakProgress++;

        // Show cracking animation to players (stages 0-9)
        int stage = Math.min((int) ((float) breakProgress / totalBreakTicks * 10.0f), 9);
        mob.getWorld().setBlockBreakingInfo(mob.getId(), targetPos, stage);

        if (breakProgress >= totalBreakTicks) {
            // Destroy the block with drops
            mob.getWorld().breakBlock(targetPos, true, mob);
            mob.getWorld().setBlockBreakingInfo(mob.getId(), targetPos, -1);
            targetPos = null;
            // Short cooldown before scanning for the next block
            checkCooldown = 5;
        }
    }

    @Override
    public void stop() {
        // Clear breaking animation if interrupted
        if (targetPos != null) {
            mob.getWorld().setBlockBreakingInfo(mob.getId(), targetPos, -1);
        }
        targetPos = null;
        breakProgress = 0;
    }

    /**
     * Find a breakable block between the mob and its target.
     * Checks blocks in the horizontal direction toward the target, at feet and head level.
     * This ensures mobs break blocks IN FRONT of them, not beneath.
     */
    private BlockPos findBlockInPath(LivingEntity target) {
        BlockPos mobPos = mob.getBlockPos();
        World world = mob.getWorld();
        Vec3d direction = target.getPos().subtract(mob.getPos());

        double absX = Math.abs(direction.x);
        double absZ = Math.abs(direction.z);

        // Target is too close horizontally, no blocks to break
        if (absX < 0.1 && absZ < 0.1) {
            return null;
        }

        // Primary horizontal direction toward target
        int primaryDx, primaryDz;
        if (absX >= absZ) {
            primaryDx = direction.x > 0 ? 1 : -1;
            primaryDz = 0;
        } else {
            primaryDx = 0;
            primaryDz = direction.z > 0 ? 1 : -1;
        }

        // Check primary direction first (1-2 blocks ahead, feet + head level)
        BlockPos result = checkDirection(world, mobPos, primaryDx, primaryDz);
        if (result != null) {
            return result;
        }

        // Check diagonal if the target is at a diagonal angle
        if (absX > 0.3 && absZ > 0.3) {
            int diagDx = direction.x > 0 ? 1 : -1;
            int diagDz = direction.z > 0 ? 1 : -1;
            result = checkDirection(world, mobPos, diagDx, diagDz);
            if (result != null) {
                return result;
            }
        }

        return null;
    }

    /**
     * Check for breakable blocks in a specific direction at feet (y+0) and head (y+1) level.
     */
    private BlockPos checkDirection(World world, BlockPos mobPos, int dx, int dz) {
        for (int dist = 1; dist <= 2; dist++) {
            BlockPos feetPos = mobPos.add(dx * dist, 0, dz * dist);
            BlockPos headPos = mobPos.add(dx * dist, 1, dz * dist);

            BlockState feetState = world.getBlockState(feetPos);
            BlockState headState = world.getBlockState(headPos);

            // Prefer feet-level blocks (clears walking path first)
            if (!feetState.isAir() && canBreakBlock(feetState)) {
                return feetPos;
            }
            if (!headState.isAir() && canBreakBlock(headState)) {
                return headPos;
            }
        }
        return null;
    }

    private boolean canBreakBlock(BlockState state) {
        ModConfig config = HordeApocalypse.getConfig();
        if (config == null) {
            return false;
        }

        DayTracker tracker = HordeApocalypse.getDayTracker();
        int currentDay = (tracker != null) ? tracker.getCurrentDay() : 1;
        Block block = state.getBlock();

        // Never break indestructible / special blocks
        if (block == Blocks.BEDROCK || block == Blocks.OBSIDIAN
                || block == Blocks.NETHERITE_BLOCK || block == Blocks.ANCIENT_DEBRIS
                || block == Blocks.END_PORTAL_FRAME || block == Blocks.END_PORTAL
                || block == Blocks.NETHER_PORTAL || block == Blocks.BARRIER
                || block == Blocks.COMMAND_BLOCK || block == Blocks.CHAIN_COMMAND_BLOCK
                || block == Blocks.REPEATING_COMMAND_BLOCK || block == Blocks.STRUCTURE_BLOCK) {
            return false;
        }

        // Day 1+ : wood
        if (currentDay >= config.woodBreakStartDay) {
            if (state.isIn(BlockTags.PLANKS)
                    || state.isIn(BlockTags.WOODEN_DOORS)
                    || state.isIn(BlockTags.WOODEN_FENCES)
                    || state.isIn(BlockTags.LOGS)
                    || state.isIn(BlockTags.FENCE_GATES)
                    || state.isIn(BlockTags.WOODEN_TRAPDOORS)) {
                return true;
            }
        }

        // Day 50+ : stone
        if (currentDay >= config.stoneBreakStartDay) {
            if (block == Blocks.STONE || block == Blocks.COBBLESTONE
                    || block == Blocks.STONE_BRICKS || block == Blocks.ANDESITE
                    || block == Blocks.DIORITE || block == Blocks.GRANITE
                    || block == Blocks.MOSSY_COBBLESTONE || block == Blocks.MOSSY_STONE_BRICKS
                    || block == Blocks.DEEPSLATE || block == Blocks.COBBLED_DEEPSLATE
                    || block == Blocks.DEEPSLATE_BRICKS || block == Blocks.DEEPSLATE_TILES
                    || state.isIn(BlockTags.WALLS)) {
                return true;
            }
        }

        // Day 100+ : hard blocks
        if (currentDay >= config.hardBreakStartDay) {
            if (block == Blocks.IRON_BLOCK || block == Blocks.DIAMOND_BLOCK
                    || block == Blocks.GOLD_BLOCK || block == Blocks.EMERALD_BLOCK
                    || block == Blocks.IRON_DOOR || block == Blocks.IRON_TRAPDOOR) {
                return true;
            }
        }

        return false;
    }
}