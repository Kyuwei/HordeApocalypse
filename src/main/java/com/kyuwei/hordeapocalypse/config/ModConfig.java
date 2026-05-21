package com.kyuwei.hordeapocalypse.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public class ModConfig {
    // ----- Horde composition -----
    public int hordeDayInterval = 7;
    public int hordeSpawnDistance = 200;
    public int hordeZombieCount = 30;
    public int hordeSkeletonCount = 20;
    public int hordeCreeperCount = 10;

    // ----- Block breaking -----
    public int woodBreakStartDay = 1;
    public int stoneBreakStartDay = 50;
    public int hardBreakStartDay = 100;
    public double blockBreakSpeed = 0.1;
    public boolean breakDropsItems = false;

    // ----- Difficulty progression -----
    public int maxDifficultyDay = 100;
    public double maxHealthMultiplier = 3.0;
    public double maxDamageMultiplier = 3.0;
    public double maxSpeedMultiplier = 1.5;

    // ----- Final day bosses -----
    public int finalDayWardenCount = 2;
    public int finalDayWitherCount = 3;
    public int finalDayPillagerCount = 50;

    // ----- Multiplayer safeguards -----
    public int maxConcurrentHordeMobs = 300;
    public int clusterMergeDistance = 100;

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
                throw new JsonSyntaxException("Configuration deserialized to null");
            }
            loaded.activeFile = configFile;
            loaded.validate();
            LOGGER.info("Configuration loaded from {}", configFile);
            return loaded;
        } catch (IOException | JsonSyntaxException e) {
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
            Files.createDirectories(activeFile.getParent());
            try (Writer writer = Files.newBufferedWriter(activeFile, StandardCharsets.UTF_8)) {
                GSON.toJson(this, writer);
            }
        } catch (IOException e) {
            LOGGER.error("Failed to save configuration", e);
        }
    }

    /**
     * Clamp every field to a safe range. Invalid configuration must not crash the
     * mod or open DoS vectors (e.g. spawning billions of mobs).
     */
    void validate() {
        hordeDayInterval        = clampInt(hordeDayInterval, 1, 1000);
        hordeSpawnDistance      = clampInt(hordeSpawnDistance, 16, 2000);
        hordeZombieCount        = clampInt(hordeZombieCount, 0, 500);
        hordeSkeletonCount      = clampInt(hordeSkeletonCount, 0, 500);
        hordeCreeperCount       = clampInt(hordeCreeperCount, 0, 500);

        woodBreakStartDay       = clampInt(woodBreakStartDay, 1, 100_000);
        stoneBreakStartDay      = clampInt(stoneBreakStartDay, 1, 100_000);
        hardBreakStartDay       = clampInt(hardBreakStartDay, 1, 100_000);
        blockBreakSpeed         = clampDouble(blockBreakSpeed, 0.001, 10.0);

        maxDifficultyDay        = clampInt(maxDifficultyDay, 1, 100_000);
        maxHealthMultiplier     = clampDouble(maxHealthMultiplier, 1.0, 100.0);
        maxDamageMultiplier     = clampDouble(maxDamageMultiplier, 1.0, 100.0);
        maxSpeedMultiplier      = clampDouble(maxSpeedMultiplier,  1.0, 10.0);

        finalDayWardenCount     = clampInt(finalDayWardenCount, 0, 50);
        finalDayWitherCount     = clampInt(finalDayWitherCount, 0, 50);
        finalDayPillagerCount   = clampInt(finalDayPillagerCount, 0, 500);

        maxConcurrentHordeMobs  = clampInt(maxConcurrentHordeMobs, 1, 10_000);
        clusterMergeDistance    = clampInt(clusterMergeDistance, 0, 2000);
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

    private static int clampInt(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double clampDouble(double value, double min, double max) {
        if (Double.isNaN(value) || Double.isInfinite(value)) return min;
        return Math.max(min, Math.min(max, value));
    }

    private static void backupCorruptedFile(Path configFile) {
        try {
            Path backup = configFile.resolveSibling(configFile.getFileName() + ".bak");
            Files.move(configFile, backup, StandardCopyOption.REPLACE_EXISTING);
            LOGGER.warn("Corrupted config moved to {}", backup);
        } catch (IOException ignored) {
            // Best-effort backup; ignore failure.
        }
    }
}
