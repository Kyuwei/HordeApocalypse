package com.kyuwei.hordeapocalypse.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Mod configuration. Deliberately free of any Minecraft type so it can be
 * unit-tested without the game on the classpath.
 */
public class ModConfig {
    // ----- Horde composition -----
    public int hordeDayInterval = 7;
    public int hordeSpawnDistance = 200;
    public int hordeZombieCount = 30;
    public int hordeSkeletonCount = 20;
    public int hordeCreeperCount = 10;

    /**
     * Time of day (0-23999) at which a horde is unleashed. 13000 is dusk:
     * the horde gets the whole night before daylight burns the undead.
     */
    public int hordeStartTimeOfDay = 13000;

    // ----- Block breaking -----
    public int woodBreakStartDay = 1;
    public int stoneBreakStartDay = 50;
    public int hardBreakStartDay = 100;
    /** Fraction of a block broken per tick. 0.1 => 10 ticks (0.5 s) per block. */
    public double blockBreakSpeed = 0.1;
    public boolean breakDropsItems = false;
    /** Server-wide ceiling on blocks destroyed per tick by the whole horde. */
    public int maxBlockBreaksPerTick = 8;

    // ----- Difficulty progression -----
    public int maxDifficultyDay = 100;
    /** 4.0 => +3 %/day, reaching +300 % on day 100. */
    public double maxHealthMultiplier = 4.0;
    public double maxDamageMultiplier = 4.0;
    /** 2.5 => +1.5 %/day, reaching +150 % on day 100. */
    public double maxSpeedMultiplier = 2.5;

    // ----- Final day bosses -----
    public int finalDayWardenCount = 2;
    public int finalDayWitherCount = 3;
    public int finalDayPillagerCount = 50;

    // ----- Multiplayer / performance safeguards -----
    public int maxConcurrentHordeMobs = 300;
    public int clusterMergeDistance = 100;
    /** Mobs materialised per tick, so a horde never lands in a single tick. */
    public int maxSpawnsPerTick = 20;

    static final String CONFIG_FILE = "hordeapocalypse.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Logger LOGGER = LoggerFactory.getLogger("hordeapocalypse/config");

    private transient Path activeFile;

    public static ModConfig load(Path configDir) {
        Path configFile = configDir.resolve(CONFIG_FILE);

        if (!Files.exists(configFile)) {
            ModConfig fresh = new ModConfig();
            fresh.activeFile = configFile;
            fresh.save();
            LOGGER.info("Created default configuration file at {}", configFile);
            return fresh;
        }

        try (Reader reader = Files.newBufferedReader(configFile, StandardCharsets.UTF_8)) {
            ModConfig loaded = GSON.fromJson(reader, ModConfig.class);
            if (loaded == null) {
                throw new JsonParseException("Configuration deserialized to null");
            }
            loaded.activeFile = configFile;
            List<String> clamped = loaded.validate();
            if (!clamped.isEmpty()) {
                LOGGER.warn("Configuration values out of range were clamped: {}", String.join(", ", clamped));
                loaded.save();
            }
            LOGGER.info("Configuration loaded from {}", configFile);
            return loaded;
        } catch (IOException | JsonParseException | NumberFormatException e) {
            LOGGER.error("Failed to load configuration, regenerating defaults", e);
            backupCorruptedFile(configFile);
            ModConfig fresh = new ModConfig();
            fresh.activeFile = configFile;
            fresh.save();
            return fresh;
        }
    }

    public void save() {
        if (activeFile == null) return;
        try {
            Path parent = activeFile.getParent();
            if (parent != null) Files.createDirectories(parent);
            try (Writer writer = Files.newBufferedWriter(activeFile, StandardCharsets.UTF_8)) {
                GSON.toJson(this, writer);
            }
        } catch (IOException e) {
            LOGGER.error("Failed to save configuration", e);
        }
    }

