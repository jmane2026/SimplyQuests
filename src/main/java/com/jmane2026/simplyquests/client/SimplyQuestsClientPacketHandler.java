package com.jmane2026.simplyquests.client;

import com.jmane2026.simplyquests.client.screen.QuestScreen;
import com.jmane2026.simplyquests.data.QuestChapter;
import com.jmane2026.simplyquests.data.QuestGroup;
import com.jmane2026.simplyquests.events.QuestServerEvents;
import com.jmane2026.simplyquests.network.*;
import com.jmane2026.simplyquests.quest.CanvasText;
import com.jmane2026.simplyquests.quest.Quest;
import com.jmane2026.simplyquests.quest.QuestCanvasImage;
import com.jmane2026.simplyquests.util.QuestClientData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Dedicated handler for packets received by the client.
 * This class is never loaded on a dedicated server to prevent ClassNotFoundExceptions.
 */
public class SimplyQuestsClientPacketHandler {

    public static final Set<String> CLIENT_COMPLETED_QUESTS = new HashSet<>();
    public static final Map<String, Integer> CLIENT_TASK_PROGRESS = new ConcurrentHashMap<>();
    public static final Set<String> CLIENT_CLAIMED_REWARDS = new HashSet<>();
    public static boolean IS_CLIENT_OP = false;
    public static boolean IS_EDIT_MODE_ALLOWED = false;

    public static boolean NEEDS_REFRESH = false;

