package com.jmane2026.simplyquests.client.screen.input;

import com.jmane2026.simplyquests.client.screen.QuestEditorUI;
import com.jmane2026.simplyquests.client.screen.QuestScreen;
import com.jmane2026.simplyquests.quest.Quest;
import com.jmane2026.simplyquests.quest.QuestReward;
import com.jmane2026.simplyquests.quest.QuestTask;
import net.minecraft.client.input.KeyEvent;
import org.lwjgl.glfw.GLFW;

public class EditorHandler {

    public static boolean handle(QuestScreen screen, double mouseX, double mouseY, int button) {
        if (!screen.isTaskEditorOpen && !screen.isRewardEditorOpen && !screen.isEditorOpen && !screen.isTextEditorOpen) return false;
        if (button != GLFW.GLFW_MOUSE_BUTTON_1) return true;

        if (screen.isTaskEditorOpen) return handleTaskEditor(screen, (int) mouseX, (int) mouseY, button);
        if (screen.isRewardEditorOpen) return handleRewardEditor(screen, (int) mouseX, (int) mouseY, button);
        if (screen.isEditorOpen) return handleQuestEditor(screen, (int) mouseX, (int) mouseY, button);
        if (screen.isTextEditorOpen) return handleTextEditor(screen, (int) mouseX, (int) mouseY, button);

        return true;
    }

    private static boolean isClickInsideActiveArea(int mouseX, int mouseY, int px, int py, int winW, int winH, QuestEditorUI ui) {
        // The main editor window
        if (mouseX >= px && mouseX <= px + winW && mouseY >= py && mouseY <= py + winH) return true;
        // The picker window (px + 305, same height)
        return ui.isPickerOpen() && mouseX >= px + 305 && mouseX <= px + 305 + 100 && mouseY >= py && mouseY <= py + winH;
    }

