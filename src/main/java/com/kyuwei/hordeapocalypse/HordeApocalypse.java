package com.kyuwei.hordeapocalypse;

import com.kyuwei.hordeapocalypse.ai.BlockBreakBudget;
import com.kyuwei.hordeapocalypse.command.HordeCommands;
import com.kyuwei.hordeapocalypse.config.ModConfig;
import com.kyuwei.hordeapocalypse.event.HordeEventHandler;
import com.kyuwei.hordeapocalypse.scaling.MobScalingHandler;
import com.kyuwei.hordeapocalypse.spawner.HordeSpawner;
import com.kyuwei.hordeapocalypse.state.HordeState;
import com.kyuwei.hordeapocalypse.tracker.DayTracker;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HordeApocalypse implements ModInitializer {
    public static final String MOD_ID = "hordeapocalypse";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static volatile ModConfig config;
    private static volatile DayTracker dayTracker;

    @Override
    public void onInitialize() {
        config = ModConfig.load(FabricLoader.getInstance().getConfigDir());
        dayTracker = new DayTracker();

        HordeCommands.register();

        ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            if (entity instanceof Mob mob && HordeSpawner.isHordeMob(mob)) {
                if (!handleHordeMobLoad(mob, level)) return;
            }
            MobScalingHandler.applyScaling(entity, level);
        });

        // Refill before entities tick, so the per-tick break budget is fresh
        // when the horde's goals run.
        ServerTickEvents.START_SERVER_TICK.register(server -> BlockBreakBudget.refill());

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            dayTracker.tick(server);
            HordeEventHandler.onServerTick(server);
        });

        // Fired before any level is loaded: safe place to drop per-world state,
        // which matters in singleplayer where the mod outlives a world.
        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            dayTracker.reset();
            HordeEventHandler.reset();
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(HordeEventHandler::onServerStopping);

        LOGGER.info("Horde Apocalypse initialised");
    }

    /**
     * Deals with a horde mob entering the world, whether freshly spawned or
     * restored from disk.
     *
     * <p>AI goals are not serialised, so the block-breaking goal has to be
     * re-attached on every load — otherwise a chunk reload silently turns the
     * whole horde into harmless wanderers. Mobs that outlived their horde are
     * destroyed here: they are marked persistent, so nothing else would ever
     * remove them.
     *
     * @return whether the mob survived and should still be scaled
     */
    private static boolean handleHordeMobLoad(Mob mob, ServerLevel level) {
        MinecraftServer server = level.getServer();
        ServerLevel overworld = server != null ? server.overworld() : null;
        if (overworld == null) return true;

        HordeState state = HordeState.get(overworld);
        if (!state.hordeActive) {
            state.untrackMob(mob.getUUID());
            mob.discard();
            return false;
        }
        HordeSpawner.attachHordeAi(mob);
        return true;
    }

    public static ModConfig getConfig() {
        return config;
    }

    public static DayTracker getDayTracker() {
        return dayTracker;
    }
}
