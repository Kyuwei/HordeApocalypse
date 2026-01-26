package com.kyuwei.hordeapocalypse.event;

import com.kyuwei.hordeapocalypse.HordeApocalypse;
import com.kyuwei.hordeapocalypse.config.ModConfig;
import com.kyuwei.hordeapocalypse.spawner.HordeSpawner;
import com.kyuwei.hordeapocalypse.tracker.DayTracker;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public class HordeEventHandler {
    private static boolean hordeActive = false;
    private static long hordeStartTime = 0;
    private static final long HORDE_DURATION = 24000L; // 1 jour Minecraft
    
    public static void onServerTick(MinecraftServer server) {
        DayTracker tracker = HordeApocalypse.getDayTracker();
        
        // Vérifier si un nouveau jour commence
        if (tracker.hasDayChanged()) {
            int currentDay = tracker.getCurrentDay();
            
            // Vérifier si c'est un jour de horde
            if (tracker.isHordeDay()) {
                startHorde(server, currentDay);
            }
        }
        
        // Gérer la fin de la horde
        if (hordeActive) {
            long currentTime = server.getOverworld().getTimeOfDay();
            if (currentTime - hordeStartTime >= HORDE_DURATION) {
                endHorde(server);
            }
        }
    }
    
    private static void startHorde(MinecraftServer server, int day) {
        hordeActive = true;
        hordeStartTime = server.getOverworld().getTimeOfDay();
        
        // Message d'avertissement
        String message = day == 100 
            ? "§4§l[APOCALYPSE FINALE] Les enténèbres déferlent !"
            : "§c§l[HORDE - Jour " + day + "] Une horde approche !";
        
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            player.sendMessage(Text.literal(message), false);
            
            // Spawner la horde autour de chaque joueur
            HordeSpawner.spawnHorde(server.getOverworld(), player, day);
        }
        
        HordeApocalypse.LOGGER.info("Horde started on day " + day);
    }
    
    private static void endHorde(MinecraftServer server) {
        hordeActive = false;
        
        // Supprimer les mobs de la horde
        HordeSpawner.clearHordeMobs(server.getOverworld());
        
        // Message de fin
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            player.sendMessage(
                Text.literal("§a§l[HORDE] La horde s'est dissipée..."),
                false
            );
        }
        
        HordeApocalypse.LOGGER.info("Horde ended");
    }
    
    public static boolean isHordeActive() {
        return hordeActive;
    }
}