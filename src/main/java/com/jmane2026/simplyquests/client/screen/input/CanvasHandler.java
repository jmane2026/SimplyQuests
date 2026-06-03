package com.jmane2026.simplyquests.client.screen.input;

import com.jmane2026.simplyquests.client.SimplyQuestsClientPacketHandler;
import com.jmane2026.simplyquests.client.screen.QuestScreen;
import com.jmane2026.simplyquests.client.screen.SidebarChapter;
import com.jmane2026.simplyquests.client.screen.SidebarEntry;
import com.jmane2026.simplyquests.client.screen.SidebarGroup;
import com.jmane2026.simplyquests.network.ClaimRewardPayload;
import com.jmane2026.simplyquests.network.QuestLockPayload;
import com.jmane2026.simplyquests.network.ToggleCheckboxPayload;
import com.jmane2026.simplyquests.quest.*;
import com.jmane2026.simplyquests.util.QuestClientData;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.awt.*;

public class CanvasHandler {
    public static boolean handleDetailsWindow(QuestScreen screen, double mouseX, double mouseY, int button) {
        if (screen.selectedQuest == null || screen.isEditorOpen) return false;
        Rectangle b = screen.getDetailsBounds();

        if (button == GLFW.GLFW_MOUSE_BUTTON_1) {
            if (mouseX >= b.x + b.width - 18 && mouseX <= b.x + b.width - 5 && mouseY >= b.y + 3 && mouseY <= b.y + 17) {
                screen.selectedQuest = null;
                QuestScreen.playClickSound();
                return true;
            }
        }

        if (!b.contains(mouseX, mouseY)) {
            if (button == GLFW.GLFW_MOUSE_BUTTON_1) {
                screen.selectedQuest = null;
                QuestScreen.playClickSound();
            }
            return true;
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_1 && mouseY < b.y + 20) {
            screen.isDraggingPopup = true;
            screen.dragOffsetX = mouseX - b.x;
            screen.dragOffsetY = mouseY - b.y;
            return true;
        }

        int tasksAreaX = b.x + 10;
        int rewardsAreaX = b.x + (b.width / 2) + 10;
        int taskCount = screen.selectedQuest.getTasks().size();
        int rewardCount = screen.selectedQuest.getRewards().size();

        if (button == 0 && mouseY >= b.y + 45 && mouseY <= b.y + 65) {
            if (taskCount > 4) {
                if (mouseX >= tasksAreaX && mouseX <= tasksAreaX + 12 && screen.taskPage > 0) {
                    screen.taskPage--;
                    QuestScreen.playClickSound();
                    return true;
                }
                if (mouseX >= tasksAreaX + 108 && mouseX <= tasksAreaX + 120 && (screen.taskPage + 1) * 4 < taskCount) {
                    screen.taskPage++;
                    QuestScreen.playClickSound();
                    return true;
                }
            }
            if (rewardCount > 4) {
                if (mouseX >= rewardsAreaX && mouseX <= rewardsAreaX + 12 && screen.rewardPage > 0) {
                    screen.rewardPage--;
                    QuestScreen.playClickSound();
                    return true;
                }
                if (mouseX >= rewardsAreaX + 108 && mouseX <= rewardsAreaX + 120 && (screen.rewardPage + 1) * 4 < rewardCount) {
                    screen.rewardPage++;
                    QuestScreen.playClickSound();
                    return true;
                }
            }
        }

        if (QuestGlobalState.isEditModeEnabled && button == 0 && mouseY >= b.y + 30 - 2 && mouseY <= b.y + 30 + 12) {
            int tasksCenter = b.x + (b.width / 4);
            int plusX = tasksCenter + (Minecraft.getInstance().font.width("Tasks") / 2) + 8;
            if (mouseX >= plusX - 2 && mouseX <= plusX + 10) {

                screen.originalQuest = screen.selectedQuest;
                if (screen.originalQuest != null) {
                    screen.originalQuest.setLockedBy(Minecraft.getInstance().player.getName().getString());
                    ClientPacketDistributor.sendToServer(new QuestLockPayload(screen.originalQuest.getId(), true));
                }

                screen.taskToModify = new QuestTask(QuestTask.generateTaskId(screen.selectedQuest.getId(), "task", screen.selectedQuest.getTasks()), QuestTask.TaskType.ITEM, "", "New Task", 1, 0, false, false, false, QuestTask.TaskState.INCOMPLETE, 0, 0, 0, false);
                screen.isTaskEditorOpen = true;
                screen.editorUI.isTaskMode = true;
                screen.tempUseAsIcon = false;
                QuestScreen.playClickSound();
                return true;
            }
            int rPlusX = (b.x + (3 * b.width / 4)) + (Minecraft.getInstance().font.width("Rewards") / 2) + 8;
            if (mouseX >= rPlusX - 2 && mouseX <= rPlusX + 10) {

                screen.originalQuest = screen.selectedQuest;
                if (screen.originalQuest != null) {
                    screen.originalQuest.setLockedBy(Minecraft.getInstance().player.getName().getString());
                    ClientPacketDistributor.sendToServer(new QuestLockPayload(screen.originalQuest.getId(), true));
                }

                screen.rewardToModify = new QuestReward(QuestReward.generateRewardId(screen.selectedQuest.getId(), "item", screen.selectedQuest.getRewards()), QuestReward.RewardType.ITEM, net.minecraft.world.item.Items.AIR, 1, "", new java.util.ArrayList<>());
                screen.isRewardEditorOpen = true;
                screen.editorUI.isRewardModeOpen = true;
                QuestScreen.playClickSound();
                return true;
            }
        }

        int startIdx = screen.taskPage * 4;
        for (int i = 0; i < Math.min(4, screen.selectedQuest.getTasks().size() - startIdx); i++) {
            int ix = b.x + 10 + (screen.selectedQuest.getTasks().size() > 4 ? 12 : 0) + (i * 24);
            if (mouseX >= ix && mouseX <= ix + 20 && mouseY >= b.y + 45 && mouseY <= b.y + 65) {
                QuestTask t = screen.selectedQuest.getTasks().get(startIdx + i);
                if (button == 0) {
                    if (t.getType() == QuestTask.TaskType.ITEM && t.isConsume()) {
                        screen.submittingTask = t;
                        screen.isItemSubmissionOpen = true;
                    } else if (t.getType() == QuestTask.TaskType.CHECKBOX) {
                        ClientPacketDistributor.sendToServer(new ToggleCheckboxPayload(screen.selectedQuest.getId(), t.getId()));
                        QuestScreen.playClickSound();
                    }
                } else if (button == 1 && QuestGlobalState.isEditModeEnabled)
                    screen.openTaskContextMenu(mouseX, mouseY, t);
                else if (button == GLFW.GLFW_MOUSE_BUTTON_MIDDLE) screen.movingTask = t;
                return true;
            }
        }

        int rStart = screen.rewardPage * 4;
        int rX = (b.x + (b.width / 2) + 10 + (screen.selectedQuest.getRewards().size() > 4 ? 12 : 0));
        for (int i = 0; i < Math.min(4, screen.selectedQuest.getRewards().size() - rStart); i++) {
            int ix = rX + (i * 24);
            if (mouseX >= ix && mouseX <= ix + 20 && mouseY >= b.y + 45 && mouseY <= b.y + 65) {
                QuestReward r = screen.selectedQuest.getRewards().get(rStart + i);
                boolean isBundle = !r.getSubRewards().isEmpty();
                boolean claimed = SimplyQuestsClientPacketHandler.CLIENT_CLAIMED_REWARDS.contains(r.getId());

                if (button == 0) {
                    boolean questDone = SimplyQuestsClientPacketHandler.CLIENT_COMPLETED_QUESTS.contains(screen.selectedQuest.getId());

                    if (questDone && !claimed) {
                        if (isBundle) {
                            screen.activeChoiceBundle = r;
                            screen.selectedChoice = null;
                            screen.isChoiceModalOpen = true;
                            QuestScreen.playClickSound();
                            return true;
                        } else {
                            ClientPacketDistributor.sendToServer(new ClaimRewardPayload(r.getId()));
                        }
                    }
                } else if (button == 1 && QuestGlobalState.isEditModeEnabled)
                    screen.openRewardContextMenu(mouseX, mouseY, r);
                else if (button == GLFW.GLFW_MOUSE_BUTTON_MIDDLE && QuestGlobalState.isEditModeEnabled)
                    screen.movingReward = r;
                QuestScreen.playClickSound();
                return true;
            }
        }
        return true;
    }

