package com.jmane2026.simplyquests.client.screen.input;

import com.jmane2026.simplyquests.client.screen.QuestEditorUI;
import com.jmane2026.simplyquests.client.screen.QuestScreen;
import com.jmane2026.simplyquests.quest.Quest;
import com.jmane2026.simplyquests.quest.QuestReward;
import com.jmane2026.simplyquests.quest.QuestShape;
import com.jmane2026.simplyquests.quest.QuestTask;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.lwjgl.glfw.GLFW;

import java.util.List;

public class PickerHandler {
    public static boolean handle(QuestScreen screen, double mouseX, double mouseY, int button) {
        QuestEditorUI ui = screen.editorUI;
        if (!ui.isIconPickerOpen && !ui.isTypePickerOpen && !ui.isDependencyPickerOpen && !ui.isShapePickerOpen && !ui.isTargetPickerOpen)
            return false;

        int ph = (screen.isRewardEditorOpen) ? 200 : 250;
        int px = screen.isEditingChapterIcon ? (int) screen.currentSidebarWidth - 300 : (screen.width - 300) / 2;
        int py = screen.isEditingChapterIcon ? 20 : (screen.height - ph) / 2;
        QuestEditorUI.PickerBounds b = ui.getPickerBounds(px, py, ph);

        if (!QuestEditorUI.isMouseOver(mouseX, mouseY, b.x(), b.y(), b.w(), b.h())) {
            if (button == GLFW.GLFW_MOUSE_BUTTON_1) {
                ui.closePicker();
                screen.isEditingChapterIcon = false;
                QuestScreen.playClickSound();
            }
            return true;
        }

        if (button != GLFW.GLFW_MOUSE_BUTTON_1) return true;

        if (ui.isIconPickerOpen || ui.isTargetPickerOpen || ui.isDependencyPickerOpen) {
            if (mouseY < b.y() + 16) return true;
        } else {
            if (mouseY < b.y() + 5) return true;
        }

        if (ui.isDependencyPickerOpen) {
            List<Quest> filtered = ui.getFilteredDependencies(screen.allQuests, screen.questToModify);
            int itemRowHeight = 16;

            for (int i = 0; i < filtered.size(); i++) {
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
            int idx = (int) ((mouseY - b.y() - 5) / 16);
            if (idx >= 0 && idx < QuestShape.values().length) {
                screen.questToModify.setShape(QuestShape.values()[idx]);
                ui.isShapePickerOpen = false;
                QuestScreen.playClickSound();
            }
            return true;
        }

        if (ui.isTypePickerOpen) {
            int idx = (int) Math.floor((mouseY - b.y() - 5) / 16.0);
            if (ui.isRewardModeOpen) {
                QuestReward.RewardType[] types = QuestReward.RewardType.values();
                if (idx >= 0 && idx < types.length) {
                    QuestReward activeTarget = (ui.selectedRewardChoiceIndex == -1)
                            ? screen.rewardToModify
                            : screen.rewardToModify.getSubRewards().get(ui.selectedRewardChoiceIndex);

                    QuestReward.RewardType newType = types[idx];
                    activeTarget.setType(newType);

                    if (newType == QuestReward.RewardType.XP) activeTarget.setItem(Items.AIR);
                    if (newType != QuestReward.RewardType.COMMAND) activeTarget.setCommand("");

                    ui.isTypePickerOpen = false;
                    QuestScreen.playClickSound();
                }
            } else if (ui.isTaskMode) {
                QuestTask.TaskType[] types = QuestTask.TaskType.values();
                if (idx >= 0 && idx < types.length) {
                    QuestTask.TaskType newType = types[idx];
                    if (screen.taskToModify.getType() != newType) {
                        screen.taskToModify.setType(newType);
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
                        screen.updateTaskNameAndId(screen.taskToModify.getTargetDisplayName());
                    }
                    ui.isTypePickerOpen = false;
                    QuestScreen.playClickSound();
                }
            } else if (screen.isRewardEditorOpen) {
                QuestReward.RewardType[] types = QuestReward.RewardType.values();
                if (idx >= 0 && idx < types.length) {
                    QuestReward.RewardType newType = types[idx];

                    QuestReward activeTarget = (ui.selectedRewardChoiceIndex == -1)
                            ? screen.rewardToModify
                            : screen.rewardToModify.getSubRewards().get(ui.selectedRewardChoiceIndex);

                    activeTarget.setType(newType);

                    if (newType == QuestReward.RewardType.XP) activeTarget.setItem(Items.AIR);
                    if (newType != QuestReward.RewardType.COMMAND) activeTarget.setCommand("");

                    ui.isTypePickerOpen = false;
                    QuestScreen.playClickSound();
                }
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
            if (idx >= 0 && idx < targets.size()) {
                screen.updateTaskTargetAndName(screen.taskToModify.getType(), targets.get(idx));
                ui.isTargetPickerOpen = false;
                QuestScreen.playClickSound();
            }
            return true;
        }

        if (ui.isIconPickerOpen) {
            int col = (int) (mouseX - b.x() - 5) / 18;
            int row = (int) Math.floor(((mouseY - (b.y() + 16) - 5) + ui.scrollOffset) / 18);

            if (col < 0 || col >= 5) return true;

            List<Item> icons = ui.getCachedIcons();
            boolean showCheck = ui.searchQuery.isEmpty() || "checkmark".contains(ui.searchQuery.toLowerCase());
            int idx = col + (row * 5);

            if (showCheck && idx == 0) {
                if (screen.isEditingChapterIcon) {
                    screen.sidebarTargetChapter.setIconStack(new ItemStack(Items.PAPER));
                    screen.saveChapterData(screen.sidebarTargetChapter.getId());
                    screen.saveGroupManifest();
                } else if (screen.questToModify != null) {
                    screen.questToModify.setUseTaskIcon(true);
                    screen.questToModify.setLogo(Items.PAPER);
                }
                ui.closePicker();
                screen.isEditingChapterIcon = false;
            } else {
                int itemIdx = showCheck ? idx - 1 : idx;
                if (itemIdx >= 0 && itemIdx < icons.size()) {
                    Item s = icons.get(itemIdx);
                    if (screen.isEditingChapterIcon) {
                        screen.sidebarTargetChapter.setIconStack(new ItemStack(s));
                        screen.saveChapterData(screen.sidebarTargetChapter.getId());
                        screen.saveGroupManifest();
                    } else if (screen.rewardToModify != null) {
                        QuestReward activeTarget = (ui.selectedRewardChoiceIndex == -1)
                                ? screen.rewardToModify
                                : screen.rewardToModify.getSubRewards().get(ui.selectedRewardChoiceIndex);
                        activeTarget.setItem(s);
                    } else if (screen.questToModify != null) {
                        screen.questToModify.setLogo(s);
                        screen.questToModify.setUseTaskIcon(false);
                    }
                    ui.closePicker();
                    screen.isEditingChapterIcon = false;
                }
            }
            QuestScreen.playClickSound();
        }
        return true;
    }
}