package com.kyuwei.hordeapocalypse.command;

import com.kyuwei.hordeapocalypse.HordeApocalypse;
import com.kyuwei.hordeapocalypse.event.HordeEventHandler;
import com.kyuwei.hordeapocalypse.spawner.HordeSpawner;
import com.kyuwei.hordeapocalypse.state.HordePersistentState;
import com.kyuwei.hordeapocalypse.tracker.DayTracker;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.TypeFilter;

import static net.minecraft.server.command.CommandManager.literal;

public final class HordeCommands {
    private HordeCommands() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
            dispatcher.register(literal("hordeapocalypse")
                .requires(source -> source.hasPermissionLevel(2))
                .then(literal("force").executes(HordeCommands::force))
                .then(literal("stop").executes(HordeCommands::stop))
                .then(literal("status").executes(HordeCommands::status))
            )
        );
    }

    private static int force(CommandContext<ServerCommandSource> ctx) {
        ServerCommandSource source = ctx.getSource();
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) {
            source.sendError(Text.literal("This command must be run by a player"));
            return 0;
        }
        HordeEventHandler.forceStartHorde(source.getServer(), player);
        source.sendFeedback(() -> Text.literal("Forced horde start"), true);
        return 1;
    }

    private static int stop(CommandContext<ServerCommandSource> ctx) {
        ServerCommandSource source = ctx.getSource();
        HordeEventHandler.forceEndHorde(source.getServer());
        source.sendFeedback(() -> Text.literal("Horde stopped"), true);
        return 1;
    }

    private static int status(CommandContext<ServerCommandSource> ctx) {
        ServerCommandSource source = ctx.getSource();
        ServerWorld overworld = source.getServer().getOverworld();
        DayTracker tracker = HordeApocalypse.getDayTracker();
        int day = tracker != null ? tracker.getCurrentDay() : -1;

        if (overworld == null) {
            source.sendFeedback(() -> Text.literal("§eDay: " + day + " §7| no overworld"), false);
            return 1;
        }
        HordePersistentState state = HordePersistentState.get(overworld);
        String msg = String.format(
                "§eDay: %d §7| §eHorde: %s §7| §eMobs tracked: %d §7| §eTicks left: %d",
                day,
                state.hordeActive ? "§cACTIVE" : "§aidle",
                state.hordeMobIds.size(),
                state.hordeTicksRemaining);
        // Also report the number of mobs surviving on the world (e.g., post-restart).
        long worldMobs = overworld.getEntitiesByType(
                TypeFilter.instanceOf(MobEntity.class),
                m -> m.getCommandTags().contains(HordeSpawner.HORDE_TAG)).size();
        source.sendFeedback(() -> Text.literal(msg + " §7| §eIn-world: " + worldMobs), false);
        return 1;
    }
}