    private static boolean handleTaskEditor(QuestScreen screen, int mouseX, int mouseY, int button) {
        int winW = 300, winH = 250;
        int px = (screen.width - winW) / 2, py = (screen.height - winH) / 2;
        QuestEditorUI ui = screen.editorUI;
        ui.isTaskMode = true; ui.isRewardModeOpen = false;
        QuestTask task = screen.taskToModify;

        // 1. Sub-Editor Submission check
        if (ui.isQuantityOpen || ui.isNameOpen || ui.isXOpen || ui.isYOpen || ui.isZOpen) {
            String openField = ui.isNameOpen ? "Name" : (ui.isQuantityOpen ? "Quantity" : (ui.isXOpen ? "X" : (ui.isYOpen ? "Y" : "Z")));
            int rowY = ui.getRowY(openField, py);
            
            if (QuestEditorUI.isMouseOver(mouseX, mouseY, px + 235, rowY, 55, 18)) {
                screen.keyPressed(new KeyEvent(GLFW.GLFW_KEY_ENTER, 0, GLFW.GLFW_PRESS));
                return true;
            }

            // Click in the actual text area to cancel highlight
            if (QuestEditorUI.isMouseOver(mouseX, mouseY, px + 100, rowY - 2, 135, 14)) {
                ui.selectionStart = -1;
                ui.selectionEnd = -1;
                return true;
            }
        }

        // 2. Save / Cancel / Outside
        if (ui.isClickingSave(mouseX, mouseY, px, py, winH)) {
            if (screen.originalTask == null) screen.selectedQuest.getTasks().add(task);
            else screen.selectedQuest.getTasks().set(screen.selectedQuest.getTasks().indexOf(screen.originalTask), task);

            // Apply temporary icon state on Save
            if (screen.tempUseAsIcon) {
                screen.selectedQuest.setUseTaskIcon(true);
                screen.selectedQuest.setLogo(task.getIconStack().getItem());
            } else if (screen.selectedQuest.isUseTaskIcon() && screen.selectedQuest.getLogo() == task.getIconStack().getItem()) {
                screen.selectedQuest.setUseTaskIcon(false);
            }

            screen.saveChapterData(screen.selectedQuest.getChapterName());
            screen.isTaskEditorOpen = false; ui.isTaskMode = false; QuestScreen.playClickSound();
            return true;
        }

        if (ui.isClickingCancel(mouseX, mouseY, px, py, winH) || !isClickInsideActiveArea(mouseX, mouseY, px, py, winW, winH, ui)) {
            if (ui.isNameOpen || ui.isQuantityOpen) {
                ui.closePicker();
            } else {
                screen.isTaskEditorOpen = false; // FIX: Corrected target editor
                ui.isTaskMode = false;
                screen.checkPendingRefresh();
                QuestScreen.playClickSound();
            }
            return true;
        }

        // 3. Icon Toggle Checkbox
        if (QuestEditorUI.isMouseOver(mouseX, mouseY, px + 15, py + winH - 18, 120, 10)) {
            screen.tempUseAsIcon = !screen.tempUseAsIcon;
            QuestScreen.playClickSound();
            return true;
        }

        String field = ui.getFieldAt(mouseY, py);
        // FIX: Increased button width to 65 to match renderQuestEditor's editBtnRightEdge logic
        if (field != null && QuestEditorUI.isMouseOver(mouseX, mouseY, px + 235, ui.getRowY(field, py), 65, 18)) {
            ui.closePicker();
            switch (field) {
                case "Optional" -> task.setOptional(!task.isOptional());
                case "Repeatable" -> task.setRepeatable(!task.isRepeatable());
                case "Consume" -> task.setConsume(!task.isConsume());
                case "Type" -> ui.isTypePickerOpen = true;
                case "Target" -> { ui.searchQuery = ""; ui.isTargetPickerOpen = true; }
                case "Quantity" -> { ui.searchQuery = String.valueOf(task.getRequiredAmount()); ui.isQuantityOpen = true; setupText(ui); }
                case "Name" -> { ui.searchQuery = task.getName(); ui.isNameOpen = true; setupText(ui); }
                case "X", "Y", "Z" -> {
                    ui.searchQuery = String.valueOf(field.equals("X") ? task.getTargetX() : (field.equals("Y") ? task.getTargetY() : task.getTargetZ()));
                    if (field.equals("X")) ui.isXOpen = true; else if (field.equals("Y")) ui.isYOpen = true; else ui.isZOpen = true;
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
                if (ui.isSubTitleOpen) quest.setSubTitle(ui.searchQuery); else quest.setDescription(ui.searchQuery);
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

        // Title Interaction
        if (ui.isTitleOpen) {
            int rowY = ui.getRowY("Title", py);
            // Submit Button
            if (QuestEditorUI.isMouseOver(mouseX, mouseY, px + 240, rowY, 50, 18)) {
                screen.keyPressed(new KeyEvent(GLFW.GLFW_KEY_ENTER, 0, GLFW.GLFW_PRESS));
                return true;
            }
            // Click in the actual text area to cancel highlight
            if (QuestEditorUI.isMouseOver(mouseX, mouseY, px + 100, rowY - 2, 140, 14)) {
                ui.selectionStart = -1; ui.selectionEnd = -1;
                return true;
            }
        }

        if (mouseX >= px + 100 && mouseX <= px + 240 && mouseY >= py + 130 && mouseY <= py + 144) {
            screen.isDraggingSizeSlider = true; return true;
        }

        if (ui.isClickingSave(mouseX, mouseY, px, py, winH)) { screen.saveChanges(); QuestScreen.playClickSound(); return true; }
        if (ui.isClickingCancel(mouseX, mouseY, px, py, winH) || !isClickInsideActiveArea(mouseX, mouseY, px, py, winW, winH, ui)) {
            if (ui.isTitleOpen || ui.isSubTitleOpen || ui.isDescriptionOpen || ui.isIconPickerOpen || ui.isShapePickerOpen || ui.isDependencyPickerOpen) {
                ui.closePicker(); // This method already calls playClickSound()
            } else {
                screen.isEditorOpen = false;
                QuestScreen.playClickSound();
            }
            return true;
        }

        String field = ui.getFieldAt(mouseY, py);
        if (field != null) {
            int rowY = ui.getRowY(field, py);
            int editBtnLeft = px + 300 - 65;
            if (QuestEditorUI.isMouseOver(mouseX, mouseY, editBtnLeft, rowY, 55, 18)) {
                ui.closePicker();
                switch(field) {
                    case "Icon" -> { ui.scrollOffset = 0; ui.searchQuery = ""; ui.isIconPickerOpen = true; }
                    case "Shape" -> ui.isShapePickerOpen = true;
                    case "Title" -> { ui.searchQuery = quest.getTitle(); ui.isTitleOpen = true; setupText(ui); }
                    case "Sub-Title" -> { ui.searchQuery = quest.getSubTitle(); ui.isSubTitleOpen = true; setupText(ui); }
                    case "Description" -> { ui.searchQuery = quest.getDescription(); ui.isDescriptionOpen = true; setupText(ui); }
                    case "Optional" -> quest.setOptional(!quest.isOptional());
                    case "Repeatable" -> quest.setRepeatable(!quest.isRepeatable());
                    case "Dependencies" -> { ui.isDependencyPickerOpen = true; ui.isRemoveDependencyMode = false; }
                }
                QuestScreen.playClickSound();
                return true;
            } else if (field != null && field.equals("Dependencies")) {
                if (QuestEditorUI.isMouseOver(mouseX, mouseY, editBtnLeft, rowY, 55, 18)) {
                    ui.closePicker(); ui.isDependencyPickerOpen = true; ui.isRemoveDependencyMode = false; QuestScreen.playClickSound();
                } else if (QuestEditorUI.isMouseOver(mouseX, mouseY, editBtnLeft - 50, rowY, 55, 18)) {
                    ui.closePicker(); ui.isDependencyPickerOpen = true; ui.isRemoveDependencyMode = true; QuestScreen.playClickSound();
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
        ui.isRewardModeOpen = true; // Ensure math uses 26px rows
        QuestReward reward = screen.rewardToModify;
        ui.isTaskMode = false;

        // 1. Sub-Editor Submission check (Quantity / Command)
        if (ui.isQuantityOpen || ui.isNameOpen) {
            ui.isRewardModeOpen = true; // Ensure row math uses reward-specific row heights
            String openField = ui.isNameOpen ? "Command" : "Quantity";
            int rowY = ui.getRowY(openField, py);

            if (QuestEditorUI.isMouseOver(mouseX, mouseY, px + 235, rowY, 55, 18)) {
                screen.keyPressed(new KeyEvent(GLFW.GLFW_KEY_ENTER, 0, GLFW.GLFW_PRESS));
                return true;
            }

            // Click in text area to cancel highlight
            if (QuestEditorUI.isMouseOver(mouseX, mouseY, px + 100, rowY - 2, 135, 14)) {
                ui.selectionStart = -1;
                ui.selectionEnd = -1;
                return true;
            }
        }

        // 2. Modal Buttons (Save / Cancel / Outside)
        if (ui.isClickingSave(mouseX, mouseY, px, py, winH)) {
            if (screen.originalReward == null) screen.selectedQuest.getRewards().add(screen.rewardToModify);
            else screen.selectedQuest.getRewards().set(screen.selectedQuest.getRewards().indexOf(screen.originalReward), screen.rewardToModify);
            screen.saveChapterData(screen.selectedQuest.getChapterName());
            screen.isRewardEditorOpen = false;
            ui.isRewardModeOpen = false;
            QuestScreen.playClickSound();
            return true;
        }
        
        if (ui.isClickingCancel(mouseX, mouseY, px, py, winH) || !isClickInsideActiveArea(mouseX, mouseY, px, py, winW, winH, ui)) {
            if (ui.isNameOpen || ui.isQuantityOpen || ui.isTypePickerOpen || ui.isIconPickerOpen) {
                ui.closePicker();
            } else {
                screen.isRewardEditorOpen = false;
                ui.isRewardModeOpen = false;
                QuestScreen.playClickSound();
            }
            return true;
        }

        // 3. Add Choice Button
        if (QuestEditorUI.isMouseOver(mouseX, mouseY, px + 15, py + winH - 20, 60, 14)) {
            // Create a sub-reward based on current settings
            QuestReward choice = new QuestReward(reward.getId() + "/c" + reward.getSubRewards().size(), 
                reward.getType(), reward.getItem(), reward.getCount(), reward.getCommand(), new java.util.ArrayList<>());
            reward.getSubRewards().add(choice);
            QuestScreen.playClickSound(); return true;
        }

        String field = ui.getFieldAt(mouseY, py);
        if (field != null && QuestEditorUI.isMouseOver(mouseX, mouseY, px + 235, ui.getRowY(field, py), 65, 18)) {
            ui.closePicker();
            switch(field) {
                case "Type" -> ui.isTypePickerOpen = true;
                case "Target" -> { ui.searchQuery = ""; ui.isIconPickerOpen = true; }
                case "Quantity" -> { ui.searchQuery = String.valueOf(screen.rewardToModify.getCount()); ui.isQuantityOpen = true; setupText(ui); }
                case "Command" -> { ui.searchQuery = screen.rewardToModify.getCommand(); ui.isNameOpen = true; setupText(ui); }
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
            screen.isTextEditorOpen = false; QuestScreen.playClickSound(); return true;
        }
        screen.handleCanvasTextEditorClicks(mouseX, mouseY);
        return true;
    }

    private static void setupText(QuestEditorUI ui) {
        ui.cursorIndex = ui.searchQuery.length(); ui.selectionStart = 0; ui.selectionEnd = ui.searchQuery.length();
    }
}