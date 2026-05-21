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
 * Makes a horde mob break blocks in its path toward its target.
 * <p>
 * Uses no movement controls so it runs in parallel with the mob's regular
 * navigation/attack goals: the mob walks toward the player AND breaks blocks
 * blocking the way. Drops are suppressed by default so a few thousand wood
 * blocks don't flood the world with item entities.
 */
public class BlockBreakGoal extends Goal {
    private final MobEntity mob;
    private BlockPos targetPos;
    private int breakProgress;
    private int totalBreakTicks;
    private int checkCooldown;

    private static final int CHECK_INTERVAL = 10;
    private static final double MAX_BREAK_DISTANCE_SQ = 6.25; // 2.5 blocks

    public BlockBreakGoal(MobEntity mob) {
        this.mob = mob;
        this.setControls(EnumSet.noneOf(Control.class));
    }

    @Override
    public boolean shouldRunEveryTick() {
        // Smooth cracking animation requires per-tick updates.
        return true;
    }

    @Override
    public boolean canStart() {
        if (!HordeSpawner.isHordeMob(mob)) return false;

        if (checkCooldown > 0) {
            checkCooldown--;
            return false;
        }
        checkCooldown = CHECK_INTERVAL;

        LivingEntity target = mob.getTarget();
        if (target == null) return false;

        targetPos = findBlockInPath(target);
        return targetPos != null;
    }

    @Override
    public boolean shouldContinue() {
        if (targetPos == null) return false;
        BlockState state = mob.getWorld().getBlockState(targetPos);
        if (state.isAir() || !canBreakBlock(state)) return false;

        double distSq = mob.squaredDistanceTo(
                targetPos.getX() + 0.5,
                targetPos.getY() + 0.5,
                targetPos.getZ() + 0.5);
        return distSq < MAX_BREAK_DISTANCE_SQ;
    }

    @Override
    public void start() {
        breakProgress = 0;
        ModConfig config = HordeApocalypse.getConfig();
        // blockBreakSpeed = 0.1 → 10 ticks (0.5s) per block.
        double speed = (config != null) ? config.blockBreakSpeed : 0.1;
        totalBreakTicks = Math.max(1, (int) Math.round(1.0 / Math.max(0.001, speed)));
    }

    @Override
    public void tick() {
        if (targetPos == null) return;

        breakProgress++;
        int stage = Math.min((int) ((float) breakProgress / totalBreakTicks * 10.0f), 9);
        mob.getWorld().setBlockBreakingInfo(mob.getId(), targetPos, stage);

        if (breakProgress >= totalBreakTicks) {
            ModConfig config = HordeApocalypse.getConfig();
            boolean drops = config != null && config.breakDropsItems;
            mob.getWorld().breakBlock(targetPos, drops, mob);
            mob.getWorld().setBlockBreakingInfo(mob.getId(), targetPos, -1);
            targetPos = null;
            checkCooldown = 5;
        }
    }

    @Override
    public void stop() {
        if (targetPos != null) {
            mob.getWorld().setBlockBreakingInfo(mob.getId(), targetPos, -1);
        }
        targetPos = null;
        breakProgress = 0;
    }

    /**
     * Finds a breakable block on the path between the mob and its target.
     * Scans the primary horizontal direction first (feet, then head), then
     * a diagonal if the target is at an angle.
     */
    private BlockPos findBlockInPath(LivingEntity target) {
        BlockPos mobPos = mob.getBlockPos();
        World world = mob.getWorld();
        Vec3d direction = target.getPos().subtract(mob.getPos());

        double absX = Math.abs(direction.x);
        double absZ = Math.abs(direction.z);
        if (absX < 0.1 && absZ < 0.1) return null;

        int primaryDx;
        int primaryDz;
        if (absX >= absZ) {
            primaryDx = direction.x > 0 ? 1 : -1;
            primaryDz = 0;
        } else {
            primaryDx = 0;
            primaryDz = direction.z > 0 ? 1 : -1;
        }

        BlockPos result = checkDirection(world, mobPos, primaryDx, primaryDz);
        if (result != null) return result;

        if (absX > 0.3 && absZ > 0.3) {
            int diagDx = direction.x > 0 ? 1 : -1;
            int diagDz = direction.z > 0 ? 1 : -1;
            result = checkDirection(world, mobPos, diagDx, diagDz);
            if (result != null) return result;
        }
        return null;
    }

