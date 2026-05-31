package com.jmane2026.simplyquests.client.screen.input;

import com.jmane2026.simplyquests.client.screen.QuestEditorUI;
import com.jmane2026.simplyquests.client.screen.QuestScreen;
import com.jmane2026.simplyquests.quest.Quest;
import com.jmane2026.simplyquests.quest.QuestShape;
import com.jmane2026.simplyquests.quest.QuestReward;
import com.jmane2026.simplyquests.quest.QuestTask;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.lwjgl.glfw.GLFW;
import java.util.List;

public class PickerHandler {
    public static boolean handle(QuestScreen screen, double mouseX, double mouseY, int button) {
        QuestEditorUI ui = screen.editorUI;
        if (!ui.isIconPickerOpen && !ui.isTypePickerOpen && !ui.isDependencyPickerOpen && !ui.isShapePickerOpen && !ui.isTargetPickerOpen) return false;

        int ph = (screen.isRewardEditorOpen) ? 200 : 250;
        int px = screen.isEditingChapterIcon ? (int) screen.currentSidebarWidth - 300 : (screen.width - 300) / 2;
        // FIX: Use ph (dynamic height) instead of hardcoded 250 for centering
        int py = screen.isEditingChapterIcon ? 20 : (screen.height - ph) / 2;
        QuestEditorUI.PickerBounds b = ui.getPickerBounds(px, py, ph);

        if (!QuestEditorUI.isMouseOver(mouseX, mouseY, b.x(), b.y(), b.w(), b.h())) {
            if (button == GLFW.GLFW_MOUSE_BUTTON_1) { ui.closePicker(); screen.isEditingChapterIcon = false; QuestScreen.playClickSound(); }
            return true;
        }

        if (button != GLFW.GLFW_MOUSE_BUTTON_1) return true;

        // FIX: Only skip the 16px header if the current picker actually renders a search bar
        if (ui.isIconPickerOpen || ui.isTargetPickerOpen || ui.isDependencyPickerOpen) {
            if (mouseY < b.y() + 16) return true;
        } else {
            // For Type/Shape pickers, check if we are clicking in the top padding (5px)
            if (mouseY < b.y() + 5) return true;
        }

        // 1. Dependency Picker Selection
        if (ui.isDependencyPickerOpen) {
            List<Quest> filtered = ui.getFilteredDependencies(screen.allQuests, screen.questToModify);
            int itemRowHeight = 16;

            for (int i = 0; i < filtered.size(); i++) {
                // This math matches QuestEditorUI.render exactly
                int rowY = (b.y() + b.barHeight() + 5) + (i * itemRowHeight) - (int) ui.scrollOffset;

                if (mouseX >= b.x() && mouseX <= b.x() + b.w() && mouseY >= rowY && mouseY <= rowY + itemRowHeight) {
                    Quest target = filtered.get(i);
                    String targetId = target.getId();

                    if (ui.isRemoveDependencyMode) {
                        screen.questToModify.getDependencies().remove(targetId);
                    } else if (!screen.questToModify.getDependencies().contains(targetId)) {
                        screen.questToModify.getDependencies().add(targetId);
                    }

                    ui.closePicker();
                    QuestScreen.playClickSound();
                    return true;
                }
            }
            return true;
        }

        if (ui.isShapePickerOpen) {
            int idx = (int)((mouseY - b.y() - 5) / 16);
            if (idx >= 0 && idx < QuestShape.values().length) { screen.questToModify.setShape(QuestShape.values()[idx]); ui.isShapePickerOpen = false; QuestScreen.playClickSound(); }
            return true;
        }

        if (ui.isTypePickerOpen) {
            int idx = (int)Math.floor((mouseY - b.y() - 5) / 16.0);
            if (screen.isTaskEditorOpen) {
                QuestTask.TaskType[] types = QuestTask.TaskType.values();
                if (idx >= 0 && idx < types.length) {
                    QuestTask.TaskType newType = types[idx];
                    if (screen.taskToModify.getType() != newType) {
                        screen.taskToModify.setType(newType);

                        // Reset variables to defaults for the new type
                        screen.taskToModify.setRequiredAmount(1);
                        switch (newType) {
                            case ITEM -> screen.taskToModify.setTargetId("minecraft:air");
                            case KILL -> screen.taskToModify.setTargetId("minecraft:pig");
                            case BIOME -> screen.taskToModify.setTargetId("minecraft:plains");
                            case OBSERVE -> screen.taskToModify.setTargetId("minecraft:grass_block");
                            case LOCATION -> {
                                screen.taskToModify.setTargetId("Location");
                                screen.taskToModify.setTargetX(0);
                                screen.taskToModify.setTargetY(0);
                                screen.taskToModify.setTargetZ(0);
                            }
                            case CHECKBOX -> screen.taskToModify.setTargetId("");
                        }
                        // Automatically sync the display name with the new default target
                        screen.updateTaskNameAndId(screen.taskToModify.getTargetDisplayName());
                    }
                    ui.isTypePickerOpen = false;
                    QuestScreen.playClickSound(); }
            } else if (screen.isRewardEditorOpen) {
                QuestReward.RewardType[] types = QuestReward.RewardType.values();
                if (idx >= 0 && idx < types.length) {
                    QuestReward.RewardType newType = types[idx];
                    if (screen.rewardToModify.getType() != newType) {
                        // Reset Reward data to defaults
                        screen.rewardToModify = new QuestReward(
                                screen.rewardToModify.getId(),
                                newType,
                                Items.AIR,
                                1,
                                "",
                                new java.util.ArrayList<>()
                        );
                    }
                    ui.isTypePickerOpen = false;
                    QuestScreen.playClickSound(); }
            }
            return true;
        }

        if (ui.isTargetPickerOpen) {
            List<String> targets = ui.getFilteredTargets(screen.taskToModify.getType());
            int cols = (screen.taskToModify.getType() == QuestTask.TaskType.BIOME) ? 1 : (screen.taskToModify.getType() == QuestTask.TaskType.KILL ? 3 : 5);
            int size = (screen.taskToModify.getType() == QuestTask.TaskType.BIOME) ? 16 : (screen.taskToModify.getType() == QuestTask.TaskType.KILL ? 30 : 18);
            int col = (int) (mouseX - b.x() - 5) / size;
            int row = (int) Math.floor(((mouseY - (b.y() + 16) - 5) + ui.scrollOffset) / size);
            int idx = col + (row * cols);
            if (idx >= 0 && idx < targets.size()) { screen.updateTaskTargetAndName(screen.taskToModify.getType(), targets.get(idx)); ui.isTargetPickerOpen = false; QuestScreen.playClickSound(); }
            return true;
        }

        if (ui.isIconPickerOpen) {
            int col = (int) (mouseX - b.x() - 5) / 18;
            int row = (int) Math.floor(((mouseY - (b.y() + 16) - 5) + ui.scrollOffset) / 18);

            // FIX 1: Boundary check for columns to prevent "bleed-over" selection
            if (col < 0 || col >= 5) return true;

            List<Item> icons = ui.getCachedIcons();
            boolean showCheck = ui.searchQuery.isEmpty() || "checkmark".contains(ui.searchQuery.toLowerCase());
            int idx = col + (row * 5);

            if (showCheck && idx == 0) {
                if (screen.isEditingChapterIcon) { screen.sidebarTargetChapter.setIconStack(new ItemStack(Items.PAPER)); screen.saveChapterData(screen.sidebarTargetChapter.getName()); }
                else if (screen.questToModify != null) { screen.questToModify.setUseTaskIcon(true); screen.questToModify.setLogo(Items.PAPER); }
                ui.closePicker(); screen.isEditingChapterIcon = false;
            } else {
                // FIX 2: Use the correctly calculated 'idx' variable instead of the orphaned 'index'
                int itemIdx = showCheck ? idx - 1 : idx;
                if (itemIdx >= 0 && itemIdx < icons.size()) {
                    Item s = icons.get(itemIdx);
                    if (screen.isEditingChapterIcon) { screen.sidebarTargetChapter.setIconStack(new ItemStack(s)); screen.saveChapterData(screen.sidebarTargetChapter.getName()); }
                    else if (screen.rewardToModify != null) {
                        // Use setter to preserve subRewards (choices) inside the bundle
                        screen.rewardToModify.setItem(s);
                    }
                    else if (screen.questToModify != null) { screen.questToModify.setLogo(s); screen.questToModify.setUseTaskIcon(false); }
                    ui.closePicker(); screen.isEditingChapterIcon = false;
                }
            }
            QuestScreen.playClickSound();
        }
        return true;
    }
}