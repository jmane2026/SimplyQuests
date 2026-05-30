package com.jmane2026.simplyquests.client;

import com.jmane2026.simplyquests.events.QuestServerEvents;
import com.jmane2026.simplyquests.network.*;
import com.jmane2026.simplyquests.client.screen.QuestScreen;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.io.File;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class ClientPacketHandler {
    public static boolean IS_CLIENT_OP = false;

    public static final Set<String> CLIENT_COMPLETED_QUESTS = new HashSet<>();
    public static final Map<String, Integer> CLIENT_TASK_PROGRESS = new HashMap<>();
    public static final Set<String> CLIENT_CLAIMED_REWARDS = new HashSet<>();

    public static void handleSyncProgress(final SyncQuestProgressPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (Minecraft.getInstance().screen instanceof QuestScreen screen) {
                screen.refreshTaskProgress(payload.questId(), payload.taskId(), payload.currentAmount(), payload.state());
            }
        });
    }

    public static void handleSyncPlayerProgress(final SyncPlayerProgressPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            // Update local cache from the server's NBT data
            CLIENT_COMPLETED_QUESTS.clear();
            CLIENT_COMPLETED_QUESTS.addAll(payload.completedQuests());

            CLIENT_TASK_PROGRESS.clear();
            CLIENT_TASK_PROGRESS.putAll(payload.taskProgress());

            CLIENT_CLAIMED_REWARDS.clear();
            CLIENT_CLAIMED_REWARDS.addAll(payload.claimedRewards());

            // Refresh UI if open
            if (Minecraft.getInstance().screen instanceof QuestScreen screen) {
                screen.refreshGlobalProgress();
            }
        });
    }

    public static void handleQuestCompleted(final QuestCompletedPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            Minecraft.getInstance().getToastManager().addToast(new QuestToast(payload.title(), payload.icon()));
        });
    }

    public static void handleSyncChapter(final SyncChapterPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            // Update the client-side quest manager directly
            QuestServerEvents.getQuestManager().updateChapterInMemory(payload.id(), payload.chapter());

            // If the player has the quest screen open, we need to refresh it
            if (Minecraft.getInstance().screen instanceof QuestScreen screen) {
                screen.init();
            }
        });
    }

    public static void handleSyncImage(final SyncImagePayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            // Save to local cache folder
            try {
                File cacheDir = Minecraft.getInstance().gameDirectory.toPath().resolve("simplyquests_cache").toFile();
                if (!cacheDir.exists()) cacheDir.mkdirs();
                File file = new File(cacheDir, payload.imageId());
                Files.write(file.toPath(), payload.data());
                
                // Register texture immediately
                QuestScreen.loadTextureFromFile(payload.imageId(), payload.data());
            } catch (Exception ignored) {}
        });
    }

    public static void handleSyncQuestTree(final SyncQuestTreePayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            var manager = QuestServerEvents.getQuestManager();
            if (manager != null) {
                manager.setChaptersFromList(payload.chapters());
                manager.setGroups(payload.groups());

                // If the QuestScreen is currently open, we need to refresh its data
                if (Minecraft.getInstance().screen instanceof QuestScreen screen) {
                    screen.init(); // Re-running init rebuilds sidebar and allQuests list from the new manager data
                }
            }
        });
    }

    public static void handleSyncOpStatus(final SyncOpStatusPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            IS_CLIENT_OP = payload.isOp();
        });
    }
}