    public static boolean handleCanvas(QuestScreen screen, double mouseX, double mouseY, int button) {
        String activeChapterId = screen.selectedChapter.getId();
        if (button == 0) {
            for (int i = screen.allCanvasImages.size() - 1; i >= 0; i--) {
                QuestCanvasImage ci = screen.allCanvasImages.get(i);
                if (ci.getChapterName().equals(activeChapterId) && screen.isMouseOverImage(mouseX, mouseY, ci)) {
                    screen.selectedCanvasImage = ci;
                    screen.selectedQuest = null;
                    QuestScreen.playClickSound();
                    return true;
                }
            }
            screen.selectedCanvasImage = null;

            for (Quest q : screen.allQuests) {
                if (q.getChapterName().equals(activeChapterId) && screen.isMouseOverNode(mouseX, mouseY, q)) {
                    if (!q.getLockedBy().isEmpty() && !q.getLockedBy().equals(Minecraft.getInstance().player.getName().getString()))
                        return true;
                    screen.selectedQuest = q;
                    QuestScreen.playClickSound();
                    return true;
                }
            }
        } else if (button == GLFW.GLFW_MOUSE_BUTTON_MIDDLE && QuestGlobalState.isEditModeEnabled) {
            for (int i = screen.allCanvasImages.size() - 1; i >= 0; i--) {
                QuestCanvasImage ci = screen.allCanvasImages.get(i);
                if (ci.getChapterName().equals(activeChapterId) && screen.isMouseOverImage(mouseX, mouseY, ci)) {
                    screen.movingCanvasImage = ci;
                    double absoluteCenterX = screen.width / 2.0;
                    double absoluteCenterY = screen.height / 2.0;
                    double worldMouseX = screen.offsetX + ((mouseX - absoluteCenterX) / screen.zoom);
                    double worldMouseY = screen.offsetY + ((mouseY - absoluteCenterY) / screen.zoom);
                    screen.dragOffsetX = worldMouseX - ci.getX();
                    screen.dragOffsetY = worldMouseY - ci.getY();
                    QuestScreen.playClickSound();
                    return true;
                }
            }
            for (Quest q : screen.allQuests) {
                if (q.getChapterName().equals(activeChapterId) && screen.isMouseOverNode(mouseX, mouseY, q)) {
                    if (!q.getLockedBy().isEmpty() && !q.getLockedBy().equals(Minecraft.getInstance().player.getName().getString()))
                        return true;
                    screen.movingQuest = q;
                    QuestScreen.playClickSound();
                    return true;
                }
            }
            for (CanvasText ct : screen.allCanvasTexts) {
                if (ct.getChapterName().equals(activeChapterId) && screen.isMouseOverText(mouseX, mouseY, ct)) {
                    screen.movingCanvasText = ct;

                    double absoluteCenterX = screen.width / 2.0;
                    double absoluteCenterY = screen.height / 2.0;
                    double worldMouseX = screen.offsetX + ((mouseX - absoluteCenterX) / screen.zoom);
                    double worldMouseY = screen.offsetY + ((mouseY - absoluteCenterY) / screen.zoom);
                    screen.dragOffsetX = worldMouseX - ct.getX();
                    screen.dragOffsetY = worldMouseY - ct.getY();

                    QuestScreen.playClickSound();
                    return true;
                }
            }
        } else if (button == 1 && QuestGlobalState.isEditModeEnabled) {
            for (int i = screen.allCanvasImages.size() - 1; i >= 0; i--) {
                QuestCanvasImage ci = screen.allCanvasImages.get(i);
                if (ci.getChapterName().equals(activeChapterId) && screen.isMouseOverImage(mouseX, mouseY, ci)) {
                    screen.openImageContextMenu(mouseX, mouseY, ci);
                    return true;
                }
            }
            for (Quest q : screen.allQuests) {
                if (q.getChapterName().equals(activeChapterId) && screen.isMouseOverNode(mouseX, mouseY, q)) {

                    if (!q.getLockedBy().isEmpty() && !q.getLockedBy().equals(Minecraft.getInstance().player.getName().getString()))
                        return true;
                    screen.openQuestContextMenu(mouseX, mouseY, q);
                    return true;
                }
            }
            for (CanvasText ct : screen.allCanvasTexts)
                if (ct.getChapterName().equals(activeChapterId) && screen.isMouseOverText(mouseX, mouseY, ct)) {
                    screen.openTextContextMenu(mouseX, mouseY, ct);
                    return true;
                }
            screen.openCanvasContextMenu(mouseX, mouseY);
            return true;
        }
        return false;
    }

