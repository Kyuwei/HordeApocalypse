package com.kyuwei.hordeapocalypse.ai;

import com.kyuwei.hordeapocalypse.HordeApocalypse;
import com.kyuwei.hordeapocalypse.config.ModConfig;
import com.kyuwei.hordeapocalypse.tracker.DayTracker;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * Lets a horde mob chew through the blocks standing between it and its target.
 *
 * <p>The goal claims no movement flags, so it runs alongside the mob's regular
 * navigation and attack goals: the mob walks towards the player *and* breaks
 * what is in the way. Drops are suppressed by default, otherwise a besieged
 * base showers the world with item entities.
 */
public class BlockBreakGoal extends Goal {
    private final Mob mob;
    private BlockPos targetPos;
    private int breakProgress;
    private int totalBreakTicks;
    private int lastSentStage = -1;
    /** Game time before which we do not scan for a new block. */
    private long nextScanTime;

    private static final int SCAN_INTERVAL_TICKS = 10;
    private static final int POST_BREAK_COOLDOWN_TICKS = 5;
    private static final double MAX_BREAK_DISTANCE_SQ = 6.25; // 2.5 blocks

    public BlockBreakGoal(Mob mob) {
        this.mob = mob;
        // No flags claimed: this goal must not preempt movement or attacking.
        this.setFlags(EnumSet.noneOf(Goal.Flag.class));
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        // The cracking animation needs per-tick progress.
        return true;
    }

    @Override
    public boolean canUse() {
        // Throttle on real game time: canUse() is not evaluated every tick, so
        // counting invocations would silently double every interval.
        long now = mob.level().getGameTime();
        if (now < nextScanTime) return false;
        nextScanTime = now + SCAN_INTERVAL_TICKS;

        LivingEntity target = mob.getTarget();
        if (target == null) return false;

        targetPos = findBlockInPath(target);
        return targetPos != null;
    }

    @Override
    public boolean canContinueToUse() {
        if (targetPos == null) return false;
        BlockState state = mob.level().getBlockState(targetPos);
        if (state.isAir() || !canBreakBlock(state, targetPos)) return false;

        double distSq = mob.distanceToSqr(
                targetPos.getX() + 0.5,
                targetPos.getY() + 0.5,
                targetPos.getZ() + 0.5);
        return distSq < MAX_BREAK_DISTANCE_SQ;
    }

    @Override
    public void start() {
        breakProgress = 0;
        lastSentStage = -1;
        ModConfig config = HordeApocalypse.getConfig();
        double speed = (config != null) ? config.blockBreakSpeed : 0.1;
        totalBreakTicks = Math.max(1, (int) Math.round(1.0 / Math.max(0.001, speed)));
    }

    @Override
    public void tick() {
        if (targetPos == null) return;

        breakProgress++;

        // Only broadcast when the visible stage actually changes: this packet
        // goes to every player tracking the mob, every tick, otherwise.
        int stage = Math.min((int) ((float) breakProgress / totalBreakTicks * 10.0f), 9);
        if (stage != lastSentStage) {
            lastSentStage = stage;
            mob.level().destroyBlockProgress(mob.getId(), targetPos, stage);
        }

        if (breakProgress >= totalBreakTicks) {
            // Respect the server-wide break budget; if it is exhausted this
            // tick, hold the block at full crack and retry next tick.
            if (!BlockBreakBudget.tryConsume()) {
                breakProgress = totalBreakTicks;
                return;
            }
            ModConfig config = HordeApocalypse.getConfig();
            boolean drops = config != null && config.breakDropsItems;
            mob.level().destroyBlock(targetPos, drops, mob);
            clearProgress();
            targetPos = null;
            nextScanTime = mob.level().getGameTime() + POST_BREAK_COOLDOWN_TICKS;
        }
    }

    @Override
    public void stop() {
        clearProgress();
        targetPos = null;
        breakProgress = 0;
    }