    /**
     * Clamps every field into a safe range. Invalid configuration must never
     * crash the mod nor open a denial-of-service vector such as spawning
     * billions of mobs.
     *
     * @return human readable descriptions of the values that had to be changed
     */
    public List<String> validate() {
        List<String> changes = new ArrayList<>();

        hordeDayInterval       = clampInt("hordeDayInterval", hordeDayInterval, 1, 1000, changes);
        hordeSpawnDistance     = clampInt("hordeSpawnDistance", hordeSpawnDistance, 16, 2000, changes);
        hordeZombieCount       = clampInt("hordeZombieCount", hordeZombieCount, 0, 500, changes);
        hordeSkeletonCount     = clampInt("hordeSkeletonCount", hordeSkeletonCount, 0, 500, changes);
        hordeCreeperCount      = clampInt("hordeCreeperCount", hordeCreeperCount, 0, 500, changes);
        hordeStartTimeOfDay    = clampInt("hordeStartTimeOfDay", hordeStartTimeOfDay, 0, 23999, changes);

        woodBreakStartDay      = clampInt("woodBreakStartDay", woodBreakStartDay, 1, 100_000, changes);
        stoneBreakStartDay     = clampInt("stoneBreakStartDay", stoneBreakStartDay, 1, 100_000, changes);
        hardBreakStartDay      = clampInt("hardBreakStartDay", hardBreakStartDay, 1, 100_000, changes);
        blockBreakSpeed        = clampDouble("blockBreakSpeed", blockBreakSpeed, 0.001, 10.0, changes);
        maxBlockBreaksPerTick  = clampInt("maxBlockBreaksPerTick", maxBlockBreaksPerTick, 1, 512, changes);

        maxDifficultyDay       = clampInt("maxDifficultyDay", maxDifficultyDay, 1, 100_000, changes);
        maxHealthMultiplier    = clampDouble("maxHealthMultiplier", maxHealthMultiplier, 1.0, 100.0, changes);
        maxDamageMultiplier    = clampDouble("maxDamageMultiplier", maxDamageMultiplier, 1.0, 100.0, changes);
        maxSpeedMultiplier     = clampDouble("maxSpeedMultiplier", maxSpeedMultiplier, 1.0, 10.0, changes);

        finalDayWardenCount    = clampInt("finalDayWardenCount", finalDayWardenCount, 0, 50, changes);
        finalDayWitherCount    = clampInt("finalDayWitherCount", finalDayWitherCount, 0, 50, changes);
        finalDayPillagerCount  = clampInt("finalDayPillagerCount", finalDayPillagerCount, 0, 500, changes);

        maxConcurrentHordeMobs = clampInt("maxConcurrentHordeMobs", maxConcurrentHordeMobs, 1, 10_000, changes);
        clusterMergeDistance   = clampInt("clusterMergeDistance", clusterMergeDistance, 0, 2000, changes);
        maxSpawnsPerTick       = clampInt("maxSpawnsPerTick", maxSpawnsPerTick, 1, 1000, changes);

        return changes;
    }

    public double getHealthMultiplier(int currentDay) {
        return progressiveMultiplier(currentDay, maxHealthMultiplier);
    }

    public double getDamageMultiplier(int currentDay) {
        return progressiveMultiplier(currentDay, maxDamageMultiplier);
    }

    public double getSpeedMultiplier(int currentDay) {
        return progressiveMultiplier(currentDay, maxSpeedMultiplier);
    }

    private double progressiveMultiplier(int currentDay, double max) {
        if (maxDifficultyDay <= 0) return max;
        int clampedDay = Math.max(0, currentDay);
        if (clampedDay >= maxDifficultyDay) return max;
        return 1.0 + ((max - 1.0) * clampedDay / maxDifficultyDay);
    }

    /** Ticks the horde should stay active when it starts at the configured dusk. */
    public int hordeDurationTicks() {
        return Math.max(1, 24000 - hordeStartTimeOfDay);
    }

    private static int clampInt(String name, int value, int min, int max, List<String> changes) {
        int result = Math.max(min, Math.min(max, value));
        if (result != value) changes.add(name + " " + value + " -> " + result);
        return result;
    }

    private static double clampDouble(String name, double value, double min, double max, List<String> changes) {
        double result;
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            result = min;
        } else {
            result = Math.max(min, Math.min(max, value));
        }
        if (result != value) changes.add(name + " " + value + " -> " + result);
        return result;
    }

    private static void backupCorruptedFile(Path configFile) {
        try {
            Path backup = configFile.resolveSibling(configFile.getFileName().toString() + ".bak");
            Files.move(configFile, backup, StandardCopyOption.REPLACE_EXISTING);
            LOGGER.warn("Corrupted config moved to {}", backup);
        } catch (IOException ignored) {
            // Best-effort backup; a failure here must not stop the mod from loading.
        }
    }
}
