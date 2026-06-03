package com.jmane2026.simplyquests.client.screen.input;

import com.jmane2026.simplyquests.client.screen.QuestEditorUI;
import com.jmane2026.simplyquests.client.screen.QuestScreen;
import com.jmane2026.simplyquests.network.QuestLockPayload;
import com.jmane2026.simplyquests.quest.Quest;
import com.jmane2026.simplyquests.quest.QuestReward;
import com.jmane2026.simplyquests.quest.QuestTask;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public class EditorHandler {

    public static boolean handle(QuestScreen screen, double mouseX, double mouseY, int button) {
        if (!screen.isTaskEditorOpen && !screen.isRewardEditorOpen && !screen.isEditorOpen && !screen.isTextEditorOpen)
            return false;
        if (button != GLFW.GLFW_MOUSE_BUTTON_1) return true;

        if (screen.isTaskEditorOpen) return handleTaskEditor(screen, (int) mouseX, (int) mouseY, button);
        if (screen.isRewardEditorOpen) return handleRewardEditor(screen, (int) mouseX, (int) mouseY, button);
        if (screen.isEditorOpen) return handleQuestEditor(screen, (int) mouseX, (int) mouseY, button);
        if (screen.isTextEditorOpen) return handleTextEditor(screen, (int) mouseX, (int) mouseY, button);

        return true;
    }

    private static boolean isClickInsideActiveArea(int mouseX, int mouseY, int px, int py, int winW, int winH, QuestEditorUI ui) {
        if (mouseX >= px && mouseX <= px + winW && mouseY >= py && mouseY <= py + winH) return true;
        return ui.isPickerOpen() && mouseX >= px + 305 && mouseX <= px + 305 + 100 && mouseY >= py && mouseY <= py + winH;
    }

    private static boolean handleTaskEditor(QuestScreen screen, int mouseX, int mouseY, int button) {
        int winW = 300, winH = 250;
        int px = (screen.width - winW) / 2, py = (screen.height - winH) / 2;
        QuestEditorUI ui = screen.editorUI;
        ui.isTaskMode = true;
        ui.isRewardModeOpen = false;
        QuestTask task = screen.taskToModify;

        if (ui.isQuantityOpen || ui.isNameOpen || ui.isXOpen || ui.isYOpen || ui.isZOpen) {
            String openField = ui.isNameOpen ? "Name" : (ui.isQuantityOpen ? "Quantity" : (ui.isXOpen ? "X" : (ui.isYOpen ? "Y" : "Z")));
            int rowY = ui.getRowY(openField, py);

            if (QuestEditorUI.isMouseOver(mouseX, mouseY, px + 235, rowY, 55, 18)) {
                screen.keyPressed(new KeyEvent(GLFW.GLFW_KEY_ENTER, 0, GLFW.GLFW_PRESS));
                return true;
            }

            if (QuestEditorUI.isMouseOver(mouseX, mouseY, px + 100, rowY - 2, 135, 14)) {
                ui.selectionStart = -1;
                ui.selectionEnd = -1;
                return true;
            }
        }

        if (ui.isClickingSave(mouseX, mouseY, px, py, winH)) {
            if (screen.originalTask == null) {
                screen.selectedQuest.getTasks().add(task);
            } else {
                int index = screen.selectedQuest.getTasks().indexOf(screen.originalTask);
                if (index != -1) screen.selectedQuest.getTasks().set(index, task);
                else screen.selectedQuest.getTasks().add(task);
            }

            if (screen.tempUseAsIcon) {
                for (QuestTask t : screen.selectedQuest.getTasks()) {
                    t.setIcon(false);
                }
                task.setIcon(true);
                screen.selectedQuest.setUseTaskIcon(true);
                screen.selectedQuest.setLogo(task.getIconStack().getItem());
            } else {
                task.setIcon(false);
                boolean anyIcons = screen.selectedQuest.getTasks().stream().anyMatch(QuestTask::isIcon);
                if (!anyIcons) screen.selectedQuest.setUseTaskIcon(false);
            }

            screen.saveChapterData(screen.selectedQuest.getChapterName());
            screen.isTaskEditorOpen = false;
            ui.isTaskMode = false;
            QuestScreen.playClickSound();
            screen.originalQuest = null;
            return true;
        }

        if (ui.isClickingCancel(mouseX, mouseY, px, py, winH) || !isClickInsideActiveArea(mouseX, mouseY, px, py, winW, winH, ui)) {
            if (ui.isNameOpen || ui.isQuantityOpen) {
                ui.closePicker();
            } else {
                if (screen.originalQuest != null) {
                    screen.originalQuest.setLockedBy("");
                    ClientPacketDistributor.sendToServer(new QuestLockPayload(screen.originalQuest.getId(), false));
                }
                screen.isTaskEditorOpen = false;
                ui.isTaskMode = false;
                screen.checkPendingRefresh();
                QuestScreen.playClickSound();
            }
            return true;
        }

        int cbX = px + 15;
        int cbY = py + winH - 18;

        if (QuestEditorUI.isMouseOver(mouseX, mouseY, cbX, cbY, 120, 10)) {
            screen.tempUseAsIcon = !screen.tempUseAsIcon;
            QuestScreen.playClickSound();
            return true;
        }

        String field = ui.getFieldAt(mouseY, py);
        if (field != null && QuestEditorUI.isMouseOver(mouseX, mouseY, px + 235, ui.getRowY(field, py), 65, 18)) {
            ui.closePicker();
            switch (field) {
                case "Optional" -> task.setOptional(!task.isOptional());
                case "Repeatable" -> task.setRepeatable(!task.isRepeatable());
                case "Consume" -> task.setConsume(!task.isConsume());
                case "Type" -> ui.isTypePickerOpen = true;
                case "Target" -> {
                    ui.searchQuery = "";
                    ui.isTargetPickerOpen = true;
                }
                case "Quantity" -> {
                    ui.searchQuery = String.valueOf(task.getRequiredAmount());
                    ui.isQuantityOpen = true;
                    setupText(ui);
                }
                case "Name" -> {
                    ui.searchQuery = task.getName();
                    ui.isNameOpen = true;
                    setupText(ui);
                }
                case "X", "Y", "Z" -> {
                    ui.searchQuery = String.valueOf(field.equals("X") ? task.getTargetX() : (field.equals("Y") ? task.getTargetY() : task.getTargetZ()));
                    if (field.equals("X")) ui.isXOpen = true;
                    else if (field.equals("Y")) ui.isYOpen = true;
                    else ui.isZOpen = true;
                    setupText(ui);
                }
            }
            QuestScreen.playClickSound();
            return true;
        }
        return true;
    }

    private static boolean handleQuestEditor(QuestScreen screen, int mouseX, int mouseY, int button) {
        int winW = 300, winH = 250;
        int px = (screen.width - winW) / 2, py = (screen.height - winH) / 2;
        QuestEditorUI ui = screen.editorUI;
        Quest quest = screen.questToModify;

        if (ui.isSubTitleOpen || ui.isDescriptionOpen) {
            int bx = (screen.width - 240) / 2, by = (screen.height - 140) / 2;
            if (QuestEditorUI.isMouseOver(mouseX, mouseY, bx + 240 - 55, by + 120, 50, 14)) {
                if (ui.isSubTitleOpen) quest.setSubTitle(ui.searchQuery);
                else quest.setDescription(ui.searchQuery);
                ui.closePicker();
            } else if (QuestEditorUI.isMouseOver(mouseX, mouseY, bx + 240 - 110, by + 120, 50, 14) || (mouseX < bx || mouseX > bx + 240 || mouseY < by || mouseY > by + 140)) {
                ui.closePicker();
            } else if (QuestEditorUI.isMouseOver(mouseX, mouseY, bx + 5, by + 20, 230, 95)) {
                ui.selectionStart = -1;
                ui.selectionEnd = -1;
                return true;
            }
            return true;
        }

        if (ui.isTitleOpen) {
            int rowY = ui.getRowY("Title", py);
            if (QuestEditorUI.isMouseOver(mouseX, mouseY, px + 240, rowY, 50, 18)) {
                screen.keyPressed(new KeyEvent(GLFW.GLFW_KEY_ENTER, 0, GLFW.GLFW_PRESS));
                return true;
            }
            if (QuestEditorUI.isMouseOver(mouseX, mouseY, px + 100, rowY - 2, 140, 14)) {
                ui.selectionStart = -1;
                ui.selectionEnd = -1;
                return true;
            }
        }

        int sizeRowY = ui.getRowY("Size", py);
        if (mouseX >= px + 100 && mouseX <= px + 240 && mouseY >= sizeRowY + 2 && mouseY <= sizeRowY + 14) {
            screen.isDraggingSizeSlider = true;
            return true;
        }

        if (ui.isClickingSave(mouseX, mouseY, px, py, winH)) {
            screen.saveChanges();
            QuestScreen.playClickSound();
            return true;
        }
        if (ui.isClickingCancel(mouseX, mouseY, px, py, winH) || !isClickInsideActiveArea(mouseX, mouseY, px, py, winW, winH, ui)) {
            if (ui.isTitleOpen || ui.isSubTitleOpen || ui.isDescriptionOpen || ui.isIconPickerOpen || ui.isShapePickerOpen || ui.isDependencyPickerOpen) {
                ui.closePicker();
            } else {
                if (screen.originalQuest != null) {
                    screen.originalQuest.setLockedBy("");
                    ClientPacketDistributor.sendToServer(new QuestLockPayload(screen.originalQuest.getId(), false));
                }
                screen.isEditorOpen = false;
                QuestScreen.playClickSound();
            }
            return true;
        }

        int cbX = px + 15;
        int cbY = py + winH - 18;
        if (QuestEditorUI.isMouseOver(mouseX, mouseY, cbX, cbY, 120, 10)) {
            screen.tempUseAsIcon = !screen.tempUseAsIcon;
            QuestScreen.playClickSound();
            return true;
        }

        String field = ui.getFieldAt(mouseY, py);
        if (field != null) {
            int rowY = ui.getRowY(field, py);
            int editBtnLeft = px + 300 - 65;
            if (QuestEditorUI.isMouseOver(mouseX, mouseY, editBtnLeft, rowY, 55, 18)) {
                ui.closePicker();
                switch (field) {
                    case "Icon" -> {
                        ui.scrollOffset = 0;
                        ui.searchQuery = "";
                        ui.isIconPickerOpen = true;
                    }
                    case "Shape" -> ui.isShapePickerOpen = true;
                    case "Title" -> {
                        ui.searchQuery = quest.getTitle();
                        ui.isTitleOpen = true;
                        setupText(ui);
                    }
                    case "Sub-Title" -> {
                        ui.searchQuery = quest.getSubTitle();
                        ui.isSubTitleOpen = true;
                        setupText(ui);
                    }
                    case "Description" -> {
                        ui.searchQuery = quest.getDescription();
                        ui.isDescriptionOpen = true;
                        setupText(ui);
                    }
                    case "Optional" -> quest.setOptional(!quest.isOptional());
                    case "Repeatable" -> quest.setRepeatable(!quest.isRepeatable());
                    case "Dependencies" -> {
                        ui.isDependencyPickerOpen = true;
                        ui.isRemoveDependencyMode = false;
                    }
                }
                QuestScreen.playClickSound();
                return true;
            } else if (field != null && field.equals("Dependencies")) {
                if (QuestEditorUI.isMouseOver(mouseX, mouseY, editBtnLeft, rowY, 55, 18)) {
                    ui.closePicker();
                    ui.isDependencyPickerOpen = true;
                    ui.isRemoveDependencyMode = false;
                    QuestScreen.playClickSound();
                } else if (QuestEditorUI.isMouseOver(mouseX, mouseY, editBtnLeft - 50, rowY, 55, 18)) {
                    ui.closePicker();
                    ui.isDependencyPickerOpen = true;
                    ui.isRemoveDependencyMode = true;
                    QuestScreen.playClickSound();
                }
                return true;
            }
        }
        return true;
    }

    private static boolean handleRewardEditor(QuestScreen screen, int mouseX, int mouseY, int button) {
        int winW = 300, winH = 200;
        int px = (screen.width - winW) / 2, py = (screen.height - winH) / 2;
        QuestEditorUI ui = screen.editorUI;
        ui.isRewardModeOpen = true;
        QuestReward reward = screen.rewardToModify;
        ui.isTaskMode = false;

        int barX = px + 95;
        int barY = py + 3;
        int maxSlots = 7;

        List<QuestReward> allOptions = new ArrayList<>();
        allOptions.add(reward);
        allOptions.addAll(reward.getSubRewards());
        int totalEntries = allOptions.size() + 1;

        if (mouseY >= barY && mouseY <= barY + 18) {
            if (mouseX >= barX - 12 && mouseX <= barX && ui.rewardChoicePage > 0) {
                ui.rewardChoicePage--;
                QuestScreen.playClickSound();
                return true;
            }

            int rightArrowX = barX + (maxSlots * 22);
            if (mouseX >= rightArrowX && mouseX <= rightArrowX + 12 && (ui.rewardChoicePage + 1) * maxSlots < totalEntries) {
                ui.rewardChoicePage++;
                QuestScreen.playClickSound();
                return true;
            }
        }

        List<QuestReward> choices = reward.getSubRewards();

        if (mouseY >= barY && mouseY <= barY + 18 && mouseX >= barX) {
            int slotIdx = (int) ((mouseX - barX) / 22);
            int globalIdx = (ui.rewardChoicePage * maxSlots) + slotIdx;

            if (slotIdx >= 0 && slotIdx < maxSlots) {
                if (globalIdx == 0) {
                    ui.selectedRewardChoiceIndex = -1;
                    QuestScreen.playClickSound();
                    return true;
                } else if (globalIdx <= choices.size()) {
                    int choiceIdx = globalIdx - 1;
                    if (button == 1) {
                        if (!(choices instanceof ArrayList)) {
                            List<QuestReward> mutable = new ArrayList<>(choices);
                            mutable.remove(choiceIdx);
                            reward.setSubRewards(mutable);
                        } else {
                            choices.remove(choiceIdx);
                        }
                        ui.selectedRewardChoiceIndex = -1;
                    } else {
                        ui.selectedRewardChoiceIndex = choiceIdx;
                    }
                    QuestScreen.playClickSound();
                    return true;
                } else if (globalIdx == choices.size() + 1) {
                    if ((reward.getType() == QuestReward.RewardType.ITEM && reward.getItem() == Items.AIR) ||
                            (reward.getType() == QuestReward.RewardType.COMMAND && reward.getCommand().trim().isEmpty())) {
                        ui.selectedRewardChoiceIndex = -1;
                    } else {
                        int existingBlankIdx = -1;
                        for (int j = 0; j < choices.size(); j++) {
                            QuestReward r = choices.get(j);
                            if ((r.getType() == QuestReward.RewardType.ITEM && r.getItem() == Items.AIR) ||
                                    (r.getType() == QuestReward.RewardType.COMMAND && r.getCommand().trim().isEmpty())) {
                                existingBlankIdx = j;
                                break;
                            }
                        }

                        if (existingBlankIdx != -1) {
                            ui.selectedRewardChoiceIndex = existingBlankIdx;
                        } else {

                            String uniqueId = reward.getId() + "_c" + Long.toHexString(System.currentTimeMillis()).substring(8);
                            QuestReward newChoice = new QuestReward(uniqueId, QuestReward.RewardType.ITEM, Items.AIR, 1, "", new java.util.ArrayList<>());
                            choices.add(newChoice);
                            ui.selectedRewardChoiceIndex = choices.size() - 1;
                        }
                    }

                    ui.rewardChoicePage = (ui.selectedRewardChoiceIndex + 1) / maxSlots;
                    QuestScreen.playClickSound();
                    return true;
                }
            }

            return true;
        }

        QuestReward activeTarget = (ui.selectedRewardChoiceIndex == -1) ? reward : choices.get(ui.selectedRewardChoiceIndex);

        if (ui.isQuantityOpen || ui.isNameOpen) {
            ui.isRewardModeOpen = true;
            String openField = ui.isNameOpen ? "Command" : "Quantity";
            int rowY = ui.getRowY(openField, py);

            if (QuestEditorUI.isMouseOver(mouseX, mouseY, px + 235, rowY, 55, 18)) {

                if (ui.isQuantityOpen) {
                    try {
                        activeTarget.setCount(Integer.parseInt(ui.searchQuery.trim()));
                    } catch (Exception ignored) {
                    }
                } else if (ui.isNameOpen) {
                    activeTarget.setCommand(ui.searchQuery);
                }
                ui.closePicker();
                return true;
            }

            if (QuestEditorUI.isMouseOver(mouseX, mouseY, px + 100, rowY - 2, 135, 14)) {
                ui.selectionStart = -1;
                ui.selectionEnd = -1;
                return true;
            }
        }

        if (ui.isClickingSave(mouseX, mouseY, px, py, winH)) {

            screen.rewardToModify.getSubRewards().removeIf(r -> (r.getType() == QuestReward.RewardType.ITEM && r.getItem() == Items.AIR) ||
                    (r.getType() == QuestReward.RewardType.COMMAND && r.getCommand().trim().isEmpty()));

            if (screen.originalReward == null) {
                screen.selectedQuest.getRewards().add(screen.rewardToModify);
            } else {
                int index = screen.selectedQuest.getRewards().indexOf(screen.originalReward);

                if (index != -1) screen.selectedQuest.getRewards().set(index, screen.rewardToModify);
                else screen.selectedQuest.getRewards().add(screen.rewardToModify);
            }
            screen.saveChapterData(screen.selectedQuest.getChapterName());
            screen.isRewardEditorOpen = false;
            ui.isRewardModeOpen = false;
            screen.originalQuest = null;
            QuestScreen.playClickSound();
            return true;
        }

        int btnY = py + winH - 20;
        if (ui.selectedRewardChoiceIndex != -1 && QuestEditorUI.isMouseOver(mouseX, mouseY, px + 15, btnY, 45, 14)) {
            if (!(choices instanceof ArrayList)) {
                reward.setSubRewards(new ArrayList<>(choices));
            }
            reward.getSubRewards().remove(ui.selectedRewardChoiceIndex);
            ui.selectedRewardChoiceIndex = -1;
            QuestScreen.playClickSound();
            return true;
        }

        if (ui.isClickingCancel(mouseX, mouseY, px, py, winH) || !isClickInsideActiveArea(mouseX, mouseY, px, py, winW, winH, ui)) {
            if (ui.isNameOpen || ui.isQuantityOpen || ui.isTypePickerOpen || ui.isIconPickerOpen) {
                ui.closePicker();
            } else {
                if (screen.originalQuest != null) {
                    screen.originalQuest.setLockedBy("");
                    ClientPacketDistributor.sendToServer(new QuestLockPayload(screen.originalQuest.getId(), false));
                }
                screen.isRewardEditorOpen = false;
                ui.isRewardModeOpen = false;
                QuestScreen.playClickSound();
            }
            return true;
        }

        String field = ui.getFieldAt(mouseY, py);
        if (field != null && QuestEditorUI.isMouseOver(mouseX, mouseY, px + 235, ui.getRowY(field, py), 65, 18)) {
            ui.closePicker();
            switch (field) {
                case "Type" -> ui.isTypePickerOpen = true;
                case "Target" -> {
                    ui.searchQuery = "";
                    ui.isIconPickerOpen = true;
                }
                case "Quantity" -> {
                    ui.searchQuery = String.valueOf(activeTarget.getCount());
                    ui.isQuantityOpen = true;
                    setupText(ui);
                }
                case "Command" -> {
                    ui.searchQuery = activeTarget.getCommand();
                    ui.isNameOpen = true;
                    setupText(ui);
                }
            }
            QuestScreen.playClickSound();
            return true;
        }
        return true;
    }

    private static boolean handleTextEditor(QuestScreen screen, int mouseX, int mouseY, int button) {
        int winW = 200, winH = 145;
        int px = (screen.width - winW) / 2, py = (screen.height - winH) / 2;
        QuestEditorUI ui = screen.editorUI;

        if (ui.isColorPickerOpen) {
            QuestEditorUI.PickerBounds pB = new QuestEditorUI.PickerBounds(screen.pickerX, screen.pickerY, 135, 168, 16);
            if (QuestEditorUI.isMouseOver(mouseX, mouseY, pB.x(), pB.y(), pB.w(), pB.h())) {
                screen.handleCanvasTextEditorClicks(mouseX, mouseY);
            } else {
                ui.isColorPickerOpen = false;
                QuestScreen.playClickSound();
            }
            return true;
        }

        if (mouseX < px || mouseX > px + winW || mouseY < py || mouseY > py + winH) {
            screen.isTextEditorOpen = false;
            QuestScreen.playClickSound();
            return true;
        }
        screen.handleCanvasTextEditorClicks(mouseX, mouseY);
        return true;
    }

    private static void setupText(QuestEditorUI ui) {
        ui.cursorIndex = ui.searchQuery.length();
        ui.selectionStart = 0;
        ui.selectionEnd = ui.searchQuery.length();
    }
}