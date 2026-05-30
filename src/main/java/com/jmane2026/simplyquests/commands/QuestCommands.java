package com.jmane2026.simplyquests.commands;

import com.jmane2026.simplyquests.events.QuestServerEvents;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

public class QuestCommands {

    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(Commands.literal("simplyquests")
                .requires(source -> true)

                .then(Commands.literal("enableEditMode")
                        .requires(source -> {
                            // 1. Check if the sender is an actual player
                            if (source.getEntity() instanceof ServerPlayer player) {
                                // 2. Construct NameAndId from the player's name and UUID
                                NameAndId identity = new NameAndId(player.getGameProfile().id(), player.getGameProfile().name());

                                // 3. Perform the check
                                return source.getServer().getPlayerList().isOp(identity);
                            }
                            // Fallback for console/command blocks
                            return false;
                        })
                        .then(Commands.argument("enabled", BoolArgumentType.bool())
                                .executes(context -> {
                                    // Retrieve the boolean value from the argument
                                    boolean newValue = BoolArgumentType.getBool(context, "enabled");

                                    if (context.getSource().getEntity() instanceof ServerPlayer player) {
                                        // FIX: Apply and persist to the specific player's NBT
                                        QuestServerEvents.updateEditModeAndSync(player, newValue);
                                    }

                                    context.getSource().sendSuccess(() -> Component.literal("Quest Edit Mode: " + newValue), true);
                                    return 1;
                                })
                        )
                )
        );
    }
}