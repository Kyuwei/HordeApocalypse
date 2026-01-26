package com.kyuwei.hordeapocalypse.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.kyuwei.hordeapocalypse.HordeApocalypse;
import net.fabricmc.loader.api.FabricLoader;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

public class ModConfig {
    // Configuration de la horde
    public int hordeDayInterval = 7; // Tous les 7 jours
    public int hordeSpawnDistance = 200; // Distance de spawn en blocs
    public int hordeZombieCount = 30;
    public int hordeSkeletonCount = 20;
    public int hordeCreeperCount = 10;
    
    // Configuration de la casse des blocs
    public int woodBreakStartDay = 1;
    public int stoneBreakStartDay = 50;
    public int hardBreakStartDay = 100;
    public double blockBreakSpeed = 0.1; // Vitesse de casse (0.1 = lent)
    
    // Configuration de l'évolution des monstres
    public int maxDifficultyDay = 100;
    public double maxHealthMultiplier = 3.0; // +300%
    public double maxDamageMultiplier = 3.0; // +300%
    public double maxSpeedMultiplier = 1.5; // x1.5
    
    // Configuration du jour 100
    public int finalDayWardenCount = 2;
    public int finalDayWitherCount = 3;
    public int finalDayPillagerCount = 50;
    
    private static final String CONFIG_FILE = "hordeapocalypse.json";
    
    public static ModConfig load() {
        File configDir = FabricLoader.getInstance().getConfigDir().toFile();
        File configFile = new File(configDir, CONFIG_FILE);
        
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        
        if (configFile.exists()) {
            try (FileReader reader = new FileReader(configFile)) {
                ModConfig config = gson.fromJson(reader, ModConfig.class);
                HordeApocalypse.LOGGER.info("Configuration loaded from file");
                return config;
            } catch (IOException e) {
                HordeApocalypse.LOGGER.error("Failed to load config, using defaults", e);
            }
        } else {
            ModConfig config = new ModConfig();
            config.save();
            HordeApocalypse.LOGGER.info("Created default configuration file");
            return config;
        }
        
        return new ModConfig();
    }
    
    public void save() {
        File configDir = FabricLoader.getInstance().getConfigDir().toFile();
        File configFile = new File(configDir, CONFIG_FILE);
        
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        
        try (FileWriter writer = new FileWriter(configFile)) {
            gson.toJson(this, writer);
            HordeApocalypse.LOGGER.info("Configuration saved");
        } catch (IOException e) {
            HordeApocalypse.LOGGER.error("Failed to save config", e);
        }
    }
    
    public double getHealthMultiplier(int currentDay) {
        if (currentDay >= maxDifficultyDay) {
            return maxHealthMultiplier;
        }
        return 1.0 + ((maxHealthMultiplier - 1.0) * currentDay / maxDifficultyDay);
    }
    
    public double getDamageMultiplier(int currentDay) {
        if (currentDay >= maxDifficultyDay) {
            return maxDamageMultiplier;
        }
        return 1.0 + ((maxDamageMultiplier - 1.0) * currentDay / maxDifficultyDay);
    }
    
    public double getSpeedMultiplier(int currentDay) {
        if (currentDay >= maxDifficultyDay) {
            return maxSpeedMultiplier;
        }
        return 1.0 + ((maxSpeedMultiplier - 1.0) * currentDay / maxDifficultyDay);
    }
}