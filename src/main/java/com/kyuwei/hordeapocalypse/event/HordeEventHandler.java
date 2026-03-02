package com.kyuwei.hordeapocalypse.event;

import com.kyuwei.hordeapocalypse.HordeApocalypse;
import com.kyuwei.hordeapocalypse.config.ModConfig;
import com.kyuwei.hordeapocalypse.spawner.HordeSpawner;
import com.kyuwei.hordeapocalypse.tracker.DayTracker;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

public class HordeEventHandler {
    private static boolean hordeActive = false;
    /** Tick countdown — avoids the getTimeOfDay() wrap-around bug */
    private static int hordeTicksRemaining = 0;
    private static final int HORDE_DURATION_TICKS = 24000; // 1 Minecraft day

    public static void onServerTick(MinecraftServer server) {
        DayTracker tracker = HordeApocalypse.getDayTracker();
        if (tracker == null) return;

        // Check for new day
        if (tracker.hasDayChanged()) {
            int currentDay = tracker.getCurrentDay();
            if (tracker.isHordeDay()) {
                startHorde(server, currentDay);
            }
        }

        // Count down horde duration
        if (hordeActive) {
            hordeTicksRemaining--;
            if (hordeTicksRemaining <= 0) {
                endHorde(server);
            }
        }
    }

    private static void startHorde(MinecraftServer server, int day) {
        hordeActive = true;
        hordeTicksRemaining = HORDE_DURATION_TICKS;

        ModConfig config = HordeApocalypse.getConfig();
        String message = day >= config.maxDifficultyDay
                ? "§4§l[APOCALYPSE FINALE] Les enténèbres déferlent !"
                : "§c§l[HORDE - Jour " + day + "] Une horde approche !";

        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            player.sendMessage(Text.literal(message), false);
            HordeSpawner.spawnHorde(server.getOverworld(), player, day);
        }

        HordeApocalypse.LOGGER.info("Horde started on day {}", day);
    }

    private static void endHorde(MinecraftServer server) {
        hordeActive = false;
        hordeTicksRemaining = 0;

        HordeSpawner.clearHordeMobs(server.getOverworld());

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