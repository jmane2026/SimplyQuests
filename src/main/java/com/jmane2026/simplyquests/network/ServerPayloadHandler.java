package com.jmane2026.simplyquests.network;

import com.jmane2026.simplyquests.data.QuestChapter;
import com.jmane2026.simplyquests.data.QuestGroup;
import com.jmane2026.simplyquests.data.QuestManager;
import com.jmane2026.simplyquests.events.QuestServerEvents;
import com.jmane2026.simplyquests.quest.Quest;
import com.jmane2026.simplyquests.quest.QuestTask;
import com.jmane2026.simplyquests.registry.QuestAttachmentRegistry;
import com.jmane2026.simplyquests.player.PlayerQuestProgress;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.ArrayList;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class ServerPayloadHandler {

    private static boolean isOp(IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            NameAndId identity = new NameAndId(player.getGameProfile().id(), player.getGameProfile().name());
            return player.level().getServer().getPlayerList().isOp(identity);
        }
        return false;
    }

    public static void handleSaveChapter(final SaveChapterPayload payload, final IPayloadContext context) {
        if (!isOp(context)) return;

        context.enqueueWork(() -> {
            String chName = payload.chapter().getName();
            // Sanitize: Replace any character that is NOT a-z, 0-9, /, ., _, or - with an underscore
            Identifier chId = Identifier.fromNamespaceAndPath("simplyquests", chName.toLowerCase().replaceAll("[^a-z0-9/._-]", "_"));
            
            // FIX: Explicitly wipe progress for quests removed in this update BEFORE saving the new chapter.
            // This prevents recreated quests from inheriting completion status from deleted versions.
            QuestChapter oldChapter = QuestServerEvents.getQuestManager().getChapters().get(chId);
            if (oldChapter != null) {
                Set<String> newQuestIds = payload.chapter().getQuests().stream().map(Quest::getId).collect(Collectors.toSet());
                for (Quest q : oldChapter.getQuests()) {
                    // If the quest ID is no longer in the chapter, it was deleted
                    if (q.getId() != null && !newQuestIds.contains(q.getId())) {
                        // FIX: Use level().getServer() for reliable server access and PLAYER_PROGRESS.get() for the attachment
                        var server = context.player().level().getServer();
                        if (server != null) server.getPlayerList().getPlayers().forEach(player -> {
                            PlayerQuestProgress progress = player.getData(QuestAttachmentRegistry.PLAYER_PROGRESS.get());
                            progress.getCompletedQuests().remove(q.getId());
                            for (QuestTask task : q.getTasks()) {
                                progress.getTaskProgressMap().remove(task.getId());
                            }
                        });
                    }
                }
            }

            QuestServerEvents.getQuestManager().saveChapter(chId, payload.chapter());
            QuestServerEvents.getQuestManager().updateChapterInMemory(chId, payload.chapter());
            broadcastFullSync();
            QuestServerEvents.refreshAllCaches(((ServerPlayer) context.player()).level().getServer());
        });
    }

    public static void handleSaveGroups(final SaveGroupsPayload payload, final IPayloadContext context) {
        if (!isOp(context)) return;

        context.enqueueWork(() -> {
            QuestServerEvents.getQuestManager().saveGroups(payload.groups(), payload.rootChapters());
            broadcastFullSync();
            QuestServerEvents.refreshAllCaches(((ServerPlayer) context.player()).level().getServer());
        });
    }

    public static void handleDeleteChapter(final DeleteChapterPayload payload, final IPayloadContext context) {
        if (!isOp(context)) return;

        context.enqueueWork(() -> {
            // Sanitize name for deletion to match the saved file format
            Identifier chId = Identifier.fromNamespaceAndPath("simplyquests", payload.chapterName().toLowerCase().replaceAll("[^a-z0-9/._-]", "_"));
            
            // Wipe progress for all quests in the deleted chapter for all players
            QuestChapter chapter = QuestServerEvents.getQuestManager().getChapters().get(chId);
            if (chapter != null && context.player().level().getServer() != null) {
                var server = context.player().level().getServer();
                server.getPlayerList().getPlayers().forEach(player -> {
                    // FIX: Use the correct Supplier constant PLAYER_PROGRESS.get()
                    PlayerQuestProgress progress = player.getData(QuestAttachmentRegistry.PLAYER_PROGRESS.get());
                    for (Quest q : chapter.getQuests()) {
                        progress.getCompletedQuests().remove(q.getId());
                        for (QuestTask task : q.getTasks()) progress.getTaskProgressMap().remove(task.getId());
                    }
                    progress.getCompletedChapters().remove(chapter.getName());
                });
            }

            QuestServerEvents.getQuestManager().deleteChapterFile(chId);
            broadcastFullSync();
            QuestServerEvents.refreshAllCaches(((ServerPlayer) context.player()).level().getServer());
        });
    }

    public static void handleDeleteGroup(final DeleteGroupPayload payload, final IPayloadContext context) {
        if (!isOp(context)) return;

        context.enqueueWork(() -> {
            // The screen already requests deletion for individual chapters,
            // the server just needs to ensure groups.json is updated if needed.
            // Currently, saveGroups handles the manifest update.
            broadcastFullSync();
        });
    }

    public static void handleUploadImage(final UploadImagePayload payload, final IPayloadContext context) {
        if (!isOp(context)) return;

        context.enqueueWork(() -> {
            // Security Check: If somehow the client sent a non-png, reject and notify
            if (!payload.fileName().toLowerCase().endsWith(".png")) {
                context.reply(new SimpleErrorPayload("Upload rejected: Only .png files are allowed."));
                return;
            }

            try {
                Path dir = QuestManager.getImagesDirectory();
                Path target = dir.resolve(payload.fileName());
                Files.write(target, payload.data());
                // Note: We don't broadcast the image here; clients will request it via RequestImagePayload when they see the ID in the chapter file
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    public static void handleDeleteImage(final DeleteImagePayload payload, final IPayloadContext context) {
        if (!isOp(context)) return;

        context.enqueueWork(() -> {
            try {
                Path dir = QuestManager.getImagesDirectory();
                Path target = dir.resolve(payload.imageId());
                Files.deleteIfExists(target);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    private static void broadcastFullSync() {
        var manager = QuestServerEvents.getQuestManager();

        // FIX: Create snapshots (copies) of the collections.
        // This prevents ConcurrentModificationException by ensuring the network thread
        // has its own stable copy of the data that won't change during encoding.
        List<QuestChapter> chaptersSnapshot = new ArrayList<>(manager.getChapters().values());
        List<QuestGroup> groupsSnapshot = new ArrayList<>(manager.getGroups());

        PacketDistributor.sendToAllPlayers(new SyncQuestTreePayload(chaptersSnapshot, groupsSnapshot));
    }
}