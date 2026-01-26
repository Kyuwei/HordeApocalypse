package com.kyuwei.hordeapocalypse;

import com.kyuwei.hordeapocalypse.config.ModConfig;
import com.kyuwei.hordeapocalypse.event.HordeEventHandler;
import com.kyuwei.hordeapocalypse.tracker.DayTracker;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HordeApocalypse implements ModInitializer {
    public static final String MOD_ID = "hordeapocalypse";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    
    private static ModConfig config;
    private static DayTracker dayTracker;
    
    @Override
    public void onInitialize() {
        LOGGER.info("Initializing Horde Apocalypse mod...");
        
        // Charger la configuration
        config = ModConfig.load();
        LOGGER.info("Configuration loaded successfully");
        
        // Initialiser le tracker de jours
        dayTracker = new DayTracker();
        
        // Enregistrer les événements
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            dayTracker.tick(server);
            HordeEventHandler.onServerTick(server);
        });
        
        LOGGER.info("Horde Apocalypse mod initialized!");
    }
    
    public static ModConfig getConfig() {
        return config;
    }
    
    public static DayTracker getDayTracker() {
        return dayTracker;
    }
}