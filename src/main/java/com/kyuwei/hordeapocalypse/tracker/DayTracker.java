package com.kyuwei.hordeapocalypse.tracker;

import com.kyuwei.hordeapocalypse.HordeApocalypse;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.World;

public class DayTracker {
    private long lastDayTime = 0;
    private int currentDay = 1;
    private boolean dayChanged = false;
    
    public void tick(MinecraftServer server) {
        World overworld = server.getOverworld();
        long currentTime = overworld.getTimeOfDay();
        long currentDayTime = currentTime / 24000L;
        
        if (currentDayTime > lastDayTime) {
            lastDayTime = currentDayTime;
            currentDay++;
            dayChanged = true;
            HordeApocalypse.LOGGER.info("Day changed to: " + currentDay);
        }
    }
    
    public int getCurrentDay() {
        return currentDay;
    }
    
    public boolean hasDayChanged() {
        boolean result = dayChanged;
        dayChanged = false;
        return result;
    }
    
    public boolean isHordeDay() {
        int interval = HordeApocalypse.getConfig().hordeDayInterval;
        return currentDay % interval == 0;
    }
    
    public boolean isFinalDay() {
        return currentDay == HordeApocalypse.getConfig().maxDifficultyDay;
    }
}