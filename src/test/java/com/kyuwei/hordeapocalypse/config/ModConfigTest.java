package com.kyuwei.hordeapocalypse.config;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ModConfigTest {
    private static final double EPS = 1e-9;

    // ---- Progression documented in the README: +3 %/day health & damage,
    // ---- +1.5 %/day speed, capped at day 100.

    @Test
    void healthGainsThreePercentPerDay() {
        ModConfig config = new ModConfig();
        for (int day : new int[] {1, 7, 42, 99}) {
            assertEquals(1.0 + 0.03 * day, config.getHealthMultiplier(day), EPS,
                    "health multiplier on day " + day);
        }
    }

    @Test
    void damageGainsThreePercentPerDay() {
        ModConfig config = new ModConfig();
        assertEquals(1.0 + 0.03 * 50, config.getDamageMultiplier(50), EPS);
    }

    @Test
    void speedGainsOnePointFivePercentPerDay() {
        ModConfig config = new ModConfig();
        for (int day : new int[] {1, 20, 80}) {
            assertEquals(1.0 + 0.015 * day, config.getSpeedMultiplier(day), EPS,
                    "speed multiplier on day " + day);
        }
    }

    @Test
    void multipliersReachDocumentedMaximaOnDayHundred() {
        ModConfig config = new ModConfig();
        // "+300 %" means four times the base value, "+150 %" two and a half.
        assertEquals(4.0, config.getHealthMultiplier(100), EPS);
        assertEquals(4.0, config.getDamageMultiplier(100), EPS);
        assertEquals(2.5, config.getSpeedMultiplier(100), EPS);
    }

    @Test
    void multipliersAreNeutralOnDayZero() {
        ModConfig config = new ModConfig();
        assertEquals(1.0, config.getHealthMultiplier(0), EPS);
        assertEquals(1.0, config.getSpeedMultiplier(0), EPS);
    }

    @Test
    void multipliersStayCappedBeyondFinalDay() {
        ModConfig config = new ModConfig();
        assertEquals(4.0, config.getHealthMultiplier(100_000), EPS);
    }

    @Test
    void negativeDayIsTreatedAsDayZero() {
        ModConfig config = new ModConfig();
        assertEquals(1.0, config.getHealthMultiplier(-10), EPS);
    }

    @Test
    void multiplierIsSafeWhenFinalDayIsZero() {
        ModConfig config = new ModConfig();
        config.maxDifficultyDay = 0; // would divide by zero if unguarded
        assertEquals(config.maxHealthMultiplier, config.getHealthMultiplier(50), EPS);
    }

    // ---- Hardening against a hand-edited config file.

    @Test
    void validateClampsOutOfRangeValuesAndReportsThem() {
        ModConfig config = new ModConfig();
        config.hordeZombieCount = -5;
        config.hordeSpawnDistance = 0;
        config.hordeDayInterval = -1;
        config.maxHealthMultiplier = 0.5;
        config.blockBreakSpeed = -1.0;

        List<String> changes = config.validate();

        assertEquals(0, config.hordeZombieCount);
        assertTrue(config.hordeSpawnDistance >= 16);
        assertTrue(config.hordeDayInterval >= 1);
        assertEquals(1.0, config.maxHealthMultiplier, EPS);
        assertTrue(config.blockBreakSpeed > 0);
        assertEquals(5, changes.size(), "every clamped field should be reported: " + changes);
    }

    @Test
    void validateClampsRunawayValues() {
        ModConfig config = new ModConfig();
        config.hordeZombieCount = 1_000_000;
        config.maxConcurrentHordeMobs = -10;
        config.maxSpawnsPerTick = 0;
        config.maxHealthMultiplier = Double.POSITIVE_INFINITY;
        config.validate();

        assertTrue(config.hordeZombieCount <= 500);
        assertTrue(config.maxConcurrentHordeMobs >= 1);
        assertTrue(config.maxSpawnsPerTick >= 1);
        assertTrue(Double.isFinite(config.maxHealthMultiplier));
    }

    @Test
    void validateRejectsNaN() {
        ModConfig config = new ModConfig();
        config.maxHealthMultiplier = Double.NaN;
        config.validate();
        assertTrue(Double.isFinite(config.maxHealthMultiplier));
    }

    @Test
    void validateIsSilentOnDefaults() {
        assertTrue(new ModConfig().validate().isEmpty(), "shipped defaults must already be valid");
    }

    @Test
    void hordeStartTimeIsClampedToADay() {
        ModConfig config = new ModConfig();
        config.hordeStartTimeOfDay = 999_999;
        config.validate();
        assertTrue(config.hordeStartTimeOfDay <= 23999);
    }

    // ---- Horde duration: dusk to dawn.

    @Test
    void hordeLastsFromDuskUntilTheEndOfTheDay() {
        ModConfig config = new ModConfig();
        assertEquals(24000 - config.hordeStartTimeOfDay, config.hordeDurationTicks());
        assertTrue(config.hordeDurationTicks() > 0);
    }

    @Test
    void hordeDurationStaysPositiveAtMidnightStart() {
        ModConfig config = new ModConfig();
        config.hordeStartTimeOfDay = 23999;
        assertTrue(config.hordeDurationTicks() >= 1);
    }
}
