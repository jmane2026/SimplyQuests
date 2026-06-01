package com.jmane2026.simplyquests.client;

import com.jmane2026.simplyquests.client.screen.QuestScreen;
import com.jmane2026.simplyquests.data.QuestChapter;
import com.jmane2026.simplyquests.data.QuestGroup;
import com.jmane2026.simplyquests.events.QuestServerEvents;
import com.jmane2026.simplyquests.network.*;
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
            if (Minecraft.getInstance().player != null) {
                // Show red error text in the action bar
                Minecraft.getInstance().player.sendSystemMessage(Component.literal("§c" + payload.message()));
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
            
            // 1. Update the Manager (which handles sanitization internally now)
            manager.setChaptersFromList(payload.chapters());
            manager.setGroups(payload.groups());

            if (Minecraft.getInstance().screen instanceof QuestScreen screen) {
                // FIX: If an editor is open, do NOT call init(). Flag it for later.
                // This prevents "orphaned references" and sync-drift while editing.
                if (screen.isEditorOpen || screen.isTaskEditorOpen || screen.isRewardEditorOpen || screen.isTextEditorOpen) {
                    NEEDS_REFRESH = true;
                    return;
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