    public static void handleSimpleError(final SimpleErrorPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            // FIX: Use context.player() (Common) instead of Minecraft.getInstance() (Client-only)
            if (context.player() != null) {
                context.player().sendSystemMessage(Component.literal("§c" + payload.message()));
            }
        });
    }

    public static void handleQuestCompleted(final QuestCompletedPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            mc.getToastManager().addToast(new QuestToast(payload.title(), payload.icon()));
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_TOAST_IN, 1.0F));
        });
    }

    public static void handleChapterCompleted(final ChapterCompletedPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            mc.getToastManager().addToast(new ChapterToast(payload.icon()));
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.0F));
        });
    }

    public static void handleSyncQuestTree(final SyncQuestTreePayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            var manager = QuestServerEvents.getQuestManager();
            String myName = Minecraft.getInstance().player.getName().getString();
            
            // 1. Update the Manager (which handles sanitization internally now)
            manager.setChaptersFromList(payload.chapters());
            manager.setGroups(payload.groups());

            if (Minecraft.getInstance().screen instanceof QuestScreen screen) {
                // DEBUG: Log the arrival of new data
                int totalQuests = payload.chapters().stream().mapToInt(c -> c.getQuests().size()).sum();

                // FIX: Surgically update the screen's local data pointers without calling init()
                // This ensures that even if a refresh is deferred, the screen is using the LATEST objects.
                screen.allQuests.clear();
                screen.questLookup.clear();
                for (QuestChapter newChapter : payload.chapters()) {
                    for (Quest newQuest : newChapter.getQuests()) {
                        screen.allQuests.add(newQuest);
                        screen.questLookup.put(newQuest.getId(), newQuest);

                        // FIX 1: Lock Loopback Protection
                        // If the server says unlocked, but we are mid-edit, keep the local lock red.
                        if (newQuest.getLockedBy().isEmpty() && screen.originalQuest != null && screen.originalQuest.getId().equals(newQuest.getId())) {
                            if (screen.isEditorOpen || screen.isTaskEditorOpen || screen.isRewardEditorOpen) {
                                newQuest.setLockedBy(myName);
                            }
                        }
                    }
                }

                // 2. Images & Texts
                screen.allCanvasImages.clear();
                screen.allCanvasTexts.clear();
                for (QuestChapter newChapter : payload.chapters()) {
                    for (QuestCanvasImage img : newChapter.getCanvasImages()) {
                        img.setChapterName(newChapter.getName());
                        screen.allCanvasImages.add(img);
                    }
                    for (CanvasText txt : newChapter.getCanvasTexts()) {
                        txt.setChapterName(newChapter.getName());
                        screen.allCanvasTexts.add(txt);
                    }
                }

                // FIX: Update the "Editor" reference to point to the fresh server instance.
                // This ensures that when you hit 'Save', you are modifying the object currently in the registry.
                if (screen.originalQuest != null) {
                    Quest updatedOriginal = screen.questLookup.get(screen.originalQuest.getId());
                    if (updatedOriginal != null) screen.originalQuest = updatedOriginal;
                }

                // 3. Hot-Swap active pointers to the fresh server instances
                // This ensures that if you are currently editing or looking at something,
                // your local variables point to the newest data immediately.
                if (screen.selectedQuest != null) {
                    Quest updated = screen.questLookup.get(screen.selectedQuest.getId());
                    if (updated != null) {
                        screen.selectedQuest = updated;
                    }

                    if (screen.selectedCanvasImage != null) {
                        String currentId = screen.selectedCanvasImage.getId();
                        screen.selectedCanvasImage = screen.allCanvasImages.stream()
                                .filter(i -> i.getId().equals(currentId))
                                .findFirst().orElse(screen.selectedCanvasImage);

                        // Update moving image reference if currently dragging
                        if (screen.movingCanvasImage != null) screen.movingCanvasImage = screen.selectedCanvasImage;
                    }

                    if (screen.editingCanvasText != null) {
                        String currentText = screen.editingCanvasText.getText();
                        screen.editingCanvasText = screen.allCanvasTexts.stream()
                                .filter(t -> t.getText().equals(currentText))
                                .findFirst().orElse(screen.editingCanvasText);

                        if (screen.movingCanvasText != null) screen.movingCanvasText = screen.editingCanvasText;
                    }
                }

                if (screen.isEditorOpen || screen.isTaskEditorOpen || screen.isRewardEditorOpen || screen.isTextEditorOpen || screen.isSidebarEditing()) {
                    NEEDS_REFRESH = true;
                } else {
                    screen.init(); // Instant refresh for anyone just viewing the canvas
                }
                // 2. Refresh the claimable badge cache
                QuestScreen.updateClaimableCache();
            }
        });
    }

    public static Map<Identifier, QuestChapter> getChapters() { return QuestServerEvents.getQuestManager().getChapters(); }
    public static List<QuestGroup> getGroups() { return QuestServerEvents.getQuestManager().getGroups(); }

    public static void handleSyncOpStatus(final SyncOpStatusPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            IS_CLIENT_OP = payload.isOp();
            IS_EDIT_MODE_ALLOWED = payload.editModeEnabled();

            // FIX: If in Single Player, persist the server's command state to our local data
            if (Minecraft.getInstance().getSingleplayerServer() != null) {
                QuestClientData.setEditModeEnabled(payload.editModeEnabled());
            }

            // If the player has the screen open, force a refresh so edit buttons appear/disappear instantly
            if (Minecraft.getInstance().screen instanceof QuestScreen screen) {
                screen.init();
            }
        });
    }

    public static void handleSyncProgress(SyncQuestProgressPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            CLIENT_TASK_PROGRESS.put(payload.taskId(), payload.currentAmount());
            if (Minecraft.getInstance().screen instanceof QuestScreen screen) {
                screen.refreshTaskProgress(payload.questId(), payload.taskId(), payload.currentAmount(), payload.state());
            }
        });
    }

    public static void handleSyncPlayerProgress(SyncPlayerProgressPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            CLIENT_COMPLETED_QUESTS.clear();
            CLIENT_COMPLETED_QUESTS.addAll(payload.completedQuests());
            CLIENT_TASK_PROGRESS.clear();
            CLIENT_TASK_PROGRESS.putAll(payload.taskProgress());
            CLIENT_CLAIMED_REWARDS.clear();
            CLIENT_CLAIMED_REWARDS.addAll(payload.claimedRewards());

            if (Minecraft.getInstance().screen instanceof QuestScreen screen) {
                screen.refreshGlobalProgress();
            }
        });
    }

    public static void handleSyncChapter(SyncChapterPayload payload, IPayloadContext context) {}

    public static void handleSyncImage(SyncImagePayload payload, IPayloadContext context) {
        QuestScreen.loadTextureFromFile(payload.imageId(), payload.data());
    }
}