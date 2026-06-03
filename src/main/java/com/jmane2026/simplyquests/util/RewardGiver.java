package com.jmane2026.simplyquests.util;

import com.jmane2026.simplyquests.quest.QuestReward;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.world.item.ItemStack;

public class RewardGiver {
    public static void giveReward(ServerPlayer player, QuestReward reward) {
        switch (reward.getType()) {
            case ITEM -> {
                ItemStack stack = new ItemStack(reward.getItem(), reward.getCount());
                if (!player.getInventory().add(stack)) {
                    player.drop(stack, false);
                }
            }
            case XP -> {
                player.giveExperiencePoints(reward.getCount());
            }
            case COMMAND -> {
                if (!reward.getCommand().isEmpty()) {
                    CommandSourceStack source = player.createCommandSourceStack();
                    source.getServer().getCommands().performPrefixedCommand(
                            source.withPermission(PermissionSet.ALL_PERMISSIONS).withSuppressedOutput(),
                            reward.getCommand()
                    );
                }
            }
        }
    }
}