    public static boolean handleSidebar(QuestScreen screen, double mouseX, double mouseY, int button) {
        if (mouseX > screen.currentSidebarWidth) return false;
        float scale = 0.75f;
        int sw = (int) screen.currentSidebarWidth;
        double lmx = mouseX / scale, lmy = mouseY / scale;
        double translatedLmy = lmy + screen.sidebarScrollOffset;

        int gearX = (int) (sw / scale) - 15;
        if (lmx >= gearX && lmx <= gearX + 12 && lmy >= 3 && lmy <= 13) {
            if (button == 0) {
                screen.isSettingsOpen = true;
                QuestScreen.playClickSound();
            }
            return true;
        }

        int mainPlusX = (int) (sw / scale) - 30;
        if (QuestGlobalState.isEditModeEnabled && lmx >= mainPlusX - 4 && lmx <= mainPlusX + 12 && lmy >= 3 && lmy <= 13) {
            if (button == 0) screen.openSideBarContextMenu(mouseX, mouseY);
            return true;
        }

        int curY = (int) (15 / scale);
        int scaledMaxW = (int) (QuestScreen.MAX_SIDEBAR_WIDTH / scale);

        for (SidebarEntry entry : screen.getSidebarEntries()) {
            if (entry instanceof SidebarGroup group) {
                if (lmy >= 15 && translatedLmy >= curY && translatedLmy < curY + 14) {
                    int gPlusX = scaledMaxW - 12;
                    if (QuestGlobalState.isEditModeEnabled && group.isExpanded() && lmx >= gPlusX - 4 && lmx <= gPlusX + 12) {
                        if (button == 0) {
                            if (screen.isSidebarEditing()) screen.stopSidebarEditing(true);
                            SidebarChapter ch = new SidebarChapter("New Chapter");
                            String uniqueId = "chapter_" + Long.toHexString(System.currentTimeMillis()).substring(8);
                            ch.setId(uniqueId);

                            group.addChapter(ch);
                            screen.editingChapter = ch;
                            screen.editingGroup = null;
                            screen.sidebarSearchQuery = "";
                            screen.sidebarTextScrollOffset = 0;
                            QuestScreen.playClickSound();
                        }
                        return true;
                    }

                    if (button == GLFW.GLFW_MOUSE_BUTTON_MIDDLE && QuestGlobalState.isEditModeEnabled) {
                        screen.movingSidebarGroup = group;
                        QuestScreen.playClickSound();
                        return true;
                    }

                    if (button == 0) {
                        group.toggleExpanded();
                        QuestClientData.setGroupExpanded(group.getName(), group.isExpanded());
                        QuestScreen.playClickSound();
                    } else if (button == 1 && QuestGlobalState.isEditModeEnabled) {
                        screen.openSidebarEntryContextMenu(mouseX, mouseY, group, null);
                    }
                    return true;
                }

                curY += 18;
                if (group.isExpanded()) {
                    for (SidebarChapter ch : group.getChapters()) {
                        if (lmy >= 15 && translatedLmy >= curY && translatedLmy < curY + 16) {
                            if (button == GLFW.GLFW_MOUSE_BUTTON_MIDDLE && QuestGlobalState.isEditModeEnabled) {
                                screen.movingSidebarChapter = ch;
                                QuestScreen.playClickSound();
                            } else if (button == 0) {
                                screen.selectChapter(ch);
                            } else if (button == 1 && QuestGlobalState.isEditModeEnabled) {
                                screen.openSidebarEntryContextMenu(mouseX, mouseY, ch, group);
                            }
                            return true;
                        }
                        curY += 16;
                    }
                }
            } else if (entry instanceof SidebarChapter ch) {
                if (lmy >= 15 && translatedLmy >= curY && translatedLmy < curY + 16) {
                    if (button == GLFW.GLFW_MOUSE_BUTTON_MIDDLE && QuestGlobalState.isEditModeEnabled) {
                        screen.movingSidebarChapter = ch;
                        QuestScreen.playClickSound();
                    } else if (button == 0) {
                        screen.selectChapter(ch);
                    } else if (button == 1 && QuestGlobalState.isEditModeEnabled) {
                        screen.openSidebarEntryContextMenu(mouseX, mouseY, ch, null);
                    }
                    return true;
                }
                curY += 16;
            }
            curY += 6;
        }
        return true;
    }

