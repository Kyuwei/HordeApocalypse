package com.kyuwei.hordeapocalypse.command;

import com.kyuwei.hordeapocalypse.HordeApocalypse;
import com.kyuwei.hordeapocalypse.event.HordeEventHandler;
import com.kyuwei.hordeapocalypse.spawner.HordeSpawner;
import com.kyuwei.hordeapocalypse.state.HordeState;
import com.kyuwei.hordeapocalypse.tracker.DayTracker;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.permission.v1.PermissionPredicates;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.PermissionLevel;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

/** Operator commands for testing and support. */
public final class HordeCommands {
    private static final Identifier ADMIN_PERMISSION =
            Identifier.fromNamespaceAndPath(HordeApocalypse.MOD_ID, "command/admin");

    private HordeCommands() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, buildContext, selection) ->
            dispatcher.register(literal("hordeapocalypse")
                .requires(PermissionPredicates.require(ADMIN_PERMISSION, PermissionLevel.GAMEMASTERS))
                .then(literal("force").executes(HordeCommands::force))
                .then(literal("stop").executes(HordeCommands::stop))
                .then(literal("status").executes(HordeCommands::status))
                .then(literal("day")
                        .then(argument("day", IntegerArgumentType.integer(1))
                                .executes(HordeCommands::setDay)))
            )
        );
    }

    private static int force(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendSystemMessage(Component.literal("§cThis command must be run by a player."));
            return 0;
        }
        boolean started = HordeEventHandler.forceStartHorde(source.getServer(), player);
        if (!started) {
            // Do not claim success when a horde is already running or when the
            // player is not in the overworld.
            source.sendSystemMessage(Component.literal(
                    "§cNo horde started: one is already active, or you are not in the overworld."));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("§aHorde forced."), true);
        return 1;
    }

    private static int stop(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        HordeEventHandler.forceEndHorde(source.getServer());
        source.sendSuccess(() -> Component.literal("§aHorde stopped and mobs cleared."), true);
        return 1;
    }

    private static int setDay(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        DayTracker tracker = HordeApocalypse.getDayTracker();
        if (tracker == null) {
            source.sendSystemMessage(Component.literal("§cDay tracker unavailable."));
            return 0;
        }
        int day = IntegerArgumentType.getInteger(ctx, "day");
        tracker.setCurrentDay(day);
        source.sendSuccess(() -> Component.literal("§aSurvival day set to " + tracker.getCurrentDay() + "."), true);
        return 1;
    }

    private static int status(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        ServerLevel overworld = source.getServer().overworld();
        DayTracker tracker = HordeApocalypse.getDayTracker();
        int day = tracker != null ? tracker.getCurrentDay() : -1;

        if (overworld == null) {
            source.sendSuccess(() -> Component.literal("§eDay: " + day + " §7| no overworld"), false);
            return 1;
        }
        HordeState state = HordeState.get(overworld);
        boolean hordeDay = tracker != null && tracker.isHordeDay();
        String message = String.format(
                "§eDay: %d §7(%s) §7| §eHorde: %s §7| §eTracked mobs: %d §7| §eQueued: %d §7| §eTicks left: %d",
                day,
                hordeDay ? "§chorde night§7" : "quiet",
                state.hordeActive ? "§cACTIVE" : "§aidle",
                state.hordeMobIds.size(),
                HordeSpawner.queuedCount(),
                state.hordeTicksRemaining);
        source.sendSuccess(() -> Component.literal(message), false);
        return 1;
    }
}
