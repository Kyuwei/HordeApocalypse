package com.kyuwei.hordeapocalypse;

import com.kyuwei.hordeapocalypse.command.HordeCommands;
import com.kyuwei.hordeapocalypse.config.ModConfig;
import com.kyuwei.hordeapocalypse.event.HordeEventHandler;
import com.kyuwei.hordeapocalypse.scaling.MobScalingHandler;
import com.kyuwei.hordeapocalypse.tracker.DayTracker;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HordeApocalypse implements ModInitializer {
    public static final String MOD_ID = "hordeapocalypse";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static volatile ModConfig config;
    private static volatile DayTracker dayTracker;

    @Override
    public void onInitialize() {
        LOGGER.info("Initializing Horde Apocalypse mod...");

        config = ModConfig.load(FabricLoader.getInstance().getConfigDir());
        dayTracker = new DayTracker();

        MobScalingHandler.register();
        HordeCommands.register();

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            dayTracker.tick(server);
            HordeEventHandler.onServerTick(server);
        });

        // In singleplayer the mod instance survives across world loads:
        // reset day tracking each time a server starts.
        ServerLifecycleEvents.SERVER_STARTING.register(server -> dayTracker.reset());
        ServerLifecycleEvents.SERVER_STOPPING.register(HordeEventHandler::onServerStopping);

        LOGGER.info("Horde Apocalypse mod initialized");
    }

    public static ModConfig getConfig() {
        return config;
    }

    public static DayTracker getDayTracker() {
        return dayTracker;
    }
}