    private BlockPos checkDirection(World world, BlockPos mobPos, int dx, int dz) {
        for (int dist = 1; dist <= 2; dist++) {
            BlockPos feetPos = mobPos.add(dx * dist, 0, dz * dist);
            BlockPos headPos = mobPos.add(dx * dist, 1, dz * dist);

            BlockState feetState = world.getBlockState(feetPos);
            BlockState headState = world.getBlockState(headPos);

            if (!feetState.isAir() && canBreakBlock(feetState)) return feetPos;
            if (!headState.isAir() && canBreakBlock(headState)) return headPos;
        }
        return null;
    }

    private boolean canBreakBlock(BlockState state) {
        ModConfig config = HordeApocalypse.getConfig();
        if (config == null) return false;

        DayTracker tracker = HordeApocalypse.getDayTracker();
        int currentDay = (tracker != null) ? tracker.getCurrentDay() : 1;
        Block block = state.getBlock();

        if (isProtected(block)) return false;
        // Avoid breaking blocks with attached inventories (chests, furnaces…).
        // Doors and trapdoors don't carry block entities in vanilla 1.21.
        if (state.hasBlockEntity()) return false;

        if (currentDay >= config.woodBreakStartDay && isWood(state)) return true;
        if (currentDay >= config.stoneBreakStartDay && isStone(state, block)) return true;
        if (currentDay >= config.hardBreakStartDay && isHard(block)) return true;
        return false;
    }

    private static boolean isProtected(Block block) {
        return block == Blocks.BEDROCK
                || block == Blocks.OBSIDIAN
                || block == Blocks.CRYING_OBSIDIAN
                || block == Blocks.NETHERITE_BLOCK
                || block == Blocks.ANCIENT_DEBRIS
                || block == Blocks.REINFORCED_DEEPSLATE
                || block == Blocks.END_PORTAL_FRAME
                || block == Blocks.END_PORTAL
                || block == Blocks.END_GATEWAY
                || block == Blocks.NETHER_PORTAL
                || block == Blocks.BARRIER
                || block == Blocks.LIGHT
                || block == Blocks.STRUCTURE_BLOCK
                || block == Blocks.STRUCTURE_VOID
                || block == Blocks.JIGSAW
                || block == Blocks.COMMAND_BLOCK
                || block == Blocks.CHAIN_COMMAND_BLOCK
                || block == Blocks.REPEATING_COMMAND_BLOCK;
    }

    private static boolean isWood(BlockState state) {
        return state.isIn(BlockTags.PLANKS)
                || state.isIn(BlockTags.WOODEN_DOORS)
                || state.isIn(BlockTags.WOODEN_FENCES)
                || state.isIn(BlockTags.LOGS)
                || state.isIn(BlockTags.FENCE_GATES)
                || state.isIn(BlockTags.WOODEN_TRAPDOORS);
    }

    private static boolean isStone(BlockState state, Block block) {
        if (state.isIn(BlockTags.WALLS)) return true;
        return block == Blocks.STONE
                || block == Blocks.COBBLESTONE
                || block == Blocks.STONE_BRICKS
                || block == Blocks.ANDESITE
                || block == Blocks.DIORITE
                || block == Blocks.GRANITE
                || block == Blocks.MOSSY_COBBLESTONE
                || block == Blocks.MOSSY_STONE_BRICKS
                || block == Blocks.DEEPSLATE
                || block == Blocks.COBBLED_DEEPSLATE
                || block == Blocks.DEEPSLATE_BRICKS
                || block == Blocks.DEEPSLATE_TILES;
    }

    private static boolean isHard(Block block) {
        return block == Blocks.IRON_BLOCK
                || block == Blocks.DIAMOND_BLOCK
                || block == Blocks.GOLD_BLOCK
                || block == Blocks.EMERALD_BLOCK
                || block == Blocks.IRON_DOOR
                || block == Blocks.IRON_TRAPDOOR;
    }
}