    private void clearProgress() {
        if (targetPos != null && lastSentStage >= 0) {
            mob.level().destroyBlockProgress(mob.getId(), targetPos, -1);
        }
        lastSentStage = -1;
    }

    /**
     * Finds a breakable block on the path towards the target: the primary
     * horizontal direction first (feet then head), then the diagonal when the
     * target sits at an angle.
     */
    private BlockPos findBlockInPath(LivingEntity target) {
        BlockPos mobPos = mob.blockPosition();
        Level level = mob.level();
        Vec3 direction = target.position().subtract(mob.position());

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

        BlockPos result = checkDirection(level, mobPos, primaryDx, primaryDz);
        if (result != null) return result;

        if (absX > 0.3 && absZ > 0.3) {
            result = checkDirection(level, mobPos,
                    direction.x > 0 ? 1 : -1,
                    direction.z > 0 ? 1 : -1);
            if (result != null) return result;
        }
        return null;
    }

    private BlockPos checkDirection(Level level, BlockPos mobPos, int dx, int dz) {
        for (int dist = 1; dist <= 2; dist++) {
            BlockPos feetPos = mobPos.offset(dx * dist, 0, dz * dist);
            BlockPos headPos = feetPos.above();

            BlockState feetState = level.getBlockState(feetPos);
            if (!feetState.isAir() && canBreakBlock(feetState, feetPos)) return feetPos;

            BlockState headState = level.getBlockState(headPos);
            if (!headState.isAir() && canBreakBlock(headState, headPos)) return headPos;
        }
        return null;
    }

    private boolean canBreakBlock(BlockState state, BlockPos pos) {
        ModConfig config = HordeApocalypse.getConfig();
        if (config == null) return false;

        // Unbreakable in survival (bedrock, barrier, portal frames, command
        // blocks, jigsaw...) all report a negative destroy speed.
        if (state.getDestroySpeed(mob.level(), pos) < 0) return false;
        // Never destroy anything holding items: chests, furnaces, shulkers.
        if (state.hasBlockEntity()) return false;
        if (isProtected(state.getBlock())) return false;

        DayTracker tracker = HordeApocalypse.getDayTracker();
        int currentDay = (tracker != null) ? tracker.getCurrentDay() : 1;

        // Day 1+: everything wooden. Using the axe tag also covers stairs,
        // slabs, fences and trapdoors of every wood type.
        if (currentDay >= config.woodBreakStartDay && state.is(BlockTags.MINEABLE_WITH_AXE)) {
            return true;
        }
        boolean pickaxeMineable = state.is(BlockTags.MINEABLE_WITH_PICKAXE);
        // Day 50+: masonry — stone, bricks, deepslate, and their stairs/slabs.
        if (currentDay >= config.stoneBreakStartDay && pickaxeMineable && !isHardMetal(state.getBlock())) {
            return true;
        }
        // Day 100+: nothing mineable holds them back any more.
        return currentDay >= config.hardBreakStartDay && pickaxeMineable;
    }

    /** Blocks the README promises will always hold. */
    private static boolean isProtected(Block block) {
        return block == Blocks.OBSIDIAN
                || block == Blocks.CRYING_OBSIDIAN
                || block == Blocks.NETHERITE_BLOCK
                || block == Blocks.ANCIENT_DEBRIS
                || block == Blocks.REINFORCED_DEEPSLATE
                || block == Blocks.END_PORTAL_FRAME
                || block == Blocks.SPAWNER
                || block == Blocks.TRIAL_SPAWNER;
    }

    private static boolean isHardMetal(Block block) {
        return block == Blocks.IRON_BLOCK
                || block == Blocks.DIAMOND_BLOCK
                || block == Blocks.GOLD_BLOCK
                || block == Blocks.EMERALD_BLOCK
                || block == Blocks.NETHERITE_BLOCK
                || block == Blocks.IRON_DOOR
                || block == Blocks.IRON_TRAPDOOR
                || block == Blocks.IRON_BARS;
    }
}