    public static boolean handleMoveModes(QuestScreen screen, double mouseX, double mouseY, int button) {
        if (screen.movingQuest == null && screen.movingCanvasText == null &&
                screen.movingTask == null && screen.movingReward == null && screen.movingCanvasImage == null &&
                screen.movingSidebarGroup == null && screen.movingSidebarChapter == null) {
            return false;
        }

        if (button == 0) {
            if (screen.movingQuest != null) screen.dropQuest(mouseX, mouseY);
            else if (screen.movingCanvasText != null) screen.dropText(mouseX, mouseY);
            else if (screen.movingCanvasImage != null) screen.dropImage(mouseX, mouseY);
            else if (screen.movingTask != null) screen.dropTask(mouseX, mouseY);
            else if (screen.movingReward != null) screen.dropReward(mouseX, mouseY);
            else if (screen.movingSidebarGroup != null) screen.dropSidebarGroup(mouseX, mouseY);
            else if (screen.movingSidebarChapter != null) screen.dropSidebarChapter(mouseX, mouseY);
            QuestScreen.playClickSound();
        }
        screen.movingQuest = null;
        screen.movingCanvasText = null;
        screen.movingCanvasImage = null;
        screen.movingTask = null;
        screen.movingReward = null;
        screen.movingSidebarGroup = null;
        screen.movingSidebarChapter = null;
        return true;
    }
}