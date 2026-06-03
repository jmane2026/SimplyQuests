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
                            if (source.getEntity() instanceof ServerPlayer player) {
                                NameAndId identity = new NameAndId(player.getGameProfile().id(), player.getGameProfile().name());

                                return source.getServer().getPlayerList().isOp(identity);
                            }
                            return false;
                        })
                        .then(Commands.argument("enabled", BoolArgumentType.bool())
                                .executes(context -> {
                                    boolean newValue = BoolArgumentType.getBool(context, "enabled");

                                    if (context.getSource().getEntity() instanceof ServerPlayer player) {
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