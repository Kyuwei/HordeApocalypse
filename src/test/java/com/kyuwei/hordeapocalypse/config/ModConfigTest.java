package com.kyuwei.hordeapocalypse.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ModConfigTest {
    private static final double EPS = 1e-9;

    @Test
    void healthMultiplierIsOneOnDayZero() {
        ModConfig config = new ModConfig();
        assertEquals(1.0, config.getHealthMultiplier(0), EPS);
    }

    @Test
    void healthMultiplierReachesMaxAtFinalDay() {
        ModConfig config = new ModConfig();
        assertEquals(config.maxHealthMultiplier, config.getHealthMultiplier(config.maxDifficultyDay), EPS);
    }

    @Test
    void healthMultiplierIsLinearMidway() {
        ModConfig config = new ModConfig();
        double mid = config.getHealthMultiplier(config.maxDifficultyDay / 2);
        double expected = 1.0 + (config.maxHealthMultiplier - 1.0) / 2.0;
        assertEquals(expected, mid, EPS);
    }

    @Test
    void multiplierCapsAtMaxBeyondFinalDay() {
        ModConfig config = new ModConfig();
        assertEquals(config.maxHealthMultiplier, config.getHealthMultiplier(config.maxDifficultyDay + 50), EPS);
    }

    @Test
    void multiplierHandlesNegativeDay() {
        ModConfig config = new ModConfig();
        // Negative day is clamped to 0 → returns 1.0 (no scaling).
        assertEquals(1.0, config.getHealthMultiplier(-10), EPS);
    }

    @Test
    void validateClampsNegativeCounts() {
        ModConfig config = new ModConfig();
        config.hordeZombieCount = -5;
        config.hordeSpawnDistance = 0;
        config.hordeDayInterval = -1;
        config.maxHealthMultiplier = 0.5; // below floor of 1.0
        config.blockBreakSpeed = -1.0;
        config.validate();

        assertEquals(0, config.hordeZombieCount);
        assertTrue(config.hordeSpawnDistance >= 16);
        assertTrue(config.hordeDayInterval >= 1);
        assertEquals(1.0, config.maxHealthMultiplier, EPS);
        assertTrue(config.blockBreakSpeed > 0);
    }

    @Test
    void validateClampsRunawayValues() {
        ModConfig config = new ModConfig();
        config.hordeZombieCount = 1_000_000;
        config.maxConcurrentHordeMobs = -10;
        config.maxHealthMultiplier = Double.POSITIVE_INFINITY;
        config.validate();

        assertTrue(config.hordeZombieCount <= 500);
        assertTrue(config.maxConcurrentHordeMobs >= 1);
        assertTrue(Double.isFinite(config.maxHealthMultiplier));
    }

    @Test
    void validateHandlesNaN() {
        ModConfig config = new ModConfig();
        config.maxHealthMultiplier = Double.NaN;
        config.validate();
        assertTrue(Double.isFinite(config.maxHealthMultiplier));
    }

    @Test
    void multiplierIsSafeWhenMaxDifficultyDayIsZero() {
        ModConfig config = new ModConfig();
        config.maxDifficultyDay = 0;
        // Pre-validation: not division-by-zero, returns max directly.
        assertEquals(config.maxHealthMultiplier, config.getHealthMultiplier(50), EPS);
    }
}
